'use strict';

// ============================================================================
// subdomain-listener.js
// Standalone process. Does NOT require or modify index.js in any way.
// Talks to the existing relay only through its already-exposed, read-only
// GET /status endpoint on CONTROL_PORT (8080) — the same endpoint the app
// already polls. No changes to userPortMap, activeTunnels, or any locked
// logic in index.js are needed for this to work.
//
// Purpose: let Minecraft Java clients connect via
//   <subdomain>.as.pocketcraft.online   or   <subdomain>.eu.pocketcraft.online
// on the standard port 25565, get routed to whichever port index.js already
// assigned that user, with zero changes to the locked relay file.
// ============================================================================

const net = require('net');
const http = require('http');
const fs = require('fs');
const path = require('path');
const admin = require('firebase-admin');
const { resolveSubdomainRoute } = require('./hostname-router');

const LISTEN_PORT = Number(process.env.SUBDOMAIN_LISTEN_PORT || 25565);
const CONTROL_API_BASE = process.env.CONTROL_API_BASE || 'http://127.0.0.1:8080';
const BASE_DOMAIN = process.env.SUBDOMAIN_BASE_DOMAIN || 'pocketcraft.online';
const HANDSHAKE_TIMEOUT_MS = 5_000;
const MAX_HANDSHAKE_BYTES = 1024;
const BACKEND_CONNECT_TIMEOUT_MS = 5_000;

// --- Firebase Admin init (separate credential from the app/functions side) ---
// Supports either FIREBASE_SERVICE_ACCOUNT_JSON or a colocated service-account.json
// next to this script so the listener can survive simpler relay deployments.
if (!admin.apps.length) {
  const raw = process.env.FIREBASE_SERVICE_ACCOUNT_JSON;
  const credentialPath = path.join(__dirname, 'service-account.json');
  const fallbackRaw = fs.existsSync(credentialPath)
    ? fs.readFileSync(credentialPath, 'utf8')
    : '';
  const credentialJson = raw || fallbackRaw;
  if (!credentialJson) {
    console.error('[subdomain-listener] No Firebase service account found. Set FIREBASE_SERVICE_ACCOUNT_JSON or deploy service-account.json.');
    process.exit(1);
  }
  admin.initializeApp({
    credential: admin.credential.cert(JSON.parse(credentialJson))
  });
}
const firestore = admin.firestore();

// --- Minecraft handshake parsing (VarInt-based, modern protocol only) ---

function readVarInt(buf, offset) {
  let result = 0;
  let shift = 0;
  let pos = offset;
  while (true) {
    if (pos >= buf.length) return null;
    const byte = buf[pos];
    result |= (byte & 0x7f) << shift;
    pos++;
    if ((byte & 0x80) === 0) break;
    shift += 7;
    if (shift > 35) return { invalid: true };
  }
  return { value: result, bytesRead: pos - offset };
}

// Returns:
//   { status: 'incomplete' }                            - need more bytes, keep buffering
//   { status: 'invalid' }                               - not a parseable handshake, drop
//   { status: 'ok', hostname, nextState, totalLength }  - parsed; totalLength = bytes to replay
function tryParseHandshake(buf) {
  const lenRes = readVarInt(buf, 0);
  if (!lenRes) return { status: 'incomplete' };
  if (lenRes.invalid) return { status: 'invalid' };
  const packetLength = lenRes.value;
  const packetStart = lenRes.bytesRead;
  if (packetLength <= 0 || packetLength > MAX_HANDSHAKE_BYTES) return { status: 'invalid' };
  if (buf.length < packetStart + packetLength) return { status: 'incomplete' };

  let offset = packetStart;

  const idRes = readVarInt(buf, offset);
  if (!idRes || idRes.invalid) return { status: 'invalid' };
  if (idRes.value !== 0x00) return { status: 'invalid' };
  offset += idRes.bytesRead;

  const protoRes = readVarInt(buf, offset);
  if (!protoRes || protoRes.invalid) return { status: 'invalid' };
  offset += protoRes.bytesRead;

  const strLenRes = readVarInt(buf, offset);
  if (!strLenRes || strLenRes.invalid) return { status: 'invalid' };
  offset += strLenRes.bytesRead;
  const strLen = strLenRes.value;
  if (strLen < 0 || strLen > 255 || offset + strLen > packetStart + packetLength) {
    return { status: 'invalid' };
  }

  const hostname = buf.toString('utf8', offset, offset + strLen);
  offset += strLen;

  if (offset + 2 > packetStart + packetLength) return { status: 'invalid' };
  offset += 2;

  const nextStateRes = readVarInt(buf, offset);
  if (!nextStateRes || nextStateRes.invalid) return { status: 'invalid' };
  const nextState = nextStateRes.value;

  return { status: 'ok', hostname, nextState, totalLength: packetStart + packetLength };
}

// --- Relay status lookup (read-only, existing endpoint) ---

function fetchRelayStatus() {
  return new Promise((resolve, reject) => {
    const req = http.get(`${CONTROL_API_BASE}/status`, (res) => {
      let data = '';
      res.on('data', (chunk) => { data += chunk; });
      res.on('end', () => {
        try {
          resolve(JSON.parse(data));
        } catch (err) {
          reject(err);
        }
      });
    });
    req.on('error', reject);
    req.setTimeout(BACKEND_CONNECT_TIMEOUT_MS, () => req.destroy(new Error('status request timed out')));
  });
}

// --- Minecraft SLP status response builders ---

function readPacketVarInt(buf, offset = 0) {
  let val = 0;
  let shift = 0;
  for (let i = 0; i < 5; i++) {
    if (offset + i >= buf.length) return null;
    const byte = buf[offset + i];
    val |= (byte & 0x7f) << shift;
    if ((byte & 0x80) === 0) {
      return { value: val, size: i + 1 };
    }
    shift += 7;
  }
  return null;
}

function readPacket(buf, offset = 0) {
  const lengthVarInt = readPacketVarInt(buf, offset);
  if (!lengthVarInt) return null;
  const payloadStart = offset + lengthVarInt.size;
  const payloadEnd = payloadStart + lengthVarInt.value;
  if (payloadEnd > buf.length) return null;
  const packetIdVarInt = readPacketVarInt(buf, payloadStart);
  if (!packetIdVarInt) return null;
  return {
    packetId: packetIdVarInt.value,
    payload: buf.subarray(payloadStart + packetIdVarInt.size, payloadEnd),
    nextOffset: payloadEnd
  };
}

function varIntBuffer(val) {
  const bytes = [];
  while (true) {
    let byte = val & 0x7f;
    val >>>= 7;
    if (val !== 0) byte |= 0x80;
    bytes.push(byte);
    if (val === 0) break;
  }
  return Buffer.from(bytes);
}

function wrapPacket(packetId, payload = Buffer.alloc(0)) {
  const packetIdBuf = varIntBuffer(packetId);
  const dataLenBuf = varIntBuffer(packetIdBuf.length + payload.length);
  return Buffer.concat([dataLenBuf, packetIdBuf, payload]);
}

function createCustomJavaSLPResponse(motd) {
  const responseObj = {
    version: { name: 'PocketCraft', protocol: 774 },
    players: { max: 0, online: 0, sample: [] },
    description: { text: motd }
  };
  const jsonStr = JSON.stringify(responseObj);
  const jsonBuf = Buffer.from(jsonStr, 'utf8');
  const jsonLen = varIntBuffer(jsonBuf.length);
  return wrapPacket(0x00, Buffer.concat([jsonLen, jsonBuf]));
}

function createJavaPongResponse(payload) {
  return wrapPacket(0x01, payload);
}

function handleOfflineStatusPackets(socket, motd, initialBuffer) {
  let buffer = Buffer.from(initialBuffer || Buffer.alloc(0));
  let respondedToStatus = false;
  let closed = false;
  const closeSoon = (delayMs = 100) => {
    if (closed) return;
    closed = true;
    setTimeout(() => { if (!socket.destroyed) socket.end(); }, delayMs);
  };
  const timeout = setTimeout(() => closeSoon(0), 5000);
  const processBufferedPackets = () => {
    let offset = 0;
    while (offset < buffer.length) {
      const packet = readPacket(buffer, offset);
      if (!packet) break;
      offset = packet.nextOffset;
      if (packet.packetId === 0x00 && !respondedToStatus) {
        respondedToStatus = true;
        socket.write(createCustomJavaSLPResponse(motd));
        continue;
      }
      if (packet.packetId === 0x01 && packet.payload.length >= 8) {
        socket.write(createJavaPongResponse(packet.payload.subarray(0, 8)));
        clearTimeout(timeout);
        closeSoon(100);
        continue;
      }
    }
    if (offset > 0) buffer = buffer.subarray(offset);
  };
  socket.on('data', (chunk) => {
    if (!chunk || chunk.length === 0) return;
    buffer = Buffer.concat([buffer, chunk]);
    processBufferedPackets();
  });
  socket.on('close', () => clearTimeout(timeout));
  socket.on('error', () => clearTimeout(timeout));
  processBufferedPackets();
}

function respondError(playerSocket, nextState, remainingBytes, message) {
  if (nextState === 1) {
    handleOfflineStatusPackets(playerSocket, message, remainingBytes);
  } else {
    playerSocket.destroy();
  }
}

// --- Routing ---

async function routeConnection(playerSocket, bufferedBytes, hostname, nextState, remainingBytes) {
  let result;
  try {
    result = await resolveSubdomainRoute({ hostname, firestore, baseDomain: BASE_DOMAIN });
  } catch (err) {
    console.error(`[subdomain-listener] Firestore lookup failed for ${hostname}:`, err.message);
    respondError(playerSocket, nextState, remainingBytes, `§c[PocketCraft] Database error.`);
    return;
  }

  if (!result.ok) {
    console.warn(`[subdomain-listener] Rejecting ${hostname}: ${result.code}`);
    const msg = (result.code === 'invalid-hostname')
      ? `§c[PocketCraft] Please use your custom subdomain!`
      : `§c[PocketCraft] Subdomain not found: ${hostname}`;
    respondError(playerSocket, nextState, remainingBytes, msg);
    return;
  }

  const ownerId = result.route.ownerId;
  if (!ownerId) {
    console.warn(`[subdomain-listener] No ownerId for subdomain ${result.route.subdomain}`);
    respondError(playerSocket, nextState, remainingBytes, `§c[PocketCraft] Subdomain has no owner.`);
    return;
  }

  let status;
  try {
    status = await fetchRelayStatus();
  } catch (err) {
    console.error('[subdomain-listener] Failed to fetch relay status:', err.message);
    respondError(playerSocket, nextState, remainingBytes, `§c[PocketCraft] Relay status error.`);
    return;
  }

  const entry = status[ownerId];
  if (!entry || !entry.port) {
    console.warn(`[subdomain-listener] No active tunnel for owner ${ownerId} (subdomain ${result.route.subdomain})`);
    respondError(playerSocket, nextState, remainingBytes, `§c[PocketCraft] Server is offline (not started in app).`);
    return;
  }

  const backendSocket = net.connect({ host: '127.0.0.1', port: entry.port, timeout: BACKEND_CONNECT_TIMEOUT_MS });
  playerSocket.setNoDelay(true);
  playerSocket.setKeepAlive(true, 10000);
  if (typeof playerSocket.setSendBufferSize === 'function') {
    try { playerSocket.setSendBufferSize(64 * 1024); } catch (e) {}
  }
  if (typeof playerSocket.setRecvBufferSize === 'function') {
    try { playerSocket.setRecvBufferSize(64 * 1024); } catch (e) {}
  }

  const cleanup = () => {
    if (!playerSocket.destroyed) playerSocket.destroy();
    if (!backendSocket.destroyed) backendSocket.destroy();
  };

  backendSocket.once('connect', () => {
    backendSocket.setNoDelay(true);
    backendSocket.setKeepAlive(true, 10000);
    if (typeof backendSocket.setSendBufferSize === 'function') {
      try { backendSocket.setSendBufferSize(64 * 1024); } catch (e) {}
    }
    if (typeof backendSocket.setRecvBufferSize === 'function') {
      try { backendSocket.setRecvBufferSize(64 * 1024); } catch (e) {}
    }
    backendSocket.write(bufferedBytes);
    if (remainingBytes && remainingBytes.length > 0) {
      backendSocket.write(remainingBytes);
    }
    playerSocket.pipe(backendSocket);
    backendSocket.pipe(playerSocket);
    console.log(`[subdomain-listener] Routed ${hostname} -> 127.0.0.1:${entry.port} (owner=${ownerId})`);
  });

  backendSocket.on('error', (err) => {
    console.warn(`[subdomain-listener] Backend connect error for ${hostname}:`, err.message);
    cleanup();
  });
  backendSocket.on('timeout', () => {
    console.warn(`[subdomain-listener] Backend connect timeout for ${hostname}`);
    cleanup();
  });
  playerSocket.on('error', cleanup);
  playerSocket.on('close', cleanup);
  backendSocket.on('close', cleanup);
}

// --- TCP listener ---

const server = net.createServer((playerSocket) => {
  playerSocket.setNoDelay(true);
  playerSocket.setKeepAlive(true, 10000);
  if (typeof playerSocket.setSendBufferSize === 'function') {
    try { playerSocket.setSendBufferSize(64 * 1024); } catch (e) {}
  }
  if (typeof playerSocket.setRecvBufferSize === 'function') {
    try { playerSocket.setRecvBufferSize(64 * 1024); } catch (e) {}
  }
  let buffer = Buffer.alloc(0);
  let settled = false;

  const timeout = setTimeout(() => {
    if (!settled) {
      settled = true;
      playerSocket.destroy();
    }
  }, HANDSHAKE_TIMEOUT_MS);

  const onData = (chunk) => {
    if (settled) return;
    buffer = Buffer.concat([buffer, chunk]);

    if (buffer.length > MAX_HANDSHAKE_BYTES) {
      settled = true;
      clearTimeout(timeout);
      playerSocket.destroy();
      return;
    }

    const parsed = tryParseHandshake(buffer);
    if (parsed.status === 'incomplete') return;

    playerSocket.removeListener('data', onData);
    clearTimeout(timeout);

    if (parsed.status !== 'ok') {
      settled = true;
      playerSocket.destroy();
      return;
    }

    settled = true;
    routeConnection(
      playerSocket,
      buffer.subarray(0, parsed.totalLength),
      parsed.hostname,
      parsed.nextState,
      buffer.subarray(parsed.totalLength)
    ).catch((err) => {
      console.error('[subdomain-listener] Unexpected routing error:', err.message);
      if (!playerSocket.destroyed) playerSocket.destroy();
    });
  };

  playerSocket.on('data', onData);
  playerSocket.on('error', () => clearTimeout(timeout));
});

server.on('error', (err) => {
  console.error('[subdomain-listener] Server error:', err.message);
});

server.listen(LISTEN_PORT, '0.0.0.0', () => {
  console.log(`[subdomain-listener] Listening on port ${LISTEN_PORT}, control API at ${CONTROL_API_BASE}`);
});

process.on('SIGTERM', () => server.close(() => process.exit(0)));
process.on('SIGINT', () => server.close(() => process.exit(0)));

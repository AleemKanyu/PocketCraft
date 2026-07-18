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
const { resolveSubdomainRoute, parseRegionalHostname } = require('./hostname-router');
const { createJavaSLPResponseDirect, createJavaPongResponse, readPacket } = require('./java-ping');

const LISTEN_PORT = Number(process.env.SUBDOMAIN_LISTEN_PORT || 25565);
const CONTROL_API_BASE = process.env.CONTROL_API_BASE || 'http://127.0.0.1:8080';
const BASE_DOMAIN = process.env.SUBDOMAIN_BASE_DOMAIN || 'pocketcraft.online';
const HANDSHAKE_TIMEOUT_MS = 3_000;
const MAX_HANDSHAKE_BYTES = 1024;
const BACKEND_CONNECT_TIMEOUT_MS = 3_000;
// Reduced from 128KB: smaller HWM fires data events sooner, lowering Java player ping.
const STREAM_HIGH_WATER_MARK = 16 * 1024; // Match index.js

// Short-lived cache for relay /status responses.
// Prevents a burst of simultaneous joins from all making individual HTTP calls.
const STATUS_CACHE_TTL_MS = 5_000;
let _cachedStatus = null;
let _cachedStatusAt = 0;

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
//   { status: 'incomplete' }                - need more bytes, keep buffering
//   { status: 'invalid' }                   - not a parseable handshake, drop
//   { status: 'ok', hostname, totalLength } - parsed; totalLength = bytes to replay
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
  offset += 2; // skip port

  const nextStateRes = readVarInt(buf, offset);
  if (!nextStateRes || nextStateRes.invalid) return { status: 'invalid' };
  const nextState = nextStateRes.value;

  return { status: 'ok', hostname, nextState, totalLength: packetStart + packetLength };
}

// --- Relay status lookup (read-only, existing endpoint) ---

function fetchRelayStatusFromNetwork() {
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

async function fetchRelayStatus() {
  const now = Date.now();
  if (_cachedStatus && (now - _cachedStatusAt) < STATUS_CACHE_TTL_MS) {
    return _cachedStatus;
  }
  const status = await fetchRelayStatusFromNetwork();
  _cachedStatus = status;
  _cachedStatusAt = Date.now();
  return status;
}

// Backpressure-aware pipe — mirrors wireBackpressure() in index.js.
// Raw .pipe() doesn't respect TCP backpressure, causing buffer bloat and
// elevated RTT when chunk throughput is high (e.g. during chunk loading).
function wirePipe(src, dst) {
  const onData = (chunk) => {
    if (dst.destroyed) return;
    const ok = dst.write(chunk);
    if (!ok) src.pause();
  };
  const onDrain = () => {
    if (!src.destroyed) src.resume();
  };
  src.on('data', onData);
  dst.on('drain', onDrain);
  return () => {
    src.removeListener('data', onData);
    dst.removeListener('drain', onDrain);
  };
}

// --- Routing ---

async function routeConnection(playerSocket, fullBuffer, handshakeLength, hostname) {
  const bufferedBytes = fullBuffer.subarray(0, handshakeLength);
  const extraBytes = fullBuffer.subarray(handshakeLength);
  let result;
  try {
    result = await resolveSubdomainRoute({ hostname, firestore, baseDomain: BASE_DOMAIN });
  } catch (err) {
    console.error(`[subdomain-listener] Firestore lookup failed for ${hostname}:`, err.message);
    const parsed = parseRegionalHostname(hostname, BASE_DOMAIN);
    if (parsed && parsed.subdomain) {
      console.warn(`[subdomain-listener] FALLBACK: Routing connection to subdomain "${parsed.subdomain}" directly as ownerId`);
      result = {
        ok: true,
        route: {
          subdomain: parsed.subdomain,
          region: parsed.region,
          ownerId: parsed.subdomain,
        }
      };
    } else {
      playerSocket.destroy();
      return;
    }
  }

  if (!result.ok) {
    console.warn(`[subdomain-listener] Rejecting ${hostname}: ${result.code}`);
    playerSocket.destroy();
    return;
  }

  const ownerId = result.route.ownerId;
  if (!ownerId) {
    console.warn(`[subdomain-listener] No ownerId for subdomain ${result.route.subdomain}`);
    playerSocket.destroy();
    return;
  }

  let status;
  try {
    status = await fetchRelayStatus();
  } catch (err) {
    console.error('[subdomain-listener] Failed to fetch relay status:', err.message);
    playerSocket.destroy();
    return;
  }

  const entry = status[ownerId];
  if (!entry || !entry.port) {
    console.warn(`[subdomain-listener] No active tunnel for owner ${ownerId} (subdomain ${result.route.subdomain})`);
    playerSocket.destroy();
    return;
  }

  const backendSocket = net.connect({
    host: '127.0.0.1',
    port: entry.port,
    timeout: BACKEND_CONNECT_TIMEOUT_MS,
    allowHalfOpen: false,
  });
  playerSocket.setNoDelay(true);
  playerSocket.setKeepAlive(true, 10000);

  const cleanup = () => {
    if (!playerSocket.destroyed) playerSocket.destroy();
    if (!backendSocket.destroyed) backendSocket.destroy();
  };

  backendSocket.once('connect', () => {
    backendSocket.setNoDelay(true);
    backendSocket.setKeepAlive(true, 10000);
    // Write the buffered handshake bytes first, then write any extra payload bytes
    // that arrived alongside the handshake, and finally wire bidirectional piping.
    if (!backendSocket.destroyed) {
      backendSocket.write(bufferedBytes);
      if (extraBytes.length > 0) {
        backendSocket.write(extraBytes);
      }
    }
    const unwire1 = wirePipe(playerSocket, backendSocket);
    const unwire2 = wirePipe(backendSocket, playerSocket);
    const fullCleanup = () => { unwire1(); unwire2(); cleanup(); };
    playerSocket.once('close', fullCleanup);
    backendSocket.once('close', fullCleanup);
    playerSocket.resume();
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

// --- TCP listener (separate port, separate process — index.js untouched) ---

function sendSLPAndPong(socket, status, initialBuffer) {
  let buffer = Buffer.from(initialBuffer || Buffer.alloc(0));
  let respondedToStatus = false;
  let closed = false;

  const closeSoon = (delayMs = 100) => {
    if (closed) return;
    closed = true;
    setTimeout(() => {
      if (!socket.destroyed) socket.end();
    }, delayMs);
  };

  const timeout = setTimeout(() => closeSoon(0), 2000);

  const processBufferedPackets = () => {
    let offset = 0;
    while (offset < buffer.length) {
      const packet = readPacket(buffer, offset);
      if (!packet) break;

      offset = packet.nextOffset;

      if (packet.packetId === 0x00 && !respondedToStatus) {
        respondedToStatus = true;
        socket.write(createJavaSLPResponseDirect(status));
        continue;
      }

      if (packet.packetId === 0x01 && packet.payload.length >= 8) {
        socket.write(createJavaPongResponse(packet.payload.subarray(0, 8)));
        clearTimeout(timeout);
        closeSoon(100);
        continue;
      }
    }

    if (offset > 0) {
      buffer = buffer.subarray(offset);
    }
  };

  socket.on('data', (chunk) => {
    if (!chunk || chunk.length === 0) return;
    buffer = Buffer.concat([buffer, chunk]);
    processBufferedPackets();
  });

  socket.on('close', () => clearTimeout(timeout));
  socket.on('error', () => clearTimeout(timeout));

  socket.resume();
  processBufferedPackets();
}

async function handleCloudStatusPing(playerSocket, fullBuffer, handshakeLength, hostname) {
  const remaining = fullBuffer.subarray(handshakeLength);
  let result;
  try {
    result = await resolveSubdomainRoute({ hostname, firestore, baseDomain: BASE_DOMAIN });
  } catch (err) {
    console.error(`[subdomain-listener] Firestore lookup failed for ping ${hostname}:`, err.message);
    const parsed = parseRegionalHostname(hostname, BASE_DOMAIN);
    if (parsed && parsed.subdomain) {
      result = { ok: true, route: { ownerId: parsed.subdomain } };
    } else {
      playerSocket.destroy();
      return;
    }
  }

  if (!result.ok || !result.route.ownerId) {
    playerSocket.destroy();
    return;
  }

  const ownerId = result.route.ownerId;
  let status;
  try {
    status = await fetchRelayStatus();
  } catch (err) {
    console.error('[subdomain-listener] Failed to fetch status for ping:', err.message);
    playerSocket.destroy();
    return;
  }

  const entry = status[ownerId];
  if (!entry) {
    const offlineStatus = {
      version: '1.21.11',
      motd: '§cServer is offline',
      players: 0,
      maxPlayers: 20
    };
    sendSLPAndPong(playerSocket, offlineStatus, remaining);
    return;
  }

  sendSLPAndPong(playerSocket, entry, remaining);
}

const server = net.createServer({
  allowHalfOpen: false,
  highWaterMark: STREAM_HIGH_WATER_MARK,
}, (playerSocket) => {
  playerSocket.setNoDelay(true);
  playerSocket.setKeepAlive(true, 10000);
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
    playerSocket.pause(); // Pause so we do not lose subsequent network chunks while routing async
    clearTimeout(timeout);

    if (parsed.status !== 'ok') {
      settled = true;
      playerSocket.destroy();
      return;
    }

    settled = true;
    if (parsed.nextState === 1) {
      handleCloudStatusPing(playerSocket, buffer, parsed.totalLength, parsed.hostname)
        .catch((err) => {
          console.error('[subdomain-listener] Cloud status ping error:', err.message);
          if (!playerSocket.destroyed) playerSocket.destroy();
        });
      return;
    }

    routeConnection(playerSocket, buffer, parsed.totalLength, parsed.hostname)
      .catch((err) => {
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

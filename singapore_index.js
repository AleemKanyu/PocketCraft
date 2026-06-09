'use strict';

const net     = require('net');
const express = require('express');
const dgram   = require('dgram');
const dns     = require('dns');
const { startBedrockPing, updateServerStatus } = require('./bedrock-ping');
const { handleJavaPing } = require('./java-ping');

// Constants

const CONTROL_PORT               = 8080;
const PHONE_RELAY_PORT           = 9000;
const PUBLIC_HOST                = 'play.pocketcraft.online';
const PORT_POOL_START            = 25500;
const PORT_POOL_END              = 35500;
const PLAYER_WAIT_TIMEOUT_MS     = 30_000;
const SOCKET_KEEPALIVE_MS        = 10_000;
const PHONE_POOL_IDLE_TIMEOUT_MS = 130_000;
const CLEANUP_INTERVAL_MS        = 30_000;
const BEDROCK_CLIENT_TTL_MS      = 60_000;
const MAX_PHONE_POOL_SIZE        = 20;
const MAX_PENDING_PLAYERS        = 50;
const MAX_UDP_PAYLOAD            = 65535;
const REGISTER_RATE_WINDOW_MS    = 60_000;
const REGISTER_RATE_LIMIT        = 10;
const MAX_USER_ID_LENGTH         = 128;
const VALID_USER_ID_RE           = /^[a-zA-Z0-9_-]+$/;
const RELAY_SECRET               = process.env.RELAY_SECRET || '';
const APP_RELAY_SECRET           = 'e7f5fbdda85c265419e519454f8d54643930116b89a1b58dcb2b86f91889d3d3';
const PUBLIC_IP_ENV              = process.env.PUBLIC_IP || '';

let RESOLVED_PUBLIC_IPV4 = null;

// State

const activeTunnels = new Map();
const userPortMap   = new Map();

// Bedrock per-user state

const userUdpSockets   = new Map();
const udpRestartTimers = new Map();
const bedrockClientMap = new Map();

// Socket helpers

function configureSocket(socket) {
  socket.setNoDelay(true);
  socket.setKeepAlive(true, SOCKET_KEEPALIVE_MS);
  socket.allowHalfOpen = false;
}

// userId validation

function parseUserId(raw) {
  const id = String(raw || '').trim();
  if (!id) return null;
  if (id.length > MAX_USER_ID_LENGTH) return null;
  if (!VALID_USER_ID_RE.test(id)) return null;
  return id;
}

// Port assignment

function getOrAssignPort(userId) {
  if (userPortMap.has(userId)) return userPortMap.get(userId);

  let hash = 0;
  for (let i = 0; i < userId.length; i++) {
    hash = ((hash << 5) - hash) + userId.charCodeAt(i);
    hash |= 0;
  }

  const range = PORT_POOL_END - PORT_POOL_START + 1;
  let port = PORT_POOL_START + (Math.abs(hash) % range);

  const usedPorts = new Set([
    ...[...activeTunnels.values()].map((t) => t.port),
    ...userPortMap.values(),
  ]);

  let attempts = 0;
  while (usedPorts.has(port)) {
    port++;
    if (port > PORT_POOL_END) port = PORT_POOL_START;
    if (++attempts >= range) return null;
  }

  userPortMap.set(userId, port);
  return port;
}

// Bedrock framing

const IPV4_RE = /^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$/;

function isValidUdpPort(port) {
  return Number.isInteger(port) && port > 0 && port < 65536;
}

function isValidIPv4(ip) {
  const m = IPV4_RE.exec(String(ip || ''));
  if (!m) return false;
  return [Number(m[1]), Number(m[2]), Number(m[3]), Number(m[4])]
    .every((b) => Number.isInteger(b) && b >= 0 && b <= 255) && ip !== '0.0.0.0';
}

function refreshPublicIpv4() {
  if (isValidIPv4(PUBLIC_IP_ENV)) {
    RESOLVED_PUBLIC_IPV4 = PUBLIC_IP_ENV;
    return;
  }

  if (isValidIPv4(PUBLIC_HOST)) {
    RESOLVED_PUBLIC_IPV4 = PUBLIC_HOST;
    return;
  }

  dns.lookup(PUBLIC_HOST, { family: 4 }, (err, address) => {
    if (err) {
      console.warn(`[bedrock] Failed to resolve PUBLIC_HOST=${PUBLIC_HOST}: ${err.message}`);
      return;
    }
    if (isValidIPv4(address)) {
      RESOLVED_PUBLIC_IPV4 = address;
      console.log(`[bedrock] Resolved public IPv4 ${PUBLIC_HOST} -> ${address}`);
    }
  });
}

function writeRakNetAddress(buf, offset, ip, port) {
  if (!Buffer.isBuffer(buf)) return false;
  if (!isValidIPv4(ip)) return false;
  if (!isValidUdpPort(port)) return false;
  if (offset < 0 || (offset + 7) > buf.length) return false;

  const parts = ip.split('.').map((x) => Number(x));
  buf[offset] = 0x04;
  buf[offset + 1] = 0xFF - parts[0];
  buf[offset + 2] = 0xFF - parts[1];
  buf[offset + 3] = 0xFF - parts[2];
  buf[offset + 4] = 0xFF - parts[3];
  buf.writeUInt16BE(port, offset + 5);
  return true;
}

function isRakNetFrameSet(packetId) {
  return packetId >= 0x80 && packetId <= 0x8d;
}

function encapsulatedHeaderLength(flags, buf, offset) {
  const reliability = (flags & 0xe0) >> 5;
  const hasSplit = (flags & 0x10) !== 0;
  let headerLen = 3;

  const isReliable = (reliability === 2 || reliability === 3 || reliability === 4 || reliability === 6 || reliability === 7);
  const isOrdered = (reliability === 3 || reliability === 7);
  const isSequenced = (reliability === 1 || reliability === 4);

  if (isReliable) {
    headerLen += 3;
  }
  if (isOrdered || isSequenced) {
    headerLen += 4;
  }
  if (hasSplit) {
    headerLen += 10;
  }

  if (offset + headerLen > buf.length) return -1;
  return headerLen;
}

function rewriteOpenConnectionReply2Packet(packet, clientIp, clientPort) {
  // RakNet OpenConnectionReply2:
  // 0x08 | magic(16) | serverGuid(8) | clientAddress(7) | mtu(2) | encryption(1)
  if (!Buffer.isBuffer(packet) || packet.length < 35) return packet;
  if (packet.readUInt8(0) !== 0x08) return packet;

  const out = Buffer.from(packet);
  const addressOffset = 1 + 16 + 8;
  if (out[addressOffset] === 0x04 && writeRakNetAddress(out, addressOffset, clientIp, clientPort)) {
    console.log(`[bedrock] Rewrote OpenConnectionReply2 client address to ${clientIp}:${clientPort}`);
    return out;
  }
  return packet;
}

function rewriteNewIncomingConnectionPacket(packet, publicIp, relayPort, clientIp, clientPort) {
  if (!Buffer.isBuffer(packet) || packet.length < 8) return packet;
  const packetId = packet.readUInt8(0);
  if (packetId !== 0x10) return packet;

  const out = Buffer.from(packet);
  // ConnectionRequestAccepted layout:
  // 0x10 | clientAddress(7) | systemIndex(2) | internalAddresses...
  // Geyser sees the Android bridge as 127.0.0.1, so rewrite clientAddress
  // to the real remote Bedrock client endpoint and internal slots to the relay.
  const rewroteClient = writeRakNetAddress(out, 1, clientIp, clientPort);
  let rewriteCount = 0;
  let offset = 10;

  for (let i = 0; i < 10; i++) {
    if (offset + 7 > out.length) break;
    if (out[offset] !== 0x04) {
      // If it's an IPv6 address, it takes 29 bytes.
      if (out[offset] === 0x06) {
        offset += 29;
        continue;
      }
      break;
    }
    if (writeRakNetAddress(out, offset, publicIp, relayPort)) {
      rewriteCount++;
    }
    offset += 7;
  }

  if (rewroteClient || rewriteCount > 0) {
    console.log(
      `[bedrock] Rewrote ConnectionRequestAccepted client=${clientIp}:${clientPort}, ` +
      `internalSlots=${rewriteCount} to ${publicIp}:${relayPort}`
    );
  }
  return out;
}

function rewriteEncapsulatedRakNetPackets(payload, publicIp, relayPort, clientIp, clientPort) {
  let offset = 4; // FrameSet packet id (1) + sequence number (3)
  let out = null;
  let rewroteAny = false;

  while (offset + 3 <= payload.length) {
    const flags = payload.readUInt8(offset);
    const bitLength = payload.readUInt16BE(offset + 1);
    const byteLength = Math.ceil(bitLength / 8);
    const headerLen = encapsulatedHeaderLength(flags, payload, offset);

    if (headerLen < 0) break;
    
    const payloadOffset = offset + headerLen;
    const payloadEnd = payloadOffset + byteLength;
    if (payloadEnd > payload.length) break;
    
    if (byteLength > 0 && payload[payloadOffset] === 0x10) {
      if (!out) out = Buffer.from(payload); // Only copy if we actually find a 0x10 packet!
      
      const rewritten = rewriteNewIncomingConnectionPacket(
        out.subarray(payloadOffset, payloadEnd),
        publicIp,
        relayPort,
        clientIp,
        clientPort
      );
      rewritten.copy(out, payloadOffset);
      rewroteAny = true;
    }
    
    offset = payloadEnd;

  }

  return rewroteAny ? out : payload;
}

function rewriteAdvertisedRelayAddress(payload, relayPort, clientIp, clientPort) {
  if (!Buffer.isBuffer(payload) || payload.length < 1) return payload;

  const publicIp = RESOLVED_PUBLIC_IPV4;
  if (
    !isValidIPv4(publicIp) ||
    !isValidUdpPort(relayPort) ||
    !isValidIPv4(clientIp) ||
    !isValidUdpPort(clientPort)
  ) {
    return payload;
  }

  const packetId = payload.readUInt8(0);

  if (packetId === 0x08) {
    return rewriteOpenConnectionReply2Packet(payload, clientIp, clientPort);
  }

  if (packetId === 0x10) {
    return rewriteNewIncomingConnectionPacket(payload, publicIp, relayPort, clientIp, clientPort);
  }

  if (isRakNetFrameSet(packetId)) {
    return rewriteEncapsulatedRakNetPackets(payload, publicIp, relayPort, clientIp, clientPort);
  }

  return payload;
}

function buildBedrockFrame(clientIp, clientPort, payload) {
  const m = IPV4_RE.exec(clientIp);
  if (!m) return null;
  const o = [Number(m[1]), Number(m[2]), Number(m[3]), Number(m[4])];
  if (o.some((b) => b > 255)) return null;
  if (!isValidUdpPort(clientPort)) return null;
  if (!Buffer.isBuffer(payload) || payload.length > MAX_UDP_PAYLOAD) return null;

  const frame = Buffer.allocUnsafe(1 + 2 + 4 + 2 + payload.length);
  let offset = 0;
  frame.writeUInt8(0x02, offset++);
  frame.writeUInt16BE(payload.length, offset); offset += 2;
  frame[offset++] = o[0];
  frame[offset++] = o[1];
  frame[offset++] = o[2];
  frame[offset++] = o[3];
  frame.writeUInt16BE(clientPort, offset); offset += 2;
  payload.copy(frame, offset);
  return frame;
}

function parseBedrockResponseFrame(data) {
  if (data.length < 9) return null;
  if (data.readUInt8(0) !== 0x03) return null;

  const payloadLen = data.readUInt16BE(1);
  if (payloadLen > MAX_UDP_PAYLOAD) return null;
  if (data.length < 9 + payloadLen) return null;

  const ip = `${data[3]}.${data[4]}.${data[5]}.${data[6]}`;
  const port = data.readUInt16BE(7);
  const payload = data.subarray(9, 9 + payloadLen);
  return { ip, port, payload, frameLength: 9 + payloadLen };
}

function handlePhoneFrameData(userId, data) {
  if (!data || data.length < 1) return false;

  let buffer = data;
  let handledAny = false;
  const relayPort = activeTunnels.get(userId)?.port || null;

  while (buffer.length > 0) {
    const opcodeIndex = buffer.indexOf(0x03);
    if (opcodeIndex === -1) {
      if (!handledAny) {
        console.warn(`[bedrock] Dropping non-Bedrock phone data for ${userId}, len=${buffer.length}`);
      }
      return handledAny;
    }
    if (opcodeIndex > 0) {
      console.warn(`[bedrock] Dropping ${opcodeIndex} desynced byte(s) before 0x03 for ${userId}`);
      buffer = buffer.subarray(opcodeIndex);
    }

    if (buffer.length < 9) return handledAny || true;
    const payloadLen = buffer.readUInt16BE(1);
    if (payloadLen > MAX_UDP_PAYLOAD) {
      console.warn(`[bedrock] Dropping frame with oversized payload ${payloadLen} for ${userId}`);
      buffer = buffer.subarray(1);
      handledAny = true;
      continue;
    }
    
    const frameLength = 9 + payloadLen;
    if (buffer.length < frameLength) return handledAny || true;
    
    const parsed = parseBedrockResponseFrame(buffer.subarray(0, frameLength));
    buffer = buffer.subarray(frameLength);
    handledAny = true;
    
    if (!parsed) continue;
    if (!isValidUdpPort(parsed.port)) {
      console.warn(`[bedrock] Dropping frame with invalid port ${parsed.port} for ${userId}`);
      continue;
    }
    if (!isValidIPv4(parsed.ip)) {
      console.warn(`[bedrock] Dropping frame with invalid ip ${parsed.ip} for ${userId}`);
      continue;
    }
    
    const udpSock = userUdpSockets.get(userId);
    if (!udpSock) {
      console.warn(`[bedrock] No UDP socket for ${userId} while sending to ${parsed.ip}:${parsed.port}`);
      continue;
    }
    
    const rewrittenPayload = rewriteAdvertisedRelayAddress(
      parsed.payload,
      relayPort || parsed.port,
      parsed.ip,
      parsed.port
    );
    
    // Only log the first few packets to avoid console.log blocking the event loop and causing high ping
    if (!global.udpSendCount) global.udpSendCount = 0;
    global.udpSendCount++;
    if (global.udpSendCount <= 20 || global.udpSendCount % 500 === 0) {
      console.log(`[bedrock] Sending UDP to client ${parsed.ip}:${parsed.port} len=${rewrittenPayload.length} (#${global.udpSendCount})`);
    }
    
    udpSock.send(rewrittenPayload, parsed.port, parsed.ip, (err) => {
      if (err) {
        console.error(`[bedrock] UDP send error for ${userId}:`, err.message);
      }
    });

  }

  return handledAny;
}

// Tunnel socket helpers

function removePendingEntry(tunnel, entry) {
  const idx = tunnel.pendingPlayers.indexOf(entry);
  if (idx !== -1) tunnel.pendingPlayers.splice(idx, 1);
}

function takeNextPendingPlayer(tunnel) {
  while (tunnel.pendingPlayers.length > 0) {
    const entry = tunnel.pendingPlayers.shift();
    if (!entry) continue;
    if (entry.timeout) clearTimeout(entry.timeout);
    if (entry.socket && !entry.socket.destroyed) return entry.socket;
  }
  return null;
}

function takeNextPhoneSocket(tunnel) {
  while (tunnel.phoneSocketPool.length > 0) {
    const entry = tunnel.phoneSocketPool.shift();
    const socket = entry && entry.socket ? entry.socket : entry;
    if (socket && !socket.destroyed) return socket;
  }
  return null;
}

function clearBedrockPhoneSocket(tunnel) {
  if (!tunnel) return;
  tunnel.bedrockPhoneSocket = null;
  tunnel.bedrockBuffer = Buffer.alloc(0);
}

function attachBedrockPhoneSocket(tunnel, userId, phoneSocket) {
  if (!tunnel || !phoneSocket || phoneSocket.destroyed) return null;

  clearBedrockPhoneSocket(tunnel);
  tunnel.bedrockPhoneSocket = phoneSocket;
  tunnel.bedrockBuffer = Buffer.alloc(0);

  phoneSocket.setTimeout(0);

  const onBedrockData = (chunk) => {
    if (!chunk || chunk.length === 0) return;

    if (tunnel.bedrockBuffer.length === 0) {
      tunnel.bedrockBuffer = chunk;
    } else {
      tunnel.bedrockBuffer = Buffer.concat([tunnel.bedrockBuffer, chunk]);
    }
    
    while (tunnel.bedrockBuffer.length > 0) {
      const opcodeIndex = tunnel.bedrockBuffer.indexOf(0x03);
      if (opcodeIndex === -1) {
        console.warn(`[bedrock] Dropping non-Bedrock dedicated data for ${userId}, len=${tunnel.bedrockBuffer.length}`);
        tunnel.bedrockBuffer = Buffer.alloc(0);
        return;
      }
      if (opcodeIndex > 0) {
        console.warn(`[bedrock] Dropping ${opcodeIndex} desynced dedicated byte(s) for ${userId}`);
        tunnel.bedrockBuffer = tunnel.bedrockBuffer.subarray(opcodeIndex);
      }
      if (tunnel.bedrockBuffer.length < 9) return;
    
      const payloadLen = tunnel.bedrockBuffer.readUInt16BE(1);
      if (payloadLen > MAX_UDP_PAYLOAD) {
        console.warn(`[bedrock] Dropping dedicated frame with oversized payload ${payloadLen} for ${userId}`);
        tunnel.bedrockBuffer = tunnel.bedrockBuffer.subarray(1);
        continue;
      }
    
      const frameLength = 9 + payloadLen;
      if (tunnel.bedrockBuffer.length < frameLength) return;
    
      const frame = tunnel.bedrockBuffer.subarray(0, frameLength);
      tunnel.bedrockBuffer = tunnel.bedrockBuffer.subarray(frameLength);
      handlePhoneFrameData(userId, frame);
    }

  };

  const onSocketGone = () => {
    phoneSocket.removeListener('data', onBedrockData);
    if (tunnel.bedrockPhoneSocket === phoneSocket) {
      clearBedrockPhoneSocket(tunnel);
    }
  };

  phoneSocket.on('data', onBedrockData);
  phoneSocket.once('close', onSocketGone);
  phoneSocket.once('error', onSocketGone);

  console.log(`[bedrock] Dedicated phone socket attached for ${userId}`);
  return phoneSocket;
}

function getBedrockPhoneSocket(userId) {
  const tunnel = activeTunnels.get(userId);
  if (!tunnel) return null;

  if (tunnel.bedrockPhoneSocket && !tunnel.bedrockPhoneSocket.destroyed) {
    return tunnel.bedrockPhoneSocket;
  }

  const phoneSocket = takeNextPhoneSocket(tunnel);
  if (!phoneSocket) return null;

  return attachBedrockPhoneSocket(tunnel, userId, phoneSocket);
}

function pairSockets(playerSocket, phoneSocket, userId) {
  configureSocket(playerSocket);
  configureSocket(phoneSocket);

  playerSocket.on('data', (chunk) => {
    if (!phoneSocket.destroyed) {
      phoneSocket.write(chunk);
    }
  });

  phoneSocket.on('data', (chunk) => {
    if (!playerSocket.destroyed) {
      playerSocket.write(chunk);
    }
  });

  if (playerSocket.initialChunk) {
    if (!phoneSocket.destroyed) {
      phoneSocket.write(playerSocket.initialChunk);
    }
    playerSocket.initialChunk = null;
  }

  playerSocket.resume();

  let killed = false;

  const killBoth = (src) => {
    if (killed) return;
    killed = true;
    console.log(`[relay] Pairing broken for ${userId} (source: ${src})`);
    if (!playerSocket.destroyed) playerSocket.destroy();
    if (!phoneSocket.destroyed) phoneSocket.destroy();
  };

  playerSocket.on('error', (e) => killBoth(`player_error: ${e.message}`));
  phoneSocket.on('error', (e) => killBoth(`phone_error: ${e.message}`));
  playerSocket.on('close', () => killBoth('player_close'));
  phoneSocket.on('close', () => killBoth('phone_close'));

  console.log(`[relay] Paired player <-> phone for ${userId}`);
}

function queuePlayer(tunnel, userId, playerSocket) {
  if (tunnel.pendingPlayers.length >= MAX_PENDING_PLAYERS) {
    console.warn(`[relay] Pending queue full for ${userId}, dropping player`);
    playerSocket.destroy();
    return;
  }

  const entry = { socket: playerSocket, timeout: null };
  entry.timeout = setTimeout(() => {
    removePendingEntry(tunnel, entry);
    if (!playerSocket.destroyed) {
      console.log(`[relay] Player timed out waiting for phone socket for ${userId}`);
      playerSocket.destroy();
    }
  }, PLAYER_WAIT_TIMEOUT_MS);

  tunnel.pendingPlayers.push(entry);
  console.log(`[relay] Player queued for ${userId}, pending=${tunnel.pendingPlayers.length}`);
}

function closeTunnel(userId, reason = 'manual') {
  const tunnel = activeTunnels.get(userId);
  if (!tunnel) return;

  try {
    if (tunnel.server) tunnel.server.close();
  } catch (_) {}

  for (const entry of tunnel.phoneSocketPool) {
    const s = entry && entry.socket ? entry.socket : entry;
    try { s.destroy(); } catch (_) {}
  }
  tunnel.phoneSocketPool.length = 0;

  if (tunnel.bedrockPhoneSocket) {
    try { tunnel.bedrockPhoneSocket.destroy(); } catch (_) {}
    clearBedrockPhoneSocket(tunnel);
  }

  for (const entry of tunnel.pendingPlayers) {
    if (entry.timeout) clearTimeout(entry.timeout);
    try { entry.socket.destroy(); } catch (_) {}
  }
  tunnel.pendingPlayers.length = 0;

  stopUserUdpSocket(userId);
  activeTunnels.delete(userId);

  const { clearServerStatus } = require('./bedrock-ping');
  clearServerStatus(tunnel.port);

  console.log(`[relay] Tunnel closed for ${userId} reason=${reason}`);
}

// Bedrock UDP per-user

function startUserUdpSocket(userId, assignedPort, getPhoneSocket) {
  if (userUdpSockets.has(userId)) return;

  const udpSock = dgram.createSocket('udp4');
  const clientMap = new Map();
  bedrockClientMap.set(userId, clientMap);
  const { buildPong, getMotd, isRakNetPing } = require('./bedrock-ping');

  udpSock.on('message', (msg, rinfo) => {
    if (isRakNetPing(msg)) {
      try {
        const pingTime = msg.readBigUInt64BE(1);
        const pong = buildPong(pingTime, getMotd(assignedPort));
        udpSock.send(pong, rinfo.port, rinfo.address, (err) => {
          if (err) console.error('[udp] pong send error:', err.message);
        });
      } catch (e) {
        console.error('[udp] ping handler error:', e.message);
      }
      return;
    }

    if (msg.length > MAX_UDP_PAYLOAD) return;
    if (!isValidUdpPort(rinfo.port) || !isValidIPv4(rinfo.address)) return;
    
    // MTU Clamping: Intercept OpenConnectionRequest1 (0x05) and slice its padding
    // to force Geyser to negotiate a safe 1300 byte MTU, avoiding IP fragmentation later.
    let payload = msg;
    if (msg[0] === 0x05 && msg.length > 1300) {
      console.log(`[bedrock] Clamping inbound OpenConnectionRequest1 MTU padding from ${msg.length} to 1300 for ${userId}`);
      payload = msg.subarray(0, 1300);
    }
    
    const key = `${rinfo.address}:${rinfo.port}`;
    clientMap.set(key, { address: rinfo.address, port: rinfo.port, lastSeen: Date.now() });
    
    const phoneSocket = getPhoneSocket(userId);
    if (!phoneSocket) return;
    
    const frame = buildBedrockFrame(rinfo.address, rinfo.port, payload);
    if (!frame) return;
    
    try {
      phoneSocket.write(frame);
    } catch (err) {
      console.error(`[bedrock] frame write error for ${userId}:`, err.message);
    }

  });

  udpSock.on('error', (err) => {
    console.error(`[bedrock] UDP error for ${userId}:`, err.message);
    userUdpSockets.delete(userId);
    bedrockClientMap.delete(userId);
    try { udpSock.close(); } catch (_) {}

    const timer = setTimeout(() => {
      udpRestartTimers.delete(userId);
      if (activeTunnels.has(userId)) {
        console.log(`[bedrock] Restarting UDP socket for ${userId}`);
        startUserUdpSocket(userId, assignedPort, getPhoneSocket);
      }
    }, 2_000);
    udpRestartTimers.set(userId, timer);

  });

  udpSock.bind(assignedPort, () => {
    console.log(`[bedrock] UDP bound on port ${assignedPort} for ${userId}`);
  });

  userUdpSockets.set(userId, udpSock);
}

function stopUserUdpSocket(userId) {
  const timer = udpRestartTimers.get(userId);
  if (timer) {
    clearTimeout(timer);
    udpRestartTimers.delete(userId);
  }

  const sock = userUdpSockets.get(userId);
  if (sock) {
    try { sock.close(); } catch (_) {}
    userUdpSockets.delete(userId);
  }
  bedrockClientMap.delete(userId);
}

// Prune stale Bedrock clients every 30s
setInterval(() => {
  const cutoff = Date.now() - BEDROCK_CLIENT_TTL_MS;
  for (const [userId, clientMap] of bedrockClientMap) {
    if (!activeTunnels.has(userId)) {
      bedrockClientMap.delete(userId);
      continue;
    }
    for (const [key, entry] of clientMap) {
      if (entry.lastSeen < cutoff) clientMap.delete(key);
    }
  }
}, 30_000);

// Rate limiter

const registerRateMap = new Map();

function isRateLimited(ip) {
  const now = Date.now();
  const rec = registerRateMap.get(ip) || { count: 0, windowStart: now };
  if (now - rec.windowStart > REGISTER_RATE_WINDOW_MS) {
    rec.count = 0;
    rec.windowStart = now;
  }
  rec.count++;
  registerRateMap.set(ip, rec);
  return rec.count > REGISTER_RATE_LIMIT;
}

setInterval(() => {
  const cutoff = Date.now() - REGISTER_RATE_WINDOW_MS;
  for (const [ip, rec] of registerRateMap) {
    if (rec.windowStart < cutoff) registerRateMap.delete(ip);
  }
}, 300_000);

// Express control API

const app = express();
app.use(express.json());

app.post('/register', (req, res) => {
  const ip = req.socket.remoteAddress || 'unknown';
  const userId = parseUserId(req.body?.userId);

  if (!userId) {
    return res.status(400).json({ error: 'userId required (max 128 chars, alphanumeric / - / _)' });
  }

  if (isRateLimited(ip)) {
    console.warn(`[api] Rate limit hit for IP ${ip}`);
    return res.status(429).json({ error: 'Too many registrations' });
  }

  if (activeTunnels.has(userId)) {
    const existing = activeTunnels.get(userId);
    return res.json({ port: existing.port, ip: PUBLIC_HOST });
  }

  const port = getOrAssignPort(userId);
  if (port === null) return res.status(503).json({ error: 'No ports available' });

  const tunnel = {
    userId,
    port,
    phoneSocketPool: [],
    pendingPlayers: [],
    server: null,
    bedrockPhoneSocket: null,
    bedrockBuffer: Buffer.alloc(0),
    lastReadyAt: 0,
    createdAt: Date.now(),
  };

  tunnel.server = net.createServer({ allowHalfOpen: false }, (playerSocket) => {
    configureSocket(playerSocket);
    
    playerSocket.once('data', (chunk) => {
      if (handleJavaPing(playerSocket, chunk, tunnel.port)) return;
      
      playerSocket.initialChunk = chunk;
      const phoneSocket = takeNextPhoneSocket(tunnel);
      if (phoneSocket) {
        pairSockets(playerSocket, phoneSocket, userId);
        return;
      }
      playerSocket.pause();
      queuePlayer(tunnel, userId, playerSocket);
    });
  });

  tunnel.server.on('error', (err) => {
    console.error(`[relay] Tunnel server error for ${userId}:`, err.message);
    closeTunnel(userId, 'server_error');
  });

  tunnel.server.listen(port, '0.0.0.0', () => {
    console.log(`[relay] Started tunnel for ${userId} on port ${port}`);
  });

  activeTunnels.set(userId, tunnel);

  startUserUdpSocket(userId, port, getBedrockPhoneSocket);

  return res.json({ port, ip: PUBLIC_HOST });
});

app.post(['/phone-ready', '/phone_ready', '/ready', '/phoneReady'], (req, res) => {
  const userId = parseUserId(req.body?.userId);
  if (!userId) return res.status(400).json({ ok: false, error: 'userId required' });

  const tunnel = activeTunnels.get(userId);
  if (tunnel) {
    tunnel.lastReadyAt = Date.now();
    return res.json({ ok: true, tunnelExists: true, ip: PUBLIC_HOST, port: tunnel.port });
  }
  return res.json({ ok: true, tunnelExists: false, note: 'register not completed yet' });
});

app.post('/unregister', (req, res) => {
  const userId = parseUserId(req.body?.userId);
  if (!userId) return res.status(400).json({ error: 'userId required' });
  closeTunnel(userId, 'unregister');
  return res.json({ success: true });
});

app.get('/status', (_req, res) => {
  const out = {};
  for (const [userId, t] of activeTunnels.entries()) {
    out[userId] = {
      port: t.port,
      idlePhoneSockets: t.phoneSocketPool.filter((entry) => {
        const s = entry && entry.socket ? entry.socket : entry;
        return s && !s.destroyed;
      }).length,
      pendingPlayers: t.pendingPlayers.filter((p) => p.socket && !p.socket.destroyed).length,
      lastReadyAt: t.lastReadyAt || 0,
      uptimeSec: Math.floor((Date.now() - t.createdAt) / 1000),
      bedrockUdpActive: userUdpSockets.has(userId),
      bedrockClients: bedrockClientMap.get(userId)?.size ?? 0,
      hasDedicatedBedrockSocket: !!(t.bedrockPhoneSocket && !t.bedrockPhoneSocket.destroyed),
    };
  }
  return res.json(out);
});

// Bedrock server status update endpoint

app.post('/bedrock-status', (req, res) => {
  const suppliedSecret = String(req.headers['x-pocketcraft-secret'] || '').trim();
  if (
    !suppliedSecret ||
    (suppliedSecret !== RELAY_SECRET && suppliedSecret !== APP_RELAY_SECRET)
  ) {
    return res.status(401).json({ ok: false });
  }
  const { port, motd, players, maxPlayers, version } = req.body;
  if (!isValidUdpPort(Number(port))) {
    return res.status(400).json({ ok: false, error: 'invalid port' });
  }
  updateServerStatus(port, { motd, players, maxPlayers, version });
  res.json({ ok: true });
});

app.get('/health', (_req, res) => {
  res.json({
    ok: true,
    tunnels: activeTunnels.size,
    portsUsed: userPortMap.size,
    uptimeMs: (process.uptime() * 1000) | 0,
  });
});

app.get('/download/paper', async (req, res) => {
  const version = String(req.query.version || '').trim();
  if (!version || !/^\d+\.\d+(\.\d+)?$/.test(version)) {
    return res.status(400).send('Invalid version format.');
  }

  try {
    const apiRes = await fetch(`https://api.papermc.io/v2/projects/paper/versions/${version}`);
    if (!apiRes.ok) {
      return res.status(apiRes.status).send(`Failed to fetch version metadata from PaperMC: ${apiRes.statusText}`);
    }
    const data = await apiRes.json();
    if (!data.builds || !Array.isArray(data.builds) || data.builds.length === 0) {
      return res.status(404).send('No builds found for this version.');
    }

    const latestBuild = data.builds[data.builds.length - 1];
    const downloadUrl = `https://api.papermc.io/v2/projects/paper/versions/${version}/builds/${latestBuild}/downloads/paper-${version}-${latestBuild}.jar`;

    const jarRes = await fetch(downloadUrl, {
      headers: {
        'User-Agent': 'PocketCraft/1.0.0'
      }
    });

    if (!jarRes.ok) {
      return res.status(jarRes.status).send(`Failed to download JAR from PaperMC: ${jarRes.statusText}`);
    }

    res.setHeader('Content-Type', 'application/java-archive');
    res.setHeader('Content-Disposition', `attachment; filename="paper-${version}-${latestBuild}.jar"`);
    
    const readableStream = jarRes.body;
    if (readableStream && typeof readableStream.pipeTo === 'function') {
      const { Readable } = require('stream');
      Readable.fromWeb(readableStream).pipe(res);
    } else {
      const { Readable } = require('stream');
      Readable.from(readableStream).pipe(res);
    }
  } catch (error) {
    console.error('[download] Paper download error:', error);
    res.status(500).send('Internal server error while processing download.');
  }
});

// Phone relay TCP listener

const phoneServer = net.createServer({ allowHalfOpen: false }, (phoneSocket) => {
  configureSocket(phoneSocket);

  let handshakeBuffer = Buffer.alloc(0);
  const onHandshakeData = (chunk) => {
    if (!chunk || chunk.length === 0) return;
    handshakeBuffer = Buffer.concat([handshakeBuffer, chunk]);

    const newlineIndex = handshakeBuffer.indexOf(0x0a);
    if (newlineIndex === -1) {
      if (handshakeBuffer.length > MAX_USER_ID_LENGTH + 2) {
        phoneSocket.destroy();
      }
      return;
    }
    
    phoneSocket.removeListener('data', onHandshakeData);
    
    const firstLineBuffer = handshakeBuffer.subarray(0, newlineIndex);
    const firstLine = firstLineBuffer.toString('utf8');
    const userId = parseUserId(firstLine);
    
    if (!userId) {
      phoneSocket.destroy();
      return;
    }
    
    const tunnel = activeTunnels.get(userId);
    if (!tunnel) {
      console.log(`[relay] No active tunnel for userId: ${userId}`);
      phoneSocket.destroy();
      return;
    }
    
    const dedicatedBedrockActive = tunnel.bedrockPhoneSocket && !tunnel.bedrockPhoneSocket.destroyed ? 1 : 0;
    const alivePoolSize = tunnel.phoneSocketPool.filter((entry) => {
      const s = entry && entry.socket ? entry.socket : entry;
      return s && !s.destroyed;
    }).length + dedicatedBedrockActive;
    
    if (alivePoolSize >= MAX_PHONE_POOL_SIZE) {
      console.warn(`[relay] Phone pool full for ${userId} (${alivePoolSize}), rejecting`);
      phoneSocket.destroy();
      return;
    }
    
    const pendingPlayer = takeNextPendingPlayer(tunnel);
    if (pendingPlayer) {
      pairSockets(pendingPlayer, phoneSocket, userId);
      return;
    }
    
    phoneSocket.setTimeout(PHONE_POOL_IDLE_TIMEOUT_MS);
    phoneSocket.on('timeout', () => {
      console.log(`[relay] Idle phone socket timed out for ${userId}`);
      phoneSocket.destroy();
    });
    
    const remainder = handshakeBuffer.subarray(newlineIndex + 1);
    if (remainder.length > 0) {
      console.warn(`[relay] Unexpected post-handshake data from ${userId}, len=${remainder.length}; ignoring until socket is assigned`);
    }
    
    const poolEntry = { socket: phoneSocket };
    tunnel.phoneSocketPool.push(poolEntry);
    console.log(`[relay] Phone socket registered for ${userId}, pool=${tunnel.phoneSocketPool.length}`);
    
    const removeFromPool = (src) => {
      console.log(`[relay] Phone socket removed from pool for ${userId} (${src})`);
      const idx = tunnel.phoneSocketPool.indexOf(poolEntry);
      if (idx !== -1) tunnel.phoneSocketPool.splice(idx, 1);
      if (tunnel.bedrockPhoneSocket === phoneSocket) {
        clearBedrockPhoneSocket(tunnel);
      }
    };
    
    phoneSocket.on('close', () => removeFromPool('close'));
    phoneSocket.on('error', (e) => removeFromPool(`error: ${e.message}`));

  };

  phoneSocket.on('data', onHandshakeData);
});

phoneServer.listen(PHONE_RELAY_PORT, '0.0.0.0', () => {
  console.log(`[relay] Phone relay listener on port ${PHONE_RELAY_PORT}`);
});

// Periodic cleanup

setInterval(() => {
  for (const [userId, t] of activeTunnels.entries()) {
    t.phoneSocketPool = t.phoneSocketPool.filter((entry) => {
      const s = entry && entry.socket ? entry.socket : entry;
      return s && !s.destroyed;
    });

    if (t.bedrockPhoneSocket && t.bedrockPhoneSocket.destroyed) {
      clearBedrockPhoneSocket(t);
    }
    
    t.pendingPlayers = t.pendingPlayers.filter((entry) => {
      if (!entry.socket || entry.socket.destroyed) {
        if (entry.timeout) clearTimeout(entry.timeout);
        return false;
      }
      return true;
    });
    
    if (!t.server || !t.server.listening) {
      console.warn(`[cleanup] Tunnel server not listening for ${userId}, closing`);
      closeTunnel(userId, 'listener_not_active');
      continue;
    }
    
    console.log(
      `[cleanup] ${userId} - pool: ${t.phoneSocketPool.length}, ` +
      `pending: ${t.pendingPlayers.length}, udp: ${userUdpSockets.has(userId)}, ` +
      `bedrockSocket: ${!!(t.bedrockPhoneSocket && !t.bedrockPhoneSocket.destroyed)}`
    );

  }
}, CLEANUP_INTERVAL_MS);

// Graceful shutdown

function gracefulShutdown(signal) {
  console.log(`[relay] Received ${signal}, shutting down...`);

  for (const userId of [...activeTunnels.keys()]) {
    closeTunnel(userId, `shutdown:${signal}`);
  }

  phoneServer.close(() => {
    console.log('[relay] Phone server closed');
    process.exit(0);
  });

  setTimeout(() => {
    console.error('[relay] Forced exit after timeout');
    process.exit(1);
  }, 10_000);
}

process.on('SIGTERM', () => gracefulShutdown('SIGTERM'));
process.on('SIGINT', () => gracefulShutdown('SIGINT'));

process.on('uncaughtException', (err) => {
  if (
    err.code === 'ECONNRESET' ||
    err.code === 'EPIPE' ||
    err.code === 'ERR_SOCKET_BAD_PORT' ||
    err.code === 'ETIMEDOUT'
  ) {
    console.warn('[relay] Ignored socket error:', err.code);
    return;
  }
  console.error('[relay] Uncaught exception:', err);
});

process.on('unhandledRejection', (reason) => {
  console.error('[relay] Unhandled rejection:', reason);
});

// Start control API

const controlServer = app.listen(CONTROL_PORT, '0.0.0.0', () => {
  console.log(`[relay] Control API on port ${CONTROL_PORT}`);
});

controlServer.on('error', (err) => {
  console.error(`[relay] Control API failed to start on port ${CONTROL_PORT}:`, err.message);
  process.exit(1);
});

refreshPublicIpv4();
setInterval(refreshPublicIpv4, 5 * 60 * 1000);

// Start Bedrock UDP ping responder

startBedrockPing(19132);

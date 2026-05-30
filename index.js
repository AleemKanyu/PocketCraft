'use strict';

const net     = require('net');
const express = require('express');
const dgram   = require('dgram');
const dns     = require('dns');
const { startBedrockPing, updateServerStatus } = require('./bedrock-ping');

// Constants

const CONTROL_PORT               = 8080;
const PHONE_RELAY_PORT           = 9000;
const PUBLIC_HOST                = 'mine.pocketcraft.online';
const PORT_POOL_START            = 25500;
const PORT_POOL_END              = 35500;
const PLAYER_WAIT_TIMEOUT_MS     = 30_000;
const SOCKET_KEEPALIVE_MS        = 10_000;
const PHONE_POOL_IDLE_TIMEOUT_MS = 8 * 60_000;
const CLEANUP_INTERVAL_MS        = 30_000;
const BEDROCK_CLIENT_TTL_MS      = 60_000;
const MAX_PHONE_POOL_SIZE        = 20;
const MAX_PENDING_PLAYERS        = 50;
const MAX_UDP_PAYLOAD            = 65535;
const PHONE_POOL_IDLE_JITTER_MS  = 60_000;
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

function rakNetAddressByteLength(family) {
  if (family === 0x04) return 7;
  if (family === 0x06) return 29;
  return 0;
}

function isLikelyConnectionRequestAcceptedPacket(packet) {
  if (!Buffer.isBuffer(packet) || packet.length < 26) return false;
  if (packet.readUInt8(0) !== 0x10) return false;

  let offset = 1;
  const clientAddrLen = rakNetAddressByteLength(packet[offset]);
  if (!clientAddrLen || (offset + clientAddrLen) > packet.length) return false;
  offset += clientAddrLen;

  if ((offset + 2) > packet.length) return false;
  offset += 2; // system index

  for (let i = 0; i < 10; i++) {
    const addrLen = rakNetAddressByteLength(packet[offset]);
    if (!addrLen || (offset + addrLen) > packet.length) return false;
    offset += addrLen;
  }

  return (packet.length - offset) === 16;
}

function getPhonePoolIdleTimeoutMs() {
  return PHONE_POOL_IDLE_TIMEOUT_MS + Math.floor(Math.random() * PHONE_POOL_IDLE_JITTER_MS);
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

function rewriteOpenConnectionReply1Packet(packet) {
  if (!Buffer.isBuffer(packet) || packet.length < 20) return packet;
  if (packet.readUInt8(0) !== 0x06) return packet;

  console.log(`[bedrock] OpenConnectionReply1 hex: ${packet.toString('hex')} len=${packet.length}`);
  const out = Buffer.from(packet);
  
  // MTU is always the last 2 bytes of OpenConnectionReply1 (at packet.length - 2)
  const mtuOffset = out.length - 2;
  if (mtuOffset >= 0) {
    const negotiatedMtu = out.readUInt16BE(mtuOffset);
    console.log(`[bedrock] Found OpenConnectionReply1 MTU at offset ${mtuOffset}: ${negotiatedMtu}`);
    if (negotiatedMtu > 1200) {
      console.log(`[bedrock] Rewriting OpenConnectionReply1 MTU from ${negotiatedMtu} to 1200`);
      out.writeUInt16BE(1200, mtuOffset);
      return out;
    }
  }
  return packet;
}

function rewriteOpenConnectionReply2Packet(packet, clientIp, clientPort) {
  if (!Buffer.isBuffer(packet) || packet.length < 28) return packet;
  if (packet.readUInt8(0) !== 0x08) return packet;

  console.log(`[bedrock] OpenConnectionReply2 hex: ${packet.toString('hex')} len=${packet.length}`);
  const out = Buffer.from(packet);
  
  // Rewrite client address if found
  const addressOffset = 1 + 16 + 8; // 25
  if (out[addressOffset] === 0x04 && writeRakNetAddress(out, addressOffset, clientIp, clientPort)) {
    console.log(`[bedrock] Rewrote OpenConnectionReply2 client address to ${clientIp}:${clientPort}`);
  }

  // MTU is always at out.length - 3 (just before the final 1-byte encryption flag)
  const mtuOffset = out.length - 3;
  if (mtuOffset >= 0) {
    const currentMtu = out.readUInt16BE(mtuOffset);
    console.log(`[bedrock] Found OpenConnectionReply2 MTU at offset ${mtuOffset}: ${currentMtu}`);
    if (currentMtu > 1200) {
      console.log(`[bedrock] Rewriting OpenConnectionReply2 MTU from ${currentMtu} to 1200`);
      out.writeUInt16BE(1200, mtuOffset);
    }
  }
  return out;
}

function rewriteNewIncomingConnectionPacket(packet, publicIp, relayPort, clientIp, clientPort) {
  if (!isLikelyConnectionRequestAcceptedPacket(packet)) return packet;

  const out = Buffer.from(packet);
  // ConnectionRequestAccepted layout:
  // 0x10 | clientAddress(7) | systemIndex(2) | internalAddresses...
  // Geyser sees the Android bridge as 127.0.0.1, so rewrite clientAddress
  // to the real remote Bedrock client endpoint and internal slots to the relay.
  const clientAddrFamily = out[1];
  const rewroteClient = clientAddrFamily === 0x04 && writeRakNetAddress(out, 1, clientIp, clientPort);
  let rewriteCount = 0;
  let offset = 1 + rakNetAddressByteLength(clientAddrFamily) + 2;

  for (let i = 0; i < 10; i++) {
    const family = out[offset];
    const addrLen = rakNetAddressByteLength(family);
    if (!addrLen || (offset + addrLen) > out.length) break;
    if (family === 0x04 && writeRakNetAddress(out, offset, publicIp, relayPort)) {
      rewriteCount++;
    }
    offset += addrLen;
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
  const out = Buffer.from(payload);
  let offset = 4; // FrameSet packet id (1) + sequence number (3)
  let rewroteAny = false;

  while (offset + 3 <= out.length) {
    const flags = out.readUInt8(offset);
    const bitLength = out.readUInt16BE(offset + 1);
    const byteLength = Math.ceil(bitLength / 8);
    const headerLen = encapsulatedHeaderLength(flags, out, offset);

    if (headerLen < 0) break;
    
    const payloadOffset = offset + headerLen;
    const payloadEnd = payloadOffset + byteLength;
    if (payloadEnd > out.length) break;
    
    if (byteLength > 0 && out[payloadOffset] === 0x10) {
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

function rewriteClientNewIncomingConnection(payload, localGeyserIp = '127.0.0.1', localGeyserPort = 19132) {
  if (!Buffer.isBuffer(payload) || payload.length < 8) return payload;
  const packetId = payload.readUInt8(0);
  if (!isRakNetFrameSet(packetId)) return payload;

  let out = Buffer.from(payload);
  let offset = 4;
  let rewrote = false;

  while (offset + 3 <= out.length) {
    const flags = out.readUInt8(offset);
    const bitLength = out.readUInt16BE(offset + 1);
    const byteLength = Math.ceil(bitLength / 8);
    const headerLen = encapsulatedHeaderLength(flags, out, offset);
    if (headerLen < 0) break;

    const payloadOffset = offset + headerLen;
    const payloadEnd = payloadOffset + byteLength;
    if (payloadEnd > out.length) break;

    if (byteLength > 0 && out[payloadOffset] === 0x13) {
      if (payloadOffset + 8 <= out.length && out[payloadOffset + 1] === 0x04) {
        if (writeRakNetAddress(out, payloadOffset + 1, localGeyserIp, localGeyserPort)) {
          rewrote = true;
        }
      } else if (payloadOffset + 30 <= out.length && out[payloadOffset + 1] === 0x06) {
        const ipBuf = Buffer.alloc(7);
        ipBuf[0] = 0x04;
        const parts = localGeyserIp.split('.').map((x) => Number(x));
        ipBuf[1] = 0xFF - parts[0];
        ipBuf[2] = 0xFF - parts[1];
        ipBuf[3] = 0xFF - parts[2];
        ipBuf[4] = 0xFF - parts[3];
        ipBuf.writeUInt16BE(localGeyserPort, 5);

        const pre = out.subarray(0, payloadOffset + 1);
        const post = out.subarray(payloadOffset + 30);
        const newOut = Buffer.concat([pre, ipBuf, post]);
        newOut.writeUInt16BE(bitLength - 176, offset + 1);
        out = newOut;
        rewrote = true;
        break;
      }
    }

    offset = payloadEnd;
  }

  return rewrote ? out : payload;
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

  if (packetId === 0x06) {
    return rewriteOpenConnectionReply1Packet(payload);
  }

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
  tunnel.bedrockChunks = [];
  tunnel.bedrockChunksLen = 0;
}

function attachBedrockPhoneSocket(tunnel, userId, phoneSocket) {
  if (!tunnel || !phoneSocket || phoneSocket.destroyed) return null;

  clearBedrockPhoneSocket(tunnel);
  tunnel.bedrockPhoneSocket = phoneSocket;
  tunnel.bedrockBuffer = Buffer.alloc(0);
  tunnel.bedrockChunks = [];
  tunnel.bedrockChunksLen = 0;

  phoneSocket.setTimeout(0);

  const onBedrockData = (chunk) => {
    if (!chunk || chunk.length === 0) return;

    if (!tunnel.bedrockChunks) {
      tunnel.bedrockChunks = [];
      tunnel.bedrockChunksLen = 0;
    }

    tunnel.bedrockChunks.push(chunk);
    tunnel.bedrockChunksLen += chunk.length;

    // Only flatten if we have multiple chunks to avoid O(n) allocations
    let buf;
    if (tunnel.bedrockChunks.length === 1) {
      buf = tunnel.bedrockChunks[0];
    } else {
      buf = Buffer.concat(tunnel.bedrockChunks, tunnel.bedrockChunksLen);
    }

    let offset = 0;
    while (offset < buf.length) {
      const opcodeIndex = buf.indexOf(0x03, offset);
      if (opcodeIndex === -1) {
        console.warn(`[bedrock] Dropping non-Bedrock dedicated data for ${userId}, len=${buf.length - offset}`);
        offset = buf.length;
        break;
      }
      if (opcodeIndex > offset) {
        console.warn(`[bedrock] Dropping ${opcodeIndex - offset} desynced dedicated byte(s) for ${userId}`);
        offset = opcodeIndex;
      }
      if (buf.length - offset < 9) break;

      const payloadLen = buf.readUInt16BE(offset + 1);
      if (payloadLen > MAX_UDP_PAYLOAD) {
        console.warn(`[bedrock] Dropping dedicated frame with oversized payload ${payloadLen} for ${userId}`);
        offset += 1;
        continue;
      }

      const frameLength = 9 + payloadLen;
      if (buf.length - offset < frameLength) break;

      const frame = buf.subarray(offset, offset + frameLength);
      offset += frameLength;
      handlePhoneFrameData(userId, frame);
    }

    // Keep the remainder
    if (offset > 0) {
      if (offset < buf.length) {
        const remainder = buf.subarray(offset);
        tunnel.bedrockChunks = [remainder];
        tunnel.bedrockChunksLen = remainder.length;
      } else {
        tunnel.bedrockChunks = [];
        tunnel.bedrockChunksLen = 0;
      }
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

  playerSocket.resume();
  playerSocket.pipe(phoneSocket);
  phoneSocket.pipe(playerSocket);

  let killed = false;

  const killBoth = (src) => {
    if (killed) return;
    killed = true;
    console.log(`[relay] Pairing broken for ${userId} (source: ${src})`);
    playerSocket.unpipe(phoneSocket);
    phoneSocket.unpipe(playerSocket);
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
    
    // MTU Clamping: Intercept OpenConnectionRequest1 (0x05) and OpenConnectionRequest2 (0x07)
    // to negotiate a safe 1200 byte MTU, avoiding IP fragmentation later.
    let payload = msg;
    if (msg[0] === 0x05 && msg.length > 1200) {
      console.log(`[bedrock] Clamping inbound OpenConnectionRequest1 MTU padding from ${msg.length} to 1200 for ${userId}`);
      payload = msg.subarray(0, 1200);
    } else if (msg[0] === 0x07 && msg.length >= 34) {
      console.log(`[bedrock] OpenConnectionRequest2 hex: ${msg.toString('hex')} len=${msg.length}`);
      
      // MTU is always the 2 bytes right before the final 8-byte client GUID (at msg.length - 10)
      const mtuOffset = msg.length - 10;
      if (mtuOffset >= 0) {
        const clientMtu = msg.readUInt16BE(mtuOffset);
        console.log(`[bedrock] Found OpenConnectionRequest2 MTU at offset ${mtuOffset}: ${clientMtu}`);
        if (clientMtu > 1200) {
          console.log(`[bedrock] Rewriting inbound OpenConnectionRequest2 MTU from ${clientMtu} to 1200 for ${userId}`);
          msg.writeUInt16BE(1200, mtuOffset);
        }
      }
    }
    
    payload = rewriteClientNewIncomingConnection(payload);

    const key = `${rinfo.address}:${rinfo.port}`;
    clientMap.set(key, { address: rinfo.address, port: rinfo.port, lastSeen: Date.now() });
    
    const phoneSocket = getBedrockPhoneSocket(userId);
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
    try {
      udpSock.setRecvBufferSize(4 * 1024 * 1024); // 4MB
      udpSock.setSendBufferSize(1024 * 1024);     // 1MB
    } catch (e) {
      console.warn('[bedrock] Failed to set UDP buffer sizes:', e.message);
    }
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
    staleCycles: 0,
    lastReadyAt: 0,
    createdAt: Date.now(),
  };

  tunnel.server = net.createServer({ allowHalfOpen: false }, (playerSocket) => {
    configureSocket(playerSocket);
    playerSocket.pause();
    const phoneSocket = takeNextPhoneSocket(tunnel);
    if (phoneSocket) {
      pairSockets(playerSocket, phoneSocket, userId);
      return;
    }
    queuePlayer(tunnel, userId, playerSocket);
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

  let tunnel = activeTunnels.get(userId);
  if (!tunnel) {
    console.log(`[relay] Automatically registering missing tunnel for userId: ${userId} during phone-ready`);
    const port = getOrAssignPort(userId);
    if (port === null) return res.status(503).json({ ok: false, error: 'No ports available' });

    tunnel = {
      userId,
      port,
      phoneSocketPool: [],
      pendingPlayers: [],
      server: null,
      bedrockPhoneSocket: null,
      bedrockBuffer: Buffer.alloc(0),
      staleCycles: 0,
      lastReadyAt: 0,
      createdAt: Date.now(),
    };

    tunnel.server = net.createServer({ allowHalfOpen: false }, (playerSocket) => {
      configureSocket(playerSocket);
      playerSocket.pause();
      const phoneSocket = takeNextPhoneSocket(tunnel);
      if (phoneSocket) {
        pairSockets(playerSocket, phoneSocket, userId);
        return;
      }
      queuePlayer(tunnel, userId, playerSocket);
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
  } else {
    if (!userUdpSockets.has(userId)) {
      console.log(`[relay] Restoring missing UDP socket for existing tunnel ${userId} during phone-ready`);
      startUserUdpSocket(userId, tunnel.port, getBedrockPhoneSocket);
    }
  }

  tunnel.lastReadyAt = Date.now();
  return res.json({ ok: true, tunnelExists: true, ip: PUBLIC_HOST, port: tunnel.port });
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
    
    phoneSocket.setTimeout(getPhonePoolIdleTimeoutMs());
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
    
    const poolSize = t.phoneSocketPool.length;
    const hasBedrockSocket = !!(t.bedrockPhoneSocket && !t.bedrockPhoneSocket.destroyed);
    const hasUdp = userUdpSockets.has(userId);

    if (hasUdp && poolSize === 0 && !hasBedrockSocket) {
      t.staleCycles = (t.staleCycles || 0) + 1;
      console.warn(
        `[cleanup] ${userId} - STALE bedrock tunnel (cycle ${t.staleCycles}/3): ` +
        `pool=0, no bedrockSocket. Bedrock frames are being silently dropped.`
      );
      if (t.staleCycles >= 3) {
        console.warn(`[cleanup] ${userId} - Evicting stale UDP socket after ${t.staleCycles} cycles to force client reconnect.`);
        const udpSock = userUdpSockets.get(userId);
        if (udpSock) {
          try { udpSock.close(); } catch (_) {}
          userUdpSockets.delete(userId);
          bedrockClientMap.delete(userId);
        }
        t.staleCycles = 0;
      }
    } else {
      t.staleCycles = 0;
      if (!hasUdp && (poolSize > 0 || hasBedrockSocket)) {
        console.log(`[cleanup] Restoring missing UDP socket for active tunnel ${userId}`);
        startUserUdpSocket(userId, t.port, getBedrockPhoneSocket);
      }
    }

    console.log(
      `[cleanup] ${userId} - pool: ${poolSize}, ` +
      `pending: ${t.pendingPlayers.length}, udp: ${hasUdp || userUdpSockets.has(userId)}, ` +
      `bedrockSocket: ${hasBedrockSocket}, staleCycles: ${t.staleCycles}`
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

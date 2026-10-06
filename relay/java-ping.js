const { getServerStatus } = require('./bedrock-ping');

const STATUS_REQUEST_TIMEOUT_MS = 2_500;
const DEFAULT_JAVA_VERSION = '1.21.11';
const DEFAULT_JAVA_PROTOCOL = 774;

// Minecraft switched to year-based version names after 1.21.11 (26.1, 26.2, ...).
// 26.2 and 26.3 are the numbers ViaVersion reports on those servers; the 26.1 line is
// the step between them and 1.21.11.
const JAVA_PROTOCOL_BY_VERSION = {
  '26.3': 777,
  '26.2': 776,
  '26.1.2': 775,
  '26.1.1': 775,
  '26.1': 775,
  '1.21.11': 774,
  '1.21.10': 773,
  '1.21.9': 773,
  '1.21.8': 772,
  '1.21.7': 772,
  '1.21.6': 771,
  '1.21.5': 770,
  '1.21.4': 769,
  '1.21.3': 768,
  '1.21.2': 768,
  '1.21.1': 767,
  '1.21': 767,
  '1.20.6': 766,
  '1.20.5': 766,
  '1.20.4': 765,
  '1.20.3': 765,
  '1.20.2': 764,
  '1.20.1': 763,
  '1.20': 763,
  '1.19.4': 762,
  '1.19.3': 761,
  '1.19.2': 760,
  '1.19.1': 760,
  '1.19': 759,
  '1.18.2': 758,
  '1.18.1': 757,
  '1.18': 757,
  '1.17.1': 756,
  '1.17': 755,
  '1.16.5': 754,
  '1.16.4': 754
};

function varIntBuffer(val) {
  const bytes = [];
  while (true) {
    let byte = val & 0x7f;
    val >>>= 7;
    if (val !== 0) {
      byte |= 0x80;
    }
    bytes.push(byte);
    if (val === 0) break;
  }
  return Buffer.from(bytes);
}

function readVarInt(buf, offset = 0) {
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
  const lengthVarInt = readVarInt(buf, offset);
  if (!lengthVarInt) return null;

  const payloadStart = offset + lengthVarInt.size;
  const payloadEnd = payloadStart + lengthVarInt.value;
  if (payloadEnd > buf.length) return null;

  const packetIdVarInt = readVarInt(buf, payloadStart);
  if (!packetIdVarInt) return null;

  return {
    packetId: packetIdVarInt.value,
    payload: buf.subarray(payloadStart + packetIdVarInt.size, payloadEnd),
    nextOffset: payloadEnd
  };
}

function wrapPacket(packetId, payload = Buffer.alloc(0)) {
  const packetIdBuf = varIntBuffer(packetId);
  const dataLenBuf = varIntBuffer(packetIdBuf.length + payload.length);
  return Buffer.concat([dataLenBuf, packetIdBuf, payload]);
}

function normalizeJavaVersion(version) {
  // "1.21.11" and the year-based names that followed it ("26.3"). Matching only "1.x" made
  // every 26.x server show up in the multiplayer list as 1.21.11.
  const match = String(version || '').match(/\b(?:1|[2-9]\d)\.\d+(?:\.\d+)?\b/);
  return match ? match[0] : DEFAULT_JAVA_VERSION;
}

// For a version this table does not know yet, answer with the protocol the client asked
// with: the name is still right, and the client is not told it is incompatible on a guess.
function protocolForJavaVersion(version, clientProtocol) {
  const normalized = normalizeJavaVersion(version);
  const known = JAVA_PROTOCOL_BY_VERSION[normalized];
  if (known) return known;
  return Number.isInteger(clientProtocol) && clientProtocol > 0 ? clientProtocol : DEFAULT_JAVA_PROTOCOL;
}

function createJavaSLPResponse(port, clientProtocol) {
  const status = getServerStatus(port) || {};
  const versionName = normalizeJavaVersion(status.version);
  const motd = status.motd || 'A Minecraft Server';
  const players = Number.isFinite(Number(status.players)) ? Math.max(0, Number(status.players) | 0) : 0;
  const maxPlayers = Number.isFinite(Number(status.maxPlayers)) ? Math.max(1, Number(status.maxPlayers) | 0) : 20;

  const responseObj = {
    version: {
      name: versionName,
      protocol: protocolForJavaVersion(versionName, clientProtocol)
    },
    players: {
      max: maxPlayers,
      online: players,
      sample: []
    },
    description: {
      text: motd
    }
  };

  const jsonStr = JSON.stringify(responseObj);
  const jsonBuf = Buffer.from(jsonStr, 'utf8');
  
  const jsonLen = varIntBuffer(jsonBuf.length);

  return wrapPacket(0x00, Buffer.concat([jsonLen, jsonBuf]));
}

function createJavaSLPResponseDirect(status, clientProtocol) {
  const versionName = normalizeJavaVersion(status?.version);
  const motd = status?.motd || 'A Minecraft Server';
  const players = Number.isFinite(Number(status?.players)) ? Math.max(0, Number(status.players) | 0) : 0;
  const maxPlayers = Number.isFinite(Number(status?.maxPlayers)) ? Math.max(1, Number(status.maxPlayers) | 0) : 20;

  const responseObj = {
    version: {
      name: versionName,
      protocol: protocolForJavaVersion(versionName, clientProtocol)
    },
    players: {
      max: maxPlayers,
      online: players,
      sample: []
    },
    description: {
      text: motd
    }
  };

  const jsonStr = JSON.stringify(responseObj);
  const jsonBuf = Buffer.from(jsonStr, 'utf8');
  const jsonLen = varIntBuffer(jsonBuf.length);

  return wrapPacket(0x00, Buffer.concat([jsonLen, jsonBuf]));
}

function createJavaPongResponse(payload) {
  return wrapPacket(0x01, payload);
}

function parseHandshake(buf) {
  const packet = readPacket(buf, 0);
  if (!packet || packet.packetId !== 0x00) return null;

  let offset = 0;
  const protocol = readVarInt(packet.payload, offset);
  if (!protocol) return null;
  offset += protocol.size;

  const addressLength = readVarInt(packet.payload, offset);
  if (!addressLength) return null;
  offset += addressLength.size;

  if (offset + addressLength.value + 2 > packet.payload.length) return null;
  offset += addressLength.value + 2;

  const nextState = readVarInt(packet.payload, offset);
  if (!nextState) return null;

  return {
    protocol: protocol.value,
    nextState: nextState.value,
    remaining: buf.subarray(packet.nextOffset)
  };
}

function handleStatusPackets(socket, port, initialBuffer, clientProtocol) {
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

  const timeout = setTimeout(() => closeSoon(0), STATUS_REQUEST_TIMEOUT_MS);

  const processBufferedPackets = () => {
    let offset = 0;

    while (offset < buffer.length) {
      const packet = readPacket(buffer, offset);
      if (!packet) break;

      offset = packet.nextOffset;

      if (packet.packetId === 0x00 && !respondedToStatus) {
        respondedToStatus = true;
        socket.write(createJavaSLPResponse(port, clientProtocol));
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
  processBufferedPackets();
}

function handleJavaPing(socket, chunk, port) {
  // Legacy Ping (0xFE)
  if (chunk[0] === 0xFE) {
    const status = getServerStatus(port) || {};
    const versionName = normalizeJavaVersion(status.version);
    const motd = status.motd || 'A Minecraft Server';
    const players = status.players || 0;
    const maxPlayers = status.maxPlayers || 20;
    
    // §1\0Protocol\0Version\0MOTD\0Players\0Max
    const payload = `\xa71\x00${protocolForJavaVersion(versionName)}\x00${versionName}\x00${motd}\x00${players}\x00${maxPlayers}`;
    const buf = Buffer.alloc(3 + payload.length * 2);
    buf[0] = 0xFF; // Kick packet
    buf.writeUInt16BE(payload.length, 1);
    buf.write(payload, 3, payload.length * 2, 'utf16le');
    socket.write(buf);
    
    // Wait for the response to be written before destroying
    setTimeout(() => { if (!socket.destroyed) socket.end(); }, 100);
    return true;
  }

  // Modern SLP: handshake(nextState=1), status request, optional ping echo.
  const handshake = parseHandshake(chunk);
  if (!handshake) return false;

  if (handshake.nextState === 1) {
    handleStatusPackets(socket, port, handshake.remaining, handshake.protocol);
    return true;
  }

  if (handshake.nextState === 2) {
    // Login request, MUST proxy to the phone.
    return false;
  }

  return false;
}

module.exports = {
  handleJavaPing,
  normalizeJavaVersion,
  protocolForJavaVersion,
  createJavaSLPResponse,
  createJavaSLPResponseDirect,
  createJavaPongResponse,
  readPacket,
  readVarInt
};

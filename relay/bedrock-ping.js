'use strict';

const dgram = require('dgram');

const RAKNET_MAGIC = Buffer.from([
  0x00, 0xff, 0xff, 0x00, 0xfe, 0xfe, 0xfe, 0xfe,
  0xfd, 0xfd, 0xfd, 0xfd, 0x12, 0x34, 0x56, 0x78
]);

const SERVER_GUID = 0xDEADBEEFCAFE1234n;
// Only used when the phone's own Bedrock server has not answered a ping recently.
// Geyser accepts current Bedrock clients whatever the Java version, so advertise the
// current Bedrock release (what Geyser 2.11.3 reports) rather than an older one.
const DEFAULT_PROTOCOL = '2193';
const DEFAULT_VERSION = '26.52';
const statusMap = new Map();
// Ping replies captured from the phone's real Bedrock server (Geyser), per relay port.
// Bedrock clients refuse servers whose advertised protocol is older than their own, and
// guessing the protocol from the Java version kept advertising old ones, so the real
// reply is preferred whenever there is a recent one.
const realMotdMap = new Map();
const REAL_MOTD_TTL_MS = 60_000;
const DEBUG_NETWORK = process.env.POCKETCRAFT_DEBUG_NETWORK === '1';
let pingLogCount = 0;

function shouldLogPing() {
  if (!DEBUG_NETWORK) return false;
  pingLogCount += 1;
  return pingLogCount <= 10 || pingLogCount % 500 === 0;
}

function normalizeMotdPart(value, fallback) {
  return String(value ?? fallback)
    .replace(/[;\r\n]/g, ' ')
    .trim() || fallback;
}

function buildMotd(entry, port = 19132) {
  const finalPort = String(port || 19132);
  if (!entry) {
    return `MCPE;PocketCraft;${DEFAULT_PROTOCOL};${DEFAULT_VERSION};0;20;12345;PocketCraft Server;Survival;1;${finalPort};${finalPort};`;
  }

  const motd = normalizeMotdPart(entry.motd, 'PocketCraft');
  // entry.version is the Java version of the server; it says nothing about which Bedrock
  // clients Geyser accepts, and mapping it to old Bedrock protocols made current clients
  // treat the server as outdated.
  const version = DEFAULT_VERSION;
  const protocol = DEFAULT_PROTOCOL;
  const players = Number.isFinite(Number(entry.players)) ? Math.max(0, Number(entry.players) | 0) : 0;
  const maxPlayers = Number.isFinite(Number(entry.maxPlayers)) ? Math.max(1, Number(entry.maxPlayers) | 0) : 20;

  return `MCPE;${motd};${protocol};${version};${players};${maxPlayers};12345;PocketCraft Server;Survival;1;${finalPort};${finalPort};`;
}

function buildPong(pingTime, motd) {
  const pingId = typeof pingTime === 'bigint' ? pingTime : BigInt(pingTime);
  const motdBuf = Buffer.from(String(motd || buildMotd(null)), 'utf8');
  const buf = Buffer.alloc(1 + 8 + 8 + RAKNET_MAGIC.length + 2 + motdBuf.length);
  let o = 0;

  buf.writeUInt8(0x1c, o++);
  buf.writeBigUInt64BE(BigInt.asUintN(64, pingId), o); o += 8;
  buf.writeBigUInt64BE(BigInt.asUintN(64, SERVER_GUID), o); o += 8;
  RAKNET_MAGIC.copy(buf, o); o += RAKNET_MAGIC.length;
  buf.writeUInt16BE(motdBuf.length, o); o += 2;
  motdBuf.copy(buf, o);

  return buf;
}

function getMotd(port) {
  const real = port ? getRealMotd(port) : null;
  if (real) return real;
  const entry = port ? statusMap.get(port) : statusMap.values().next().value;
  if (!entry) return null;
  return buildMotd(entry, port);
}

/**
 * Remembers the MOTD string from a pong the phone's Bedrock server sent, with its
 * IPv4/IPv6 port fields pointed at the relay port players actually use.
 */
function setRealMotd(port, motd) {
  const parsedPort = Number(port);
  if (!Number.isInteger(parsedPort) || parsedPort <= 0 || parsedPort >= 65536) return;
  const text = String(motd || '');
  if (!text.startsWith('MCPE;') && !text.startsWith('MCEE;')) return;
  const parts = text.split(';');
  if (parts.length > 11) {
    parts[10] = String(parsedPort);
    parts[11] = String(parsedPort);
  }
  realMotdMap.set(parsedPort, { motd: parts.join(';'), at: Date.now() });
}

function getRealMotd(port) {
  const entry = realMotdMap.get(Number(port));
  if (!entry) return null;
  if (Date.now() - entry.at > REAL_MOTD_TTL_MS) return null;
  return entry.motd;
}

/** MOTD string inside a RakNet unconnected pong (0x1c), or null. */
function parsePongMotd(packet) {
  if (!Buffer.isBuffer(packet) || packet.length < 35 || packet[0] !== 0x1c) return null;
  const len = packet.readUInt16BE(33);
  if (35 + len > packet.length) return null;
  return packet.toString('utf8', 35, 35 + len);
}

function isRakNetPing(msg) {
  if (!Buffer.isBuffer(msg)) return false;
  if (msg.length < 1 + 8 + RAKNET_MAGIC.length) return false;
  // Support both 0x01 (ID_UNCONNECTED_PING) and 0x02 (ID_UNCONNECTED_PING_OPEN_CONNECTIONS)
  if (msg[0] !== 0x01 && msg[0] !== 0x02) return false;

  const magicOffset = 1 + 8;
  return msg.subarray(magicOffset, magicOffset + RAKNET_MAGIC.length).equals(RAKNET_MAGIC);
}

function startBedrockPing(udpPort = 19132) {
  const server = dgram.createSocket({ type: 'udp4', reuseAddr: true });

  server.on('error', (err) => {
    console.error(`[BedrockPing] UDP error on port ${udpPort}:`, err.message);
  });

  server.on('message', (msg, rinfo) => {
    if (!isRakNetPing(msg)) return;

    const logPing = shouldLogPing();
    if (logPing) {
      console.log(`[BedrockPing] Got ping from ${rinfo.address}:${rinfo.port}, len=${msg.length}`);
    }

    try {
      const pingTime = msg.readBigUInt64BE(1);
      const motd = getMotd();
      if (!motd) return;
      const pong = buildPong(pingTime, motd);
      server.send(pong, 0, pong.length, rinfo.port, rinfo.address, (err) => {
        if (err) {
          console.error(`[BedrockPing] Pong send error to ${rinfo.address}:${rinfo.port}:`, err.message);
        } else if (logPing) {
          console.log(`[BedrockPing] Pong sent to ${rinfo.address}:${rinfo.port}, len=${pong.length}`);
        }
      });
    } catch (err) {
      console.error(`[BedrockPing] Ping handler error from ${rinfo.address}:${rinfo.port}:`, err.message);
    }
  });

  server.bind(udpPort, '0.0.0.0', () => {
    console.log(`[BedrockPing] UDP pong on port ${udpPort}`);
  });

  return server;
}

function updateServerStatus(port, status) {
  const parsedPort = Number(port);
  if (!Number.isInteger(parsedPort) || parsedPort <= 0 || parsedPort >= 65536) {
    console.warn(`[BedrockPing] Ignoring status update with invalid port=${port}`);
    return;
  }
  statusMap.set(parsedPort, status || {});
}

function clearServerStatus(port) {
  statusMap.delete(Number(port));
  realMotdMap.delete(Number(port));
}

function getServerStatus(port) {
  return port ? statusMap.get(port) : statusMap.values().next().value;
}

module.exports = {
  startBedrockPing,
  updateServerStatus,
  buildPong,
  getMotd,
  getServerStatus,
  clearServerStatus,
  isRakNetPing,
  setRealMotd,
  getRealMotd,
  parsePongMotd
};

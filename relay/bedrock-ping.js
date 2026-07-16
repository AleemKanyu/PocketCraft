'use strict';

const dgram = require('dgram');

const RAKNET_MAGIC = Buffer.from([
  0x00, 0xff, 0xff, 0x00, 0xfe, 0xfe, 0xfe, 0xfe,
  0xfd, 0xfd, 0xfd, 0xfd, 0x12, 0x34, 0x56, 0x78
]);

const SERVER_GUID = 0xDEADBEEFCAFE1234n;
const DEFAULT_PROTOCOL = '800';
const DEFAULT_VERSION = '1.22.0';
const statusMap = new Map();
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

function getProtocolForVersion(version) {
  if (!version) return DEFAULT_PROTOCOL;
  const v = version.trim();
  if (v.startsWith('1.20.8')) return '671';
  if (v.startsWith('1.21.1') || v.startsWith('1.21.2')) return '723';
  if (v.startsWith('1.21.3')) return '729';
  if (v.startsWith('1.21.4')) return '748';
  if (v.startsWith('1.21.5')) return '766';
  if (v.startsWith('1.21.0') || v === '1.21') return '685';
  if (v.startsWith('1.22.0')) return '800';
  return DEFAULT_PROTOCOL;
}

function buildMotd(entry, port = 19132) {
  const finalPort = String(port || 19132);
  if (!entry) {
    return `MCPE;PocketCraft;${DEFAULT_PROTOCOL};${DEFAULT_VERSION};0;20;12345;PocketCraft Server;Survival;1;${finalPort};${finalPort};`;
  }

  const motd = normalizeMotdPart(entry.motd, 'PocketCraft');
  const version = normalizeMotdPart(entry.version, DEFAULT_VERSION);
  const protocol = getProtocolForVersion(version);
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
  const entry = port ? statusMap.get(port) : statusMap.values().next().value;
  if (!entry) return null;
  return buildMotd(entry, port);
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
  isRakNetPing
};

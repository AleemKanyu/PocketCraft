function writeRakNetAddress(buf, offset, ip, port) {
  const parts = ip.split('.').map(x => Number(x));
  buf[offset] = 0x04;
  buf[offset + 1] = 0xFF - parts[0];
  buf[offset + 2] = 0xFF - parts[1];
  buf[offset + 3] = 0xFF - parts[2];
  buf[offset + 4] = 0xFF - parts[3];
  buf.writeUInt16BE(port, offset + 5);
  return true;
}

function encapsulatedHeaderLength(flags) {
  let length = 3;
  const reliability = flags >> 5;
  if (reliability === 2 || reliability === 3 || reliability === 4 || reliability === 6 || reliability === 7) length += 3;
  if (reliability === 1 || reliability === 3 || reliability === 4 || reliability === 5 || reliability === 7) length += 4;
  if ((flags & 0x10) !== 0) length += 10;
  return length;
}

function rewriteClientNewIncomingConnection(payload, localGeyserIp = "127.0.0.1", localGeyserPort = 19132) {
  if (!Buffer.isBuffer(payload) || payload.length < 8) return payload;
  const packetId = payload.readUInt8(0);
  if (packetId >= 0x80 && packetId <= 0x8D) {
    const out = Buffer.from(payload);
    let offset = 4;
    let rewrote = false;
    while (offset + 3 <= out.length) {
      const flags = out.readUInt8(offset);
      const bitLength = out.readUInt16BE(offset + 1);
      const byteLength = Math.ceil(bitLength / 8);
      const headerLen = encapsulatedHeaderLength(flags);
      const payloadOffset = offset + headerLen;
      const payloadEnd = payloadOffset + byteLength;
      
      if (payloadEnd > out.length) break;
      
      if (byteLength > 0 && out[payloadOffset] === 0x13) {
        // 0x13: NewIncomingConnection
        // ServerAddress is at payloadOffset + 1
        if (payloadOffset + 8 <= out.length && out[payloadOffset + 1] === 0x04) {
          writeRakNetAddress(out, payloadOffset + 1, localGeyserIp, localGeyserPort);
          console.log("Rewrote NewIncomingConnection ServerAddress to", localGeyserIp, localGeyserPort);
          rewrote = true;
        }
      }
      offset = payloadEnd;
    }
    return rewrote ? out : payload;
  }
  return payload;
}

// Test packet
const buf = Buffer.alloc(20);
buf[0] = 0x80;
buf[1] = 0; buf[2] = 0; buf[3] = 0; // seq
buf[4] = 0; // flags (reliable = 0)
buf.writeUInt16BE(8 * 8, 5); // bitLength
buf[7] = 0x13; // NewIncomingConnection
buf[8] = 0x04; // IPv4
buf.writeUInt32BE(0, 9);
buf.writeUInt16BE(0, 13);
const rewritten = rewriteClientNewIncomingConnection(buf);
console.log(rewritten);

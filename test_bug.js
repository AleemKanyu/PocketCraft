const buf = Buffer.alloc(100);
buf.writeUInt8(0x84, 0); // FrameSet
buf.writeUInt16BE(0, 1); buf.writeUInt8(0, 3); // seq
// Single frame, reliable, length = 96 (12 bytes)
buf.writeUInt8(0x40, 4); // flags
buf.writeUInt16BE(96 * 8, 5); // bitLength
buf.writeUInt8(0, 7); buf.writeUInt8(0, 8); buf.writeUInt8(0, 9); // Reliable index
buf.writeUInt8(0x10, 10); // ConnectionRequestAccepted

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

function writeRakNetAddress(buf, offset, ip, port) { return true; }

function rewriteNewIncomingConnectionPacket(packet, publicIp, relayPort, clientIp, clientPort) {
  const out = Buffer.from(packet);
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

try {
  rewriteEncapsulatedRakNetPackets(buf, "1.2.3.4", 1234, "5.6.7.8", 5678);
  console.log("No crash.");
} catch(e) {
  console.log("CRASH!", e);
}

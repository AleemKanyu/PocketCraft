function rewriteClientNewIncomingConnection(payload, localGeyserIp = "127.0.0.1", localGeyserPort = 19132) {
  if (!Buffer.isBuffer(payload) || payload.length < 8) return payload;
  const packetId = payload.readUInt8(0);
  if (packetId >= 0x80 && packetId <= 0x8D) {
    let out = Buffer.from(payload);
    let offset = 4;
    let rewrote = false;
    
    // We only support changing one packet per frame, since shifting changes lengths
    while (offset + 3 <= out.length) {
      const flags = out.readUInt8(offset);
      const bitLength = out.readUInt16BE(offset + 1);
      const byteLength = Math.ceil(bitLength / 8);
      // Simplified header len
      const reliability = (flags & 0xe0) >> 5;
      const hasSplit = (flags & 0x10) !== 0;
      let headerLen = 3;
      if (reliability === 1 || reliability === 2 || reliability === 3 || reliability === 4 || reliability === 6 || reliability === 7) headerLen += 3;
      if (reliability === 3 || reliability === 4 || reliability === 7) headerLen += 4;
      if (hasSplit) headerLen += 10;
      
      const payloadOffset = offset + headerLen;
      const payloadEnd = payloadOffset + byteLength;
      
      if (payloadEnd > out.length) break;
      
      if (byteLength > 0 && out[payloadOffset] === 0x13) {
        if (payloadOffset + 8 <= out.length && out[payloadOffset + 1] === 0x04) {
          // IPv4
          console.log("Rewriting IPv4");
          // writeRakNetAddress
        } else if (payloadOffset + 30 <= out.length && out[payloadOffset + 1] === 0x06) {
          console.log("Rewriting IPv6 to IPv4 and shifting!");
          
          // Create a new RakNet IPv4 address buffer
          const ipBuf = Buffer.alloc(7);
          ipBuf[0] = 0x04;
          const parts = localGeyserIp.split('.').map(x => Number(x));
          ipBuf[1] = 0xFF - parts[0];
          ipBuf[2] = 0xFF - parts[1];
          ipBuf[3] = 0xFF - parts[2];
          ipBuf[4] = 0xFF - parts[3];
          ipBuf.writeUInt16BE(localGeyserPort, 5);
          
          // Construct new buffer
          const pre = out.subarray(0, payloadOffset + 1); // Up to packet ID 0x13
          const post = out.subarray(payloadOffset + 30); // After the 29-byte IPv6 address
          
          // We need to update bitLength in the header!
          // We removed 29 bytes and added 7 bytes -> difference is -22 bytes (-176 bits).
          const newByteLength = byteLength - 22;
          const newBitLength = bitLength - 176;
          
          const newOut = Buffer.concat([pre, ipBuf, post]);
          newOut.writeUInt16BE(newBitLength, offset + 1); // Update bitLength
          
          out = newOut;
          rewrote = true;
          break; // Stop after rewriting since offsets changed
        }
      }
      offset = payloadEnd;
    }
    return rewrote ? out : payload;
  }
  return payload;
}

const b = Buffer.alloc(100);
b[0] = 0x84; // frame set
b[4] = 0; // flags
b.writeUInt16BE(30 * 8, 5); // 30 bytes
b[7] = 0x13; // new incoming connection
b[8] = 0x06; // ipv6
const changed = rewriteClientNewIncomingConnection(b);
console.log(changed.length);
console.log(changed.readUInt16BE(5) / 8); // Should be 30 - 22 = 8

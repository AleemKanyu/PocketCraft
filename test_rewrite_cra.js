function writeRakNetAddress2(buf, offset, ip, port) {
  const parts = ip.split('.').map(x => Number(x));
  buf[offset] = 0x04;
  buf[offset + 1] = 0xFF - parts[0];
  buf[offset + 2] = 0xFF - parts[1];
  buf[offset + 3] = 0xFF - parts[2];
  buf[offset + 4] = 0xFF - parts[3];
  buf.writeUInt16BE(port, offset + 5);
  return true;
}

function rewriteNewIncomingConnectionPacket(packet, publicIp, relayPort, clientIp, clientPort) {
  const out = Buffer.from(packet);
  writeRakNetAddress2(out, 1, clientIp, clientPort);
  let rewriteCount = 0;
  let offset = 10;
  while (offset + 7 <= out.length) {
    if (out[offset] !== 0x04) break;
    writeRakNetAddress2(out, offset, publicIp, relayPort);
    rewriteCount++;
    offset += 7;
  }
  return out;
}

const buf = Buffer.alloc(10 + 10 * 7 + 16);
buf[0] = 0x10;
writeRakNetAddress2(buf, 1, "192.168.95.51", 12345);
buf.writeUInt16BE(0, 8);
let off = 10;
for (let i = 0; i < 10; i++) {
  writeRakNetAddress2(buf, off, "192.168.95.51", 19132);
  off += 7;
}
buf.writeBigInt64BE(123n, off);
buf.writeBigInt64BE(456n, off + 8);

const rewritten = rewriteNewIncomingConnectionPacket(buf, "13.201.57.41", 29677, "157.48.10.59", 63117);
console.log(rewritten);

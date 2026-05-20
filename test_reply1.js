const buf = Buffer.alloc(28);
buf[0] = 0x06; // Packet ID
// RakNet magic (16 bytes)
// serverGuid (8 bytes)
buf.writeBigUInt64BE(123456789n, 17);
// useSecurity (1 byte)
buf[25] = 0x00;
// mtu (2 bytes)
buf.writeUInt16BE(1492, 26);
console.log(buf);

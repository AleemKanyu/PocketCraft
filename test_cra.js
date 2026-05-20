const buf = Buffer.alloc(100);
buf[0] = 0x10;
// 1-7: client address
buf[1] = 0x04; buf.writeUInt32BE(0, 2); buf.writeUInt16BE(0, 6);
// 8-9: system index
buf.writeUInt16BE(0, 8);
// 10+: internal addresses. Wait! In Geyser/netty-raknet, how many internal addresses are sent?

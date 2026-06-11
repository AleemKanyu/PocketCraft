const { handleJavaPing } = require('./java-ping.js');
const assert = require('assert');

// Construct a valid handshake packet.
// packet length (varint), packet id (varint=0x00), protocol (varint), server address (string), port (ushort), next state (varint)

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

// 0x00 (Packet ID) + Protocol (763) + "localhost" + 25565 + 1 (Status)
const protocol = varIntBuffer(763);
const host = Buffer.from("localhost");
const hostLen = varIntBuffer(host.length);
const port = Buffer.alloc(2);
port.writeUInt16BE(25565);
const state = varIntBuffer(1);

const payload = Buffer.concat([varIntBuffer(0x00), protocol, hostLen, host, port, state]);
const pkt = Buffer.concat([varIntBuffer(payload.length), payload]);

const socket = {
  write: () => {},
  end: () => {},
  on: () => {}
};

console.log("Result:", handleJavaPing(socket, pkt, 1234));

function encapsulatedHeaderLength(flags, buf, offset) {
  const reliability = (flags & 0xe0) >> 5;
  const hasSplit = (flags & 0x10) !== 0;
  let headerLen = 3;

  if (reliability === 1 || reliability === 2 || reliability === 3 || reliability === 4 || reliability === 6 || reliability === 7) {
    headerLen += 3; // +3? No, reliability 1 is Reliable (MessageIndex 3 bytes)
    // Actually Reliable is 3 bytes (message index 24 bits)
  }
  if (reliability === 3 || reliability === 4 || reliability === 7) {
    headerLen += 4; // +4 for ordering index (3 bytes) and ordering channel (1 byte)
  }
  if (hasSplit) headerLen += 10;

  if (offset + headerLen > buf.length) return -1;
  return headerLen;
}
console.log(encapsulatedHeaderLength(0x40, Buffer.alloc(100), 0));

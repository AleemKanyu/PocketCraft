const tunnel = { bedrockBuffer: Buffer.alloc(0) };
const userId = "test";
const chunk1 = Buffer.from('0300050102030412344142434445', 'hex'); // Frame: 0x03, len=5, IP=1.2.3.4, port=0x1234, payload="ABCDE"
const chunk2 = Buffer.from('0300050102030412344142434445', 'hex');

function handlePhoneFrameData(uid, frame) {
  console.log("Handled frame of length", frame.length);
}

const onBedrockData = (chunk) => {
    if (!chunk || chunk.length === 0) return;

    if (tunnel.bedrockBuffer.length === 0) {
      tunnel.bedrockBuffer = chunk;
    } else {
      tunnel.bedrockBuffer = Buffer.concat([tunnel.bedrockBuffer, chunk]);
    }
    
    while (tunnel.bedrockBuffer.length > 0) {
      const opcodeIndex = tunnel.bedrockBuffer.indexOf(0x03);
      if (opcodeIndex === -1) {
        console.log("Dropped no 0x03");
        tunnel.bedrockBuffer = Buffer.alloc(0);
        return;
      }
      if (opcodeIndex > 0) {
        tunnel.bedrockBuffer = tunnel.bedrockBuffer.subarray(opcodeIndex);
      }
      if (tunnel.bedrockBuffer.length < 9) return;
    
      const payloadLen = tunnel.bedrockBuffer.readUInt16BE(1);
      const frameLength = 9 + payloadLen;
      if (tunnel.bedrockBuffer.length < frameLength) return;
    
      const frame = tunnel.bedrockBuffer.subarray(0, frameLength);
      tunnel.bedrockBuffer = tunnel.bedrockBuffer.subarray(frameLength);
      handlePhoneFrameData(userId, frame);
    }
};

onBedrockData(chunk1);
onBedrockData(chunk2);
onBedrockData(Buffer.concat([chunk1, chunk2]));

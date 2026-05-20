const fs = require('fs');
const logs = fs.readFileSync('full_device_logcat.txt', 'utf8');
const lines = logs.split('\n');

for (const line of lines) {
  if (line.includes('packet=0x06') && line.includes('payload=')) {
    const match = line.match(/payload=(\d+)/);
    if (match) {
      console.log('Found 0x06 packet with length:', match[1]);
      break;
    }
  }
}

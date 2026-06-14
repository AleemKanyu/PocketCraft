import sys
import struct

with open(sys.argv[1], "rb") as f:
    data = bytearray(f.read())

tags = [0x6fffffff, 0x6ffffffe, 0x6ffffff0]

for i in range(0, len(data) - 16, 8):
    tag = struct.unpack("<Q", data[i:i+8])[0]
    if tag in tags:
        print(f"Found tag {hex(tag)} at {hex(i)}, replacing with DT_RPATH (15)")
        data[i:i+8] = struct.pack("<Q", 15)
        data[i+8:i+16] = struct.pack("<Q", 0)

with open(sys.argv[2], "wb") as f:
    f.write(data)

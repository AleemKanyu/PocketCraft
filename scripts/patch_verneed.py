import sys
import struct

with open(sys.argv[1], "rb") as f:
    data = bytearray(f.read())

# Tag for DT_VERNEEDNUM is 0x6fffffff
tag_verneednum = struct.pack("<Q", 0x6fffffff)

for i in range(len(data) - 16):
    if data[i:i+8] == tag_verneednum:
        print("Found DT_VERNEEDNUM, setting value to 0")
        # The next 8 bytes are the value, zero them out
        data[i+8:i+16] = b'\x00' * 8

with open(sys.argv[2], "wb") as f:
    f.write(data)

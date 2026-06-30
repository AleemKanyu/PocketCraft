import sys
import zipfile
import struct

def remap_class_bytes(data):
    if not data.startswith(b'\xca\xfe\xba\xbe'):
        return data
    
    # Header: magic (4), minor (2), major (2)
    cp_count = struct.unpack(">H", data[8:10])[0]
    
    offset = 10
    new_data = bytearray(data[:10])
    
    i = 1
    while i < cp_count:
        tag = data[offset]
        if tag == 1: # Utf8
            length = struct.unpack(">H", data[offset+1:offset+3])[0]
            val = data[offset+3 : offset+3+length]
            
            # Perform remapping
            new_val = val
            if b"org/bukkit/craftbukkit/v1_21_R1/" in val:
                new_val = val.replace(b"org/bukkit/craftbukkit/v1_21_R1/", b"org/bukkit/craftbukkit/")
            elif b"org.bukkit.craftbukkit.v1_21_R1" in val:
                new_val = val.replace(b"org.bukkit.craftbukkit.v1_21_R1", b"org.bukkit.craftbukkit")
                
            new_len = len(new_val)
            new_data.append(tag)
            new_data.extend(struct.pack(">H", new_len))
            new_data.extend(new_val)
            offset += 3 + length
            i += 1
        elif tag in (3, 4, 9, 10, 11, 12, 18): # 4 bytes
            new_data.extend(data[offset : offset+5])
            offset += 5
            i += 1
        elif tag in (5, 6): # 8 bytes (occupies 2 CP slots)
            new_data.extend(data[offset : offset+9])
            offset += 9
            i += 2
        elif tag in (7, 8, 16, 19, 20): # 2 bytes
            new_data.extend(data[offset : offset+3])
            offset += 3
            i += 1
        elif tag == 15: # 3 bytes
            new_data.extend(data[offset : offset+4])
            offset += 4
            i += 1
        elif tag == 17: # Dynamic: 6 bytes
            new_data.extend(data[offset : offset+7])
            offset += 7
            i += 1
        else:
            raise ValueError(f"Unknown constant pool tag: {tag} at offset {offset}")

    # Append the rest of the class file (fields, methods, attributes)
    new_data.extend(data[offset:])
    return bytes(new_data)

def remap_jar(in_path, out_path):
    with zipfile.ZipFile(in_path, 'r') as zh_in:
        with zipfile.ZipFile(out_path, 'w', zipfile.ZIP_DEFLATED) as zh_out:
            for item in zh_in.infolist():
                data = zh_in.read(item.filename)
                if item.filename.endswith('.class'):
                    data = remap_class_bytes(data)
                zh_out.writestr(item, data)

if __name__ == '__main__':
    if len(sys.argv) < 3:
        print("Usage: python remap_jar.py <input.jar> <output.jar>")
        sys.exit(1)
    remap_jar(sys.argv[1], sys.argv[2])
    print("Remapping completed successfully!")

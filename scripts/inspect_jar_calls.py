import zipfile
import struct

def inspect_class(data, class_name):
    if not data.startswith(b'\xca\xfe\xba\xbe'):
        return
    cp_count = struct.unpack(">H", data[8:10])[0]
    
    tags = {}
    values = {}
    offset = 10
    i = 1
    while i < cp_count:
        tag = data[offset]
        tags[i] = tag
        if tag == 1: # Utf8
            length = struct.unpack(">H", data[offset+1:offset+3])[0]
            val = data[offset+3 : offset+3+length]
            values[i] = val
            offset += 3 + length
            i += 1
        elif tag in (3, 4, 9, 10, 11, 12, 18): # 4 bytes
            values[i] = (struct.unpack(">H", data[offset+1:offset+3])[0], struct.unpack(">H", data[offset+3:offset+5])[0])
            offset += 5
            i += 1
        elif tag in (5, 6): # 8 bytes
            offset += 9
            i += 2
        elif tag in (7, 8, 16, 19, 20): # 2 bytes
            values[i] = struct.unpack(">H", data[offset+1:offset+3])[0]
            offset += 3
            i += 1
        elif tag == 15: # 3 bytes
            offset += 4
            i += 1
        elif tag == 17: # 6 bytes
            offset += 7
            i += 1
            
    # Print all method/field refs to class net/minecraft/server/...
    for idx, tag in tags.items():
        if tag in (9, 10, 11):
            class_idx, nt_idx = values[idx]
            class_name_idx = values[class_idx]
            class_name_val = values[class_name_idx]
            
            name_idx, desc_idx = values[nt_idx]
            name_val = values[name_idx]
            desc_val = values[desc_idx]
            
            if b"net/minecraft" in class_name_val:
                print(f"[{class_name}] Ref: class={class_name_val.decode()} name={name_val.decode()} desc={desc_val.decode()}")

with zipfile.ZipFile("app/src/main/assets/plugins/DummyPlayers.jar", "r") as z:
    for name in z.namelist():
        if name.endswith(".class"):
            inspect_class(z.read(name), name)

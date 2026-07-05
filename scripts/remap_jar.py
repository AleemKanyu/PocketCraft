import sys
import zipfile
import struct

METHOD_REPLACEMENTS = {
    (b"an", b"()I"): b"getId",
    (b"cz", b"()Ljava/util/UUID;"): b"getUUID",
    (b"dt", b"()D"): b"getX",
    (b"dv", b"()D"): b"getY",
    (b"dz", b"()D"): b"getZ",
    (b"dG", b"()F"): b"getYRot",
    (b"dE", b"()F"): b"getXRot",
    (b"am", b"()Lnet/minecraft/world/entity/EntityTypes;"): b"getType",
    (b"dr", b"()Lnet/minecraft/world/phys/Vec3D;"): b"getDeltaMovement",
    (b"ct", b"()F"): b"getYHeadRot",
    (b"a_", b"(DDD)V"): b"setPos",
    (b"a", b"(FF)V"): b"setRot",
    (b"b", b"(Lnet/minecraft/network/protocol/Packet;)V"): b"send",
    (b"do", b"()Lnet/minecraft/core/BlockPosition;"): b"blockPosition",
    (b"dO", b"()Lnet/minecraft/world/level/World;"): b"level",
    (b"fX", b"()Lcom/mojang/authlib/GameProfile;"): b"getGameProfile",
    (b"a", b"(Lnet/minecraft/server/level/WorldServer;DDDFF)V"): b"teleportTo",
    (b"ar", b"()Lnet/minecraft/network/syncher/DataWatcher;"): b"getEntityData",
    (b"ar", b"()Lnet/minecraft/network/syncher/SynchedEntityData;"): b"getEntityData",
    (b"a", b"(Lnet/minecraft/server/level/EntityPlayer;Lnet/minecraft/world/entity/Entity$RemovalReason;)V"): b"removePlayerImmediately",
    (b"a", b"(Lnet/minecraft/server/level/EntityPlayer;)V"): b"addNewPlayer",
    (b"c", b"()Ljava/util/List;"): b"getNonDefaultValues",
}

def remap_class_bytes(data):
    if not data.startswith(b'\xca\xfe\xba\xbe'):
        return data
    
    # 1. Parse constant pool
    minor = data[4:6]
    major_val = struct.unpack(">H", data[6:8])[0]
    if major_val > 65:
        major_bytes = struct.pack(">H", 65)
    else:
        major_bytes = data[6:8]
        
    cp_count = struct.unpack(">H", data[8:10])[0]
    
    offsets = {}
    tags = {}
    values = {}
    
    offset = 10
    i = 1
    while i < cp_count:
        offsets[i] = offset
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
        else:
            raise ValueError(f"Unknown constant pool tag: {tag} at offset {offset}")

    entity_player_classes = set()
    for idx, tag in tags.items():
        if tag == 7: # Class
            name_idx = values[idx]
            class_name = values.get(name_idx)
            if class_name in (
                b"net/minecraft/server/level/EntityPlayer", b"net/minecraft/server/level/ServerPlayer",
                b"net/minecraft/server/network/PlayerConnection", b"net/minecraft/server/network/ServerGamePacketListenerImpl",
                b"net/minecraft/server/level/WorldServer", b"net/minecraft/server/level/ServerLevel",
                b"net/minecraft/network/syncher/DataWatcher", b"net/minecraft/network/syncher/SynchedEntityData"
            ):
                entity_player_classes.add(idx)

    nt_replacements = {}
    appended_utf8s = []
    next_cp_idx = cp_count

    for idx, tag in tags.items():
        if tag in (9, 10, 11): # Fieldref, Methodref, InterfaceMethodref
            class_idx, nt_idx = values[idx]
            if class_idx in entity_player_classes:
                name_idx, desc_idx = values[nt_idx]
                name_val = values.get(name_idx)
                desc_val = values.get(desc_idx)
                
                norm_desc = desc_val
                if desc_val:
                    norm_desc = desc_val.replace(b"Lnet/minecraft/server/network/PlayerConnection;", b"Lnet/minecraft/server/network/ServerGamePacketListenerImpl;")
                
                new_name = None
                if (name_val, norm_desc) in METHOD_REPLACEMENTS:
                    new_name = METHOD_REPLACEMENTS[(name_val, norm_desc)]
                elif name_val == b"c" and desc_val in (b"Lnet/minecraft/server/network/PlayerConnection;", b"Lnet/minecraft/server/network/ServerGamePacketListenerImpl;"):
                    new_name = b"connection"
                
                if new_name:
                    if nt_idx not in nt_replacements:
                        nt_replacements[nt_idx] = next_cp_idx
                        appended_utf8s.append(new_name)
                        next_cp_idx += 1

    # 2. Rebuild constant pool, remapping when needed
    offset = 10
    new_data = bytearray(data[:4])
    new_data.extend(minor)
    new_data.extend(major_bytes)
    
    new_cp_count = cp_count + len(appended_utf8s)
    new_data.extend(struct.pack(">H", new_cp_count))
    
    i = 1
    while i < cp_count:
        tag = data[offset]
        if tag == 1: # Utf8
            length = struct.unpack(">H", data[offset+1:offset+3])[0]
            val = data[offset+3 : offset+3+length]
            
            new_val = val
            if b"org/bukkit/craftbukkit/v1_21_R1/" in val:
                new_val = val.replace(b"org/bukkit/craftbukkit/v1_21_R1/", b"org/bukkit/craftbukkit/")
            elif b"org.bukkit.craftbukkit.v1_21_R1" in val:
                new_val = val.replace(b"org.bukkit.craftbukkit.v1_21_R1", b"org.bukkit.craftbukkit")
            elif b"Lnet/minecraft/server/network/PlayerConnection;" in val:
                new_val = val.replace(b"Lnet/minecraft/server/network/PlayerConnection;", b"Lnet/minecraft/server/network/ServerGamePacketListenerImpl;")
            elif b"net/minecraft/server/network/PlayerConnection" in val:
                new_val = val.replace(b"net/minecraft/server/network/PlayerConnection", b"net/minecraft/server/network/ServerGamePacketListenerImpl")
                
            new_len = len(new_val)
            new_data.append(tag)
            new_data.extend(struct.pack(">H", new_len))
            new_data.extend(new_val)
            offset += 3 + length
            i += 1
        elif tag == 12: # NameAndType
            name_idx, desc_idx = struct.unpack(">HH", data[offset+1:offset+5])
            if i in nt_replacements:
                name_idx = nt_replacements[i]
            new_data.append(tag)
            new_data.extend(struct.pack(">HH", name_idx, desc_idx))
            offset += 5
            i += 1
        elif tag in (3, 4, 9, 10, 11, 18): # 4 bytes
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

    for val in appended_utf8s:
        new_data.append(1)
        new_data.extend(struct.pack(">H", len(val)))
        new_data.extend(val)

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

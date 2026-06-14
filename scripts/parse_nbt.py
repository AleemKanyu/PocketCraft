import gzip
import os
import sys

def check_inventory(filepath):
    try:
        with gzip.open(filepath, 'rb') as f:
            data = f.read()
            inv_idx = data.find(b'Inventory')
            if inv_idx != -1:
                print(f"{filepath} contains Inventory tag. Size: {len(data)}")
            else:
                print(f"{filepath} DOES NOT contain Inventory tag. Size: {len(data)}")
    except Exception as e:
        print(f"Error reading {filepath}: {e}")

check_inventory('/data/user/0/com.pocketcraft.server/files/servers/worlds/world/world/playerdata/31c57a4c-5b47-360a-85e4-76a99bbf8a53.dat')
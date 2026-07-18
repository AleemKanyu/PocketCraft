import nbtlib

def inspect_nbt(filepath):
    try:
        nbt_file = nbtlib.load(filepath)
        print(f"--- File: {filepath} ---")
        print("Root keys:", list(nbt_file.keys()))
        if 'Data' in nbt_file:
            data = nbt_file['Data']
            print("Data keys:", list(data.keys()))
            if 'Player' in data:
                print("Player tag exists in level.dat!")
                print("Player keys:", list(data['Player'].keys())[:10])
            else:
                print("Player tag DOES NOT exist in level.dat.")
        else:
            print("No Data key in root.")
    except Exception as e:
        print(f"Error inspecting {filepath}: {e}")

inspect_nbt('test_extract/world/level.dat')
inspect_nbt('test_extract/world/playerdata/31c57a4c-5b47-360a-85e4-76a99bbf8a53.dat')

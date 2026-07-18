import nbtlib

def inject_player():
    try:
        level_file = nbtlib.load('test_extract/world/level.dat')
        player_file = nbtlib.load('test_extract/world/playerdata/31c57a4c-5b47-360a-85e4-76a99bbf8a53.dat')
        
        # In level_file, the root has key 'Data'
        # player_file itself is a Compound tag representing the player
        # We need to copy/assign it to level_file['Data']['Player']
        
        # To avoid schema conflicts, we can copy the player compound tag
        # player_file is a Compound tag, let's check its type
        print("Player tag type:", type(player_file))
        
        # Assign it
        level_file['Data']['Player'] = player_file
        
        # Save to a new file
        level_file.save('test_extract/world/level_modified.dat')
        print("Successfully saved level_modified.dat!")
        
        # Reload and check
        reloaded = nbtlib.load('test_extract/world/level_modified.dat')
        if 'Player' in reloaded['Data']:
            print("Verified: Player tag exists in reloaded NBT!")
            print("Verified Player Inventory tag exists:", 'Inventory' in reloaded['Data']['Player'])
        else:
            print("Failed: Player tag NOT found in reloaded NBT.")
            
    except Exception as e:
        print("Error during player injection:", e)

inject_player()

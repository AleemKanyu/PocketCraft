import os
import shutil
import subprocess
import nbtlib

ZIP_PATH = '/home/aleemkanyu/Downloads/world-20260715-084248.zip'
EXTRACT_DIR = '/home/aleemkanyu/.gemini/antigravity/scratch/PocketCraft/temp_extract'
OUTPUT_ZIP = '/home/aleemkanyu/Downloads/pocketcraft_world_playable.zip'

def main():
    # 1. Clean previous extraction if any
    if os.path.exists(EXTRACT_DIR):
        print(f"Cleaning existing temporary directory: {EXTRACT_DIR}")
        shutil.rmtree(EXTRACT_DIR)
    os.makedirs(EXTRACT_DIR, exist_ok=True)
    
    # 2. Extract necessary directories from the ZIP file using system unzip
    print("Extracting world files from ZIP...")
    cmd = [
        'unzip',
        ZIP_PATH,
        'world/*',
        'world_nether/DIM-1/*',
        'world_the_end/DIM1/*',
        '-d', EXTRACT_DIR
    ]
    # Run the unzip command
    res = subprocess.run(cmd, capture_output=True, text=True)
    if res.returncode not in [0, 1]:  # unzip returns 1 for warnings, which is fine
        print("Unzip failed!")
        print("Stdout:", res.stdout)
        print("Stderr:", res.stderr)
        return
    print("Extraction completed successfully!")
    
    # 3. Merge nether and end into the main world folder
    world_dir = os.path.join(EXTRACT_DIR, 'world')
    nether_src = os.path.join(EXTRACT_DIR, 'world_nether', 'DIM-1')
    end_src = os.path.join(EXTRACT_DIR, 'world_the_end', 'DIM1')
    
    nether_dst = os.path.join(world_dir, 'DIM-1')
    end_dst = os.path.join(world_dir, 'DIM1')
    
    if os.path.exists(nether_src):
        print("Moving Nether (DIM-1) to world folder...")
        shutil.move(nether_src, nether_dst)
    else:
        print("Nether (DIM-1) source not found.")
        
    if os.path.exists(end_src):
        print("Moving End (DIM1) to world folder...")
        shutil.move(end_src, end_dst)
    else:
        print("End (DIM1) source not found.")
        
    # 4. Modify level.dat to inject Makstien's player data
    level_dat_path = os.path.join(world_dir, 'level.dat')
    level_dat_old_path = os.path.join(world_dir, 'level.dat_old')
    player_dat_path = os.path.join(world_dir, 'playerdata', '31c57a4c-5b47-360a-85e4-76a99bbf8a53.dat')
    
    if os.path.exists(level_dat_path) and os.path.exists(player_dat_path):
        print("Injecting Makstien's player data into level.dat...")
        try:
            # Backup original level.dat to level.dat_old
            if os.path.exists(level_dat_old_path):
                os.remove(level_dat_old_path)
            shutil.copy2(level_dat_path, level_dat_old_path)
            
            # Load level.dat and player data
            level_nbt = nbtlib.load(level_dat_path)
            player_nbt = nbtlib.load(player_dat_path)
            
            # Convert player_nbt File to a Compound tag
            from nbtlib.tag import Compound
            player_compound = Compound(player_nbt)
            
            # Inject
            level_nbt['Data']['Player'] = player_compound
            level_nbt.save(level_dat_path)
            print("Successfully updated level.dat with Makstien's player data!")
        except Exception as e:
            print(f"Error modifying level.dat NBT: {e}")
            return
    else:
        print("Error: level.dat or player data file for Makstien was not found in the extracted files.")
        return
        
    # 5. Strip unnecessary files from the world directory
    unnecessary_items = [
        'bukkit.yml',
        'commands.yml',
        'cache',
        'banned-ips.json',
        'banned-players.json',
        '.thanos',
        'paper-world.yml',
        'spigot.yml'
    ]
    for item in unnecessary_items:
        item_path = os.path.join(world_dir, item)
        if os.path.exists(item_path):
            print(f"Removing unnecessary item: {item}")
            if os.path.isdir(item_path):
                shutil.rmtree(item_path)
            else:
                os.remove(item_path)
                
    # 6. Zip the clean world folder
    print(f"Creating playable world ZIP at: {OUTPUT_ZIP}...")
    if os.path.exists(OUTPUT_ZIP):
        os.remove(OUTPUT_ZIP)
        
    new_world_name = 'PocketCraft_World'
    new_world_dir = os.path.join(EXTRACT_DIR, new_world_name)
    shutil.move(world_dir, new_world_dir)
    
    zip_cmd = ['zip', '-r', OUTPUT_ZIP, new_world_name]
    zip_res = subprocess.run(zip_cmd, cwd=EXTRACT_DIR, capture_output=True, text=True)
    if zip_res.returncode != 0:
        print("Zipping failed!")
        print("Stdout:", zip_res.stdout)
        print("Stderr:", zip_res.stderr)
        return
    print("ZIP created successfully!")
    
    # 7. Clean up temporary directory
    print("Cleaning up temporary directory...")
    shutil.rmtree(EXTRACT_DIR)
    print("Conversion complete! The world is ready to play.")

if __name__ == '__main__':
    main()

import nbtlib

level_file = nbtlib.load('test_extract/world/level.dat')
data = level_file['Data']
for key in ['GameType', 'Difficulty', 'DifficultyLocked', 'hardcore', 'LevelName', 'SpawnX', 'SpawnY', 'SpawnZ']:
    if key in data:
        print(f"{key}: {data[key]} ({type(data[key])})")
    else:
        print(f"{key}: NOT FOUND")

player_file = nbtlib.load('test_extract/world/playerdata/31c57a4c-5b47-360a-85e4-76a99bbf8a53.dat')
for key in ['playerGameType', 'Dimension', 'Pos']:
    if key in player_file:
        print(f"Player {key}: {player_file[key]}")

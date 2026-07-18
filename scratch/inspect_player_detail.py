import nbtlib

player_file = nbtlib.load('test_extract/world/playerdata/31c57a4c-5b47-360a-85e4-76a99bbf8a53.dat')
print("Type:", type(player_file))
print("Is dict?", isinstance(player_file, dict))
print("Keys:", list(player_file.keys()))
if '' in player_file:
    print("Found empty string tag! Type:", type(player_file['']))
    print("Keys of empty string tag:", list(player_file[''].keys()))
else:
    print("No empty string tag.")

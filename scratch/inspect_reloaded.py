import nbtlib

level_file = nbtlib.load('test_extract/world/level_modified.dat')
player = level_file['Data']['Player']
print("Reloaded player type:", type(player))
print("Reloaded player keys:", list(player.keys()))

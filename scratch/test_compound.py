import nbtlib

player_file = nbtlib.load('test_extract/world/playerdata/31c57a4c-5b47-360a-85e4-76a99bbf8a53.dat')
print("Has root attribute?", hasattr(player_file, 'root'))
if hasattr(player_file, 'root'):
    print("root type:", type(player_file.root))
    print("root keys:", list(player_file.root.keys()))
    
# Let's try to convert it to a standard Compound tag or use its dict contents
# In nbtlib, Compound is the tag type. Let's see if we can do:
from nbtlib.tag import Compound
player_compound = Compound(player_file)
print("player_compound type:", type(player_compound))
print("player_compound keys:", list(player_compound.keys()))

# Let's write a test script that assigns player_compound
level_file = nbtlib.load('test_extract/world/level.dat')
level_file['Data']['Player'] = player_compound
level_file.save('test_extract/world/level_modified2.dat')

reloaded = nbtlib.load('test_extract/world/level_modified2.dat')
print("Reloaded keys under Player:", list(reloaded['Data']['Player'].keys())[:10])

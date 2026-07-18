import nbtlib

level_file = nbtlib.load('test_extract/world/level.dat')
data = level_file['Data']
if 'spawn' in data:
    print("spawn:", data['spawn'])
    print("spawn type:", type(data['spawn']))
    if hasattr(data['spawn'], 'keys'):
        print("spawn keys:", list(data['spawn'].keys()))
else:
    print("spawn key not found")

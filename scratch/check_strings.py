def get_balanced_block(content, start_sig):
    idx = content.find(start_sig)
    if idx == -1:
        return ""
    # Find the opening parenthesis of the constructor
    start_paren = content.find("(", idx)
    if start_paren == -1:
        return ""
    
    # Track balanced parentheses
    count = 1
    i = start_paren + 1
    while i < len(content) and count > 0:
        if content[i] == '(':
            count += 1
        elif content[i] == ')':
            count -= 1
        i += 1
    return content[start_paren+1:i-1]

def analyze():
    filepath = "/home/aleemkanyu/.gemini/antigravity/scratch/PocketCraft/app/src/main/kotlin/com/pocketcraft/server/util/AppStrings.kt"
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()

    import re

    # Extract default keys from the data class AppStrings definition
    default_block = get_balanced_block(content, "data class AppStrings")
    keys = re.findall(r'val\s+(\w+)\s*:', default_block)
    print(f"Total keys in default AppStrings: {len(keys)}")

    german_block = get_balanced_block(content, "val German = AppStrings")
    spanish_block = get_balanced_block(content, "val Spanish = AppStrings")
    russian_block = get_balanced_block(content, "val Russian = AppStrings")
    chinese_block = get_balanced_block(content, "val Chinese = AppStrings")

    def find_assigned_keys(text):
        if not text:
            return set()
        # Find matches like name = "value", or name = ...
        return set(re.findall(r'(\w+)\s*=', text))

    german_keys = find_assigned_keys(german_block)
    spanish_keys = find_assigned_keys(spanish_block)
    russian_keys = find_assigned_keys(russian_block)
    chinese_keys = find_assigned_keys(chinese_block)

    print(f"German has {len(german_keys)} keys")
    print(f"Spanish has {len(spanish_keys)} keys")
    print(f"Russian has {len(russian_keys)} keys")
    print(f"Chinese has {len(chinese_keys)} keys")

    for name, keys_set in [("German", german_keys), ("Spanish", spanish_keys), ("Russian", russian_keys), ("Chinese", chinese_keys)]:
        missing = [k for k in keys if k not in keys_set]
        print(f"Missing in {name}: {missing}")

if __name__ == '__main__':
    analyze()

import re

PROTECT_MAP = {
    "%1$d": "__VAR_A__",
    "%2$d": "__VAR_B__",
    "%1$.1f": "__VAR_C__",
    "%1$s": "__VAR_D__",
    "%s": "__VAR_E__"
}

def protect_string(s):
    # Unescape first
    s = s.replace("\\'", "'").replace('\\"', '"')
    # Protect newlines
    s = s.replace("\\n", " __NEWLINE__ ")
    # Protect variables
    for var, placeholder in PROTECT_MAP.items():
        s = s.replace(var, f" {placeholder} ")
    return s

def restore_string(s):
    # Restore variables
    # We use regex to match the placeholder name even if translation introduced spaces or casing changes
    s = re.sub(r'__\s*VAR\s*A\s*__', '%1$d', s, flags=re.IGNORECASE)
    s = re.sub(r'__\s*VAR\s*B\s*__', '%2$d', s, flags=re.IGNORECASE)
    s = re.sub(r'__\s*VAR\s*C\s*__', '%1$.1f', s, flags=re.IGNORECASE)
    s = re.sub(r'__\s*VAR\s*D\s*__', '%1$s', s, flags=re.IGNORECASE)
    s = re.sub(r'__\s*VAR\s*E\s*__', '%s', s, flags=re.IGNORECASE)
    
    # Restore newlines
    s = re.sub(r'__\s*NEWLINE\s*__', '\\n', s, flags=re.IGNORECASE)
    
    # Standardize spaces around slash for players_online
    s = s.replace("%1$d / %2$d", "%1$d/%2$d")
    s = s.replace("%1$d /%2$d", "%1$d/%2$d")
    s = s.replace("%1$d/ %2$d", "%1$d/%2$d")
    
    return s

def test():
    tests = [
        "%1$d/%2$d players",
        "TPS: %1$.1f",
        "Uptime: %1$s",
        "Recommended: %s",
        "Line1\\n\\nLine2 with\\'s quotes"
    ]
    
    for t in tests:
        p = protect_string(t)
        print(f"Original:  {t}")
        print(f"Protected: {p}")
        
        # Simulate translation changes (e.g. spaces added)
        translated = p.replace("__VAR_A__", "__ VAR A __").replace("__NEWLINE__", "__ newline __")
        r = restore_string(translated)
        print(f"Restored:  {r}")
        print("-" * 30)

if __name__ == '__main__':
    test()

import urllib.request
import urllib.parse
import re
import html as html_lib
import json

PROTECT_MAP = {
    "%1$d": "__VARA__",
    "%2$d": "__VARB__",
    "%1$.1f": "__VARC__",
    "%1$s": "__VARD__",
    "%s": "__VARE__"
}

def protect_string(s):
    s = s.replace("\\'", "'").replace('\\"', '"')
    s = s.replace("\\n", " __NEWLINE__ ")
    for var, placeholder in PROTECT_MAP.items():
        s = s.replace(var, f" {placeholder} ")
    return s

def restore_string(s):
    s = re.sub(r'__\s*VAR\s*A\s*__', '%1$d', s, flags=re.IGNORECASE)
    s = re.sub(r'__\s*VAR\s*B\s*__', '%2$d', s, flags=re.IGNORECASE)
    s = re.sub(r'__\s*VAR\s*C\s*__', '%1$.1f', s, flags=re.IGNORECASE)
    s = re.sub(r'__\s*VAR\s*D\s*__', '%1$s', s, flags=re.IGNORECASE)
    s = re.sub(r'__\s*VAR\s*E\s*__', '%s', s, flags=re.IGNORECASE)
    
    # Restore newlines
    s = re.sub(r'__\s*NEWLINE\s*__', '\\n', s, flags=re.IGNORECASE)
    
    s = s.replace("%1$d / %2$d", "%1$d/%2$d")
    s = s.replace("%1$d /%2$d", "%1$d/%2$d")
    s = s.replace("%1$d/ %2$d", "%1$d/%2$d")
    return s

def translate_mobile_batch_robust(strings, target_lang):
    placeholder = "PC_APP_NAME"
    lines = []
    for i, s in enumerate(strings):
        protected = protect_string(s)
        protected = protected.replace("PocketCraft", placeholder).replace("Pocketcraft", placeholder)
        lines.append(f"- [{i}] {protected}")
    
    full_text = "\n".join(lines)
    
    url = "https://translate.google.com/m"
    params = {
        "sl": "en",
        "tl": target_lang,
        "hl": "en",
        "q": full_text
    }
    query_string = urllib.parse.urlencode(params)
    full_url = f"{url}?{query_string}"
    
    req = urllib.request.Request(full_url, headers={
        "User-Agent": "Mozilla/5.0 (iPhone; CPU iPhone OS 14_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/14.0 Mobile/15E148 Safari/604.1"
    })
    
    try:
        with urllib.request.urlopen(req) as response:
            html = response.read().decode("utf-8")
            match = re.search(r'class="result-container">(.*?)</div>', html, re.DOTALL)
            if not match:
                match = re.search(r'class="t0">(.*?)</div>', html, re.DOTALL)
            
            if match:
                translated_full = html_lib.unescape(match.group(1))
                
                # Split using regex that allows optional spaces inside brackets
                results = [None] * len(strings)
                pattern = r'(?:-\s*)?\[\s*(\d+)\s*\]\s*(.*?)(?=\s*(?:-\s*)?\[\s*\d+\s*\]|$)'
                matches = re.findall(pattern, translated_full, re.DOTALL)
                
                for index_str, text in matches:
                    idx = int(index_str)
                    if 0 <= idx < len(strings):
                        restored = text.replace(placeholder, "PocketCraft")
                        restored = restore_string(restored)
                        results[idx] = restored.strip()
                
                for j, res in enumerate(results):
                    if res is None:
                        results[j] = strings[j]
                
                return results
            else:
                return None
    except Exception as e:
        print(f"Error: {e}")
        return None

def test():
    test_strings = [
        "Start Server",
        "Stop Server",
        "Server Status",
        "%1$d/%2$d players",
        "TPS: %1$.1f",
        "Uptime: %1$s",
        "PocketCraft will be free forever for everyone, but some features are in Pro just so that you can support our server costs and app running costs. They are cheap, and that\\'s why they are in there, including that for more value.\\n\\nIf you\\'d like to help us, or just want to chat with the team, join our Discord — we\\'d love to have you."
    ]
    res = translate_mobile_batch_robust(test_strings, "rw") # test on Kinyarwanda
    if res:
        for orig, trans in zip(test_strings, res):
            print(f"Original:  {orig}")
            print(f"Translated: {trans}")
            print("-" * 20)

if __name__ == '__main__':
    test()

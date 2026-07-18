import urllib.request
import urllib.parse
import re
import html as html_lib
import json

def translate_mobile_batch(strings, target_lang):
    placeholder = "PC_APP_NAME"
    lines = []
    for i, s in enumerate(strings):
        protected = s.replace("PocketCraft", placeholder).replace("Pocketcraft", placeholder)
        escaped = protected.replace("\n", "\\n")
        lines.append(f"[{i}] {escaped}")
    
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
                
                # Split using regex
                results = [None] * len(strings)
                pattern = r'\[(\d+)\]\s*(.*?)(?=\s*\[\d+\]|$)'
                matches = re.findall(pattern, translated_full, re.DOTALL)
                
                for index_str, text in matches:
                    idx = int(index_str)
                    if 0 <= idx < len(strings):
                        restored = text.replace(placeholder, "PocketCraft")
                        unescaped = restored.replace("\\n", "\n").replace("\\ n", "\n").strip()
                        results[idx] = unescaped
                
                # Check missed
                for j, res in enumerate(results):
                    if res is None:
                        results[j] = strings[j]
                
                return results
            else:
                print("Could not find result-container in HTML response.")
                return None
    except Exception as e:
        print(f"Error: {e}")
        return None

def test():
    test_strings = [
        "Home",
        "Players",
        "Storage",
        "This will permanently delete the world. Are you sure?",
        "Warning: Device is overheating — consider stopping the server",
        "PocketCraft will be free forever for everyone, but some features are in Pro just so that you can support our server costs."
    ]
    res = translate_mobile_batch(test_strings, "fr")
    if res:
        for orig, trans in zip(test_strings, res):
            print(f"Original: {orig}")
            print(f"Translated: {trans}")
            print("-" * 20)

if __name__ == '__main__':
    test()

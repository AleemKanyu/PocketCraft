import urllib.request
import urllib.parse
import json
import re

def translate_batch(strings, target_lang):
    lines = []
    for i, s in enumerate(strings):
        escaped = s.replace("\n", "\\n")
        lines.append(f"[{i}] {escaped}")
    
    full_text = "\n".join(lines)
    url = "https://translate.googleapis.com/translate_a/single"
    params = {
        "client": "gtx",
        "sl": "en",
        "tl": target_lang,
        "dt": "t",
        "q": full_text
    }
    
    data = urllib.parse.urlencode(params).encode("utf-8")
    req = urllib.request.Request(url, data=data, headers={
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"
    })
    
    try:
        with urllib.request.urlopen(req) as response:
            res_data = response.read().decode("utf-8")
            parsed = json.loads(res_data)
            translated_segments = []
            for segment in parsed[0]:
                if segment[0]:
                    translated_segments.append(segment[0])
            
            translated_full = "".join(translated_segments)
            results = [None] * len(strings)
            pattern = r'\[(\d+)\]\s*(.*?)(?=\s*\[\d+\]|$)'
            matches = re.findall(pattern, translated_full, re.DOTALL)
            
            for index_str, text in matches:
                idx = int(index_str)
                if 0 <= idx < len(strings):
                    unescaped = text.replace("\\n", "\n").replace("\\ n", "\n").strip()
                    results[idx] = unescaped
            
            for i, res in enumerate(results):
                if res is None:
                    results[i] = strings[i]
            
            return results
    except Exception as e:
        print(f"Error during translation to {target_lang}: {e}")
        return None

def test():
    test_strings = [
        "%1$d/%2$d players",
        "TPS: %1$.1f",
        "Uptime: %1$s",
        "You're on Minecraft %s. We're still improving 26.x in PocketCraft — switch to 1.21.x for a better experience.",
        "A note from PocketCraft",
        "Tap to copy IP",
        "IP copied to clipboard"
    ]
    for lang in ["de", "es", "ru", "zh-CN"]:
        print(f"Translating to {lang}...")
        res = translate_batch(test_strings, lang)
        if res:
            for orig, trans in zip(test_strings, res):
                print(f"Original: {orig}")
                print(f"Translated: {trans}")
                print("-" * 10)
            print("=" * 30)

if __name__ == '__main__':
    test()

import urllib.request
import json

def fetch_languages():
    url = "https://translate.googleapis.com/translate_a/l?client=gtx&hl=en"
    req = urllib.request.Request(url, headers={
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"
    })
    try:
        with urllib.request.urlopen(req) as response:
            res_data = response.read().decode("utf-8")
            parsed = json.loads(res_data)
            # The API returns an object. Let's see the structure.
            print("Keys in response:", parsed.keys() if isinstance(parsed, dict) else type(parsed))
            if isinstance(parsed, dict) and "tl" in parsed:
                tl = parsed["tl"]
                print(f"Total target languages: {len(tl)}")
                # Show first 10
                for k, v in list(tl.items())[:10]:
                    print(f"  {k}: {v}")
            else:
                print("Response content snippet:", res_data[:200])
    except Exception as e:
        print(f"Error: {e}")

if __name__ == '__main__':
    fetch_languages()

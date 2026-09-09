import urllib.request
import urllib.parse
import json
import xml.etree.ElementTree as ET
import re
import os
import time
import html as html_lib

# Target directories
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
BASE_DIR = os.path.normpath(os.path.join(SCRIPT_DIR, ".."))
RES_DIR = os.path.join(BASE_DIR, "app/src/main/res")
ASSETS_DIR = os.path.join(BASE_DIR, "app/src/main/assets/locales")

ONBOARDING_STRINGS = {
    "onboardingStepWelcome": "WELCOME",
    "onboardingStepHowItWorks": "HOW IT WORKS",
    "onboardingStepBringYourWorld": "BRING YOUR WORLD",
    "onboardingStepFullControl": "FULL CONTROL",
    "onboardingStepCrossPlay": "CROSS-PLAY READY",
    "onboardingStepPickRegion": "PICK REGION",
    "onboardingStepPermissions": "PERMISSIONS",
    "onboardingStepGoogleSignIn": "GOOGLE SIGN-IN",
    "onboardingStepSetup": "SETUP",
    
    "onboardingWelcomeTitle": "Your phone is now\\na Minecraft server",
    "onboardingWelcomeSubtitle": "Host Java Edition servers for free.\\nNo PC required. Play with anyone.",
    "onboardingFreeToHost": "Free to host",
    "onboardingNoPcNeeded": "No PC needed",
    "onboardingInviteAnyone": "Invite anyone",
    "onboardingAgreeTermsPolicy": "I agree to the terms & policy",
    "onboardingPrivacyPolicy": "Privacy Policy",
    "onboardingTermsOfUse": "Terms of Use",
    
    "onboardingHowItWorksTitle": "How it works",
    "onboardingStep1Title": "1. Start your server",
    "onboardingStep1Body": "Paper runs locally on your phone and uses the memory you already have.",
    "onboardingStep2Title": "2. Share your address",
    "onboardingStep2Body": "PocketCraft gives you a unique relay address that friends can connect to.",
    "onboardingStep3Title": "3. Friends join instantly",
    "onboardingStep3Body": "Java Edition players connect straight through the relay with minimal friction.",
    
    "onboardingImportTitle": "Moving from Aternos or Minehut?",
    "onboardingImportSubtitle": "Bring your world, plugins, and config with you. No starting over.",
    "onboardingImportWorldTitle": "Upload world zip",
    "onboardingImportWorldBody": "Export from Aternos, drop the archive in PocketCraft, and keep going.",
    "onboardingImportPluginsTitle": "Keep your plugins",
    "onboardingImportPluginsBody": "Reuse the same .jar files. Your plugin stack moves with you.",
    "onboardingImportConfigTitle": "Config & ops carry over",
    "onboardingImportConfigBody": "server.properties, whitelist, and operator access remain intact.",
    
    "onboardingFeaturesTitle": "Full control, right in your pocket",
    "onboardingFeaturesSubtitle": "Not a stripped-down app. This is the real server toolkit.",
    "onboardingFeaturesBanner": "Everything you need in one place",
    "onboardingFeaturesPluginsBody": "Install compatible plugins",
    "onboardingFeaturesConsoleTitle": "Live console",
    "onboardingFeaturesConsoleBody": "Run commands and watch logs",
    "onboardingFeaturesPlayersBody": "Manage players",
    "onboardingFeaturesConfigTitle": "properties",
    "onboardingFeaturesConfigBody": "Edit settings directly",
    
    "onboardingCrossPlayTitle": "Cross-play is supported",
    "onboardingCrossPlaySubtitle": "PocketCraft supports Java and Bedrock cross-play when the bridge is enabled in your server setup.",
    "onboardingCrossPlayJavaBedrock": "Java + Bedrock",
    "onboardingCrossPlayJavaBedrockBody": "Friends on PC and mobile can join the same world together through the relay.",
    "onboardingCrossPlayNoExtraApp": "No extra app for players",
    "onboardingCrossPlayNoExtraAppBody": "Share your server address and players can connect from their own edition right away.",
    "onboardingCrossPlayExperimental": "⚠️ Experimental Feature - Play at Your Own Discretion\\n\\nCross-play is still in active development. Bugs and stability issues may occur as this feature is not yet officially supported. Use at your own risk.",
    
    "onboardingRegionTitle": "Choose your relay region",
    "onboardingRegionSubtitle": "Pick the relay server closest to your players. You can change this later from the dashboard too.",
    "onboardingRegionFinding": "Finding best server...",
    "onboardingRegionRecommended": "Recommended: %s. You can still change it below.",
    
    "onboardingPermissionsComplete": "Permission complete",
    "onboardingPermissionsRequired": "Required before continuing",
    "onboardingPermissionsTitle": "Allow notifications",
    "onboardingPermissionsSubtitle": "This is used for server status, player count, and important background updates while your server is running.",
    "onboardingPermissionsCardTitle": "Notifications",
    "onboardingPermissionsCardBody": "Shows server status and player count while the server is running.",
    "onboardingPermissionsButtonEnabled": "NOTIFICATIONS ENABLED",
    "onboardingPermissionsButtonAllow": "ALLOW NOTIFICATIONS",
    "onboardingPermissionsStatusSet": "You\\'re all set. Tap Next to keep going.",
    "onboardingPermissionsStatusWarn": "Notification access is required on this step. Tap the button above, then allow it in Android.",
    "onboardingPermissionsStatusTap": "Tap the button above, then accept the Android permission prompt to unlock Next.",
    "onboardingPermissionsError": "Allow notification permission to continue.",
    
    "onboardingGoogleTitle": "Optional Google sign-in",
    "onboardingGoogleSubtitle": "Sign in now to enable private Google Drive backups. You can skip this and finish setup first.",
    "onboardingGoogleStatusTitle": "Cloud Sync Status",
    "onboardingGoogleConnectedTitle": "Backup Account Connected",
    "onboardingGoogleStatusInactive": "Inactive (Local Only)",
    "onboardingGoogleStatusActive": "Active & Secured",
    "onboardingGoogleStatusDesc": "Backups are stored inside your private Google Drive app folder. PocketCraft cannot see or access your other Drive files.",
    "onboardingGoogleConnectedDesc": "Your server worlds, plugins, and settings will automatically backup to: %s",
    "onboardingGoogleSignInButton": "Sign in with Google",
    "onboardingGoogleSignedInButton": "Signed in",
    "onboardingGoogleSkipNotice": "You can press Next without signing in.",
    
    "onboardingSetupTitle": "Final setup",
    "onboardingSetupServerLabel": "Server name (required)",
    "onboardingSetupWorldDescLabel": "World description (optional)",
    "onboardingSetupVersionPlaceholder": "Select server type + version",
    "onboardingSetupCustomJar": "%s (Custom JAR)",
    "onboardingSetupVersionLabel": "Game version (required)",
    "onboardingSetupVersionError": "Please select a game version",
    "onboardingSetupSeedLabel": "World seed (optional)",
    "onboardingSetupBackupNotice": "You can restore world backups later in Settings whenever you are ready.",
    "onboardingSetupErrorServerName": "Server name is required.",
    "onboardingSetupErrorVersion": "Game version is required.",
    
    "onboardingButtonBack": "Back",
    "onboardingButtonSkip": "Skip",
    "onboardingButtonFinish": "Finish setup",
    "onboardingButtonNext": "Next",
    "onboardingScrollDown": "Scroll down"
}

PROTECT_MAP = {
    "%1$d": "__VARA__",
    "%2$d": "__VARB__",
    "%1$.1f": "__VARC__",
    "%1$s": "__VARD__",
    "%s": "__VARE__"
}

def protect_string(s):
    # Remove existing escapes to present clean strings to the translator
    s = s.replace("\\'", "'").replace('\\"', '"')
    s = s.replace("\\n", " __NEWLINE__ ")
    for var, placeholder in PROTECT_MAP.items():
        s = s.replace(var, f" {placeholder} ")
    return s

def restore_string(s):
    # Regex match allows translation engines to add spaces or change casing (e.g. "__ VAR A __")
    s = re.sub(r'__\s*VAR\s*A\s*__', '%1$d', s, flags=re.IGNORECASE)
    s = re.sub(r'__\s*VAR\s*B\s*__', '%2$d', s, flags=re.IGNORECASE)
    s = re.sub(r'__\s*VAR\s*C\s*__', '%1$.1f', s, flags=re.IGNORECASE)
    s = re.sub(r'__\s*VAR\s*D\s*__', '%1$s', s, flags=re.IGNORECASE)
    s = re.sub(r'__\s*VAR\s*E\s*__', '%s', s, flags=re.IGNORECASE)
    
    # Restore newlines
    s = re.sub(r'__\s*NEWLINE\s*__', '\\n', s, flags=re.IGNORECASE)
    
    # Normalize formatting spacing for players_online
    s = s.replace("%1$d / %2$d", "%1$d/%2$d")
    s = s.replace("%1$d /%2$d", "%1$d/%2$d")
    s = s.replace("%1$d/ %2$d", "%1$d/%2$d")
    return s

def get_balanced_block(content, start_sig):
    idx = content.find(start_sig)
    if idx == -1:
        return ""
    start_paren = content.find("(", idx)
    if start_paren == -1:
        return ""
    count = 1
    i = start_paren + 1
    while i < len(content) and count > 0:
        if content[i] == '(':
            count += 1
        elif content[i] == ')':
            count -= 1
        i += 1
    return content[start_paren+1:i-1]

def get_android_qualifier(code):
    if code == "zh-CN":
        return "zh-rCN"
    if code == "zh-TW":
        return "zh-rTW"
    if code == "he":
        return "iw"
    if code == "id":
        return "in"
    if code == "yi":
        return "ji"
    if "-" in code:
        parts = code.split("-")
        if len(parts) == 2:
            return f"{parts[0]}-r{parts[1]}"
    return code

def escape_xml_string(val):
    # Escape special XML chars first
    escaped = val.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    # Escape quotes and apostrophes for XML resource compatibility
    escaped = escaped.replace("'", "\\'").replace('"', '\\"')
    return escaped

def fetch_languages():
    url = "https://translate.googleapis.com/translate_a/l?client=gtx&hl=en"
    req = urllib.request.Request(url, headers={
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"
    })
    try:
        with urllib.request.urlopen(req) as response:
            res_data = response.read().decode("utf-8")
            parsed = json.loads(res_data)
            return parsed.get("tl", {})
    except Exception as e:
        print(f"Error fetching languages list: {e}. Using fallback common list.")
        return {
            "af": "Afrikaans", "sq": "Albanian", "am": "Amharic", "ar": "Arabic", "hy": "Armenian",
            "az": "Azerbaijani", "eu": "Basque", "be": "Belarusian", "bn": "Bengali", "bs": "Bosnian",
            "bg": "Bulgarian", "ca": "Catalan", "ceb": "Cebuano", "ny": "Chichewa", "zh-CN": "Chinese (Simplified)",
            "zh-TW": "Chinese (Traditional)", "co": "Corsican", "hr": "Croatian", "cs": "Czech", "da": "Danish",
            "nl": "Dutch", "eo": "Esperanto", "et": "Estonian", "tl": "Filipino", "fi": "Finnish", "fr": "French",
            "fy": "Frisian", "gl": "Galician", "ka": "Georgian", "de": "German", "el": "Greek", "gu": "Gujarati",
            "ht": "Haitian Creole", "ha": "Hausa", "haw": "Hawaiian", "iw": "Hebrew", "hi": "Hindi", "hmn": "Hmong",
            "hu": "Hungarian", "is": "Icelandic", "ig": "Igbo", "id": "Indonesian", "ga": "Irish", "it": "Italian",
            "ja": "Japanese", "jw": "Javanese", "kn": "Kannada", "kk": "Kazakh", "km": "Khmer", "rw": "Kinyarwanda",
            "ko": "Korean", "ku": "Kurdish (Kurmanji)", "ky": "Kyrgyz", "lo": "Lao", "la": "Latin", "lv": "Latvian",
            "lt": "Lithuanian", "lb": "Luxembourgish", "mk": "Macedonian", "mg": "Malagasy", "ms": "Malay",
            "ml": "Malayalam", "mt": "Maltese", "mi": "Maori", "mr": "Marathi", "mn": "Mongolian", "my": "Myanmar (Burmese)",
            "ne": "Nepali", "no": "Norwegian", "or": "Odia (Oriya)", "ps": "Pashto", "fa": "Persian", "pl": "Polish",
            "pt": "Portuguese", "pa": "Punjabi", "ro": "Romanian", "ru": "Russian", "sm": "Samoan", "gd": "Scots Gaelic",
            "sr": "Serbian", "st": "Sesotho", "sn": "Shona", "sd": "Sindhi", "si": "Sinhala", "sk": "Slovak", "sl": "Slovenian",
            "so": "Somali", "es": "Spanish", "su": "Sundanese", "sw": "Swahili", "sv": "Swedish", "tg": "Tajik", "ta": "Tamil",
            "tt": "Tatar", "te": "Telugu", "th": "Thai", "tr": "Turkish", "tk": "Turkmen", "uk": "Ukrainian", "ur": "Urdu",
            "ug": "Uyghur", "uz": "Uzbek", "vi": "Vietnamese", "cy": "Welsh", "xh": "Xhosa", "yi": "Yiddish", "yo": "Yoruba", "zu": "Zulu"
        }

def translate_chunk_mobile(strings_to_trans, target_lang):
    placeholder = "PC_APP_NAME"
    lines = []
    for i, s in enumerate(strings_to_trans):
        protected = protect_string(s)
        protected = protected.replace("PocketCraft", placeholder).replace("Pocketcraft", placeholder)
        # Use Markdown bullet list item structure so translate doesn't merge lines
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
    
    for attempt in range(5):
        try:
            with urllib.request.urlopen(req) as response:
                html = response.read().decode("utf-8")
                match = re.search(r'class="result-container">(.*?)</div>', html, re.DOTALL)
                if not match:
                    match = re.search(r'class="t0">(.*?)</div>', html, re.DOTALL)
                
                if match:
                    translated_full = html_lib.unescape(match.group(1))
                    
                    results = [None] * len(strings_to_trans)
                    # Robust pattern allowing optional spaces or hyphens inside list structure
                    pattern = r'(?:-\s*)?\[\s*(\d+)\s*\]\s*(.*?)(?=\s*(?:-\s*)?\[\s*\d+\s*\]|$)'
                    matches = re.findall(pattern, translated_full, re.DOTALL)
                    
                    for index_str, text in matches:
                        idx = int(index_str)
                        if 0 <= idx < len(strings_to_trans):
                            restored = text.replace(placeholder, "PocketCraft")
                            restored = restore_string(restored)
                            results[idx] = restored.strip()
                    
                    # Fill missing
                    for j, res in enumerate(results):
                        if res is None:
                            results[j] = strings_to_trans[j]
                    
                    return results
                else:
                    print(f"  Attempt {attempt + 1}: result-container not found in HTML response.")
                    time.sleep(2 + attempt * 2)
        except Exception as e:
            print(f"  Attempt {attempt + 1} failed for {target_lang}: {e}")
            time.sleep(2 + attempt * 2)
            
    return None

def batch_translate(strings_list, target_lang, max_chars=3500):
    batches = []
    current_batch = []
    current_chars = 0
    
    for i, s in enumerate(strings_list):
        # Allow formatting parameters
        line = f"- [{i}] {s}"
        line_len = len(line) + 1
        if current_chars + line_len > max_chars and current_batch:
            batches.append(current_batch)
            current_batch = []
            current_chars = 0
        current_batch.append((i, s))
        current_chars += line_len
    if current_batch:
        batches.append(current_batch)
        
    final_translations = [None] * len(strings_list)
    for batch_idx, batch in enumerate(batches):
        batch_strings = [item[1] for item in batch]
        translated_batch = translate_chunk_mobile(batch_strings, target_lang)
        if translated_batch is None:
            translated_batch = batch_strings
        
        for local_idx, orig_idx in enumerate([item[0] for item in batch]):
            final_translations[orig_idx] = translated_batch[local_idx]
            
    return final_translations

def main():
    os.makedirs(ASSETS_DIR, exist_ok=True)
    
    # 1. Fetch target languages
    print("Fetching supported languages from Google Translate...")
    lang_map = fetch_languages()
    if not lang_map:
        print("Failed to fetch languages list. Exiting.")
        return
    
    # Ensure system default and standard languages are defined first
    all_languages = {"system": "System Default", "en": "English (US)"}
    for code, name in lang_map.items():
        all_languages[code] = name
        
    # Write languages.json
    with open(os.path.join(ASSETS_DIR, "languages.json"), "w", encoding="utf-8") as f:
        json.dump(all_languages, f, ensure_ascii=False, indent=2)
    print(f"Saved {len(all_languages)} languages to languages.json")
    
    # 2. Parse English strings.xml
    strings_xml_path = os.path.join(RES_DIR, "values/strings.xml")
    xml_tree = ET.parse(strings_xml_path)
    xml_root = xml_tree.getroot()
    
    xml_keys = []
    xml_values = []
    for string_elem in xml_root.findall("string"):
        if string_elem.get("translatable") == "false":
            continue
        xml_keys.append(string_elem.get("name"))
        xml_values.append(string_elem.text or "")
        
    print(f"Found {len(xml_keys)} translatable strings in strings.xml")
    
    # 3. Parse English AppStrings.kt default values
    app_strings_path = os.path.join(BASE_DIR, "app/src/main/kotlin/com/pocketcraft/server/util/AppStrings.kt")
    with open(app_strings_path, "r", encoding="utf-8") as f:
        kt_content = f.read()
        
    default_block = get_balanced_block(kt_content, "data class AppStrings")
    kt_props = re.findall(r'val\s+(\w+)\s*:\s*String\s*=\s*"([^"]*)"', default_block)
    
    kt_keys = [prop[0] for prop in kt_props]
    kt_values = [prop[1] for prop in kt_props]
    print(f"Found {len(kt_keys)} keys in AppStrings data class")
    
    onboarding_keys = list(ONBOARDING_STRINGS.keys())
    onboarding_values = list(ONBOARDING_STRINGS.values())
    
    all_compose_keys = kt_keys + onboarding_keys
    all_compose_values = kt_values + onboarding_values
    
    # 4. Loop over languages and translate
    langs_to_translate = [code for code in all_languages.keys() if code not in ["system", "en"]]
    total_langs = len(langs_to_translate)
    
    for idx, lang in enumerate(langs_to_translate):
        print(f"[{idx+1}/{total_langs}] Translating to {lang} ({all_languages[lang]})...")
        
        # Translate XML strings
        translated_xml = batch_translate(xml_values, lang)
        
        # Write strings.xml
        qualifier = get_android_qualifier(lang)
        lang_res_dir = os.path.join(RES_DIR, f"values-{qualifier}")
        os.makedirs(lang_res_dir, exist_ok=True)
        
        xml_out = '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
        for k, v in zip(xml_keys, translated_xml):
            escaped_val = escape_xml_string(v)
            xml_out += f'    <string name="{k}">{escaped_val}</string>\n'
        xml_out += '    <string name="discord_invite_url" translatable="false">https://discord.gg/7xw3Rd2vs2</string>\n'
        xml_out += '</resources>\n'
        
        with open(os.path.join(lang_res_dir, "strings.xml"), "w", encoding="utf-8") as f:
            f.write(xml_out)
            
        # Translate Compose strings
        translated_compose = batch_translate(all_compose_values, lang)
        
        # Construct JSON map
        json_map = {}
        for k, v in zip(all_compose_keys, translated_compose):
            json_map[k] = v
            
        # Save JSON asset
        with open(os.path.join(ASSETS_DIR, f"{lang}.json"), "w", encoding="utf-8") as f:
            json.dump(json_map, f, ensure_ascii=False, indent=2)
            
        time.sleep(0.3)
        
    print("Translation phase completed successfully!")

if __name__ == '__main__':
    main()

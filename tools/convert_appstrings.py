import re
import os
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
app_strings_path = os.path.normpath(os.path.join(SCRIPT_DIR, "../app/src/main/kotlin/com/pocketcraft/server/util/AppStrings.kt"))

with open(app_strings_path, "r", encoding="utf-8") as f:
    content = f.read()

start_idx = content.find("data class AppStrings(")
if start_idx == -1:
    print("Could not find data class AppStrings")
    exit(1)

func_idx = content.find("fun appStringsFor")
if func_idx == -1:
    print("Could not find appStringsFor")
    exit(1)

closing_paren_idx = content.rfind(")", start_idx, func_idx)
if closing_paren_idx == -1:
    print("Could not find closing parenthesis")
    exit(1)

constructor_content = content[start_idx:closing_paren_idx+1]

lines = constructor_content.split("\n")
new_class_lines = []

new_class_lines.append("class AppStrings(private val map: Map<String, String> = emptyMap()) {")

for line in lines:
    if "data class AppStrings(" in line:
        continue
    # Match property declaration: val name: String = "value",
    match = re.search(r'val\s+(\w+)\s*:\s*String\s*=\s*"((?:[^"\\]|\\.)*)"\s*,?', line)
    if match:
        name = match.group(1)
        val = match.group(2)
        new_class_lines.append(f'    val {name}: String get() = map["{name}"] ?: "{val}"')
    else:
        cleaned = line.rstrip()
        if cleaned == ")":
            continue
        new_class_lines.append(line)

new_class_lines.append("}")

new_class_str = "\n".join(new_class_lines)

new_appstrings_for = """fun appStringsFor(context: Context, lang: String): AppStrings {
    val targetLang = if (lang == "system" || lang.isEmpty()) {
        java.util.Locale.getDefault().language
    } else {
        lang
    }
    if (targetLang == "en" || targetLang.startsWith("en_") || targetLang.startsWith("en-")) {
        return AppStrings()
    }
    return try {
        val assetPath = "locales/$targetLang.json"
        val jsonStr = context.assets.open(assetPath).bufferedReader().use { it.readText() }
        val type = object : com.google.gson.reflect.TypeToken<Map<String, String>>() {}.type
        val map: Map<String, String> = Gson().fromJson(jsonStr, type) ?: emptyMap()
        AppStrings(map)
    } catch (e: Exception) {
        if (targetLang.contains("-") || targetLang.contains("_")) {
            val base = targetLang.split('-', '_')[0]
            if (base != "en") {
                try {
                    val assetPath = "locales/$base.json"
                    val jsonStr = context.assets.open(assetPath).bufferedReader().use { it.readText() }
                    val type = object : com.google.gson.reflect.TypeToken<Map<String, String>>() {}.type
                    val map: Map<String, String> = Gson().fromJson(jsonStr, type) ?: emptyMap()
                    return AppStrings(map)
                } catch (e2: Exception) {
                    // fall through
                }
            }
        }
        AppStrings()
    }
}"""

header = content[:start_idx]
footer = content[closing_paren_idx+1:]

appstrings_for_start = footer.find("fun appStringsFor")
local_appstrings_start = footer.find("val LocalAppStrings =")

footer_before_func = footer[:appstrings_for_start]
footer_after_func = footer[local_appstrings_start:]

final_content = header + new_class_str + "\n\n" + footer_before_func + new_appstrings_for + "\n\n" + footer_after_func

with open(app_strings_path, "w", encoding="utf-8") as f:
    f.write(final_content)

print("Conversion complete!")

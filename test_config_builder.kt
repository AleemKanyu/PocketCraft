import java.io.File

fun ensureYamlSectionValue(original: String, section: String, key: String, value: String): String {
    val lines = original.ifBlank { "" }.split('\n').toMutableList()
    var sectionStart = lines.indexOfFirst { it.trim() == "$section:" }
    if (sectionStart == -1) {
        if (lines.size == 1 && lines[0].isBlank()) lines.clear()
        if (lines.isNotEmpty() && lines.last().isNotBlank()) lines += ""
        lines += "$section:"
        lines += "  $key: $value"
        return lines.joinToString("\n").trimEnd() + "\n"
    }
    var sectionEnd = lines.size
    for (index in (sectionStart + 1) until lines.size) {
        val line = lines[index]
        val trimmed = line.trim()
        if (trimmed.isBlank() || trimmed.startsWith("#")) continue
        if (!line.startsWith(" ") && !line.startsWith("\t")) {
            sectionEnd = index
            break
        }
    }
    val keyIndex = ((sectionStart + 1) until sectionEnd).firstOrNull { index ->
        val line = lines[index]
        (line.startsWith(" ") || line.startsWith("\t")) && line.trimStart().startsWith("$key:")
    }
    if (keyIndex != null) {
        lines[keyIndex] = "  $key: $value"
    } else {
        lines.add(sectionEnd, "  $key: $value")
    }
    return lines.joinToString("\n").trimEnd() + "\n"
}

fun main() {
    println("Testing...")
}

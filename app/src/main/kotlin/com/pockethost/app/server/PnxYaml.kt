package com.pockethost.app.server

import java.io.File

/**
 * Minimal, comment-preserving editor for PowerNukkitX's `pnx.yml`.
 *
 * PowerNukkitX 3.x dropped `server.properties` entirely: every server setting now lives in
 * `pnx.yml`, a flat two-level document (`section:` followed by two-space indented keys). The
 * file PowerNukkitX writes back is full of localized comments that explain each key, so it is
 * rewritten line by line here instead of being round-tripped through a YAML library — a
 * serializer would strip those comments and reorder the document on every launch.
 *
 * Only depth-2 keys are addressed ("settings.port"). Deeper blocks such as
 * `network-settings.rate-limit` are left untouched.
 */
object PnxYaml {

    private val SECTION_REGEX = Regex("""^([A-Za-z][\w-]*):\s*$""")
    private val ENTRY_REGEX = Regex("""^(\s{2})([A-Za-z][\w-]*):\s?(.*)$""")

    /** Reads every depth-2 scalar as a flat map of "section.key" to its raw value. */
    fun read(file: File): Map<String, String> {
        if (!file.isFile) return emptyMap()
        val values = mutableMapOf<String, String>()
        var section: String? = null
        runCatching { file.readLines() }.getOrDefault(emptyList()).forEach { line ->
            SECTION_REGEX.find(line)?.let {
                section = it.groupValues[1]
                return@forEach
            }
            if (line.isNotBlank() && !line.startsWith(" ") && !line.startsWith("#")) {
                // A top-level key that carries a value on the same line ends the current section.
                section = null
                return@forEach
            }
            val currentSection = section ?: return@forEach
            val entry = ENTRY_REGEX.find(line) ?: return@forEach
            val key = entry.groupValues[2]
            val raw = entry.groupValues[3].trim()
            if (raw.isNotEmpty()) {
                values["$currentSection.$key"] = raw
            }
        }
        return values
    }

    fun readString(file: File, path: String): String? = read(file)[path]

    fun readInt(file: File, path: String): Int? = readString(file, path)?.toIntOrNull()

    /**
     * Writes [updates] (keyed as "section.key") into [file], creating the file, any missing
     * section, and any missing key. Existing comments, ordering, and unrelated keys survive.
     */
    fun apply(file: File, updates: Map<String, String>) {
        if (updates.isEmpty()) return

        val lines = if (file.isFile) {
            runCatching { file.readLines().toMutableList() }.getOrDefault(mutableListOf())
        } else {
            mutableListOf()
        }

        // Group the requested writes by section so each section is visited once.
        val bySection = LinkedHashMap<String, LinkedHashMap<String, String>>()
        updates.forEach { (path, value) ->
            val section = path.substringBefore('.', "")
            val key = path.substringAfter('.', "")
            if (section.isBlank() || key.isBlank()) return@forEach
            bySection.getOrPut(section) { LinkedHashMap() }[key] = value
        }

        bySection.forEach { (section, entries) ->
            val sectionStart = lines.indexOfFirst { SECTION_REGEX.find(it)?.groupValues?.get(1) == section }
            if (sectionStart < 0) {
                lines.add("$section:")
                entries.forEach { (key, value) -> lines.add("  $key: $value") }
                return@forEach
            }

            // The section's extent is recomputed per key: inserting one shifts every later
            // line, and a stale end index would make the next key's lookup miss an entry that
            // sits at the end of the section and insert a duplicate of it instead.
            var insertAt = sectionStart + 1
            entries.forEach { (key, value) ->
                val sectionEnd = sectionEndIndex(lines, sectionStart)
                val keyIndex = (sectionStart + 1 until sectionEnd).firstOrNull { index ->
                    ENTRY_REGEX.find(lines[index])?.groupValues?.get(2) == key
                }
                if (keyIndex != null) {
                    lines[keyIndex] = "  $key: $value"
                } else {
                    // Insert at the top of the block so the key lands inside the section rather
                    // than after a trailing comment that belongs to the next one, advancing the
                    // cursor so multiple new keys keep their declared order.
                    lines.add(insertAt, "  $key: $value")
                    insertAt++
                }
            }
        }

        file.parentFile?.mkdirs()
        file.writeText(lines.joinToString("\n").trimEnd() + "\n")
    }

    /** Index of the first line that is no longer part of the section starting at [sectionStart]. */
    private fun sectionEndIndex(lines: List<String>, sectionStart: Int): Int {
        for (index in sectionStart + 1 until lines.size) {
            val line = lines[index]
            if (line.isBlank()) continue
            // Comments are indented at the level of the key they describe, so a non-indented
            // comment already belongs to the next section.
            if (!line.startsWith(" ")) return index
        }
        return lines.size
    }

    /**
     * Inverse of [quote]: strips surrounding quotes and undoes the backslash escaping, so a value
     * read back out of `pnx.yml` matches what the user typed.
     */
    fun unquote(raw: String): String {
        val trimmed = raw.trim()
        val body = if (trimmed.length >= 2 &&
            ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) ||
                (trimmed.startsWith("'") && trimmed.endsWith("'")))
        ) {
            trimmed.substring(1, trimmed.length - 1)
        } else {
            trimmed
        }
        return body.replace("\\\\", "\\")
    }

    /**
     * Quotes a value that YAML would otherwise read as something other than a plain string.
     *
     * Backslashes must be escaped: inside a double-quoted scalar YAML reads them as the start of
     * an escape sequence, so a MOTD like "C:\path" would make PowerNukkitX fail to parse its own
     * config and refuse to boot.
     */
    fun quote(value: String): String {
        val sanitized = value
            .replace("\\", "\\\\")
            .replace("\n", " ")
            .replace("\r", " ")
            .replace("\"", "'")
            .trim()
        return "\"$sanitized\""
    }
}

package com.pocketcraft.server.service

import com.pocketcraft.server.data.model.ConsoleMessage
import com.pocketcraft.server.data.model.LogLevel

/**
 * Parses raw stdout lines from the PaperMC server into structured [ConsoleMessage] objects
 * and extracts semantic events (player join/leave, TPS, server ready, etc.).
 */
object ConsoleParser {

    // e.g. "[17:30:01 INFO]: Done (5.123s)! For help, type "help""
    private val DONE_REGEX = Regex("""Done \([\d.]+s\)! For help, type""")

    // e.g. "[17:30:05 INFO]: UUID of player Steve is 123e4567-..."
    // Floodgate/Geyser names may be prefixed (for example ".Steve"), so do not
    // restrict this to Java-only username characters.
    private val JOIN_WITH_UUID_REGEX = Regex("""UUID of player (\S+) is ([a-f0-9\-]+)""", RegexOption.IGNORE_CASE)

    // e.g. "[17:30:06 INFO]: Steve joined the game"
    private val JOINED_GAME_REGEX = Regex("""(\S+) joined the game""", RegexOption.IGNORE_CASE)

    // e.g. "Steve lost connection", "Steve left the game", "Steve was kicked", "Steve disconnected"
    private val LEAVE_REGEX = Regex(
        """(\S+) (lost connection|left the game|was kicked|disconnected)""",
        RegexOption.IGNORE_CASE
    )

    // e.g. "[17:31:00 INFO]: TPS from last 1m, 5m, 15m: 19.98, 19.99, 20.0"
    private val TPS_REGEX = Regex("""TPS from last 1m, 5m, 15m: ([\d.]+)""")

    // e.g. "[17:30:01 WARN]: ..."   "[17:30:01 ERROR]: ..."
    private val LEVEL_REGEX = Regex("""\[\d{2}:\d{2}:\d{2} (INFO|WARN|ERROR|FATAL)\]""")

    // Chat: "[17:30:15 INFO]: <Steve> hello"
    private val CHAT_REGEX = Regex("""<(\w+)> """)

    fun parse(raw: String): ConsoleMessage {
        val level = when {
            raw.contains("[WARN]") || raw.contains("WARN]") -> LogLevel.WARN
            raw.contains("[ERROR]") || raw.contains("ERROR]") ||
                    raw.contains("[FATAL]") || raw.contains("FATAL]") -> LogLevel.ERROR
            CHAT_REGEX.containsMatchIn(raw) -> LogLevel.CHAT
            else -> LogLevel.INFO
        }
        // Strip the timestamp prefix for cleaner display
        val text = raw
            .replace(LEVEL_REGEX, "")
            .replace(Regex("""^\[[\d:]+\] """), "")
            .trim()
            .ifEmpty { raw.trim() }
        return ConsoleMessage(
            timestamp = System.currentTimeMillis(),
            level = level,
            text = text
        )
    }

    fun isDone(line: String): Boolean = DONE_REGEX.containsMatchIn(line)

    fun parseTps(line: String): Float? =
        TPS_REGEX.find(line)?.groupValues?.get(1)?.toFloatOrNull()

    /** Returns (name, uuid) if a player joined. */
    fun parseJoin(line: String): Pair<String, String>? {
        JOIN_WITH_UUID_REGEX.find(line)?.let { match ->
            return match.groupValues[1] to match.groupValues[2]
        }

        JOINED_GAME_REGEX.find(line)?.let { match ->
            return match.groupValues[1] to ""
        }

        return null
    }

    /** Returns player name if a player left. */
    fun parseLeave(line: String): String? =
        LEAVE_REGEX.find(line)?.groupValues?.get(1)

    /** Returns the cleaned console text, stripping ANSI color codes. */
    fun stripAnsi(text: String): String =
        text.replace(Regex("\u001B\\[[;\\d]*m"), "")
}

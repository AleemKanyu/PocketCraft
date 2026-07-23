package com.pocketcraft.server.service

import com.pocketcraft.server.data.model.ConsoleMessage
import com.pocketcraft.server.data.model.LogLevel

data class ChunkyProgress(
    val current: Long,
    val total: Long,
    val percent: Float
)

data class ParsedPlayerPing(
    val pingMs: Int,
    val ip: String = ""
)

sealed interface ServerEvent {
    data object ServerFullyReady : ServerEvent
}

/**
 * Parses raw stdout lines from the PaperMC server into structured [ConsoleMessage] objects
 * and extracts semantic events (player join/leave, TPS, server ready, etc.).
 */
object ConsoleParser {

    // Paper's final ready line has changed format across versions. Match the
    // canonical "Done (...)" prefix after stripping timestamps/log prefixes
    // instead of depending on a specific help suffix.
    private val DONE_PREFIX_REGEX = Regex("""^Done \([\d.,]+s\)!""", RegexOption.IGNORE_CASE)

    // e.g. "[17:30:00 INFO]: Preparing start region for dimension minecraft:overworld"
    private val PREPARING_START_REGION_REGEX = Regex("""Preparing start region for dimension""")

    // e.g. "[17:35:10 INFO]: [Chunky] Task world:overworld [0 0] [3500/10000] [35.00%] [50.5 cps] [ETA 00:02:15]"
    private val CHUNKY_PROGRESS_REGEX = Regex("""\[Chunky\] Task \S+ \[(\d+)/(\d+)\] \[([\d.]+)%\]""")

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

    // e.g. "[17:30:06 INFO]: Steve issued server command: /ram"
    private val COMMAND_REGEX = Regex("""(\S+) issued server command: (.+)""", RegexOption.IGNORE_CASE)

    // e.g. "[17:31:00 INFO]: TPS from last 1m, 5m, 15m: 19.98, 19.99, 20.0"
    private val TPS_REGEX = Regex("""TPS from last 1m, 5m, 15m: ([\d.]+)""")

    // Match format: [12:34:56] [Server thread/INFO]: or [2026-06-20 12:34:56.789] [INFO]:
    private val PREFIX_REGEX_1 = Regex(
        """^\[?(?:\d{4}-\d{2}-\d{2}[T\s])?\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:\s+[AP]M)?\]?\s+\[(?:.*?/)?(?:INFO|WARN|ERROR|FATAL|ALERT|DEBUG|TRACE)\]:?\s*""",
        RegexOption.IGNORE_CASE
    )

    // Match format: [12:34:56 INFO]: or [2026-06-20 12:34:56.789 WARN]: or [12:34:56] or 12:34:56 INFO:
    private val PREFIX_REGEX_2 = Regex(
        """^\[?(?:\d{4}-\d{2}-\d{2}[T\s])?\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:\s+[AP]M)?(?:\s+(?:INFO|WARN|ERROR|FATAL|ALERT|DEBUG|TRACE))?\]?:?\s*""",
        RegexOption.IGNORE_CASE
    )

    // Chat: "[17:30:15 INFO]: <Steve> hello"
    private val CHAT_REGEX = Regex("""<([^>]+)>\s+""")

    fun parse(raw: String): ConsoleMessage {
        val cleanRaw = stripAnsi(raw).trim()
        val level = when {
            cleanRaw.contains("[WARN]") || cleanRaw.contains("WARN]") -> LogLevel.WARN
            cleanRaw.contains("[ERROR]") || cleanRaw.contains("ERROR]") ||
                    cleanRaw.contains("[FATAL]") || cleanRaw.contains("FATAL]") -> LogLevel.ERROR
            CHAT_REGEX.containsMatchIn(cleanRaw) -> LogLevel.CHAT
            else -> LogLevel.INFO
        }
        // Strip the timestamp prefix for cleaner display
        val text = cleanRaw
            .replace(PREFIX_REGEX_1, "")
            .replace(PREFIX_REGEX_2, "")
            .trim()
            .ifEmpty { cleanRaw.trim() }
        return ConsoleMessage(
            timestamp = System.currentTimeMillis(),
            level = level,
            text = text
        )
    }

    fun isDone(line: String): Boolean {
        val clean = stripAnsi(line).trim()
        if (!clean.contains("Done (", ignoreCase = true)) return false
        
        val afterThread = clean.substringAfter("]: ", clean)
        if (afterThread.startsWith("[") && !afterThread.startsWith("[Server thread")) {
            return false
        }
        val text = stripLogDecorations(clean)
        return DONE_PREFIX_REGEX.containsMatchIn(text) || (text.startsWith("Done (", ignoreCase = true) && text.contains("help", ignoreCase = true))
    }

    fun parseEvent(line: String): ServerEvent? =
        if (isDone(line)) ServerEvent.ServerFullyReady else null

    fun parseTps(line: String): Float? =
        TPS_REGEX.find(line)?.groupValues?.get(1)?.toFloatOrNull()

    /** Returns (name, uuid) if a player joined. */
    fun parseJoin(line: String): Pair<String, String>? {
        JOINED_GAME_REGEX.find(line)?.let { match ->
            return match.groupValues[1] to ""
        }
        return null
    }

    /** Returns player name if a player left. */
    fun parseLeave(line: String): String? =
        LEAVE_REGEX.find(line)?.groupValues?.get(1)

    /** Returns (player, command) if a player issued a command. */
    fun parseCommand(line: String): Pair<String, String>? {
        COMMAND_REGEX.find(line)?.let { match ->
            return match.groupValues[1] to match.groupValues[2]
        }
        return null
    }

    fun isPreparingStartRegion(line: String): Boolean = PREPARING_START_REGION_REGEX.containsMatchIn(line)

    fun parseChunkyProgress(line: String): ChunkyProgress? {
        CHUNKY_PROGRESS_REGEX.find(line)?.let { match ->
            return ChunkyProgress(
                current = match.groupValues[1].toLong(),
                total = match.groupValues[2].toLong(),
                percent = match.groupValues[3].toFloat()
            )
        }
        return null
    }

    // e.g. "[17:30:06 INFO]: [PocketCraftPing] Steve:10@127.0.0.1 Alex:42"
    private val PING_REGEX = Regex("""\[PocketCraftPing\](.*)""")
    // Purpur: "Steve's ping is 42ms"
    private val PURPUR_PING_REGEX = Regex("""(\S+)'s ping is (\d+)ms""", RegexOption.IGNORE_CASE)
    // Paper: "Steve has a ping of 42ms"  (RCON response, may contain §-color codes)
    private val PAPER_PING_REGEX = Regex("""(\S+)\s+has a ping of\s+(\d+)\s*ms""", RegexOption.IGNORE_CASE)

    /** Strip Minecraft legacy color/format codes (§X). */
    private fun stripMinecraftColors(text: String): String =
        text.replace(Regex("§[0-9a-fk-orA-FK-OR]"), "")

    fun parsePing(line: String): Map<String, ParsedPlayerPing> {
        // Strip Minecraft color codes first so all patterns work against clean text.
        val cleanLine = stripMinecraftColors(line)

        val match = PING_REGEX.find(cleanLine)
        if (match != null) {
            val data = match.groupValues[1].trim()
            val pings = mutableMapOf<String, ParsedPlayerPing>()
            for (pair in data.split(" ")) {
                val name = pair.substringBefore(':').trim()
                if (name.isBlank() || !pair.contains(':')) continue
                val value = pair.substringAfter(':')
                val ping = value.substringBefore('@').toIntOrNull() ?: -1
                val ip = value.substringAfter('@', "").trim()
                pings[name] = ParsedPlayerPing(pingMs = ping, ip = ip)
            }
            return pings
        }

        // Paper RCON response: "<player> has a ping of <N>ms"
        val paperMatch = PAPER_PING_REGEX.find(cleanLine)
        if (paperMatch != null) {
            val name = paperMatch.groupValues[1].trim()
            val ping = paperMatch.groupValues[2].toIntOrNull() ?: -1
            if (name.isNotBlank() && ping >= 0) {
                return mapOf(name to ParsedPlayerPing(pingMs = ping))
            }
        }

        // Purpur RCON response: "<player>'s ping is <N>ms"
        val purpurMatch = PURPUR_PING_REGEX.find(cleanLine)
        if (purpurMatch != null) {
            val name = purpurMatch.groupValues[1].trim()
            val ping = purpurMatch.groupValues[2].toIntOrNull() ?: -1
            if (name.isNotBlank() && ping >= 0) {
                return mapOf(name to ParsedPlayerPing(pingMs = ping))
            }
        }

        // Carpet / Fabric mod ping responses:
        // e.g. "Steve's ping is 24 ms" or "Steve's ping is 24ms" or "Steve: 24ms"
        val carpetMatch = Regex("""^(\S+)(?:'s ping is|'s latency is|'s ping:)\s*(\d+)\s*(?:ms)?""", RegexOption.IGNORE_CASE).find(cleanLine)
        if (carpetMatch != null) {
            val name = carpetMatch.groupValues[1].trim()
            val ping = carpetMatch.groupValues[2].toIntOrNull() ?: -1
            if (name.isNotBlank() && ping >= 0) {
                return mapOf(name to ParsedPlayerPing(pingMs = ping))
            }
        }

        // Generic / Fabric / Vanilla entity data response:
        // e.g. "Steve has the following entity data: 42" or "Steve: 42"
        val genericMatch = Regex("""^(\S+)\s+(?:has the following entity data:|has a ping of|has ping|latency is)\s*:?\s*(\d+)""", RegexOption.IGNORE_CASE).find(cleanLine)
        if (genericMatch != null) {
            val name = genericMatch.groupValues[1].trim()
            val ping = genericMatch.groupValues[2].toIntOrNull() ?: -1
            if (name.isNotBlank() && ping >= 0) {
                return mapOf(name to ParsedPlayerPing(pingMs = ping))
            }
        }

        return emptyMap()
    }

    /**
     * Parses a Fabric/Vanilla RCON `data get entity` response where the reply is a bare
     * "has the following entity data: <N>" line without a player-name prefix.
     * Returns the ping in ms, or -1 if not parseable.
     */
    fun parseFabricEntityLatency(rconResponse: String): Int {
        val clean = stripMinecraftColors(rconResponse).trim()
        // "has the following entity data: 37"
        val bareMatch = Regex("""(?:has the following entity data:|entity data:)\s*(\d+)""", RegexOption.IGNORE_CASE).find(clean)
        if (bareMatch != null) {
            return bareMatch.groupValues[1].toIntOrNull() ?: -1
        }
        // Plain integer response fallback: e.g. "37"
        val intOnly = clean.trim().toIntOrNull()
        if (intOnly != null && intOnly >= 0) return intOnly
        return -1
    }


    /** Returns the cleaned console text, stripping ANSI color codes. */
    fun stripAnsi(text: String): String =
        text.replace(Regex("\u001B\\[[;\\d]*m"), "")

    private fun stripLogDecorations(text: String): String =
        text.trim()
            .replace(PREFIX_REGEX_1, "")
            .replace(PREFIX_REGEX_2, "")
            .trim()
}

package com.pocketcraft.server.data.model

enum class LogLevel { INFO, WARN, ERROR, CHAT }

/**
 * A single parsed line from the Minecraft server console output.
 */
data class ConsoleMessage(
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel = LogLevel.INFO,
    val text: String = ""
)

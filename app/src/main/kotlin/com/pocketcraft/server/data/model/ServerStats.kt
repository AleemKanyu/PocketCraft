package com.pocketcraft.server.data.model

import androidx.compose.ui.graphics.Color
import com.pocketcraft.server.ui.theme.PocketColors

data class ServerStats(
    val tps: Float = 20f,
    val uptimeSeconds: Long = 0L
) {
    // Health calculation: TPS-based (0-100%)
    val healthPercent: Float
        get() = ((tps / 20f) * 100f).coerceIn(0f, 100f)

    val healthLabel: String
        get() = when {
            healthPercent >= 90f -> "Excellent"
            healthPercent >= 70f -> "Good"
            healthPercent >= 50f -> "Fair"
            else -> "Poor"
        }

    val healthColor: Color
        get() = when {
            healthPercent >= 90f -> PocketColors.Online        // Green
            healthPercent >= 70f -> PocketColors.Starting      // Yellow
            else -> PocketColors.Offline                       // Red
        }

    // XP calculation: 1 XP per 5 minutes of uptime
    val xp: Int
        get() = (uptimeSeconds / 300).toInt()

    // Level: XP / 10
    val level: Int
        get() = xp / 10

    // Progress to next level (0.0f to 1.0f)
    val xpProgress: Float
        get() = (xp % 10) / 10f

    // Uptime formatted as HH:MM:SS
    val uptimeFormatted: String
        get() {
            val hours = uptimeSeconds / 3600
            val minutes = (uptimeSeconds % 3600) / 60
            val seconds = uptimeSeconds % 60
            return "%02d:%02d:%02d".format(hours, minutes, seconds)
        }

    // TPS formatted
    val tpsFormatted: String
        get() = String.format("%.2f", tps)
}

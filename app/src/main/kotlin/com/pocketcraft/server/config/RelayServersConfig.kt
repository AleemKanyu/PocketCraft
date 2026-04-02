package com.pocketcraft.server.config

/**
 * Central configuration for all supported relay servers.
 * Used by server location selection dialog, settings, and relay manager.
 */
data class RelayServerConfig(
    val host: String,
    val displayName: String,
    val region: String,
    val emoji: String,
    val description: String,
    val bestFor: String = ""
)

object RelayServers {
    val SINGAPORE = RelayServerConfig(
        host = "play.pocketcraft.online",
        displayName = "Global (Singapore)",
        region = "Global",
        emoji = "🌐",
        description = "Global CDN with worldwide coverage",
        bestFor = "Players worldwide, default option"
    )

    val MUMBAI = RelayServerConfig(
        host = "mine.pocketcraft.online",
        displayName = "Asia (Mumbai)",
        region = "Asia",
        emoji = "🌏",
        description = "Optimized for players in India and South Asia",
        bestFor = "Players in India, Pakistan, Bangladesh, Sri Lanka"
    )

    val ALL = listOf(SINGAPORE, MUMBAI)

    fun getBestForTimeZone(timeZoneId: String?): RelayServerConfig {
        val tz = timeZoneId.orEmpty().lowercase()
        val southAsiaTimeZones = listOf(
            "asia/kolkata",
            "asia/calcutta",
            "asia/mumbai",
            "asia/karachi",
            "asia/dhaka",
            "asia/colombo",
            "asia/kathmandu"
        )

        if (southAsiaTimeZones.any { tz == it }) return MUMBAI

        // Fallback by UTC offset for South Asia users whose devices report alias IDs.
        val offsetMillis = java.util.TimeZone.getTimeZone(timeZoneId).rawOffset
        val southAsiaOffsets = setOf(
            18_000_000, // UTC+05:00
            19_800_000, // UTC+05:30 (India/Sri Lanka)
            20_700_000, // UTC+05:45 (Nepal)
            21_600_000  // UTC+06:00 (Bangladesh)
        )

        return if (offsetMillis in southAsiaOffsets) MUMBAI else SINGAPORE
    }

    fun getByHost(host: String?): RelayServerConfig {
        return ALL.firstOrNull { it.host == host } ?: SINGAPORE
    }

    fun getDisplayName(host: String?): String {
        return getByHost(host).displayName
    }
}

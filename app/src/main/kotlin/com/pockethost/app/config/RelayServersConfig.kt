package com.pockethost.app.config

import com.pockethost.app.data.model.RelayRegion

/**
 * Central configuration for all supported relay servers.
 * Used by server location selection dialog, settings, and relay manager.
 */
data class RelayServerConfig(
    val host: String,
    val displayName: String,
    val region: String,
    val icon: String,
    val description: String,
    val bestFor: String = "",
    val fallbackIp: String? = null
)

object RelayServers {
    val MUMBAI = RelayServerConfig(
        host = "mine.pocketcraft.online",
        displayName = "Asia (India)",
        region = "Asia (India)",
        icon = "🌏",
        description = "Optimized for players in India and South Asia",
        bestFor = "Players in India, Pakistan, Bangladesh, Sri Lanka",
        fallbackIp = "13.233.131.236"
    )



    val EUROPE = RelayServerConfig(
        host = "eu.pocketcraft.online",
        displayName = "Europe",
        region = "Europe",
        icon = "🇪🇺",
        description = "Frankfurt relay for lower latency across Europe",
        bestFor = "Players in Europe, Middle East, and nearby regions",
        fallbackIp = "3.72.235.245"
    )

    val AMERICA = RelayServerConfig(
        host = "us.pocketcraft.online",
        displayName = "America",
        region = "America",
        icon = "🇺🇸",
        description = "US East relay for North and South America",
        bestFor = "Players in the Americas",
        fallbackIp = "98.83.40.35"
    )

    private val defaultRegionConfigs = listOf(MUMBAI, EUROPE, AMERICA)
    private val metadataByHost = defaultRegionConfigs.associateBy { it.host }

    private var dynamicRegions: List<RelayRegion> = defaultRegions()
    private var dynamicConfigs: List<RelayServerConfig> = buildConfigs(dynamicRegions)

    fun defaultRegions(): List<RelayRegion> = listOf(
        RelayRegion("Asia (India)", MUMBAI.host),
        RelayRegion("Europe", EUROPE.host),
        RelayRegion("America", AMERICA.host)
    )

    val ALL: List<RelayServerConfig>
        get() = dynamicConfigs

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
        val europeTimeZones = listOf(
            "europe/",
            "africa/cairo",
            "asia/dubai",
            "asia/riyadh",
            "asia/jerusalem",
            "asia/tehran"
        )
        val americaTimeZones = listOf(
            "america/",
            "us/",
            "canada/",
            "mexico/",
            "brazil/",
            "chile/",
            "argentina/"
        )

        if (southAsiaTimeZones.any { tz == it }) return MUMBAI
        if (europeTimeZones.any { tz.startsWith(it) }) return EUROPE
        if (americaTimeZones.any { tz.startsWith(it) }) return AMERICA

        // Fallback by UTC offset for South Asia users whose devices report alias IDs.
        val offsetMillis = java.util.TimeZone.getTimeZone(timeZoneId).rawOffset
        val southAsiaOffsets = setOf(
            18_000_000, // UTC+05:00
            19_800_000, // UTC+05:30 (India/Sri Lanka)
            20_700_000, // UTC+05:45 (Nepal)
            21_600_000  // UTC+06:00 (Bangladesh)
        )
        val europeOffsets = setOf(
            0,
            3_600_000,
            7_200_000,
            10_800_000,
            14_400_000
        )
        val americaOffsets = setOf(
            -36_000_000,
            -32_400_000,
            -28_800_000,
            -25_200_000,
            -21_600_000,
            -18_000_000,
            -14_400_000,
            -10_800_000
        )

        return when {
            offsetMillis in southAsiaOffsets -> MUMBAI
            offsetMillis in europeOffsets -> EUROPE
            offsetMillis in americaOffsets -> AMERICA
            else -> MUMBAI
        }
    }

    fun updateRegions(regions: List<RelayRegion>) {
        val normalized = regions
            .map { RelayRegion(it.label.trim(), it.host.trim()) }
            .filter { it.label.isNotBlank() && it.host.isNotBlank() }
            .distinctBy { it.host }
        if (normalized.isEmpty()) return
        dynamicRegions = normalized
        dynamicConfigs = buildConfigs(normalized)
    }

    fun currentRegions(): List<RelayRegion> = dynamicRegions

    fun resolveBedrockRegion(host: String): String = when (host) {
        MUMBAI.host -> "MUMBAI"
        EUROPE.host -> "EUROPE"
        AMERICA.host -> "AMERICA"
        else -> "MUMBAI"
    }

    fun getByHost(host: String?): RelayServerConfig {
        if (host.isNullOrBlank()) return MUMBAI
        val matched = ALL.firstOrNull { it.host == host } ?: metadataByHost[host]
        if (matched != null) return matched
        return RelayServerConfig(
            host = host,
            displayName = "Custom ($host)",
            region = "Custom",
            icon = "🌐",
            description = "Private custom relay server",
            bestFor = "Private server connections"
        )
    }

    fun getDisplayName(host: String?): String {
        return getByHost(host).displayName
    }

    private fun buildConfigs(regions: List<RelayRegion>): List<RelayServerConfig> {
        return regions.map { region ->
            metadataByHost[region.host]?.copy(
                displayName = region.label,
                region = region.label
            ) ?: RelayServerConfig(
                host = region.host,
                displayName = region.label,
                region = region.label,
                icon = "🌐",
                description = "Relay server selected from remote configuration",
                bestFor = region.label
            )
        }
    }
}

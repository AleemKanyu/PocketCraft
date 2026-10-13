package com.pockethost.app.data.model

import java.io.File
import java.util.Properties

/**
 * The game rules players ask for most on small servers, as simple on/off choices.
 *
 * Rules live in the world save, so the app keeps its own copy in server.properties
 * (`pocketcraft-rule-*`) and sends the matching `/gamerule` commands when the server is
 * up. A rule is only stored once the user moves it off Minecraft's default, so rules the
 * app was never asked about keep whatever was set in-game.
 */
data class GameRuleOptions(
    val keepInventory: Boolean = false,
    val onePlayerSleep: Boolean = false,
    val mobGriefing: Boolean = true,
    val phantoms: Boolean = true,
    val fireSpread: Boolean = true,
    val daylightCycle: Boolean = true,
    val weatherCycle: Boolean = true
) {
    private class Rule(
        val key: String,
        val default: Boolean,
        val value: (GameRuleOptions) -> Boolean,
        /** Command arguments for Minecraft before 1.21.11 (camelCase rule names). */
        val legacy: (Boolean) -> String,
        /** Command arguments for 1.21.11 and later, where every rule was renamed. */
        val modern: (Boolean) -> String
    )

    /** Stores the rules in [props], leaving out any still on Minecraft's default and never stored before. */
    fun writeTo(props: Properties) {
        RULES.forEach { rule ->
            val value = rule.value(this)
            if (value != rule.default || props.containsKey(rule.key)) {
                props[rule.key] = value.toString()
            }
        }
    }

    companion object {
        private const val PREFIX = "pocketcraft-rule-"

        private val RULES = listOf(
            Rule("${PREFIX}keep-inventory", false, { it.keepInventory },
                { "keepInventory $it" }, { "keep_inventory $it" }),
            // 0 lets one sleeping player skip the night; 100 is Minecraft's default (everyone).
            Rule("${PREFIX}one-player-sleep", false, { it.onePlayerSleep },
                { "playersSleepingPercentage ${if (it) 0 else 100}" },
                { "players_sleeping_percentage ${if (it) 0 else 100}" }),
            Rule("${PREFIX}mob-griefing", true, { it.mobGriefing },
                { "mobGriefing $it" }, { "mob_griefing $it" }),
            Rule("${PREFIX}phantoms", true, { it.phantoms },
                { "doInsomnia $it" }, { "spawn_phantoms $it" }),
            // 1.21.11 replaced the on/off fire rule with a radius: 0 stops spread, 128 is the default.
            Rule("${PREFIX}fire-spread", true, { it.fireSpread },
                { "doFireTick $it" }, { "fire_spread_radius_around_player ${if (it) 128 else 0}" }),
            Rule("${PREFIX}daylight-cycle", true, { it.daylightCycle },
                { "doDaylightCycle $it" }, { "advance_time $it" }),
            Rule("${PREFIX}weather-cycle", true, { it.weatherCycle },
                { "doWeatherCycle $it" }, { "advance_weather $it" })
        )

        fun readFrom(props: Map<String, String>): GameRuleOptions {
            fun rule(index: Int): Boolean =
                props[RULES[index].key]?.toBooleanStrictOrNull() ?: RULES[index].default
            return GameRuleOptions(
                keepInventory = rule(0),
                onePlayerSleep = rule(1),
                mobGriefing = rule(2),
                phantoms = rule(3),
                fireSpread = rule(4),
                daylightCycle = rule(5),
                weatherCycle = rule(6)
            )
        }

        /** Console commands for every rule stored in [propertiesFile], in the naming [gameVersion] understands. */
        fun commandsFor(propertiesFile: File, gameVersion: String): List<String> {
            if (!propertiesFile.exists()) return emptyList()
            val props = Properties().apply { propertiesFile.inputStream().use { load(it) } }
            val modern = usesRenamedRules(gameVersion)
            return RULES.mapNotNull { rule ->
                val value = props.getProperty(rule.key)?.toBooleanStrictOrNull() ?: return@mapNotNull null
                "gamerule " + if (modern) rule.modern(value) else rule.legacy(value)
            }
        }

        /** The `/gamerule` name for "mobs spawn", which the app already drives from its Spawn Monsters setting. */
        fun mobSpawningRule(gameVersion: String): String =
            if (usesRenamedRules(gameVersion)) "spawn_mobs" else "doMobSpawning"

        /**
         * Minecraft 1.21.11 renamed the game rules to snake_case. A version that cannot be
         * read counts as new, since new releases are where unfamiliar version strings come from.
         */
        fun usesRenamedRules(gameVersion: String): Boolean {
            val parts = gameVersion.trim().substringBefore('-').split('.').map { it.toIntOrNull() }
            if (parts.isEmpty() || parts.any { it == null }) return true
            val (major, minor, patch) = List(3) { parts.getOrNull(it) ?: 0 }
            return compareValuesBy(Triple(major, minor, patch), Triple(1, 21, 11), { it.first }, { it.second }, { it.third }) >= 0
        }
    }
}

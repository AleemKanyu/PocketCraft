package com.pocketcraft.server.service

import java.io.File
import java.util.Properties

object ServerPropertiesHelper {

    /**
     * Compression threshold for relay hosting. A threshold of 256 compresses
     * all packets larger than 256 bytes (such as chunk updates, blocks, and
     * inventory states). This drastically reduces the total upload bandwidth
     * required over the relay TCP tunnel (usually by 3x to 4x), preventing
     * network pipe saturation and Head-of-Line blocking (which causes ping spikes).
     */
    const val RELAY_READY_COMPRESSION_THRESHOLD = 128
    const val DEFAULT_VIEW_DISTANCE = 6
    const val DEFAULT_SIMULATION_DISTANCE = 4
    const val DESIRED_VIEW_DISTANCE_KEY = "pocketcraft-desired-view-distance"
    const val DESIRED_SIMULATION_DISTANCE_KEY = "pocketcraft-desired-simulation-distance"
    const val RELAY_READY_ENTITY_BROADCAST_PERCENT = 100
    const val POCKETCRAFT_JOIN_MESSAGE_TEXT = "hosted on Pocketcraft"
    const val POCKETCRAFT_JOIN_MESSAGE_URL = "https://discord.gg/7xw3Rd2vs2"

    fun getServerPropertiesFile(serverDir: File): File {
        return File(serverDir, "server.properties")
    }

    fun readProperties(serverDir: File, persistDefaults: Boolean = true): Properties {
        val props = Properties()
        val file = getServerPropertiesFile(serverDir)
        var needsPersist = false
        if (file.exists()) {
            if (file.length() > 100_000L) {
                android.util.Log.e("ServerPropertiesHelper", "server.properties is suspiciously large (${file.length()} bytes). Deleting corrupted file.")
                runCatching { file.delete() }
            }
        }
        if (file.exists()) {
            file.inputStream().use { props.load(it) }
        } else {
            // Default properties for a new server
            props["server-port"] = "25565"
            props["motd"] = "A PocketCraft Server"
            props["max-players"] = "5"
            props["difficulty"] = "easy"
            props["gamemode"] = "survival"
            props["online-mode"] = "false"
            props["pvp"] = "true"
            props["allow-flight"] = "true"
            props["hardcore"] = "false"
            props["spawn-monsters"] = "true"
            props["spawn-animals"] = "true"
            props["spawn-npcs"] = "true"
            props["allow-nether"] = "true"
            props["enable-command-block"] = "true"
            // Explicitly set spawn-protection so it survives all config re-writes.
            // Without this, Minecraft's implicit default (16) is used but the key is
            // never present in server.properties, so the config editor shows it blank.
            props["spawn-protection"] = "16"
            props["pocketcraft-max-ram-mb"] = "1024"
            props["view-distance"] = DEFAULT_VIEW_DISTANCE.toString()
            props["simulation-distance"] = DEFAULT_SIMULATION_DISTANCE.toString()
            props["entity-broadcast-range-percentage"] = RELAY_READY_ENTITY_BROADCAST_PERCENT.toString()
            props["network-compression-threshold"] = RELAY_READY_COMPRESSION_THRESHOLD.toString()
            props["sync-chunk-writes"] = "false"
            props["use-native-transport"] = "false"
            props["max-tick-time"] = "60000"
            props["enable-status-request"] = "true"
            props["query.port"] = "25565"
            props["prevent-proxy-connections"] = "false"
            props["pocketcraft-join-message-enabled"] = "true"
            props["pocketcraft-join-message-text"] = POCKETCRAFT_JOIN_MESSAGE_TEXT
            props["pocketcraft-join-message-url"] = POCKETCRAFT_JOIN_MESSAGE_URL
            needsPersist = true
        }
        if (props.getProperty("pocketcraft-join-message-enabled") != "true") {
            props["pocketcraft-join-message-enabled"] = "true"
            needsPersist = true
        }
        if (props.getProperty("pocketcraft-join-message-text") != POCKETCRAFT_JOIN_MESSAGE_TEXT) {
            props["pocketcraft-join-message-text"] = POCKETCRAFT_JOIN_MESSAGE_TEXT
            needsPersist = true
        }
        if (props.getProperty("pocketcraft-join-message-url") != POCKETCRAFT_JOIN_MESSAGE_URL) {
            props["pocketcraft-join-message-url"] = POCKETCRAFT_JOIN_MESSAGE_URL
            needsPersist = true
        }
        if (props.getProperty("enable-rcon") != "true" || 
            props.getProperty("rcon.port") != "25575" || 
            props.getProperty("rcon.password") != "pocketcraft-internal-rcon") {
            props["enable-rcon"] = "true"
            props["rcon.port"] = "25575"
            props["rcon.password"] = "pocketcraft-internal-rcon"
            needsPersist = true
        }
        if (syncDesiredChunkDistances(props)) {
            needsPersist = true
        }
        if (needsPersist && persistDefaults) {
            saveProperties(serverDir, props)
        }
        return props
    }

    private fun syncDesiredChunkDistances(props: Properties): Boolean {
        var changed = false

        // Seed desired keys from legacy view-distance values once.
        if (props.getProperty(DESIRED_VIEW_DISTANCE_KEY).isNullOrBlank()) {
            props.getProperty("view-distance")?.toIntOrNull()?.coerceIn(3, 32)?.let { legacy ->
                props[DESIRED_VIEW_DISTANCE_KEY] = legacy.toString()
                changed = true
            }
        }
        if (props.getProperty(DESIRED_SIMULATION_DISTANCE_KEY).isNullOrBlank()) {
            props.getProperty("simulation-distance")?.toIntOrNull()?.coerceIn(3, 32)?.let { legacy ->
                props[DESIRED_SIMULATION_DISTANCE_KEY] = legacy.toString()
                changed = true
            }
        }

        // Keep the user's requested values separate from the active runtime values.
        // Relay startup intentionally lowers view-distance and simulation-distance;
        // ordinary UI reads must not restore the larger requested values afterward.
        val desiredView = props.getProperty(DESIRED_VIEW_DISTANCE_KEY)?.toIntOrNull()?.coerceIn(3, 32)
            ?: DEFAULT_VIEW_DISTANCE
        val desiredSimulation = props.getProperty(DESIRED_SIMULATION_DISTANCE_KEY)?.toIntOrNull()?.coerceIn(3, 32)
            ?: DEFAULT_SIMULATION_DISTANCE

        if (props.getProperty(DESIRED_VIEW_DISTANCE_KEY)?.toIntOrNull() != desiredView) {
            props[DESIRED_VIEW_DISTANCE_KEY] = desiredView.toString()
            changed = true
        }
        if (props.getProperty(DESIRED_SIMULATION_DISTANCE_KEY)?.toIntOrNull() != desiredSimulation) {
            props[DESIRED_SIMULATION_DISTANCE_KEY] = desiredSimulation.toString()
            changed = true
        }
        return changed
    }

    fun saveProperties(serverDir: File, props: Properties) {
        val file = getServerPropertiesFile(serverDir)
        val parent = file.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }
        file.outputStream().use { 
            props.store(it, "PocketCraft Server Properties")
        }
    }

    fun getProperty(serverDir: File, key: String, defaultValue: String): String {
        return readProperties(serverDir).getProperty(key, defaultValue)
    }

    fun setProperty(serverDir: File, key: String, value: String) {
        val props = readProperties(serverDir)
        props.setProperty(key, value)
        saveProperties(serverDir, props)
    }
}

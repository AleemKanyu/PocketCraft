package com.pockethost.app.service

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
    const val RELAY_READY_COMPRESSION_THRESHOLD = 256
    const val DEFAULT_VIEW_DISTANCE = 6
    const val DEFAULT_SIMULATION_DISTANCE = 4
    const val DESIRED_VIEW_DISTANCE_KEY = "pocketcraft-desired-view-distance"
    const val DESIRED_SIMULATION_DISTANCE_KEY = "pocketcraft-desired-simulation-distance"
    const val RELAY_READY_ENTITY_BROADCAST_PERCENT = 100
    const val POCKETCRAFT_JOIN_MESSAGE_TEXT = "hosted on PocketHost"
    const val POCKETCRAFT_JOIN_MESSAGE_URL = "https://discord.gg/7xw3Rd2vs2"

    private const val WORLD_DETAILS_FILE = "pocketcraft-world-details.properties"
    private val WORLD_DETAIL_KEY_PREFIXES = listOf(
        "pocketcraft-world-display.",
        "pocketcraft-world-photo.",
        "pocketcraft-world-description."
    )
    private val AMPERSAND_COLOR_CODE = Regex("&(?=[0-9a-fk-orA-FK-OR])")

    fun getServerPropertiesFile(serverDir: File): File {
        return File(serverDir, "server.properties")
    }

    /** Turns the "&a" style colour codes people type into the "§a" codes Minecraft reads. */
    fun translateColorCodes(text: String): String = AMPERSAND_COLOR_CODE.replace(text, "\u00A7")

    /**
     * Loads a properties file the way the Minecraft server does: UTF-8 first, ISO-8859-1 when
     * the bytes are not valid UTF-8. The server rewrites server.properties in UTF-8 on every
     * start, so reading it as ISO-8859-1 mangles "§" and any other non-ASCII character.
     * The saved world details are then laid on top, see [saveWorldDetails].
     */
    fun loadInto(file: File, props: Properties) {
        val bytes = file.readBytes()
        val text = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(bytes))
                .toString()
        } catch (error: java.nio.charset.CharacterCodingException) {
            String(bytes, Charsets.ISO_8859_1)
        }
        try {
            props.load(java.io.StringReader(text))
        } catch (error: IllegalArgumentException) {
            // Properties.load rejects the whole file over one bad \uXXXX escape (a hand-edited
            // MOTD is the usual cause). Keep every line that can still be read as key=value.
            android.util.Log.w("ServerPropertiesHelper", "Malformed ${file.name}, reading line by line", error)
            props.clear()
            text.lineSequence()
                .filter { it.contains('=') && !it.trimStart().startsWith("#") }
                .forEach { line ->
                    val idx = line.indexOf('=')
                    val key = line.substring(0, idx).trim()
                    if (key.isNotEmpty()) props[key] = line.substring(idx + 1).trim()
                }
        }
        file.parentFile?.let { overlayWorldDetails(it, props) }
    }

    /**
     * World name, photo and description also live in a file only the app writes. A running
     * Minecraft server saves server.properties from its own memory, which puts back the
     * values it loaded at start and would undo an edit made while it was running.
     */
    fun saveWorldDetails(serverDir: File, details: Map<String, String>) {
        updateWorldDetailsFile(serverDir) { stored -> details.forEach { (key, value) -> stored[key] = value } }
    }

    fun removeWorldDetails(serverDir: File, keys: Collection<String>) {
        if (!File(serverDir, WORLD_DETAILS_FILE).exists()) return
        updateWorldDetailsFile(serverDir) { stored -> keys.forEach { stored.remove(it) } }
    }

    private fun updateWorldDetailsFile(serverDir: File, change: (Properties) -> Unit) {
        val file = File(serverDir, WORLD_DETAILS_FILE)
        val stored = Properties()
        if (file.exists()) runCatching { file.inputStream().use { stored.load(it) } }
        change(stored)
        serverDir.mkdirs()
        file.outputStream().use { stored.store(it, "PocketHost world details") }
    }

    private fun overlayWorldDetails(serverDir: File, props: Properties) {
        val file = File(serverDir, WORLD_DETAILS_FILE)
        if (!file.exists()) return
        val stored = Properties()
        runCatching { file.inputStream().use { stored.load(it) } }
        stored.stringPropertyNames()
            .filter { key -> WORLD_DETAIL_KEY_PREFIXES.any { key.startsWith(it) } }
            .forEach { key -> props[key] = stored.getProperty(key) }
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
            loadInto(file, props)
        } else {
            // Default properties for a new server
            props["server-port"] = "25565"
            props["motd"] = "A PocketHost Server"
            props["max-players"] = "5"
            props["difficulty"] = "normal"
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
            props[DESIRED_VIEW_DISTANCE_KEY] = DEFAULT_VIEW_DISTANCE.toString()
            props[DESIRED_SIMULATION_DISTANCE_KEY] = DEFAULT_SIMULATION_DISTANCE.toString()
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
        if (props.getProperty("sync-chunk-writes") != "false") {
            props["sync-chunk-writes"] = "false"
            needsPersist = true
        }
        if (props.getProperty("max-tick-time") == null) {
            props["max-tick-time"] = "60000"
            needsPersist = true
        }
        if (props.getProperty("allow-nether") == null) {
            props["allow-nether"] = "true"
            needsPersist = true
        }
        if (props.getProperty("network-compression-threshold") == null) {
            props["network-compression-threshold"] = RELAY_READY_COMPRESSION_THRESHOLD.toString()
            needsPersist = true
        }
        if (props.getProperty("enable-rcon") != "true" || 
            props.getProperty("rcon.port") != "25575" || 
            props.getProperty("rcon.password") != com.pockethost.app.network.RconSecret.current()) {
            props["enable-rcon"] = "true"
            props["rcon.port"] = "25575"
            // Replaces the old shared password too, so existing servers move to the
            // per-install secret on their next start.
            props["rcon.password"] = com.pockethost.app.network.RconSecret.current()
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

        val existingView = props.getProperty("view-distance")?.toIntOrNull()?.coerceIn(3, 32)
        val existingSim = props.getProperty("simulation-distance")?.toIntOrNull()?.coerceIn(3, 32)
        val desiredView = props.getProperty(DESIRED_VIEW_DISTANCE_KEY)?.toIntOrNull()?.coerceIn(3, 32)
            ?: existingView ?: DEFAULT_VIEW_DISTANCE
        val desiredSimulation = props.getProperty(DESIRED_SIMULATION_DISTANCE_KEY)?.toIntOrNull()?.coerceIn(3, 32)
            ?: existingSim ?: DEFAULT_SIMULATION_DISTANCE

        if (props.getProperty(DESIRED_VIEW_DISTANCE_KEY)?.toIntOrNull() != desiredView) {
            props[DESIRED_VIEW_DISTANCE_KEY] = desiredView.toString()
            changed = true
        }
        if (props.getProperty(DESIRED_SIMULATION_DISTANCE_KEY)?.toIntOrNull() != desiredSimulation) {
            props[DESIRED_SIMULATION_DISTANCE_KEY] = desiredSimulation.toString()
            changed = true
        }
        if (props.getProperty("view-distance")?.toIntOrNull() != desiredView) {
            props["view-distance"] = desiredView.toString()
            changed = true
        }
        if (props.getProperty("simulation-distance")?.toIntOrNull() != desiredSimulation) {
            props["simulation-distance"] = desiredSimulation.toString()
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
            props.store(it, "PocketHost Server Properties")
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

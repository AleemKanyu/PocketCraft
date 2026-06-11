package com.pocketcraft.server.service

import java.io.File
import java.util.Properties

object ServerPropertiesHelper {

    /**
     * Disabled for relay hosting — zlib on every packet adds CPU load on the host phone
     * and inflates Paper keepalive ping. The May 2026 regression was bridge buffer size,
     * not this value. See RelayManager KDoc.
     */
    const val RELAY_READY_COMPRESSION_THRESHOLD = -1
    const val DEFAULT_VIEW_DISTANCE = 6
    const val DEFAULT_SIMULATION_DISTANCE = 4
    const val DESIRED_VIEW_DISTANCE_KEY = "pocketcraft-desired-view-distance"
    const val DESIRED_SIMULATION_DISTANCE_KEY = "pocketcraft-desired-simulation-distance"
    const val RELAY_READY_ENTITY_BROADCAST_PERCENT = 35
    const val POCKETCRAFT_JOIN_MESSAGE_TEXT = "hosted on Pocketcraft"
    const val POCKETCRAFT_JOIN_MESSAGE_URL = "https://discord.gg/NGPzXFYp"

    fun getServerPropertiesFile(serverDir: File): File {
        return File(serverDir, "server.properties")
    }

    fun readProperties(serverDir: File, persistDefaults: Boolean = true): Properties {
        val props = Properties()
        val file = getServerPropertiesFile(serverDir)
        var needsPersist = false
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
            props["pocketcraft-max-ram-mb"] = "1024"
            props["entity-broadcast-range-percentage"] = RELAY_READY_ENTITY_BROADCAST_PERCENT.toString()
            props["network-compression-threshold"] = RELAY_READY_COMPRESSION_THRESHOLD.toString()
            props["sync-chunk-writes"] = "false"
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
        if (needsPersist && persistDefaults) {
            saveProperties(serverDir, props)
        }
        return props
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

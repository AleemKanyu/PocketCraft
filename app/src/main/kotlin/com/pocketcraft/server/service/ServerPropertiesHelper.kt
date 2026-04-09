package com.pocketcraft.server.service

import java.io.File
import java.util.Properties

object ServerPropertiesHelper {

    const val RELAY_READY_COMPRESSION_THRESHOLD = 256
    const val DEFAULT_VIEW_DISTANCE = 5
    const val DEFAULT_SIMULATION_DISTANCE = 4
    const val RELAY_READY_ENTITY_BROADCAST_PERCENT = 75

    fun getServerPropertiesFile(serverDir: File): File {
        return File(serverDir, "server.properties")
    }

    fun readProperties(serverDir: File): Properties {
        val props = Properties()
        val file = getServerPropertiesFile(serverDir)
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
            props["allow-flight"] = "false"
            props["hardcore"] = "false"
            props["view-distance"] = DEFAULT_VIEW_DISTANCE.toString()
            props["simulation-distance"] = DEFAULT_SIMULATION_DISTANCE.toString()
            props["spawn-monsters"] = "true"
            props["spawn-animals"] = "true"
            props["spawn-npcs"] = "true"
            props["allow-nether"] = "true"
            props["enable-command-block"] = "true"
            props["pocketcraft-max-ram-mb"] = "1024"
            // Relay traffic needs compression to keep chunk/login bursts stable.
            props["network-compression-threshold"] = RELAY_READY_COMPRESSION_THRESHOLD.toString()
            props["sync-chunk-writes"] = "false"
            props["enable-status-request"] = "true"
            props["query.port"] = "25565"
            props["prevent-proxy-connections"] = "false"
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

package com.pocketcraft.server.service

import java.io.File
import java.util.Properties

object ServerPropertiesHelper {

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
            props["max-players"] = "20"
            props["difficulty"] = "easy"
            props["gamemode"] = "survival"
            props["online-mode"] = "false"
            props["pvp"] = "true"
            props["allow-flight"] = "false"
            props["hardcore"] = "false"
            props["view-distance"] = "8"
            props["simulation-distance"] = "7"
            props["spawn-monsters"] = "true"
            props["spawn-animals"] = "true"
            props["spawn-npcs"] = "true"
            props["allow-nether"] = "true"
            props["enable-command-block"] = "true"
            props["pocketcraft-max-ram-mb"] = "1024"
            // Disable compression for LAN to avoid timeout issues
            props["network-compression-threshold"] = "-1"
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

package com.pocketcraft.server.server

import android.util.Log
import com.pocketcraft.server.data.model.ServerConfig
import com.pocketcraft.server.service.ServerPropertiesHelper
import java.io.File
import java.util.Properties

data class ServerPrefsSnapshot(
    val worldName: String,
    val worldSeed: String,
    val maxPlayers: Int,
    val difficulty: String,
    val gameMode: String,
    val onlineMode: Boolean,
    val motd: String,
    val pvp: Boolean,
    val viewDistance: Int,
    val simulationDistance: Int,
    val spawnProtection: Int,
    val allowFlight: Boolean,
    val whiteList: Boolean,
    val enforceWhitelist: Boolean,
    val opPermissionLevel: Int,
    val playerIdleTimeout: Int,
    val commandBlocks: Boolean,
    val netherEnabled: Boolean,
    val spawnMonsters: Boolean,
    val spawnAnimals: Boolean,
    val spawnNpcs: Boolean,
    val hardcore: Boolean,
    val maxRamMb: Int,
    val ramMode: String,
    val entityBroadcastRangePercentage: Int,
    val maxWorldSize: Int,
    val useNativeTransport: Boolean,
    val maxBuildHeight: Int,
    val generateStructures: Boolean,
    val levelType: String,
    val serverType: String,
    val gameVersion: String,
    val customJarPath: String?
)

object ServerPropertiesWriter {
    private const val TAG = "ServerPropertiesWriter"

    fun apply(serverDir: File, prefs: ServerPrefsSnapshot) {
        val file = File(serverDir, "server.properties")
        val props = loadExisting(file)
        overlayManagedValues(props, prefs)
        save(file, props)
        Log.d(
            TAG,
            "server.properties updated: view-distance=${prefs.viewDistance}, simulation-distance=${prefs.simulationDistance}, max-players=${prefs.maxPlayers}"
        )
    }

    fun write(file: File, prefs: ServerPrefsSnapshot) {
        val props = loadExisting(file)
        overlayManagedValues(props, prefs)
        save(file, props)
    }

    fun overlayManagedValues(props: Properties, prefs: ServerPrefsSnapshot) {
        props["level-name"] = prefs.worldName
        props["level-seed"] = prefs.worldSeed
        props["max-players"] = prefs.maxPlayers.coerceIn(1, 20).toString()
        props["server-port"] = "25565"
        props["difficulty"] = prefs.difficulty.lowercase()
        props["gamemode"] = prefs.gameMode.lowercase()
        props["online-mode"] = "false"
        props["motd"] = prefs.motd
        props["pvp"] = prefs.pvp.toString()
        props["view-distance"] = prefs.viewDistance.coerceIn(3, 32).toString()
        props["simulation-distance"] = prefs.simulationDistance.coerceIn(3, 32).toString()
        props["spawn-protection"] = prefs.spawnProtection.coerceAtLeast(0).toString()
        props["allow-flight"] = prefs.allowFlight.toString()
        props["white-list"] = prefs.whiteList.toString()
        props["enforce-whitelist"] = prefs.enforceWhitelist.toString()
        props["op-permission-level"] = prefs.opPermissionLevel.coerceIn(1, 4).toString()
        props["player-idle-timeout"] = prefs.playerIdleTimeout.coerceAtLeast(0).toString()
        props["enable-command-block"] = prefs.commandBlocks.toString()
        props["allow-nether"] = prefs.netherEnabled.toString()
        props["spawn-monsters"] = prefs.spawnMonsters.toString()
        props["spawn-animals"] = prefs.spawnAnimals.toString()
        props["spawn-npcs"] = prefs.spawnNpcs.toString()
        props["hardcore"] = prefs.hardcore.toString()
        props["pocketcraft-max-ram-mb"] = prefs.maxRamMb.coerceAtLeast(512).toString()
        props["pocketcraft-ram-mode"] = prefs.ramMode
        props["entity-broadcast-range-percentage"] = prefs.entityBroadcastRangePercentage.coerceIn(1, 100).toString()
        props["max-world-size"] = prefs.maxWorldSize.coerceAtLeast(1).toString()
        props["use-native-transport"] = prefs.useNativeTransport.toString()
        props["max-build-height"] = prefs.maxBuildHeight.coerceAtLeast(64).toString()
        props["generate-structures"] = prefs.generateStructures.toString()
        props["level-type"] = prefs.levelType
        props["server-ip"] = "0.0.0.0"
        props["network-compression-threshold"] = ServerPropertiesHelper.RELAY_READY_COMPRESSION_THRESHOLD.toString()
        props["sync-chunk-writes"] = "false"
        props["max-tick-time"] = "60000"
        props["enable-rcon"] = "true"
        props["rcon.port"] = "25575"
        props["rcon.password"] = "pocketcraft-internal-rcon"
        props["broadcast-rcon-to-ops"] = "false"
        props["pocketcraft-server-type"] = prefs.serverType
        props["pocketcraft-game-version"] = prefs.gameVersion
        props["pocketcraft-join-message-enabled"] = "true"
        props["pocketcraft-join-message-text"] = ServerConfig().joinMessageText
        props["pocketcraft-join-message-url"] = ServerConfig().joinMessageUrl
        if (prefs.customJarPath.isNullOrBlank()) {
            props.remove("pocketcraft-custom-jar-path")
        } else {
            props["pocketcraft-custom-jar-path"] = prefs.customJarPath
        }
    }

    fun toSnapshot(config: ServerConfig): ServerPrefsSnapshot {
        return ServerPrefsSnapshot(
            worldName = config.worldName,
            worldSeed = config.worldSeed,
            maxPlayers = config.maxPlayers,
            difficulty = config.difficulty,
            gameMode = config.gameMode,
            onlineMode = config.onlineMode,
            motd = if (config.motd.isBlank()) {
                "Hosted on Pocketcraft"
            } else {
                "${config.motd} - Hosted on Pocketcraft"
            },
            pvp = config.pvp,
            viewDistance = config.viewDistance,
            simulationDistance = config.simulationDistance,
            spawnProtection = config.spawnProtection,
            allowFlight = config.allowFlight,
            whiteList = config.whiteList,
            enforceWhitelist = config.enforceWhitelist,
            opPermissionLevel = config.opPermissionLevel,
            playerIdleTimeout = config.playerIdleTimeout,
            commandBlocks = config.commandBlocks,
            netherEnabled = config.netherEnabled,
            spawnMonsters = config.spawnMonsters,
            spawnAnimals = config.spawnAnimals,
            spawnNpcs = config.spawnNpcs,
            hardcore = config.hardcore,
            maxRamMb = config.maxRamMb,
            ramMode = config.ramMode,
            entityBroadcastRangePercentage = config.entityBroadcastRangePercentage,
            maxWorldSize = config.maxWorldSize,
            useNativeTransport = config.useNativeTransport,
            maxBuildHeight = config.maxBuildHeight,
            generateStructures = config.generateStructures,
            levelType = config.levelType,
            serverType = config.serverType.name,
            gameVersion = config.gameVersion,
            customJarPath = config.customJarPath
        )
    }

    private fun loadExistingFromServerDir(serverDir: File): Properties {
        return loadExisting(File(serverDir, "server.properties"))
    }

    private fun loadExisting(file: File): Properties {
        val props = Properties()
        if (file.exists()) {
            file.inputStream().use { input -> props.load(input) }
        }
        return props
    }

    private fun save(file: File, props: Properties) {
        file.parentFile?.mkdirs()
        file.outputStream().use { output ->
            props.store(output, "Managed by PocketCraft")
        }
    }
}

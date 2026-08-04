package com.pocketcraft.server.data.model

/**
 * All configurable server settings, mirrors server.properties fields.
 * Serialized to/from server.properties by [ServerConfigRepository].
 */
data class ServerConfig(
    val worldName: String = "world",
    val worldSeed: String = "",
    val maxPlayers: Int = 15,
    val port: Int = 25565,
    val difficulty: String = "normal",
    val gameMode: String = "survival",
    /** Must be false for LAN play without Mojang auth */
    val onlineMode: Boolean = false,
    val motd: String = "A PocketCraft Server",
    val pvp: Boolean = true,
    /** Keep low (6) for mobile performance */
    val viewDistance: Int = 6,
    val simulationDistance: Int = 4,
    val spawnProtection: Int = 16,
    val allowFlight: Boolean = true,
    val whiteList: Boolean = false,
    val enforceWhitelist: Boolean = false,
    val commandBlocks: Boolean = true,
    val netherEnabled: Boolean = true,
    val spawnMonsters: Boolean = true,
    val spawnAnimals: Boolean = true,
    val spawnNpcs: Boolean = true,
    val hardcore: Boolean = false,
    val maxRamMb: Int = 1024,
    val ramMode: String = "low",
    // Network settings
    val serverIp: String = "",
    val queryPort: Int = 25565,
    val rconPort: Int = 25575,
    val enableRcon: Boolean = true,
    // Player settings
    val playerIdleTimeout: Int = 0,
    val opPermissionLevel: Int = 4,
    // World settings
    val generateStructures: Boolean = true,
    val levelType: String = "default",
    val maxWorldSize: Int = 29999984,
    val maxBuildHeight: Int = 320,
    val useNativeTransport: Boolean = false,
    // Performance settings
    val entityBroadcastRangePercentage: Int = 100,
    // App settings
    val maxRamMbApp: Int = 1024,
    val soundEnabled: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val autoRestart: Boolean = false,
    val autoRestartDelaySecs: Int = 10,
    // Server customization
    val serverDescription: String = "",
    val serverPhotoUri: String? = null,
    // Join message settings
    val joinMessageEnabled: Boolean = true,
    val joinMessageText: String = "hosted on Pocketcraft",
    val joinMessageUrl: String = "https://discord.gg/7xw3Rd2vs2",
    // Advanced versioning
    val serverType: ServerType = ServerType.PAPER,
    val gameVersion: String = "",
    val customJarPath: String? = null
)

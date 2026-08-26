package com.pockethost.app.server

import java.io.File

object NukkitVersions {
    const val DEFAULT_NUKKIT_VERSION = "NukkitX 2.0 (Bedrock)"
    const val DEFAULT_BEDROCK_PORT = 19132
    const val NUKKIT_JAR_NAME = "nukkit.jar"

    fun generateDefaultNukkitYml(serverName: String, port: Int = DEFAULT_BEDROCK_PORT): String {
        return """
            # PowerNukkitX Configuration (v300)
            config-version: 300
            settings:
              language: "eng"
              force-language: false
              shutdown-message: "Server closed"
              query-plugins: true
              deprecated-verbose: true
              async-workers: auto
            network:
              batch-threshold: 256
              compression-level: 7
              async-compression: true
              upnp-forwarding: false
            player:
              save-player-data: true
              skin-change-cooldown: 30
              check-skin: false
              strict-skin-check: false
              allow-custom-skin: true
              force-skin-trusted: true
              check-skin-trusted: false
            world:
              default-format: "leveldb"
              auto-save: 6000
            server:
              server-ip: "0.0.0.0"
              server-port: $port
              bedrock-port: $port
              port: $port
            debug: 1
        """.trimIndent()
    }

    fun generateDefaultServerProperties(
        serverName: String,
        port: Int = DEFAULT_BEDROCK_PORT,
        gamemode: Int = 0,
        difficulty: Int = 1,
        maxPlayers: Int = 10
    ): String {
        return """
            # Minecraft Bedrock Server Properties (NukkitX)
            motd=$serverName
            server-port=$port
            bedrock-port=$port
            server-ip=0.0.0.0
            gamemode=$gamemode
            difficulty=$difficulty
            max-players=$maxPlayers
            online-mode=false
            white-list=false
            view-distance=8
            spawn-protection=16
            allow-flight=true
            announce-player-achievements=true
            generator-settings=
            level-name=world
            level-type=DEFAULT
            level-seed=
            enable-query=true
            enable-rcon=false
            auto-save=true
            check-skin=false
            allow-custom-skin=true
            trust-skin=true
            force-skin-trusted=true
            pocketcraft-server-type=BEDROCK
            pocketcraft-game-version=1.21.60
        """.trimIndent()
    }
}

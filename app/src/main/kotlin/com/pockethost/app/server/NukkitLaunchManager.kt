package com.pockethost.app.server

import android.content.Context
import android.util.Log
import com.pockethost.app.service.PluginManager
import com.pockethost.app.service.ServerFileManager
import com.pockethost.app.service.ServerPropertiesHelper
import java.io.File

object NukkitLaunchManager {
    private const val TAG = "NukkitLaunchManager"

    fun prepareNukkitServer(
        context: Context,
        worldName: String,
        serverName: String = worldName,
        port: Int = NukkitVersions.DEFAULT_BEDROCK_PORT,
        gamemode: Int = 0,
        difficulty: Int = 1,
        maxPlayers: Int = 10
    ): File {
        val serverDir = ServerFileManager.getServerDir(context, worldName).also { it.mkdirs() }
        val pluginsDir = File(serverDir, "plugins").also { it.mkdirs() }

        // 1. Write or update server.properties for Nukkit
        val propsFile = File(serverDir, "server.properties")
        if (!propsFile.exists()) {
            propsFile.writeText(
                NukkitVersions.generateDefaultServerProperties(
                    serverName = serverName,
                    port = port,
                    gamemode = gamemode,
                    difficulty = difficulty,
                    maxPlayers = maxPlayers
                )
            )
            Log.i(TAG, "Initialized default Nukkit server.properties for $worldName")
        } else {
            val props = ServerPropertiesHelper.readProperties(serverDir)
            props.setProperty("pocketcraft-server-type", "BEDROCK")
            props.setProperty("pocketcraft-game-version", "1.21.60")
            props.setProperty("server-port", port.toString())
            props.setProperty("bedrock-port", port.toString())
            props.setProperty("check-skin", "false")
            props.setProperty("allow-custom-skin", "true")
            props.setProperty("trust-skin", "true")
            props.setProperty("force-skin-trusted", "true")
            ServerPropertiesHelper.saveProperties(serverDir, props)
        }

        // 2. Write or update nukkit.yml and pnx.yml with skin validation disabled
        listOf("nukkit.yml", "pnx.yml").forEach { fileName ->
            val ymlFile = File(serverDir, fileName)
            ymlFile.writeText(NukkitVersions.generateDefaultNukkitYml(serverName, port))
            Log.i(TAG, "Enforced default $fileName with disabled skin checks for $worldName")
        }

        // 3. Pre-generate Floodgate/Geyser RSA key.pem
        PluginManager.preserveFloodgateKey(context, worldName)
        PluginManager.enforceBedrockBridgeLocalConfig(context, worldName)

        Log.i(TAG, "Nukkit Bedrock server $worldName prepared successfully in ${serverDir.path}")
        return serverDir
    }
}

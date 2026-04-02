package com.pocketcraft.server.service

import android.content.Context
import java.io.File

object ServerFileManager {

    /**
     * Returns the directory where a specific version's server files are stored.
     * e.g. .../files/servers/1.20.4/
     */
    fun getServerDir(context: Context, versionId: String): File {
        return File(context.filesDir, "servers/$versionId").also { it.mkdirs() }
    }

    /**
     * Returns the full path to the server JAR for a given version.
     * e.g. .../files/servers/1.20.4/paper-1.20.4.jar
     */
    fun getServerJarFile(context: Context, versionId: String): File {
        return File(getServerDir(context, versionId), "paper-$versionId.jar")
    }

    /**
     * Returns true if the server JAR has already been downloaded.
     */
    fun isServerJarReady(context: Context, versionId: String): Boolean {
        val jar = getServerJarFile(context, versionId)
        return jar.exists() && jar.length() > 1_000_000L
    }

    /**
     * Ensures eula.txt exists with eula=true in the server directory.
     */
    fun prepareEula(context: Context, versionId: String) {
        val serverDir = getServerDir(context, versionId)
        val eulaFile = File(serverDir, "eula.txt")
        try {
            eulaFile.writeText("eula=true\n")
        } catch (e: Exception) {
            android.util.Log.e("ServerFileManager", "Failed to write eula.txt", e)
        }
    }

    fun prepareServerProperties(context: Context, versionId: String) {
        val serverDir = getServerDir(context, versionId)
        val props = ServerPropertiesHelper.readProperties(serverDir)

        // Forced configuration for runtime compatibility.
        props.setProperty("server-port", "25565")
        props.setProperty("online-mode", "false")          // offline / LAN mode always
        props.setProperty("server-ip", "")                 // bind all interfaces
        // Ensure RCON is enabled for in-app console commands
        props.setProperty("enable-rcon", "true")
        props.setProperty("rcon.port", "25575")
        props.setProperty("rcon.password", "pocketcraft-internal-rcon")
        props.setProperty("broadcast-rcon-to-ops", "false")
        props.setProperty("resource-pack-prompt", "§b[PocketCraft]§r\\nThis server recommends a resource pack.\\nWould you like to download it?")

        runCatching {
            ServerPropertiesHelper.saveProperties(serverDir, props)
        }.onFailure { error ->
            android.util.Log.e("ServerFileManager", "Failed to write server.properties", error)
        }
    }



    fun prepareRuntimeArtifacts(context: Context, versionId: String) {
        val serverDir = getServerDir(context, versionId)
        File(serverDir, "logs").mkdirs()

        serverDir.walkTopDown().forEach { file ->
            if (file.isFile && file.name.endsWith(".tmp")) {
                runCatching { file.delete() }
            }
        }
    }
}

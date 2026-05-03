package com.pocketcraft.server.service

import android.content.Context
import com.pocketcraft.server.data.model.ServerType
import java.io.File

object ServerFileManager {

    /**
     * Returns the directory where a specific version's server files are stored.
     */
    fun getServerDir(context: Context, worldName: String): File {
        return File(context.filesDir, "servers/$worldName").also { it.mkdirs() }
    }

    fun getServerJarFile(context: Context, gameVersion: String, serverType: ServerType): File {
        val serverDir = File(context.filesDir, "servers/$gameVersion")
        serverDir.mkdirs()
        val jarName = "${serverType.name.lowercase()}-$gameVersion.jar"
        return File(serverDir, jarName)
    }

    /**
     * Returns true if the server JAR has already been downloaded.
     */
    fun isServerJarReady(context: Context, gameVersion: String, serverType: ServerType): Boolean {
        val jar = getServerJarFile(context, gameVersion, serverType)
        val expectedMinSize = if (serverType == ServerType.FABRIC) 50_000L else 1_000_000L
        return jar.exists() && jar.length() > expectedMinSize
    }

    /**
     * Ensures eula.txt exists with eula=true in the server directory.
     */
    fun prepareEula(context: Context, worldName: String) {
        val serverDir = getServerDir(context, worldName)
        val eulaFile = File(serverDir, "eula.txt")
        try {
            eulaFile.writeText("eula=true\n")
        } catch (e: Exception) {
            android.util.Log.e("ServerFileManager", "Failed to write eula.txt", e)
        }
    }

    fun prepareServerProperties(context: Context, worldName: String) {
        val serverDir = getServerDir(context, worldName)
        val props = ServerPropertiesHelper.readProperties(serverDir)
        val resolvedWorldName = resolveStableWorldName(serverDir, props)

        // Forced configuration for runtime compatibility.
        props.setProperty("server-port", "25565")
        props.setProperty("level-name", resolvedWorldName)
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

    private fun resolveStableWorldName(serverDir: File, props: java.util.Properties): String {
        val explicit = props.getProperty("level-name")?.trim().orEmpty()
        if (explicit.isNotBlank()) return explicit

        val fromRegistry = props.getProperty("pocketcraft-world-list")
            .orEmpty()
            .split(',')
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() && File(serverDir, it).exists() }
        if (!fromRegistry.isNullOrBlank()) return fromRegistry

        val discoveredWorld = serverDir.listFiles()
            .orEmpty()
            .asSequence()
            .filter { it.isDirectory }
            .firstOrNull { dir ->
                File(dir, "level.dat").exists() || File(dir, "region").isDirectory
            }
            ?.name
        return discoveredWorld ?: "world"
    }

    fun prepareRuntimeArtifacts(context: Context, worldName: String) {
        val serverDir = getServerDir(context, worldName)
        File(serverDir, "logs").mkdirs()

        serverDir.walkTopDown().forEach { file ->
            if (file.isFile && file.name.endsWith(".tmp")) {
                runCatching { file.delete() }
            }
        }
    }

    enum class LaunchMode {
        JAR,
        ARG_FILE
    }

    fun persistLaunchTarget(context: Context, worldName: String, mode: LaunchMode, relativePath: String) {
        val serverDir = getServerDir(context, worldName)
        val props = ServerPropertiesHelper.readProperties(serverDir)
        props.setProperty("pocketcraft-launch-mode", mode.name)
        props.setProperty("pocketcraft-launch-target", relativePath)
        runCatching {
            ServerPropertiesHelper.saveProperties(serverDir, props)
        }
    }
}

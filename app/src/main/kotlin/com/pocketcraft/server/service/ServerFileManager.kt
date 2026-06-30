package com.pocketcraft.server.service

import android.content.Context
import com.pocketcraft.server.data.model.ServerType
import com.pocketcraft.server.data.preferences.AppPreferences
import com.pocketcraft.server.server.BundledPluginInstaller
import java.io.File

object ServerFileManager {

    /**
     * Returns the directory where a specific world's server files are stored.
     */
    fun getServerDir(context: Context, worldName: String): File {
        return File(context.filesDir, "servers/worlds/$worldName").also { it.mkdirs() }
    }

    /**
     * Returns the directory without creating it. Useful for checking existence.
     */
    fun getServerDirNoCreate(context: Context, worldName: String): File {
        return File(context.filesDir, "servers/worlds/$worldName")
    }

    /**
     * Returns the directory where a specific version's server JAR is stored.
     */
    fun getServerJarDir(context: Context, gameVersion: String): File {
        return File(context.filesDir, "servers/binaries/$gameVersion").also { it.mkdirs() }
    }

    fun getServerJarFile(context: Context, gameVersion: String, serverType: ServerType): File {
        val jarDir = getServerJarDir(context, gameVersion)
        val jarName = "${serverType.name.lowercase()}-$gameVersion.jar"
        return File(jarDir, jarName)
    }

    /**
     * Returns true if the server JAR has already been downloaded.
     */
    fun isServerJarReady(context: Context, gameVersion: String, serverType: ServerType): Boolean {
        if (serverType == ServerType.MODPACK) {
            return false
        }
        val jar = getServerJarFile(context, gameVersion, serverType)
        val expectedMinSize = if (serverType == ServerType.FABRIC) 10_000L else 1_000_000L
        return jar.exists() && jar.length() > expectedMinSize
    }

    fun isModpackReady(context: Context, worldName: String, modpackId: String? = null): Boolean {
        val serverDir = getServerDirNoCreate(context, worldName)
        if (!serverDir.isDirectory) return false
        val props = ServerPropertiesHelper.readProperties(serverDir)
        if (ServerType.fromString(props.getProperty("pocketcraft-server-type")) != ServerType.MODPACK) return false
        val installedId = props.getProperty("pocketcraft-modpack-id")
            ?: props.getProperty("pocketcraft-custom-jar-path")
            ?: ""
        if (!modpackId.isNullOrBlank() && installedId != modpackId) return false
        val launchTarget = readLaunchTarget(serverDir) ?: return false
        return launchTarget.file.exists() && launchTarget.file.isFile && launchTarget.file.length() > 0L
    }

    /**
     * Checks if eula.txt exists and has eula=true.
     */
    fun isEulaAccepted(context: Context, worldName: String): Boolean {
        val serverDir = getServerDir(context, worldName)
        val eulaFile = File(serverDir, "eula.txt")
        if (!eulaFile.exists()) return false
        return runCatching {
            eulaFile.readLines().any { it.trim().equals("eula=true", ignoreCase = true) }
        }.getOrDefault(false)
    }

    /**
     * Writes eula.txt with eula=true in the server directory.
     */
    fun acceptEula(context: Context, worldName: String) {
        val serverDir = getServerDir(context, worldName)
        val eulaFile = File(serverDir, "eula.txt")
        try {
            eulaFile.writeText("eula=true\n")
        } catch (e: Exception) {
            android.util.Log.e("ServerFileManager", "Failed to write eula.txt", e)
        }
    }

    /**
     * Ensures eula.txt exists when the user has already accepted the EULA.
     */
    fun prepareEula(context: Context, worldName: String) {
        val prefs = AppPreferences(context)
        if (!prefs.eulaAccepted) return
        if (isEulaAccepted(context, worldName)) return

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
        // Preserve the user's online-mode choice; only default to false when the
        // property doesn't exist yet (new server, no prior value).
        if (!props.containsKey("online-mode")) {
            props.setProperty("online-mode", "false")
        }
        // enforce-secure-profile forces chat-signing requirements that break on some
        // mobile client builds; always keep it false.
        props.setProperty("enforce-secure-profile", "false")
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
        var explicit = props.getProperty("level-name")?.trim().orEmpty()
        // If the level-name was accidentally saved as a dimension folder, correct it
        if (explicit.endsWith("_nether")) explicit = explicit.removeSuffix("_nether")
        if (explicit.endsWith("_the_end")) explicit = explicit.removeSuffix("_the_end")
        
        // Only trust the existing level-name if its folder actually contains world data
        if (explicit.isNotBlank()) {
            val candidateDir = File(serverDir, explicit)
            val hasWorldData = File(candidateDir, "level.dat").exists() ||
                File(candidateDir, "region").isDirectory ||
                File(serverDir, "level.dat").exists() ||   // flat layout
                File(serverDir, "region").isDirectory       // flat layout
            if (hasWorldData) return explicit
            android.util.Log.w("ServerFileManager", "level-name='$explicit' but no world data found there — falling back to discovery")
        }

        val fromRegistry = props.getProperty("pocketcraft-world-list")
            .orEmpty()
            .split(',')
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() && File(serverDir, it).exists() }
        if (!fromRegistry.isNullOrBlank()) return fromRegistry

        val discoveredWorld = serverDir.listFiles()
            .orEmpty()
            .asSequence()
            .filter { it.isDirectory && !it.name.endsWith("_nether") && !it.name.endsWith("_the_end") }
            .firstOrNull { dir ->
                File(dir, "level.dat").exists() || File(dir, "region").isDirectory
            }
            ?.name
        return discoveredWorld ?: "world"
    }

    fun prepareRuntimeArtifacts(context: Context, worldName: String) {
        val serverDir = getServerDir(context, worldName)
        File(serverDir, "logs").mkdirs()
        
        val pluginsDir = File(serverDir, "plugins").also { it.mkdirs() }
        BundledPluginInstaller.installBundledPlugins(context, serverDir)

        runCatching {
            context.assets.open("default_plugins/PocketCraftCompanion.jar").use { input ->
                File(pluginsDir, "PocketCraftCompanion.jar").outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }.onFailure { 
            android.util.Log.e("ServerFileManager", "Failed to bundle PocketCraftCompanion.jar: ${it.message}")
        }

        // Paper's tuned async chunk pipeline already owns loading and send budgets.
        // This legacy movement listener requested multiple chunks on every move event,
        // competing with keepalives and causing high ping under exploration.
        File(pluginsDir, "PocketCraftChunkLoader.jar").takeIf { it.exists() }?.let { plugin ->
            if (!plugin.delete()) {
                android.util.Log.w("ServerFileManager", "Could not remove legacy ${plugin.name}")
            }
        }

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

    data class LaunchTarget(
        val mode: LaunchMode,
        val file: File
    )

    fun persistLaunchTarget(context: Context, worldName: String, mode: LaunchMode, relativePath: String) {
        val serverDir = getServerDir(context, worldName)
        val props = ServerPropertiesHelper.readProperties(serverDir)
        props.setProperty("pocketcraft-launch-mode", mode.name)
        props.setProperty("pocketcraft-launch-target", relativePath)
        runCatching {
            ServerPropertiesHelper.saveProperties(serverDir, props)
        }
    }

    fun readLaunchTarget(serverDir: File): LaunchTarget? {
        val props = ServerPropertiesHelper.readProperties(serverDir)
        val mode = runCatching {
            LaunchMode.valueOf(props.getProperty("pocketcraft-launch-mode", LaunchMode.JAR.name))
        }.getOrDefault(LaunchMode.JAR)
        val relativePath = props.getProperty("pocketcraft-launch-target")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return null
        if (relativePath.startsWith("/") || relativePath.contains("..")) return null
        return LaunchTarget(mode = mode, file = File(serverDir, relativePath))
    }
}

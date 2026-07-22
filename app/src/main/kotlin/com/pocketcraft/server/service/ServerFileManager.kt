package com.pocketcraft.server.service

import android.content.Context
import com.pocketcraft.server.data.model.ServerType
import com.pocketcraft.server.data.preferences.AppPreferences
import com.pocketcraft.server.server.BundledPluginInstaller
import java.io.File

object ServerFileManager {

    /**
     * Recursively applies 0755 permissions to a directory and all its parent directories
     * up to context.filesDir to prevent SELinux/umask file access errors on MIUI/HyperOS/OnePlus.
     */
    fun ensureDirectoryPermissions(dir: File) {
        runCatching {
            var current: File? = dir
            while (current != null) {
                android.system.Os.chmod(current.absolutePath, 0x1ED) // 0755
                if (current.name == "files" || current.name == "code_cache" || current.name == "cache") break
                current = current.parentFile
            }
            if (dir.exists()) {
                dir.walkTopDown().forEach { file ->
                    android.system.Os.chmod(file.absolutePath, 0x1ED) // 0755
                }
            }
        }
    }

    /**
     * Returns the directory where a specific world's server files are stored.
     */
    fun getServerDir(context: Context, worldName: String): File {
        return File(context.filesDir, "servers/worlds/$worldName").also {
            it.mkdirs()
            ensureDirectoryPermissions(it)
        }
    }

    /**
     * Returns the directory without creating it. Useful for checking existence.
     */
    fun getServerDirNoCreate(context: Context, worldName: String): File {
        return File(context.filesDir, "servers/worlds/$worldName").also {
            if (it.exists()) ensureDirectoryPermissions(it)
        }
    }

    /**
     * Returns the directory where a specific version's server JAR is stored.
     */
    fun getServerJarDir(context: Context, gameVersion: String): File {
        return File(context.filesDir, "servers/binaries/$gameVersion").also {
            it.mkdirs()
            ensureDirectoryPermissions(it)
        }
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
        
        if (explicit.isNotBlank()) {
            val candidateDir = File(serverDir, explicit)
            val candidateHasData = File(candidateDir, "level.dat").exists() ||
                File(candidateDir, "region").isDirectory
            val flatHasData = File(serverDir, "level.dat").exists() ||
                File(serverDir, "region").isDirectory

            // Flat layout data exists — only migrate if the nested dir doesn't already have good data.
            // Running migration when nested already has data would overwrite valid restored content.
            if (flatHasData) {
                val nestedAlreadyHasData = File(candidateDir, "level.dat").exists() ||
                    File(candidateDir, "region").isDirectory
                if (!nestedAlreadyHasData) {
                    android.util.Log.w("ServerFileManager", "Migrating flat world layout to nested for '$explicit'")
                    candidateDir.mkdirs()
                    migrateFlatLayoutToNested(serverDir, candidateDir)
                } else {
                    // Both flat and nested have data — nested is authoritative; clean up stale flat files
                    android.util.Log.w("ServerFileManager", "Stale flat files found alongside good nested world for '$explicit' — cleaning up")
                    cleanUpStaleRootLevelWorldFiles(serverDir, candidateDir)
                }
                return explicit
            }

            if (candidateHasData) return explicit
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

    /**
     * Removes stale world files from the server root that have already been migrated
     * into the proper nested world directory. Only removes known world-data files;
     * never touches dimension folders, plugins, config, etc.
     */
    private fun cleanUpStaleRootLevelWorldFiles(serverDir: File, nestedDir: File) {
        val staleWorldFiles = setOf(
            "level.dat", "level.dat_old", "level.dat_mcr",
            "uid.dat", "session.lock", "icon.png"
        )
        val staleWorldDirs = setOf("region", "entities", "poi", "data", "playerdata",
            "stats", "advancements", "datapacks")
        serverDir.listFiles()?.forEach { file ->
            val nameLower = file.name.lowercase()
            if (file.isFile && nameLower in staleWorldFiles) {
                android.util.Log.d("ServerFileManager", "Removing stale root file: ${file.name}")
                file.delete()
            } else if (file.isDirectory && nameLower in staleWorldDirs &&
                       file.absolutePath != nestedDir.absolutePath) {
                android.util.Log.d("ServerFileManager", "Removing stale root dir: ${file.name}")
                file.deleteRecursively()
            }
        }
    }

    private fun migrateFlatLayoutToNested(serverDir: File, nestedDir: File) {
        val skip = setOf("server.properties", "eula.txt", "usercache.json",
            "ops.json", "whitelist.json", "banned-players.json", "banned-ips.json")
        val systemDirs = setOf("plugins", "logs", "cache", "jre", "jre-21", "jre-runtime",
            "config", "libraries", "binaries", "backups", "crash-reports", "bundler", "versions")
        serverDir.listFiles()?.forEach { file ->
            val name = file.name
            if (name.startsWith("pocketcraft-") || name in skip || name in systemDirs || name == nestedDir.name) return@forEach
            val target = File(nestedDir, name)
            if (file.isDirectory) {
                file.copyRecursively(target, overwrite = true)
                file.deleteRecursively()
            } else {
                file.copyTo(target, overwrite = true)
                file.delete()
            }
        }
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

package com.pockethost.app.service

import android.content.Context
import com.pockethost.app.data.model.ServerType
import com.pockethost.app.data.preferences.AppPreferences
import com.pockethost.app.server.BundledPluginInstaller
import java.io.File

object ServerFileManager {

    /**
     * Recursively applies 0755 permissions to a directory and all its parent directories
     * up to context.filesDir to prevent SELinux/umask file access errors on MIUI/HyperOS/OnePlus.
     */
    fun ensureDirectoryPermissions(dir: File) {
        ensureDirectoryChainPermissions(dir)
        runCatching {
            if (dir.exists()) {
                dir.walkTopDown().forEach { file ->
                    android.system.Os.chmod(file.absolutePath, 0x1ED) // 0755
                }
            }
        }
    }

    /**
     * Applies 0755 to [dir] and its parents only. The directory getters below are called from
     * the UI thread on nearly every screen, and walking a whole world folder (thousands of
     * region files) there froze the app. The full recursive pass still runs before each
     * server launch, off the main thread.
     */
    private fun ensureDirectoryChainPermissions(dir: File) {
        runCatching {
            var current: File? = dir
            while (current != null) {
                android.system.Os.chmod(current.absolutePath, 0x1ED) // 0755
                if (current.name == "files" || current.name == "code_cache" || current.name == "cache") break
                current = current.parentFile
            }
        }
    }

    /**
     * Returns the directory where a specific world's server files are stored.
     */
    fun getServerDir(context: Context, worldName: String): File {
        return File(context.filesDir, "servers/worlds/$worldName").also {
            it.mkdirs()
            ensureDirectoryChainPermissions(it)
        }
    }

    /**
     * Returns the directory without creating it. Useful for checking existence.
     */
    fun getServerDirNoCreate(context: Context, worldName: String): File {
        return File(context.filesDir, "servers/worlds/$worldName").also {
            if (it.exists()) ensureDirectoryChainPermissions(it)
        }
    }

    /**
     * Returns the directory where a specific version's server JAR is stored.
     */
    fun getServerJarDir(context: Context, gameVersion: String): File {
        return File(context.filesDir, "servers/binaries/$gameVersion").also {
            it.mkdirs()
            ensureDirectoryChainPermissions(it)
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

    fun healModpackFilesIfNeeded(serverDir: File) {
        if (!serverDir.isDirectory) return
        val props = ServerPropertiesHelper.readProperties(serverDir)
        val launchTargetRel = props.getProperty("pocketcraft-launch-target")?.trim().orEmpty()
        val targetNames = if (launchTargetRel.isNotBlank()) {
            listOf(launchTargetRel, "fabric-server-launch.jar", "quilt-server-launch.jar")
        } else {
            listOf("fabric-server-launch.jar", "quilt-server-launch.jar")
        }

        val targetJar = targetNames.firstOrNull { File(serverDir, it).let { f -> f.exists() && f.isFile && f.length() > 0L } }
        if (targetJar == null) {
            // Check subdirectories (like "world") for misplaced launch target and mods
            serverDir.listFiles()?.filter { it.isDirectory }?.forEach { subDir ->
                for (name in targetNames) {
                    val misplacedJar = File(subDir, name)
                    if (misplacedJar.exists() && misplacedJar.isFile && misplacedJar.length() > 0L) {
                        val restoredJar = File(serverDir, name)
                        android.util.Log.i("ServerFileManager", "Healing misplaced modpack launch jar from ${misplacedJar.absolutePath} to ${restoredJar.absolutePath}")
                        misplacedJar.copyTo(restoredJar, overwrite = true)
                        misplacedJar.delete()
                        persistLaunchTarget(serverDir, LaunchMode.JAR, name)
                        break
                    }
                }
                val misplacedMods = File(subDir, "mods")
                val rootMods = File(serverDir, "mods")
                if (misplacedMods.isDirectory && misplacedMods.listFiles()?.isNotEmpty() == true) {
                    if (!rootMods.exists()) rootMods.mkdirs()
                    android.util.Log.i("ServerFileManager", "Healing misplaced mods directory from ${misplacedMods.absolutePath} to ${rootMods.absolutePath}")
                    misplacedMods.listFiles()?.forEach { modFile ->
                        val destFile = File(rootMods, modFile.name)
                        if (!destFile.exists() || destFile.length() == 0L) {
                            modFile.copyTo(destFile, overwrite = true)
                        }
                    }
                }
            }
        }

        val hasLaunchJar = targetJar != null || File(serverDir, "fabric-server-launch.jar").exists() || File(serverDir, "quilt-server-launch.jar").exists()
        if (hasLaunchJar) {
            val curType = props.getProperty("pocketcraft-server-type").orEmpty()
            if (!curType.equals(ServerType.MODPACK.name, ignoreCase = true)) {
                props.setProperty("pocketcraft-server-type", ServerType.MODPACK.name)
                ServerPropertiesHelper.saveProperties(serverDir, props)
            }
        }
    }

    fun isModpackReady(context: Context, worldName: String, modpackId: String? = null): Boolean {
        val serverDir = getServerDirNoCreate(context, worldName)
        if (!serverDir.isDirectory) return false
        healModpackFilesIfNeeded(serverDir)

        val props = ServerPropertiesHelper.readProperties(serverDir)
        val rawServerType = props.getProperty("pocketcraft-server-type")
        val effectiveType = ServerType.fromString(rawServerType)
        val launchTarget = readLaunchTarget(serverDir)
        val hasValidLaunchTarget = launchTarget != null && launchTarget.file.exists() && launchTarget.file.isFile && launchTarget.file.length() > 0L

        if (effectiveType != ServerType.MODPACK && !hasValidLaunchTarget) return false

        val installedId = (props.getProperty("pocketcraft-modpack-id")
            ?: props.getProperty("pocketcraft-custom-jar-path")
            ?: "").trim()
        val requestedId = modpackId?.trim().orEmpty()
        if (requestedId.isNotBlank() && installedId.isNotBlank() && !installedId.equals(requestedId, ignoreCase = true)) {
            return false
        }
        return hasValidLaunchTarget
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
     * Ensures eula.txt exists with eula=true in the server directory.
     * When launching a server in PocketHost, the user has agreed to Minecraft's EULA;
     * unconditionally ensure the file is present with eula=true so the server does not abort.
     */
    fun prepareEula(context: Context, worldName: String) {
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
        props.setProperty("rcon.password", com.pockethost.app.network.RconSecret.current())
        props.setProperty("broadcast-rcon-to-ops", "false")
        props.setProperty("resource-pack-prompt", "§b[PocketHost]§r\\nThis server recommends a resource pack.\\nWould you like to download it?")

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

    fun migrateFlatLayoutToNested(serverDir: File, nestedDir: File) {
        val skip = setOf("server.properties", "eula.txt", "usercache.json",
            "ops.json", "whitelist.json", "banned-players.json", "banned-ips.json")
        // Anything not listed here is treated as stray world data and moved into the nested world
        // directory. A modpack's mod loader files live beside the world, so they have to be named
        // explicitly — moving mods/ or the persisted launch jar into world/ leaves the pack unable
        // to start, with no obvious way back.
        val systemDirs = setOf("plugins", "logs", "cache", "jre", "jre-21", "jre-runtime", "jre17", "jre21", "jre25",
            "config", "libraries", "binaries", "backups", "crash-reports", "bundler", "versions",
            "mods", "defaultconfigs", "kubejs", "scripts", "packmenu", "patchouli_books", "schematics",
            "shaderpacks", "resourcepacks", "world_backups", ".fabric", ".mixin.out", "run")
        // The modpack launch target may sit at the server root (fabric-server-launch.jar and
        // friends); moving it would break readLaunchTarget on the next start.
        val launchTargetName = readLaunchTarget(serverDir)?.file?.relativeTo(serverDir)?.path
            ?.substringBefore('/')
            ?.takeIf { it.isNotBlank() }

        serverDir.listFiles()?.forEach { file ->
            val name = file.name
            if (name.startsWith("pocketcraft-") || name in skip || name in systemDirs ||
                name == nestedDir.name || name == launchTargetName ||
                name.endsWith("-launch.jar", ignoreCase = true)
            ) return@forEach
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
        persistLaunchTarget(serverDir, mode, relativePath)
    }

    fun persistLaunchTarget(serverDir: File, mode: LaunchMode, relativePath: String) {
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
        if (relativePath != null && !relativePath.startsWith("/") && !relativePath.contains("..")) {
            val targetFile = File(serverDir, relativePath)
            if (targetFile.exists() && targetFile.isFile && targetFile.length() > 0L) {
                return LaunchTarget(mode = mode, file = targetFile)
            }
        }

        // Fallback: check for standard launcher files in server root
        val fabricJar = File(serverDir, "fabric-server-launch.jar")
        if (fabricJar.exists() && fabricJar.isFile && fabricJar.length() > 0L) {
            persistLaunchTarget(serverDir, LaunchMode.JAR, "fabric-server-launch.jar")
            return LaunchTarget(mode = LaunchMode.JAR, file = fabricJar)
        }
        val quiltJar = File(serverDir, "quilt-server-launch.jar")
        if (quiltJar.exists() && quiltJar.isFile && quiltJar.length() > 0L) {
            persistLaunchTarget(serverDir, LaunchMode.JAR, "quilt-server-launch.jar")
            return LaunchTarget(mode = LaunchMode.JAR, file = quiltJar)
        }

        return null
    }

    fun isLikelyModpackRuntimeId(value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return false
        if (trimmed.first().isLetter()) return true
        val isMcVersion = trimmed.matches(Regex("""\d+(?:\.\d+)*(?:[-+](?:pre|rc|alpha|beta|snapshot|[a-z0-9_.-]+))?""", RegexOption.IGNORE_CASE))
        return !isMcVersion
    }

    fun detectModpackMinecraftVersion(serverDir: File): String? {
        val props = ServerPropertiesHelper.readProperties(serverDir, persistDefaults = false)
        val mcVer = props.getProperty("pocketcraft-modpack-mc-version")?.trim()
        if (!mcVer.isNullOrBlank() && !isLikelyModpackRuntimeId(mcVer)) {
            return mcVer
        }
        val gameVer = props.getProperty("pocketcraft-game-version")?.trim()
        if (!gameVer.isNullOrBlank() && !isLikelyModpackRuntimeId(gameVer)) {
            return gameVer
        }

        // Check server.jar in world directory (Mojang bundler jar)
        val serverJar = File(serverDir, "server.jar")
        if (serverJar.exists() && serverJar.isFile) {
            runCatching {
                java.util.zip.ZipFile(serverJar).use { zip ->
                    zip.getEntry("META-INF/versions.list")?.let { entry ->
                        zip.getInputStream(entry).bufferedReader().useLines { lines ->
                            for (line in lines) {
                                val parts = line.split('\t')
                                if (parts.size >= 2) {
                                    val v = parts[1].trim()
                                    if (!v.isNullOrBlank() && !isLikelyModpackRuntimeId(v)) return v
                                }
                            }
                        }
                    }
                    zip.getEntry("META-INF/version.json")?.let { entry ->
                        val text = zip.getInputStream(entry).bufferedReader().readText()
                        val id = org.json.JSONObject(text).optString("id")?.trim()
                        if (!id.isNullOrBlank() && !isLikelyModpackRuntimeId(id)) return id
                    }
                }
            }
        }

        // Check fabric-server-launch.jar or quilt-server-launch.jar
        val launcherJars = listOf(
            File(serverDir, "fabric-server-launch.jar"),
            File(serverDir, "quilt-server-launch.jar"),
            readLaunchTarget(serverDir)?.file
        ).filterNotNull().filter { it.exists() && it.isFile }

        for (launchJar in launcherJars) {
            runCatching {
                java.util.zip.ZipFile(launchJar).use { zip ->
                    zip.getEntry("install.properties")?.let { entry ->
                        val p = java.util.Properties()
                        zip.getInputStream(entry).use { p.load(it) }
                        val gv = p.getProperty("game-version")?.trim()
                        if (!gv.isNullOrBlank() && !isLikelyModpackRuntimeId(gv)) return gv
                    }
                    zip.getEntry("fabric-server-launcher.properties")?.let { entry ->
                        val p = java.util.Properties()
                        zip.getInputStream(entry).use { p.load(it) }
                        val sj = p.getProperty("serverJar")?.let { j ->
                            Regex("""\d+\.\d+(?:\.\d+)?""").find(j)?.value
                        }
                        if (!sj.isNullOrBlank()) return sj
                    }
                }
            }
        }

        // Check fabric-server-launcher.properties on disk
        val fabricProps = File(serverDir, "fabric-server-launcher.properties")
        if (fabricProps.exists() && fabricProps.isFile) {
            runCatching {
                val p = java.util.Properties()
                ServerPropertiesHelper.loadInto(fabricProps, p)
                val ver = p.getProperty("serverJar")?.let { jarName ->
                    Regex("""\d+\.\d+(?:\.\d+)?""").find(jarName)?.value
                }
                if (!ver.isNullOrBlank()) return ver
            }
        }

        // Check mod jars in mods/ for minecraft dependency declared in fabric.mod.json
        val modsDir = File(serverDir, "mods")
        if (modsDir.isDirectory) {
            runCatching {
                modsDir.listFiles()?.filter { it.extension == "jar" }?.take(15)?.forEach { modJar ->
                    runCatching {
                        java.util.zip.ZipFile(modJar).use { zip ->
                            zip.getEntry("fabric.mod.json")?.let { entry ->
                                val text = zip.getInputStream(entry).bufferedReader().readText()
                                val json = org.json.JSONObject(text)
                                val depends = json.optJSONObject("depends")
                                val mcDep = depends?.optString("minecraft")?.trim()
                                if (!mcDep.isNullOrBlank()) {
                                    val extracted = Regex("""\d+\.\d+(?:\.\d+)?""").find(mcDep)?.value
                                    if (!extracted.isNullOrBlank()) return extracted
                                }
                            }
                        }
                    }
                }
            }
        }

        return null
    }
}

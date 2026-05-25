package com.pocketcraft.server.server

import android.content.Context
import android.os.Build
import com.pocketcraft.server.NativeLauncher
import com.pocketcraft.server.data.repository.ServerConfigRepository
import com.pocketcraft.server.service.PluginManager
import com.pocketcraft.server.service.PlayerDataManager
import com.pocketcraft.server.service.ServerFileManager
import com.pocketcraft.server.service.ServerPropertiesHelper
import com.pocketcraft.server.server.ServerPropertiesWriter
import com.pocketcraft.server.setup.JreExtractor
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import android.app.ActivityManager
import com.pocketcraft.server.data.preferences.AppPreferences
import com.pocketcraft.server.data.preferences.AppPreferencesStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.Properties

class ServerLauncher(private val context: Context) {

    companion object {
        @Volatile
        private var activeExternalProcess: Process? = null

        fun hasActiveExternalProcess(): Boolean = activeExternalProcess?.isAlive == true

        fun sendCommand(command: String) {
            activeExternalProcess?.let { process ->
                runCatching {
                    val os = process.outputStream
                    os.write((command + "\n").toByteArray())
                    os.flush()
                }
            }
        }

        fun requestForceStop() {
            activeExternalProcess?.let { process ->
                runCatching {
                    process.destroy()
                    if (process.isAlive) {
                        process.destroyForcibly()
                    }
                }
            }
            activeExternalProcess = null
        }
    }

    fun startServer(
        worldName: String,
        versionId: String,
        jarPath: String,
        runtime: JreExtractor.RuntimeSpec,
        onOutput : (String) -> Unit,
        onError  : (String) -> Unit,
        onStopped: (Int) -> Unit
    ) {
        val jarFile = File(jarPath)
        
        // Pre-launch guard: abort immediately if JAR is missing or is a directory (EISDIR prevention)
        if (!jarFile.exists() || jarFile.isDirectory) {
            throw IllegalStateException("JAR not found: ${jarFile.absolutePath}")
        }


        ServerFileManager.prepareEula(context, worldName)
        ServerFileManager.prepareServerProperties(context, worldName)
        ServerFileManager.prepareRuntimeArtifacts(context, worldName)
        PluginManager.removeIncompatiblePlugins(context, worldName)
        
        val serverDirFileLocal = ServerFileManager.getServerDir(context, worldName)
        val props = ServerPropertiesHelper.readProperties(serverDirFileLocal)
        val serverTypeStr = props.getProperty("pocketcraft-server-type", "PAPER")
        val serverType = com.pocketcraft.server.data.model.ServerType.fromString(serverTypeStr)
        
        com.pocketcraft.server.service.DimensionMigrator.syncDimensionsForServerType(context, worldName, serverType)
        PluginManager.enforceBedrockBridgeLocalConfig(context, worldName)
        PlayerDataManager.warnIfFloodgateUsernamePrefixChanged(serverDirFileLocal)

        val jrePath   = JreExtractor.getJreDir(context, runtime).absolutePath
        chmodJreRuntime(context, runtime)

        val serverDirFile = ServerFileManager.getServerDir(context, worldName)
        val serverDir = serverDirFile.absolutePath
        val tmpDir    = File(context.filesDir, "runtime-tmp").also { it.mkdirs() }.absolutePath
        val totalRam = getTotalRamMb(context)
        applyRelayReadyRuntimeProfile(serverDirFile, onOutput)
        applyRelayReadyPaperGlobalConfig(serverDirFile, onOutput)
        applyRelayReadySpigotConfig(serverDirFile, onOutput)
        applyRelayReadyPaperWorldDefaults(serverDirFile, onOutput)

        // Dynamic JVM heap allocation based on per-world UI settings in server.properties
        val worldProps = ServerPropertiesHelper.readProperties(serverDirFile)
        val ramModeFromProps = worldProps.getProperty("pocketcraft-ram-mode", "low")
        val maxRamMbFromProps = worldProps.getProperty("pocketcraft-max-ram-mb", "1024").toIntOrNull() ?: 1024
        
        val availRam = com.pocketcraft.server.util.RamUtils.getAvailableRamMb(context)
        val maxAllowedRam = (totalRam * 0.90).toInt().coerceAtLeast(1024)
        
        val maxRamMb = when (ramModeFromProps) {
            "low" -> 512
            "full" -> maxAllowedRam
            "manual" -> maxRamMbFromProps.coerceIn(512, maxAllowedRam)
            else -> 1024
        }
        val minRamMb = when (ramModeFromProps) {
            "low" -> 256
            "full" -> maxAllowedRam
            "manual" -> maxRamMbFromProps.coerceIn(512, maxAllowedRam)
            else -> 512
        }

        onOutput("[PocketCraft] JVM memory: mode=$ramModeFromProps, heap=${minRamMb}MB..${maxRamMb}MB, available=${availRam}MB, total=${totalRam}MB")

        val javaBin = JreExtractor.getJavaBinary(context, runtime)
        val libjli = File(jrePath, "lib/libjli.so")
        val libjvm = File(jrePath, "lib/server/libjvm.so")
        if (!javaBin.exists()) {
            onError("java binary not found — JRE may not be extracted correctly"); return
        }
        if (!libjli.exists()) {
            onError("libjli.so not found — JRE may not be extracted correctly"); return
        }
        if (!libjvm.exists()) {
            onError("libjvm.so not found — JRE may not be extracted correctly"); return
        }

        val shimDir = File(context.filesDir, "lib-shims").also { it.mkdirs() }
        val libs = if (Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()) "/system/lib64" else "/system/lib"

        runCatching {
            if (extractAndPatchJnaLibrary(jarPath, serverDirFile, shimDir)) {
                onOutput("[PocketCraft] Patched JNA native dispatch library for Android")
            }
        }.onFailure { e ->
            onOutput("[PocketCraft] JNA patching skipped: ${e.message}")
        }

        onOutput("[PocketCraft] Starting world '$worldName' (version $versionId)...")
        onOutput("[PocketCraft] JRE: $jrePath")
        onOutput("[PocketCraft] JAR: $jarPath")
        
        val tmpShimDir = File(tmpDir)
        runCatching {
            listOf(
                "libc.so.6" to "$libs/libc.so",
                "libdl.so.2" to "$libs/libdl.so",
                "libm.so.6" to "$libs/libm.so",
                "librt.so.1" to "$libs/libc.so",
                "libpthread.so.0" to "$libs/libc.so",
                "libutil.so.1" to "$libs/libc.so"
            ).forEach { (shim, target) ->
                val shimFile = File(shimDir, shim)
                if (!shimFile.exists()) {
                    try {
                        android.system.Os.symlink(target, shimFile.absolutePath)
                        onOutput("[PocketCraft] Created shim: $shim -> $target")
                    } catch (e: Exception) {
                        onOutput("[PocketCraft] Warning: Failed to create shim $shim: ${e.message}")
                    }
                }
                val tmpShimFile = File(tmpShimDir, shim)
                if (!tmpShimFile.exists()) {
                    try {
                        android.system.Os.symlink(target, tmpShimFile.absolutePath)
                    } catch (_: Exception) {}
                }
            }
        }.onFailure { e ->
            onOutput("[PocketCraft] Warning: Shim creation pool failed: ${e.message}")
        }

        Thread {
            var result = -1
            try {
                result = runCatching {
                    onOutput("[PocketCraft] Launching in-process JVM on Android ${Build.VERSION.RELEASE}.")
                    NativeLauncher.launchJVM(
                        jrePath = jrePath,
                        jarPath = jarPath,
                        serverDir = serverDir,
                        tmpDir = tmpDir,
                        nativeLibDir = context.applicationInfo.nativeLibraryDir,
                        shimDir = shimDir.absolutePath,
                        minRamMb = minRamMb,
                        maxRamMb = maxRamMb,
                        serverType = serverType.name,
                        port = resolveServerPort(worldName)
                    )
                }.getOrElse {
                    onOutput("[PocketCraft] Failed to launch in-process JVM: ${it.message}")
                    -1
                }
                if (result != 0) {
                    reportHotspotCrash(serverDir, onError)
                    onError("[PocketCraft] JVM exited with code $result")
                }
            } catch (e: Exception) {
                onError("[PocketCraft] ${e.message}")
            } finally {
                onStopped(result)
            }
        }.apply { name = "mc-server-thread"; isDaemon = false }.start()
    }

    private fun launchExternalJvm(
        javaBin: File,
        jrePath: String,
        jarPath: String,
        serverDir: String,
        tmpDir: String,
        shimDir: File,
        minRamMb: Int,
        maxRamMb: Int,
        worldName: String,
        onOutput: (String) -> Unit,
        onError: (String) -> Unit
    ): Int {
        val errorFilePattern = File(serverDir, "hs_err_pid%p.log").absolutePath
        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        val wrapperBinSource = File(nativeLibDir, "libserverwrap.so")
        val wrapperBin = File(context.codeCacheDir, "serverwrap")
        if (wrapperBinSource.exists() && !wrapperBin.exists()) {
            wrapperBinSource.copyTo(wrapperBin)
        }
        if (wrapperBin.exists() && !wrapperBin.canExecute()) {
            wrapperBin.setExecutable(true, false)
            android.util.Log.d("ServerLauncher", "Set serverwrap executable: ${wrapperBin.absolutePath}")
        }
        val archLibDir = detectRuntimeLibDir(jrePath)
        val jvmDir = File(archLibDir, "server").takeIf { it.isDirectory } ?: File(jrePath, "lib/server")

        val ldLibraryPath = buildString {
            append("${shimDir.absolutePath}:")
            append("${File(archLibDir, "jli").absolutePath}:")
            append("${archLibDir.absolutePath}:")
            append("${jvmDir.absolutePath}:")
            append("/system/lib64:")
            append("/vendor/lib64")
            append(":")
            append("/vendor/lib64/hw:")
            append(nativeLibDir)
        }
        val javaLibraryPath = buildString {
            append(ldLibraryPath)
            append(":")
            append(nativeLibDir)
        }

        val jnaBootPath = "${shimDir.absolutePath}:$nativeLibDir"
        val jnaLibraryPath = jnaBootPath
 
        val vmArgs = mutableListOf(
            "-Xmx${maxRamMb}m",
            "-Xms${minRamMb}m",
            "-Djava.home=$jrePath",
            "-Djava.io.tmpdir=$tmpDir",
            "-Djna.tmpdir=$tmpDir",
            "-Djansi.tmpdir=$tmpDir",
            "-Dio.netty.native.workdir=$tmpDir",
            "-Djna.boot.library.path=$jnaBootPath",
            "-Djna.library.path=$jnaLibraryPath",
            "-Djna.nounpack=true",
            "-Duser.home=$serverDir",
            "-Duser.language=${System.getProperty("user.language").orEmpty()}",
            "-Duser.timezone=${java.util.TimeZone.getDefault().id}",
            "-Dos.name=Linux",
            "-Dos.version=Android-${Build.VERSION.RELEASE}",
            "-Djava.net.preferIPv4Stack=true",
            "-Djava.net.preferIPv6Addresses=false",
            "-Dio.netty.eventLoopThreads=4",
            "-Dfile.encoding=UTF-8",
            "-Dusing.aikars.flags=https://mcflags.emc.gs",
            "-Dpaper.playerconnection.keepalive=90",
            "-Dorg.jline.terminal.jna=false",
            "-Dorg.jline.terminal.jni=false",
            "-Dorg.jline.terminal.dumb=true",
            "-Djava.awt.headless=true",
            "-Djava.library.path=$javaLibraryPath",
            "-DPaper.IgnoreJavaVersion=true",
            "-Dsun.zip.disableMemoryMapping=true",
            "-Djdk.attach.allowAttachSelf=true",
            "-Djna.nosys=false",
            "-Xshare:off",
            "-XX:+UnlockExperimentalVMOptions",
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:+AlwaysPreTouch",
            "-XX:+UseStringDeduplication",
            "-XX:+UseG1GC",
            "-XX:+ParallelRefProcEnabled",
            "-XX:MaxGCPauseMillis=200",
            "-XX:+DisableExplicitGC",
            "-XX:G1NewSizePercent=30",
            "-XX:G1MaxNewSizePercent=40",
            "-XX:G1HeapRegionSize=8m",
            "-XX:G1ReservePercent=20",
            "-XX:G1HeapWastePercent=5",
            "-XX:G1MixedGCCountTarget=4",
            "-XX:InitiatingHeapOccupancyPercent=15",
            "-XX:G1MixedGCLiveThresholdPercent=90",
            "-XX:G1RSetUpdatingPauseTimePercent=5",
            "-XX:SurvivorRatio=32",
            "-XX:MaxTenuringThreshold=1",
            "-XX:+PerfDisableSharedMem",
            "-XX:-UsePerfData",
            "-XX:-UseContainerSupport",
            "-XX:ErrorFile=$errorFilePattern",
            "-Dio.netty.allocator.maxOrder=9",
            "-Dio.netty.recycler.maxCapacity=0",
            "-Dio.netty.recycler.maxCapacityPerThread=0",
            "-Dio.netty.recycler.linkCapacity=1024",
            "-Dio.netty.allocator.type=unpooled",
            "-Djdk.lang.Process.launchMechanism=FORK",
            "-jar",
            jarPath,
            "nogui",
            "--port",
            resolveServerPort(worldName).toString()
        )

        // Use Os.chmod (real syscall) instead of File.setExecutable which silently fails under SELinux.
        // 0x1ED = octal 0755 = rwxr-xr-x
        runCatching { android.system.Os.chmod(javaBin.absolutePath, 0x1ED) }
            .onFailure { android.util.Log.w("ServerLauncher", "chmod java failed: ${it.message}") }
        // Also ensure the whole bin/ directory has correct execute bits.
        javaBin.parentFile?.walkTopDown()?.filter { it.isFile }?.forEach { f ->
            runCatching { android.system.Os.chmod(f.absolutePath, 0x1ED) }
        }

        val launcherName = if (wrapperBin.exists()) "serverwrap" else "java"
        onOutput("[PocketCraft] Launching dedicated Java process on Android ${Build.VERSION.RELEASE} via $launcherName.")

        // On Android 10+ some vendors block direct execve from code_cache via SELinux.
        // Routing through /system/bin/sh bypasses this: the shell runs in a trusted domain
        // that IS permitted to exec app-owned binaries.
        val rawCommand = buildList<String> {
            if (wrapperBin.exists()) add(wrapperBin.absolutePath)
            add(javaBin.absolutePath)
            addAll(vmArgs)
        }
        // Build a shell-quoted command string so we can pass it to sh -c.
        val shellCmd = rawCommand.joinToString(" ") { arg ->
            "'" + arg.replace("'", "'\\''" ) + "'"
        }
        val command = listOf("/system/bin/sh", "-c", shellCmd)
 
        val process = ProcessBuilder(command)
            .directory(File(serverDir))
            .redirectErrorStream(true)
            .apply {
                environment()["POJAV_NATIVEDIR"] = nativeLibDir
                environment()["JAVA_HOME"] = jrePath
                environment()["HOME"] = serverDir
                environment()["TMPDIR"] = tmpDir
                environment()["LD_LIBRARY_PATH"] = "$jrePath/lib/server:$jrePath/lib:$jrePath/lib/jli:$ldLibraryPath"
                environment()["PATH"] = "$jrePath/bin:/system/bin:/system/xbin:${javaBin.parent}:${System.getenv("PATH").orEmpty()}"
                environment()["BIONIC_DISABLE_PTR_TAGGING"] = "1"
            }
            .start()

        activeExternalProcess = process
        val shutdownHook = Thread {
            runCatching {
                if (process.isAlive) {
                    process.destroyForcibly()
                }
            }
        }
        Runtime.getRuntime().addShutdownHook(shutdownHook)

        Thread {
            streamLines(process.inputStream, onOutput, onError)
        }.apply {
            name = "mc-server-output"
            isDaemon = true
        }.start()

        val exitCode = process.waitFor()
        runCatching { Runtime.getRuntime().removeShutdownHook(shutdownHook) }
        if (activeExternalProcess == process) {
            activeExternalProcess = null
        }
        if (exitCode != 0) {
            reportHotspotCrash(serverDir, onError)
        }
        return exitCode
    }

    private fun detectRuntimeLibDir(jrePath: String): File {
        val candidates = listOf(
            "lib/aarch64",
            "lib/arm64",
            "lib/amd64",
            "lib/i386",
            "lib"
        )
        return candidates
            .asSequence()
            .map { File(jrePath, it) }
            .firstOrNull { it.isDirectory }
            ?: File(jrePath, "lib")
    }

    private fun readMaxRamMb(serverDir: File): Int {
        val raw = ServerPropertiesHelper.readProperties(serverDir)
            .getProperty("pocketcraft-max-ram-mb", "1024")
            .toIntOrNull()
            ?: 1024
        return raw.coerceIn(512, 4096)
    }

    private fun applyRelayReadyRuntimeProfile(
        serverDir: File,
        onOutput: (String) -> Unit
    ) {
        val props = ServerPropertiesHelper.readProperties(serverDir)

        val flightModeEnabled = runBlocking { AppPreferencesStore.isFlightModeEnabledFlow(context).first() }
        
        val currentView = props.getProperty("view-distance", ServerPropertiesHelper.DEFAULT_VIEW_DISTANCE.toString())
            .toIntOrNull()
            ?: ServerPropertiesHelper.DEFAULT_VIEW_DISTANCE
        val currentSimulation = props.getProperty(
            "simulation-distance",
            ServerPropertiesHelper.DEFAULT_SIMULATION_DISTANCE.toString()
        ).toIntOrNull() ?: ServerPropertiesHelper.DEFAULT_SIMULATION_DISTANCE
        val currentCompression = props.getProperty(
            "network-compression-threshold",
            ServerPropertiesHelper.RELAY_READY_COMPRESSION_THRESHOLD.toString()
        ).toIntOrNull()
        val currentEntityBroadcast = props.getProperty(
            "entity-broadcast-range-percentage",
            ServerPropertiesHelper.RELAY_READY_ENTITY_BROADCAST_PERCENT.toString()
        ).toIntOrNull()
        val currentAllowFlight = props.getProperty("allow-flight", "false").toBoolean()

        // Preserve the user-selected render distance across restarts.
        // The relay tuning below keeps the lower bound sane, but should not
        // force the slider back to the app default.
        val maxPowerEnabled = AppPreferences(context).isMaxPowerMode
        val maxView = if (maxPowerEnabled) 32 else 16
        val maxSimulation = if (maxPowerEnabled) 16 else 10
        var tunedView = currentView.coerceIn(3, maxView)
        var tunedSimulation = currentSimulation.coerceIn(3, maxSimulation)
        var tunedAllowFlight = currentAllowFlight


        if (flightModeEnabled && !currentAllowFlight) {
            tunedAllowFlight = true
            onOutput("[PocketCraft] Flight Mode active: enabling allow-flight.")
        }

        val tunedCompression = -1
        val tunedEntityBroadcast = when {
            currentEntityBroadcast == null -> ServerPropertiesHelper.RELAY_READY_ENTITY_BROADCAST_PERCENT
            currentEntityBroadcast <= 0 -> ServerPropertiesHelper.RELAY_READY_ENTITY_BROADCAST_PERCENT
            else -> currentEntityBroadcast
        }

        var changed = false
        if (tunedView != currentView) {
            props["view-distance"] = tunedView.toString()
            changed = true
        }
        if (tunedSimulation != currentSimulation) {
            props["simulation-distance"] = tunedSimulation.toString()
            changed = true
        }
        if (tunedCompression != currentCompression) {
            props["network-compression-threshold"] = tunedCompression.toString()
            changed = true
        }
        if (tunedAllowFlight != currentAllowFlight) {
            props["allow-flight"] = tunedAllowFlight.toString()
            changed = true
        }
        if (tunedEntityBroadcast != currentEntityBroadcast) {
            props["entity-broadcast-range-percentage"] = tunedEntityBroadcast.toString()
            changed = true
        }
        if (props.getProperty("sync-chunk-writes") != "false") {
            props["sync-chunk-writes"] = "false"
            changed = true
        }
        // Always allow flight — prevents kick while spawn chunks are loading on join.
        if (props.getProperty("allow-flight") != "true") {
            props["allow-flight"] = "true"
            changed = true
        }

        if (changed) {
            ServerPropertiesHelper.saveProperties(serverDir, props)
            onOutput("[PocketCraft] Internet relay profile applied.")
        }
        onOutput(
            "[PocketCraft] Relay runtime profile: compression=$tunedCompression, view=$tunedView, simulation=$tunedSimulation, entity-range=$tunedEntityBroadcast%"
        )
    }

    private fun applyRelayReadyPaperGlobalConfig(
        serverDir: File,
        onOutput: (String) -> Unit
    ) {
        val configDir = File(serverDir, "config").also { it.mkdirs() }
        val paperGlobal = File(configDir, "paper-global.yml")
        val original = runCatching { paperGlobal.readText() }.getOrDefault("")

        var updated = original
        updated = ensureYamlSectionValue(updated, "chunk-loading-advanced", "auto-config-send-distance", "true")
        updated = ensureYamlSectionValue(updated, "misc", "io-threads", "2")
        updated = ensureYamlSectionValue(updated, "misc", "worker-threads", "2")
        updated = ensureYamlSectionValue(updated, "misc", "max-joins-per-tick", "2")

        // Disable bundled Spark profiler (fails to load native libraries on Android)
        updated = ensureYamlSectionValue(updated, "spark", "enabled", "false")
        updated = ensureYamlSectionValue(updated, "spark", "enable-immediately", "false")

        if (updated != original) {
            paperGlobal.writeText(updated)
            onOutput("[PocketCraft] Paper global tuning applied: adaptive chunk send + dedicated IO/worker threads.")
        }
    }

    private fun applyRelayReadySpigotConfig(
        serverDir: File,
        onOutput: (String) -> Unit
    ) {
        val spigotFile = File(serverDir, "spigot.yml")
        val original = runCatching { spigotFile.readText() }.getOrDefault("")

        var updated = original
        // Spigot can override both distances; keep them on "default" so the current
        // server.properties value is always the one Paper actually uses.
        updated = ensureYamlPathValue(updated, listOf("world-settings", "default"), "view-distance", "default")
        updated = ensureYamlPathValue(updated, listOf("world-settings", "default"), "simulation-distance", "default")
        updated = ensureYamlPathValue(updated, listOf("settings"), "moved-too-quickly-multiplier", "1000.0")
        updated = ensureYamlPathValue(updated, listOf("settings"), "moved-wrongly-threshold", "1000.0")

        if (updated != original) {
            spigotFile.writeText(updated)
            onOutput("[PocketCraft] Spigot view-distance overrides cleared so server.properties stays in control.")
        }
    }

    private fun applyRelayReadyPaperWorldDefaults(
        serverDir: File,
        onOutput: (String) -> Unit
    ) {
        val configDir = File(serverDir, "config").also { it.mkdirs() }
        val paperWorldDefaults = File(configDir, "paper-world-defaults.yml")
        val original = runCatching { paperWorldDefaults.readText() }.getOrDefault("")

        var updated = original
        // Maintain a small buffer so brief movement doesn't instantly cause chunk shedding.
        updated = ensureYamlPathValue(updated, listOf("chunks"), "delay-chunk-unloads-by", "10s")
        // Keep spawn chunks loaded so the first player to join sees terrain immediately.
        updated = ensureYamlPathValue(updated, listOf("chunks"), "keep-spawn-loaded", "true")
        // Ensure spawn radius is fully loaded (default 10) to prevent chunks not loading when joining
        updated = ensureYamlPathValue(updated, listOf("chunks"), "keep-spawn-loaded-range", "10")
        updated = ensureYamlPathValue(updated, listOf("chunks"), "max-auto-save-chunks-per-tick", "4")
        updated = ensureYamlPathValue(updated, listOf("chunks"), "prevent-moving-into-unloaded-chunks", "true")
        updated = ensureYamlPathValue(updated, listOf("tick-rates"), "mob-spawner", "2")
        updated = ensureYamlPathValue(updated, listOf("tick-rates"), "grass-spread", "4")
        updated = ensureYamlPathValue(updated, listOf("tick-rates"), "container-update", "1")
        // Entity save limits to reduce chunk I/O overhead
        updated = ensureYamlPathValue(updated, listOf("chunks", "entity-per-chunk-save-limit"), "arrow", "16")
        updated = ensureYamlPathValue(updated, listOf("chunks", "entity-per-chunk-save-limit"), "dragon_fireball", "3")
        updated = ensureYamlPathValue(updated, listOf("chunks", "entity-per-chunk-save-limit"), "egg", "8")
        updated = ensureYamlPathValue(updated, listOf("chunks", "entity-per-chunk-save-limit"), "ender_pearl", "8")
        updated = removeYamlPathKey(updated, listOf("chunks", "entity-per-chunk-save-limit"), "experience_ball")
        updated = ensureYamlPathValue(updated, listOf("chunks", "entity-per-chunk-save-limit"), "experience_orb", "8")
        updated = ensureYamlPathValue(updated, listOf("chunks", "entity-per-chunk-save-limit"), "fireball", "8")
        updated = ensureYamlPathValue(updated, listOf("chunks", "entity-per-chunk-save-limit"), "firework_rocket", "8")
        updated = ensureYamlPathValue(updated, listOf("chunks", "entity-per-chunk-save-limit"), "small_fireball", "8")
        updated = ensureYamlPathValue(updated, listOf("chunks", "entity-per-chunk-save-limit"), "snowball", "8")
        updated = removeYamlPathKey(updated, listOf("chunks", "entity-per-chunk-save-limit"), "thrown_exp_bottle")
        updated = ensureYamlPathValue(updated, listOf("chunks", "entity-per-chunk-save-limit"), "experience_bottle", "3")

        if (updated != original) {
            paperWorldDefaults.writeText(updated)
            onOutput("[PocketCraft] Paper world defaults updated: entity limits + chunk unload buffer + anti-void walking.")
        }
    }



    private fun ensureYamlSectionValue(
        original: String,
        section: String,
        key: String,
        value: String
    ): String = ensureYamlPathValue(original, listOf(section), key, value)

    private fun ensureYamlPathValue(
        original: String,
        path: List<String>,
        key: String,
        value: String
    ): String {
        val lines = original
            .ifBlank { "" }
            .split('\n')
            .toMutableList()

        if (lines.size == 1 && lines[0].isBlank()) {
            lines.clear()
        }

        var searchStart = 0
        var searchEnd = lines.size

        path.forEachIndexed { depth, section ->
            val indent = "  ".repeat(depth)
            val sectionIndex = (searchStart until searchEnd).firstOrNull { index ->
                val line = lines[index]
                line.trim() == "$section:" && leadingYamlIndent(line) == indent.length
            }

            val actualIndex = if (sectionIndex != null) {
                sectionIndex
            } else {
                val insertionIndex = searchEnd
                lines.add(insertionIndex, "$indent$section:")
                searchEnd += 1
                insertionIndex
            }

            searchStart = actualIndex + 1
            searchEnd = findYamlSectionEnd(lines, actualIndex)
        }

        val keyIndent = "  ".repeat(path.size)
        val keyIndex = (searchStart until searchEnd).firstOrNull { index ->
            val line = lines[index]
            leadingYamlIndent(line) == keyIndent.length && line.trimStart().startsWith("$key:")
        }

        if (keyIndex != null) {
            lines[keyIndex] = "$keyIndent$key: $value"
        } else {
            lines.add(searchEnd, "$keyIndent$key: $value")
        }

        return lines.joinToString("\n").trimEnd() + "\n"
    }

    private fun removeYamlPathKey(
        original: String,
        path: List<String>,
        key: String
    ): String {
        val lines = original
            .ifBlank { "" }
            .split('\n')
            .toMutableList()

        if (lines.size == 1 && lines[0].isBlank()) {
            return original
        }

        var searchStart = 0
        var searchEnd = lines.size

        path.forEachIndexed { depth, section ->
            val indent = "  ".repeat(depth)
            val sectionIndex = (searchStart until searchEnd).firstOrNull { index ->
                val line = lines[index]
                line.trim() == "$section:" && leadingYamlIndent(line) == indent.length
            } ?: return original

            searchStart = sectionIndex + 1
            searchEnd = findYamlSectionEnd(lines, sectionIndex)
        }

        val keyIndent = "  ".repeat(path.size)
        val keyIndex = (searchStart until searchEnd).firstOrNull { index ->
            val line = lines[index]
            leadingYamlIndent(line) == keyIndent.length && line.trimStart().startsWith("$key:")
        } ?: return original

        lines.removeAt(keyIndex)
        return lines.joinToString("\n").trimEnd() + "\n"
    }

    private fun findYamlSectionEnd(
        lines: List<String>,
        sectionStart: Int
    ): Int {
        val sectionIndent = leadingYamlIndent(lines[sectionStart])
        for (index in (sectionStart + 1) until lines.size) {
            val line = lines[index]
            val trimmed = line.trim()
            if (trimmed.isBlank() || trimmed.startsWith("#")) continue
            if (leadingYamlIndent(line) <= sectionIndent) {
                return index
            }
        }
        return lines.size
    }

    private fun leadingYamlIndent(line: String): Int {
        return line.takeWhile { it == ' ' || it == '\t' }.length
    }

    private fun getTotalRamMb(context: Context): Int {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return (memInfo.totalMem / 1024 / 1024).toInt()
    }

    private fun reportHotspotCrash(
        serverDir: String,
        onError: (String) -> Unit
    ) {
        val crashFile = File(serverDir)
            .listFiles()
            .orEmpty()
            .filter { it.isFile && it.name.startsWith("hs_err_pid") && it.extension == "log" }
            .maxByOrNull { it.lastModified() }
            ?: return

        onError("[PocketCraft] HotSpot crash log: ${crashFile.absolutePath}")
        runCatching {
            crashFile.useLines { lines ->
                lines
                    .take(80)
                    .forEach { onError(it) }
            }
        }.onFailure { error ->
            onError("[PocketCraft] Failed to read HotSpot crash log: ${error.message}")
        }
    }

    private fun resolveServerPort(worldName: String): Int {
        val serverDir = ServerFileManager.getServerDir(context, worldName)
        val propsFile = File(serverDir, "server.properties")
        if (!propsFile.exists()) return 25565
        return runCatching {
            propsFile.inputStream().use { input ->
                Properties().apply { load(input) }
            }.getProperty("server-port", "25565").toInt()
        }.getOrDefault(25565)
    }

    private fun streamLines(
        inputStream: InputStream,
        onOutput: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        runCatching {
            inputStream.bufferedReader().useLines { lines ->
                lines.forEach(onOutput)
            }
        }.onFailure { error ->
            onError("[PocketCraft] Failed to read server output: ${error.message}")
        }
    }

    private fun chmodJreRuntime(context: Context, runtime: JreExtractor.RuntimeSpec) {
        val jreDir = JreExtractor.getJreDir(context, runtime)
        val jreBinDir = File(jreDir, "bin")
        val jreLibDir = File(jreDir, "lib")

        // 0x1ED = octal 0755 (rwxr-xr-x)  — use the real chmod(2) syscall via Os.chmod
        // so that the execute bit is actually applied even under restrictive SELinux contexts.
        listOf(jreBinDir, jreLibDir).forEach { dir ->
            if (dir.exists()) {
                dir.walkTopDown().forEach { file ->
                    if (file.isFile) {
                        runCatching { android.system.Os.chmod(file.absolutePath, 0x1ED) }
                    }
                }
            }
        }
        android.util.Log.d("ServerLauncher", "Finished chmod on jre-runtime (Android 10 compat)")
    }

    private fun extractAndPatchJnaLibrary(paperJarPath: String, serverDir: File, shimDir: File): Boolean {
        if (isPatchedJnaCacheCurrent(paperJarPath, shimDir)) {
            return false
        }

        var patched = runCatching {
            val paperJar = ZipFile(paperJarPath)
            paperJar.use { jar ->
                val directEntry = jar.getEntry("com/sun/jna/linux-aarch64/libjnidispatch.so")
                if (directEntry != null) {
                    val libBytes = jar.getInputStream(directEntry).readBytes()
                    val patchedBytes = patchElfDtNeeded(libBytes) ?: return@runCatching false
                    writePatchedJnaCache(paperJarPath, shimDir, patchedBytes)
                    return@runCatching true
                }

                val jnaEntry = jar.entries().asSequence()
                    .filter { !it.isDirectory && it.name.endsWith(".jar") }
                    .firstOrNull {
                        val name = it.name.substringAfterLast('/')
                        name.startsWith("jna-") && !name.contains("jna-platform") && !name.contains("jna-jpms")
                    } ?: return@runCatching false

                val jnaBytes = jar.getInputStream(jnaEntry).readBytes()
                patchJnaFromBytes(jnaBytes, shimDir, paperJarPath)
            }
        }.getOrDefault(false)

        if (!patched) {
            val librariesDir = File(serverDir, "libraries")
            if (librariesDir.exists()) {
                val jnaJar = librariesDir.walkTopDown().firstOrNull {
                    it.isFile && it.name.startsWith("jna-") && !it.name.contains("jna-platform") && !it.name.contains("jna-jpms") && it.name.endsWith(".jar")
                }
                if (jnaJar != null) {
                    val jnaBytes = jnaJar.readBytes()
                    patched = patchJnaFromBytes(jnaBytes, shimDir, paperJarPath)
                }
            }
        }
        return patched
    }

    private fun patchJnaFromBytes(jnaBytes: ByteArray, shimDir: File, cacheKeyPath: String): Boolean {
        val zis = ZipInputStream(ByteArrayInputStream(jnaBytes))
        zis.use { stream ->
            var entry = stream.nextEntry
            while (entry != null) {
                if (entry.name.contains("linux-aarch64/libjnidispatch.so")) {
                    val libBytes = stream.readBytes()
                    val patched = patchElfDtNeeded(libBytes) ?: return false
                    writePatchedJnaCache(cacheKeyPath, shimDir, patched)
                    return true
                }
                entry = stream.nextEntry
            }
        }
        return false
    }

    private fun isPatchedJnaCacheCurrent(paperJarPath: String, shimDir: File): Boolean {
        val cachedLibrary = File(shimDir, "libjnidispatch.so")
        val stampFile = File(shimDir, "libjnidispatch.meta")
        if (!cachedLibrary.exists() || cachedLibrary.length() <= 0L || !stampFile.exists()) {
            return false
        }

        val expectedStamp = buildJnaCacheStamp(paperJarPath)
        val currentStamp = runCatching { stampFile.readText(Charsets.UTF_8).trim() }.getOrDefault("")
        return currentStamp == expectedStamp
    }

    private fun writePatchedJnaCache(paperJarPath: String?, shimDir: File, patchedBytes: ByteArray) {
        val dest = File(shimDir, "libjnidispatch.so")
        dest.writeBytes(patchedBytes)
        dest.setExecutable(true)
        if (paperJarPath != null) {
            File(shimDir, "libjnidispatch.meta").writeText(buildJnaCacheStamp(paperJarPath), Charsets.UTF_8)
        }
    }

    private fun buildJnaCacheStamp(paperJarPath: String): String {
        val jarFile = File(paperJarPath)
        return listOf(
            jarFile.absolutePath,
            jarFile.length().toString(),
            jarFile.lastModified().toString()
        ).joinToString("|")
    }

    private fun patchElfDtNeeded(data: ByteArray): ByteArray? {
        if (data.size < 64) return null
        if (data[0] != 0x7f.toByte() || data[1] != 'E'.code.toByte() ||
            data[2] != 'L'.code.toByte() || data[3] != 'F'.code.toByte()) return null
        if (data[4] != 2.toByte()) return null

        val result = data.copyOf()

        val shoff = readU64(result, 40)
        val shnum = readU16(result, 60)
        val shentsize = readU16(result, 58)
        val shstrndx = readU16(result, 62)

        val shstrtabOff = readU64(result, shoff + shstrndx * shentsize + 24)

        var dynstrOff = -1L
        var dynstrSize = 0L
        var dynamicOff = -1L
        var dynamicSize = 0L

        for (i in 0 until shnum) {
            val soff = shoff + i * shentsize
            val shType = readU32(result, soff + 4)
            if (shType == 3L) {
                val nameOff = readU32(result, soff)
                val name = readCString(result, shstrtabOff + nameOff)
                if (name == ".dynstr") {
                    dynstrOff = readU64(result, soff + 24)
                    dynstrSize = readU64(result, soff + 32)
                }
            } else if (shType == 6L) { // SHT_DYNAMIC
                dynamicOff = readU64(result, soff + 24)
                dynamicSize = readU64(result, soff + 32)
            }
        }

        if (dynstrOff < 0) return null

        // Each entry: (pattern bytes, replacement — must be <= pattern length, rest zero-padded)
        val patches = listOf(
            // libc.so.6\0  -> libc.so\0 + one zero pad
            "libc.so.6".toByteArray() to byteArrayOf(
                'l'.code.toByte(), 'i'.code.toByte(), 'b'.code.toByte(),
                'c'.code.toByte(), '.'.code.toByte(), 's'.code.toByte(),
                'o'.code.toByte(), 0, 0
            ),
            // libm.so.6\0 -> libm.so\0 + one zero pad
            "libm.so.6".toByteArray() to byteArrayOf(
                'l'.code.toByte(), 'i'.code.toByte(), 'b'.code.toByte(),
                'm'.code.toByte(), '.'.code.toByte(), 's'.code.toByte(),
                'o'.code.toByte(), 0, 0
            ),
            // libutil.so.1\0 -> libc.so\0 + five zero pads  (13 -> 8 bytes + 5 nulls)
            "libutil.so.1".toByteArray() to byteArrayOf(
                'l'.code.toByte(), 'i'.code.toByte(), 'b'.code.toByte(),
                'c'.code.toByte(), '.'.code.toByte(), 's'.code.toByte(),
                'o'.code.toByte(), 0, 0, 0, 0, 0, 0
            ),
            // libpthread.so.0\0 -> libc.so\0 + seven zero pads (16 -> 8 bytes + 8 nulls)
            "libpthread.so.0".toByteArray() to byteArrayOf(
                'l'.code.toByte(), 'i'.code.toByte(), 'b'.code.toByte(),
                'c'.code.toByte(), '.'.code.toByte(), 's'.code.toByte(),
                'o'.code.toByte(), 0, 0, 0, 0, 0, 0, 0, 0, 0
            ),
            // libdl.so.2\0 -> libdl.so\0 + one zero pad
            "libdl.so.2".toByteArray() to byteArrayOf(
                'l'.code.toByte(), 'i'.code.toByte(), 'b'.code.toByte(),
                'd'.code.toByte(), 'l'.code.toByte(), '.'.code.toByte(),
                's'.code.toByte(), 'o'.code.toByte(), 0, 0
            ),
            // librt.so.1\0 -> libc.so\0 + two zero pads
            "librt.so.1".toByteArray() to byteArrayOf(
                'l'.code.toByte(), 'i'.code.toByte(), 'b'.code.toByte(),
                'c'.code.toByte(), '.'.code.toByte(), 's'.code.toByte(),
                'o'.code.toByte(), 0, 0, 0
            )
        )

        var found = false
        val start = dynstrOff.toInt()
        val end = (dynstrOff + dynstrSize).toInt()

        for ((target, replacement) in patches) {
            // replacement array must exactly cover pattern length (incl. null terminator)
            var pos = start
            while (pos <= end - target.size) {
                var match = true
                for (i in target.indices) {
                    if (result[pos + i] != target[i]) { match = false; break }
                }
                if (match) {
                    for (i in replacement.indices) result[pos + i] = replacement[i]
                    found = true
                    break
                }
                pos++
            }
        }

        // Safely scan the .dynamic section to neutralize problematic versioning tags:
        // DT_VERNEEDNUM (0x6fffffff), DT_VERNEED (0x6ffffffe), and DT_VERSYM (0x6ffffff0)
        // We replace the tag with DT_RPATH (15) and set the value to 0 to appease Android 14+.
        if (dynamicOff >= 0 && dynamicSize > 0) {
            val numEntries = (dynamicSize / 16).toInt()
            for (i in 0 until numEntries) {
                val entryOff = dynamicOff + i * 16
                val dTag = readU64(result, entryOff)
                if (dTag == 0x6fffffffL || dTag == 0x6ffffffeL || dTag == 0x6ffffff0L) {
                    val idx = entryOff.toInt()
                    // Replace TAG with 15 (DT_RPATH)
                    result[idx] = 15
                    for (j in 1 until 8) result[idx + j] = 0
                    // Replace VALUE with 0
                    for (j in 0 until 8) result[idx + 8 + j] = 0
                    found = true
                }
            }
        }

        return if (found) result else null
    }

    private fun readU64(data: ByteArray, off: Long): Long {
        val i = off.toInt()
        return ((data[i].toLong() and 0xff)) or
               ((data[i + 1].toLong() and 0xff) shl 8) or
               ((data[i + 2].toLong() and 0xff) shl 16) or
               ((data[i + 3].toLong() and 0xff) shl 24) or
               ((data[i + 4].toLong() and 0xff) shl 32) or
               ((data[i + 5].toLong() and 0xff) shl 40) or
               ((data[i + 6].toLong() and 0xff) shl 48) or
               ((data[i + 7].toLong() and 0xff) shl 56)
    }

    private fun readU32(data: ByteArray, off: Long): Long {
        val i = off.toInt()
        return ((data[i].toLong() and 0xff)) or
               ((data[i + 1].toLong() and 0xff) shl 8) or
               ((data[i + 2].toLong() and 0xff) shl 16) or
               ((data[i + 3].toLong() and 0xff) shl 24)
    }

    private fun readU16(data: ByteArray, off: Long): Long {
        val i = off.toInt()
        return ((data[i].toLong() and 0xff)) or
               ((data[i + 1].toLong() and 0xff) shl 8)
    }

    private fun readCString(data: ByteArray, off: Long): String {
        val i = off.toInt()
        var end = i
        while (end < data.size && data[end] != 0.toByte()) end++
        return if (end > i) String(data.copyOfRange(i, end)) else ""
    }
}

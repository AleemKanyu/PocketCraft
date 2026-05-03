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
import java.io.File
import java.io.InputStream
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
        versionId: String,
        jarPath: String,
        onOutput : (String) -> Unit,
        onError  : (String) -> Unit,
        onStopped: (Int) -> Unit
    ) {
        val jarFile = File(jarPath)
        
        // Pre-launch guard: abort immediately if JAR is missing or is a directory (EISDIR prevention)
        if (!jarFile.exists() || jarFile.isDirectory) {
            throw IllegalStateException("JAR not found: ${jarFile.absolutePath}")
        }


        ServerFileManager.prepareEula(context, versionId)
        ServerFileManager.prepareServerProperties(context, versionId)
        ServerFileManager.prepareRuntimeArtifacts(context, versionId)
        
        val serverDirFileLocal = ServerFileManager.getServerDir(context, versionId)
        val props = ServerPropertiesHelper.readProperties(serverDirFileLocal)
        val serverTypeStr = props.getProperty("pocketcraft-server-type", "PAPER")
        val serverType = com.pocketcraft.server.data.model.ServerType.fromString(serverTypeStr)
        com.pocketcraft.server.service.DimensionMigrator.syncDimensionsForServerType(context, versionId, serverType)
        
        runBlocking(Dispatchers.IO) {
            PluginManager.ensureBedrockBridgePlugins(context, versionId).onFailure { error ->
                onOutput("[PocketCraft] Warning: Could not refresh Bedrock bridge plugins: ${error.message}")
            }
        }
        PluginManager.enforceBedrockBridgeLocalConfig(context, versionId)
        PluginManager.preserveFloodgateKey(context, versionId)
        PlayerDataManager.warnIfFloodgateUsernamePrefixChanged(serverDirFileLocal)

        val jrePath   = JreExtractor.getJreDir(context).absolutePath
        val serverDirFile = ServerFileManager.getServerDir(context, versionId)
        val serverDir = serverDirFile.absolutePath
        val tmpDir    = File(context.filesDir, "runtime-tmp").also { it.mkdirs() }.absolutePath
        val totalRam = getTotalRamMb(context)
        applyPreferencesToServerProperties(serverDirFile, onOutput)
        applyRelayReadyRuntimeProfile(serverDirFile, onOutput)
        applyRelayReadyPaperGlobalConfig(serverDirFile, onOutput)
        applyRelayReadySpigotConfig(serverDirFile, onOutput)
        applyRelayReadyPaperWorldDefaults(serverDirFile, onOutput)
        val prefs = AppPreferences(context)
        val fullMaxMb = (totalRam * 0.80).toInt().coerceAtLeast(768)

        val (minRamMb, maxRamMb) = when (prefs.ramMode) {
            "full" -> {
                val max = fullMaxMb
                val min = (max * 0.5).toInt().coerceAtLeast(512)
                Pair(min, max)
            }
            "manual" -> {
                val max = prefs.manualRamMb.coerceAtLeast(512)
                val min = (max * 0.5).toInt().coerceAtLeast(512)
                Pair(min, max)
            }
            else -> {
                val max = 512.coerceAtMost(fullMaxMb)
                val min = (max * 0.5).toInt().coerceAtLeast(256)
                Pair(min, max)
            }
        }

        onOutput("[PocketCraft] RAM profile: mode=${prefs.ramMode}, heap=${minRamMb}MB..${maxRamMb}MB, total=${totalRam}MB")

        val javaBin = JreExtractor.getJavaBinary(context)
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

        onOutput("[PocketCraft] Starting $versionId...")
        onOutput("[PocketCraft] JRE: $jrePath")
        onOutput("[PocketCraft] JAR: $jarPath")
        
        val shimDir = File(context.filesDir, "lib-shims").also { it.mkdirs() }
        val libs = if (Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()) "/system/lib64" else "/system/lib"
        
        runCatching {
            listOf(
                "libc.so.6" to "$libs/libc.so",
                "libdl.so.2" to "$libs/libdl.so",
                "libm.so.6" to "$libs/libm.so",
                "librt.so.1" to "$libs/libc.so",
                "libpthread.so.0" to "$libs/libc.so"
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
            }
        }.onFailure { e ->
            onOutput("[PocketCraft] Warning: Shim creation pool failed: ${e.message}")
        }

        Thread {
            var result = -1
            try {
                result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val externalExit = runCatching {
                        launchExternalJvm(
                            javaBin = javaBin,
                            jrePath = jrePath,
                            jarPath = jarPath,
                            serverDir = serverDir,
                            tmpDir = tmpDir,
                            shimDir = shimDir,
                            minRamMb = minRamMb,
                            maxRamMb = maxRamMb,
                            onOutput = onOutput,
                            onError = onError
                        )
                    }.getOrElse { error ->
                        onOutput(
                            "[PocketCraft] External JVM launch failed before startup on Android ${Build.VERSION.RELEASE}: ${error.message}. Falling back to isolated bootstrap."
                        )
                        Int.MIN_VALUE
                    }

                    if (externalExit == Int.MIN_VALUE || externalExit == 126 || externalExit == 127) {
                        onOutput("[PocketCraft] Launching isolated JVM bootstrap on Android ${Build.VERSION.RELEASE}.")
                        NativeLauncher.launchJVM(
                            jrePath = jrePath,
                            jarPath = jarPath,
                            serverDir = serverDir,
                            tmpDir = tmpDir,
                            nativeLibDir = context.applicationInfo.nativeLibraryDir,
                            shimDir = shimDir.absolutePath,
                            minRamMb = minRamMb,
                            maxRamMb = maxRamMb
                        )
                    } else {
                        externalExit
                    }
                } else {
                    launchExternalJvm(
                        javaBin = javaBin,
                        jrePath = jrePath,
                        jarPath = jarPath,
                        serverDir = serverDir,
                        tmpDir = tmpDir,
                        shimDir = shimDir,
                        minRamMb = minRamMb,
                        maxRamMb = maxRamMb,
                        onOutput = onOutput,
                        onError = onError
                    )
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
        onOutput: (String) -> Unit,
        onError: (String) -> Unit
    ): Int {
        val errorFilePattern = File(serverDir, "hs_err_pid%p.log").absolutePath
        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        val wrapperBin = File(nativeLibDir, "libserverwrap.so")
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

        val jnaBootPath = "$nativeLibDir:${shimDir.absolutePath}"
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
            "-Djna.nosys=true",
            "-Xshare:off",
            "-XX:+UnlockExperimentalVMOptions",
            "-XX:+AlwaysPreTouch",
            "-XX:+UseStringDeduplication",
            "-XX:+UseG1GC",
            "-XX:+ParallelRefProcEnabled",
            "-XX:MaxGCPauseMillis=200",
            "-XX:+DisableExplicitGC",
            "-XX:G1NewSizePercent=30",
            "-XX:G1MaxNewSizePercent=40",
            "-XX:G1HeapRegionSize=8M",
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
            "-Dio.netty.recycler.maxCapacityPerThread=0",
            "-Dio.netty.recycler.linkCapacity=1024",
            "-Djdk.lang.Process.launchMechanism=FORK",
            "-jar",
            jarPath,
            "--nogui",
            "--port",
            "25565"
        )

        val launcherName = if (wrapperBin.exists()) "serverwrap" else "java"
        onOutput("[PocketCraft] Launching dedicated Java process on Android ${Build.VERSION.RELEASE} via $launcherName.")
 
        val command = buildList {
            if (wrapperBin.exists()) add(wrapperBin.absolutePath)
            add(javaBin.absolutePath)
            addAll(vmArgs)
        }
 
        val process = ProcessBuilder(command)
            .directory(File(serverDir))
            .redirectErrorStream(true)
            .apply {
                environment()["POJAV_NATIVEDIR"] = nativeLibDir
                environment()["JAVA_HOME"] = jrePath
                environment()["HOME"] = serverDir
                environment()["TMPDIR"] = tmpDir
                environment()["LD_LIBRARY_PATH"] = ldLibraryPath
                environment()["PATH"] = "${javaBin.parent}:${System.getenv("PATH").orEmpty()}"
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

    private fun applyPreferencesToServerProperties(
        serverDir: File,
        onOutput: (String) -> Unit
    ) {
        val savedConfig = runBlocking(Dispatchers.IO) {
            runCatching { ServerConfigRepository(context).loadConfig() }.getOrNull()
        } ?: return
        ServerPropertiesWriter.apply(serverDir, ServerPropertiesWriter.toSnapshot(savedConfig))
        onOutput("[PocketCraft] Restored saved server settings before launch.")
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
        val maxView = 32
        val maxSimulation = 32
        var tunedView = currentView.coerceIn(3, maxView)
        var tunedSimulation = currentSimulation.coerceIn(3, maxSimulation)
        var tunedAllowFlight = currentAllowFlight


        if (flightModeEnabled && !currentAllowFlight) {
            tunedAllowFlight = true
            onOutput("[PocketCraft] Flight Mode active: enabling allow-flight.")
        }

        val tunedCompression = when {
            currentCompression == null -> ServerPropertiesHelper.RELAY_READY_COMPRESSION_THRESHOLD
            currentCompression < 0 -> ServerPropertiesHelper.RELAY_READY_COMPRESSION_THRESHOLD
            currentCompression > 512 -> ServerPropertiesHelper.RELAY_READY_COMPRESSION_THRESHOLD
            else -> currentCompression
        }
        val tunedEntityBroadcast = when {
            currentEntityBroadcast == null -> ServerPropertiesHelper.RELAY_READY_ENTITY_BROADCAST_PERCENT
            currentEntityBroadcast <= 0 -> ServerPropertiesHelper.RELAY_READY_ENTITY_BROADCAST_PERCENT
            else -> currentEntityBroadcast.coerceAtMost(ServerPropertiesHelper.RELAY_READY_ENTITY_BROADCAST_PERCENT)
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
        updated = ensureYamlSectionValue(updated, "chunk-loading-advanced", "player-max-concurrent-chunk-generates", "0")
        updated = ensureYamlSectionValue(updated, "chunk-loading-advanced", "player-max-concurrent-chunk-loads", "12")
        updated = ensureYamlSectionValue(updated, "chunk-loading-advanced", "player-loading-priority-override", "10")
        updated = ensureYamlSectionValue(updated, "chunk-loading-basic", "player-max-chunk-generate-rate", "-1.0")
        updated = ensureYamlSectionValue(updated, "chunk-loading-basic", "player-max-chunk-load-rate", "200.0")
        updated = ensureYamlSectionValue(updated, "misc", "io-threads", "3")
        updated = ensureYamlSectionValue(updated, "misc", "worker-threads", "3")
        updated = ensureYamlSectionValue(updated, "chunk-loading-basic", "player-max-chunk-send-rate", "100.0")
        updated = ensureYamlSectionValue(updated, "chunk-loading-basic", "target-player-chunk-send-rate", "-1.0")
        updated = ensureYamlSectionValue(updated, "misc", "max-joins-per-tick", "4")
        // Chunk system: dedicate threads for IO and generation
        updated = ensureYamlSectionValue(updated, "chunk-system", "gen-parallelism", "default")
        updated = ensureYamlSectionValue(updated, "chunk-system", "io-threads", "2")
        updated = ensureYamlSectionValue(updated, "chunk-system", "worker-threads", "2")

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
}

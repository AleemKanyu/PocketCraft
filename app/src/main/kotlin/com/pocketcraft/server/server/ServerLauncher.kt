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
import com.pocketcraft.server.util.NetworkUtils
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
import java.util.Locale
import java.util.Properties
import java.util.concurrent.TimeUnit
import kotlin.math.pow

class ServerLauncher(private val context: Context) {

    private data class DeviceStabilityProfile(
        val forceExternalJvm: Boolean,
        val constrainedHeap: Boolean,
        val maxHeapCapMb: Int,
        val minHeapFloorMb: Int,
        val reason: String?
    )

    private fun normalizeAndroidPath(path: String): String {
        return if (path.startsWith("/data/user/0/")) {
            path.replaceFirst("/data/user/0/", "/data/data/")
        } else {
            path
        }
    }

    private fun buildDeviceStabilityProfile(totalRamMb: Int, availableRamMb: Int): DeviceStabilityProfile {
        val manufacturer = Build.MANUFACTURER.orEmpty().lowercase(Locale.US)
        val model = Build.MODEL.orEmpty().lowercase(Locale.US)
        val device = Build.DEVICE.orEmpty().lowercase(Locale.US)
        val product = Build.PRODUCT.orEmpty().lowercase(Locale.US)
        val isGalaxyA12Family = manufacturer.contains("samsung") && listOf(model, device, product).any { value ->
            value.contains("a12") || value.contains("sm-a125") || value.contains("sm-a127")
        }
        val constrainedHeap = isGalaxyA12Family || totalRamMb <= 4096
        val targetHeapCap = when {
            isGalaxyA12Family -> minOf((availableRamMb * 0.52f).toInt(), 896)
            totalRamMb <= 3072 -> minOf((availableRamMb * 0.58f).toInt(), 768)
            totalRamMb <= 4096 -> minOf((availableRamMb * 0.62f).toInt(), 1024)
            else -> minOf((availableRamMb * 0.72f).toInt(), (totalRamMb * 0.90f).toInt())
        }
        val minHeapFloor = if (isGalaxyA12Family || totalRamMb <= 3072) 384 else 512
        val reason = when {
            isGalaxyA12Family -> "Samsung Galaxy A12 low-memory profile active. Using safer heap limits to reduce short crash loops."
            constrainedHeap -> "Low-memory device profile active. Heap is capped to reduce background crash risk."
            else -> null
        }
        return DeviceStabilityProfile(
            forceExternalJvm = false,
            constrainedHeap = constrainedHeap,
            maxHeapCapMb = targetHeapCap.coerceAtLeast(minHeapFloor),
            minHeapFloorMb = minHeapFloor,
            reason = reason
        )
    }

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
        launchMode: ServerFileManager.LaunchMode = ServerFileManager.LaunchMode.JAR,
        runtime: JreExtractor.RuntimeSpec,
        onOutput : (String) -> Unit,
        onError  : (String) -> Unit,
        onStopped: (Int) -> Unit
    ) {
        val normalizedJarPath = normalizeAndroidPath(jarPath)
        val launchTarget = File(normalizedJarPath)
        
        // Pre-launch guard: abort immediately if the persisted launch target is missing.
        if (!launchTarget.exists() || launchTarget.isDirectory) {
            throw IllegalStateException("Launch target not found: ${launchTarget.absolutePath}")
        }


        ServerFileManager.prepareEula(context, worldName)
        ServerFileManager.prepareServerProperties(context, worldName)

        // Sync configuration from ServerConfigRepository to server.properties of the world before launch
        runCatching {
            val configRepo = ServerConfigRepository(context).apply {
                setWorldNameOverride(worldName)
            }
            val config = runBlocking { configRepo.loadConfig() }
            val serverDir = ServerFileManager.getServerDir(context, worldName)
            val isPremium = AppPreferences(context).let { it.isPremiumUser || it.debugPremiumOverride }
            ServerPropertiesWriter.apply(serverDir, ServerPropertiesWriter.toSnapshot(config), isPremium)
        }.onFailure { e ->
            onOutput("[PocketCraft] Warning: Failed to sync configuration properties: ${e.message}")
        }

        ServerFileManager.prepareRuntimeArtifacts(context, worldName)
        PluginManager.removeIncompatiblePlugins(context, worldName)
        
        val serverDirFileLocal = ServerFileManager.getServerDir(context, worldName)
        val props = ServerPropertiesHelper.readProperties(serverDirFileLocal)

        // Validate level.dat and attempt recovery if corrupted
        try {
            validateAndRecoverLevelDat(serverDirFileLocal, props, onOutput)
        } catch (e: Exception) {
            onOutput("[PocketCraft] Level.dat validator exception: ${e.message}")
        }

        val serverTypeStr = props.getProperty("pocketcraft-server-type", "PAPER")
        val serverType = com.pocketcraft.server.data.model.ServerType.fromString(serverTypeStr)

        val levelName = props.getProperty("level-name", "world")
        val worldDir = File(serverDirFileLocal, levelName)
        val levelDat = File(worldDir, "level.dat")
        if (serverType != com.pocketcraft.server.data.model.ServerType.PAPER && levelDat.exists()) {
            try {
                com.pocketcraft.server.service.NBTParser.cleanPaperDatapack(levelDat, onOutput)
            } catch (e: Exception) {
                onOutput("[PocketCraft] Failed to clean paper datapack from level.dat: ${e.message}")
            }
        }
        
        com.pocketcraft.server.service.DimensionMigrator.syncDimensionsForServerType(context, worldName, serverType)
        PluginManager.preserveFloodgateKey(context, worldName)
        PluginManager.enforceBedrockBridgeLocalConfig(context, worldName)
        PlayerDataManager.warnIfFloodgateUsernamePrefixChanged(serverDirFileLocal)

        val serverDirFile = File(normalizeAndroidPath(ServerFileManager.getServerDir(context, worldName).absolutePath))
        val serverDir = serverDirFile.absolutePath
        val tmpDir    = normalizeAndroidPath(File(context.filesDir, "runtime-tmp").also { it.mkdirs() }.absolutePath)
        val shimDir = File(normalizeAndroidPath(File(context.filesDir, "lib-shims").also { it.mkdirs() }.absolutePath))
        ensureSystemShims(shimDir, File(tmpDir), onOutput)
        val deviceProfile = buildDeviceStabilityProfile(totalRamMb = getTotalRamMb(context), availableRamMb = com.pocketcraft.server.util.RamUtils.getAvailableRamMb(context))
        val forceExternal = AppPreferences(context).forceExternalJvm || deviceProfile.forceExternalJvm
        val preferInProcessJvm = launchMode == ServerFileManager.LaunchMode.JAR && !forceExternal && NativeLauncher.loadLibrary()

        val resolvedRuntime = ensureLaunchableRuntime(
            versionId = versionId,
            preferredRuntime = runtime,
            tmpDir = tmpDir,
            shimDir = shimDir,
            preferInProcessJvm = preferInProcessJvm,
            onOutput = onOutput
        ) ?: run {
            onError("[PocketCraft] Could not prepare a launchable Java runtime for this device.")
            onStopped(127)
            return
        }

        val jrePath   = normalizeAndroidPath(JreExtractor.getJreDir(context, resolvedRuntime).absolutePath)
        chmodJreRuntime(context, resolvedRuntime)
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
        val maxAllowedRam = minOf((totalRam * 0.90).toInt(), deviceProfile.maxHeapCapMb)
            .coerceAtLeast(deviceProfile.minHeapFloorMb)
        
        val requestedMaxRamMb = when (ramModeFromProps) {
            "low" -> when {
                deviceProfile.constrainedHeap && totalRam <= 3500 -> 768
                deviceProfile.constrainedHeap -> 896
                totalRam >= 6000 -> 2048
                totalRam >= 4000 -> 1536
                else -> 1024
            }
            "full" -> maxAllowedRam
            "manual" -> maxRamMbFromProps.coerceIn(deviceProfile.minHeapFloorMb, maxAllowedRam)
            else -> 1024
        }
        val requestedMinRamMb = when (ramModeFromProps) {
            "low" -> when {
                deviceProfile.constrainedHeap && totalRam <= 3500 -> 384
                deviceProfile.constrainedHeap -> 512
                totalRam >= 6000 -> 1024
                totalRam >= 4000 -> 768
                else -> 512
            }
            "full" -> maxAllowedRam
            "manual" -> maxRamMbFromProps.coerceIn(deviceProfile.minHeapFloorMb, maxAllowedRam)
            else -> 512
        }
        val maxRamMb = requestedMaxRamMb.coerceIn(deviceProfile.minHeapFloorMb, maxAllowedRam)
        val minRamMb = requestedMinRamMb.coerceIn(deviceProfile.minHeapFloorMb, maxRamMb)

        deviceProfile.reason?.let { reason ->
            onOutput("[PocketCraft] Stability mode enabled: $reason")
        }
        onOutput("[PocketCraft] JVM memory: mode=$ramModeFromProps, heap=${minRamMb}MB..${maxRamMb}MB, available=${availRam}MB, total=${totalRam}MB")

        val javaBin = File(normalizeAndroidPath(JreExtractor.getJavaBinary(context, resolvedRuntime).absolutePath))
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

        runCatching {
            if (extractAndPatchJnaLibrary(normalizedJarPath, serverDirFile, shimDir)) {
                onOutput("[PocketCraft] Patched JNA native dispatch library for Android")
            }
        }.onFailure { e ->
            onOutput("[PocketCraft] JNA patching skipped: ${e.message}")
        }

        onOutput("[PocketCraft] Starting world '$worldName' (version $versionId)...")
        onOutput("[PocketCraft] JRE: $jrePath")
        onOutput("[PocketCraft] Launch target: $normalizedJarPath")
        
        Thread {
            var result = -1
            try {
                if (launchMode != ServerFileManager.LaunchMode.JAR || forceExternal) {
                    onOutput("[PocketCraft] Routing to out-of-process JVM execution (ForceExternal=$forceExternal)")
                    result = launchExternalJvm(
                        javaBin = javaBin,
                        jrePath = jrePath,
                        launchTargetPath = normalizedJarPath,
                        launchMode = launchMode,
                        serverDir = serverDir,
                        tmpDir = tmpDir,
                        shimDir = shimDir,
                        minRamMb = minRamMb,
                        maxRamMb = maxRamMb,
                        worldName = worldName,
                        onOutput = onOutput,
                        onError = onError
                    )
                } else {
                    result = runCatching {
                        onOutput("[PocketCraft] Launching in-process JVM on Android ${Build.VERSION.RELEASE}.")
                        NativeLauncher.launchJVM(
                            jrePath = jrePath,
                            jarPath = normalizedJarPath,
                            serverDir = serverDir,
                            tmpDir = tmpDir,
                            nativeLibDir = normalizeAndroidPath(context.applicationInfo.nativeLibraryDir),
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

                    // Fallback to external JVM if JNI returns failure (but didn't hard-segfault/terminate the app)
                    if (result != 0) {
                        onOutput("[PocketCraft] In-process JVM failed with code $result. Trying out-of-process JVM fallback...")
                        result = launchExternalJvm(
                            javaBin = javaBin,
                            jrePath = jrePath,
                            launchTargetPath = normalizedJarPath,
                            launchMode = launchMode,
                            serverDir = serverDir,
                            tmpDir = tmpDir,
                            shimDir = shimDir,
                            minRamMb = minRamMb,
                            maxRamMb = maxRamMb,
                            worldName = worldName,
                            onOutput = onOutput,
                            onError = onError
                        )
                    }
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

    private fun ensureLaunchableRuntime(
        versionId: String,
        preferredRuntime: JreExtractor.RuntimeSpec,
        tmpDir: String,
        shimDir: File,
        preferInProcessJvm: Boolean,
        onOutput: (String) -> Unit
    ): JreExtractor.RuntimeSpec? {
        val candidates = buildList {
            add(preferredRuntime)
            addAll(JreExtractor.launchCandidatesForVersion(versionId))
        }.distinctBy { it.id }

        for (candidate in candidates) {
            if (verifyRuntime(candidate, tmpDir, shimDir, preferInProcessJvm, onOutput)) {
                return candidate
            }

            onOutput("[PocketCraft] ${candidate.displayName} preflight failed. Reinstalling runtime...")
            val rebuilt = runCatching {
                JreExtractor.forceReextract(context, candidate) { percent, status ->
                    if (percent == 0 || percent >= 25 || status.contains("Ready")) {
                        onOutput("[PocketCraft] ${candidate.displayName}: $status ($percent%)")
                    }
                }
                verifyRuntime(candidate, tmpDir, shimDir, preferInProcessJvm, onOutput)
            }.getOrElse { error ->
                onOutput("[PocketCraft] ${candidate.displayName} reinstall failed: ${error.message}")
                false
            }
            if (rebuilt) {
                return candidate
            }
        }

        return null
    }

    private fun verifyRuntime(
        runtime: JreExtractor.RuntimeSpec,
        tmpDir: String,
        shimDir: File,
        preferInProcessJvm: Boolean,
        onOutput: (String) -> Unit
    ): Boolean {
        chmodJreRuntime(context, runtime)
        val javaBin = JreExtractor.getJavaBinary(context, runtime)
        val jreDir = JreExtractor.getJreDir(context, runtime)
        val libjli = File(jreDir, "lib/libjli.so")
        val libjvm = File(jreDir, "lib/server/libjvm.so")

        if (!javaBin.exists() || !libjli.exists() || !libjvm.exists()) {
            onOutput("[PocketCraft] ${runtime.displayName} files are incomplete.")
            return false
        }

        if (preferInProcessJvm) {
            onOutput("[PocketCraft] ${runtime.displayName} verified for in-process JVM launch.")
            return true
        }

        val result = runJavaPreflight(
            runtime = runtime,
            tmpDir = tmpDir,
            shimDir = shimDir
        )
        if (!result.success) {
            onOutput("[PocketCraft] ${runtime.displayName} preflight failed (exit=${result.exitCode}): ${result.detail}")
        }
        return result.success
    }

    private data class RuntimePreflightResult(
        val success: Boolean,
        val exitCode: Int,
        val detail: String
    )

    private data class JavaCommandProbeResult(
        val success: Boolean,
        val exitCode: Int,
        val detail: String,
        val usedWrapper: Boolean
    )

    private fun runJavaPreflight(
        runtime: JreExtractor.RuntimeSpec,
        tmpDir: String,
        shimDir: File
    ): RuntimePreflightResult {
        val jrePath = normalizeAndroidPath(JreExtractor.getJreDir(context, runtime).absolutePath)
        val javaBin = File(normalizeAndroidPath(JreExtractor.getJavaBinary(context, runtime).absolutePath))
        val nativeLibDir = normalizeAndroidPath(context.applicationInfo.nativeLibraryDir)
        val wrapperBin = File(normalizeAndroidPath(File(nativeLibDir, "libserverwrap.so").absolutePath))
        if (wrapperBin.exists() && !wrapperBin.canExecute()) {
            runCatching { android.system.Os.chmod(wrapperBin.absolutePath, 0x1ED) }
        }

        val archLibDir = detectRuntimeLibDir(jrePath)
        val jvmDir = File(archLibDir, "server").takeIf { it.isDirectory } ?: File(jrePath, "lib/server")
        val ldLibraryPath = buildLdLibraryPath(archLibDir, jvmDir, shimDir, nativeLibDir)
        val wrapperProbe = if (wrapperBin.exists()) {
            probeJavaCommand(
                commandPrefix = listOf(wrapperBin.absolutePath, javaBin.absolutePath),
                jrePath = jrePath,
                tmpDir = tmpDir,
                homeDir = normalizeAndroidPath(context.filesDir.absolutePath),
                ldLibraryPath = ldLibraryPath,
                javaBinDir = normalizeAndroidPath(javaBin.parent.orEmpty()),
                usedWrapper = true
            )
        } else {
            null
        }
        if (wrapperProbe?.success == true) {
            return RuntimePreflightResult(true, wrapperProbe.exitCode, wrapperProbe.detail)
        }

        val directProbe = probeJavaCommand(
            commandPrefix = listOf(javaBin.absolutePath),
            jrePath = jrePath,
            tmpDir = tmpDir,
            homeDir = normalizeAndroidPath(context.filesDir.absolutePath),
            ldLibraryPath = ldLibraryPath,
            javaBinDir = normalizeAndroidPath(javaBin.parent.orEmpty()),
            usedWrapper = false
        )
        if (directProbe.success) {
            return RuntimePreflightResult(true, directProbe.exitCode, directProbe.detail)
        }

        val details = buildString {
            if (wrapperProbe != null) {
                append("wrapper: ")
                append(wrapperProbe.detail)
            }
            if (isNotEmpty()) append(" | ")
            append("direct: ")
            append(directProbe.detail)
        }
        val failingExit = wrapperProbe?.exitCode ?: directProbe.exitCode
        return RuntimePreflightResult(false, failingExit, details)
    }

    private fun probeJavaCommand(
        commandPrefix: List<String>,
        jrePath: String,
        tmpDir: String,
        homeDir: String,
        ldLibraryPath: String,
        javaBinDir: String,
        usedWrapper: Boolean
    ): JavaCommandProbeResult {
        val rawCommand = buildList {
            addAll(commandPrefix)
            add("-Xshare:off")
            add("-version")
        }
        val shellCmd = "exec " + rawCommand.joinToString(" ") { arg ->
            "'" + arg.replace("'", "'\\''") + "'"
        }
        val process = ProcessBuilder("/system/bin/sh", "-c", shellCmd)
            .redirectErrorStream(true)
            .apply {
                environment()["JAVA_HOME"] = jrePath
                environment()["TMPDIR"] = tmpDir
                environment()["HOME"] = homeDir
                environment()["LD_LIBRARY_PATH"] = "$jrePath/lib/server:$jrePath/lib:$jrePath/lib/jli:$ldLibraryPath"
                environment()["PATH"] = "$jrePath/bin:/system/bin:/system/xbin:$javaBinDir:${System.getenv("PATH").orEmpty()}"
                environment()["BIONIC_DISABLE_PTR_TAGGING"] = "1"
            }
            .start()

        val output = StringBuilder()
        val readerThread = Thread {
            runCatching {
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        if (output.length < 4000) {
                            if (output.isNotEmpty()) output.append('\n')
                            output.append(line)
                        }
                    }
                }
            }
        }.apply {
            name = "runtime-preflight-reader"
            isDaemon = true
            start()
        }

        if (!process.waitFor(20, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            readerThread.join(1000)
            return JavaCommandProbeResult(false, 124, "Timed out while executing java -version", usedWrapper)
        }

        readerThread.join(1000)
        val exitCode = process.exitValue()
        val detail = output.toString().trim().ifBlank { "No output" }
        val success = exitCode == 0 && detail.contains("version", ignoreCase = true)
        return JavaCommandProbeResult(success, exitCode, detail, usedWrapper)
    }

    private fun launchExternalJvm(
        javaBin: File,
        jrePath: String,
        launchTargetPath: String,
        launchMode: ServerFileManager.LaunchMode,
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
        val nativeLibDir = normalizeAndroidPath(context.applicationInfo.nativeLibraryDir)
        val wrapperBin = File(normalizeAndroidPath(File(nativeLibDir, "libserverwrap.so").absolutePath))
        if (wrapperBin.exists() && !wrapperBin.canExecute()) {
            runCatching { android.system.Os.chmod(wrapperBin.absolutePath, 0x1ED) }
                .onFailure { android.util.Log.w("ServerLauncher", "chmod serverwrap failed: ${it.message}") }
        }
        val archLibDir = detectRuntimeLibDir(jrePath)
        val jvmDir = File(archLibDir, "server").takeIf { it.isDirectory } ?: File(jrePath, "lib/server")
        val ldLibraryPath = buildLdLibraryPath(archLibDir, jvmDir, shimDir, nativeLibDir)
        val javaLibraryPath = buildString {
            append(ldLibraryPath)
            append(":")
            append(nativeLibDir)
        }

        val jnaBootPath = shimDir.absolutePath
        val jnaLibraryPath = jnaBootPath

        val cores = Runtime.getRuntime().availableProcessors()
        val nettyThreads = (cores / 2).coerceIn(2, 4)
        val totalRam = getTotalRamMb(context)

        val gcFlags = listOf(
            "-XX:G1HeapWastePercent=10",
            "-XX:G1MixedGCCountTarget=8",
            "-XX:G1MixedGCLiveThresholdPercent=85",
            "-XX:G1RSetUpdatingPauseTimePercent=10"
        )

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
            "-Dio.netty.eventLoopThreads=$nettyThreads",
            "-Dfile.encoding=UTF-8",
            "-Dusing.aikars.flags=https://mcflags.emc.gs",
            "-Dpaper.playerconnection.keepalive=90",
            "-Dorg.jline.terminal.jna=false",
            "-Dorg.jline.terminal.jni=false",
            "-Dorg.jline.terminal.dumb=true",
            "-Djava.awt.headless=true",
            "-Djava.library.path=$javaLibraryPath",
            "-DPaper.IgnoreJavaVersion=true",
            "-Dpaper.disable-update-check=true",
            "-Dpaper.disable-plugin-update-check=true",
            "-Dsun.net.client.defaultConnectTimeout=5000",
            "-Dsun.net.client.defaultReadTimeout=5000",
            "-Dsun.zip.disableMemoryMapping=true",
            "-Djdk.attach.allowAttachSelf=true",
            "-Djna.nosys=true",
            "-Xshare:off",
            "-XX:+UnlockExperimentalVMOptions",
            "-XX:+UnlockDiagnosticVMOptions",
            if (totalRam >= 4500) "-XX:+AlwaysPreTouch" else "-XX:-AlwaysPreTouch",
            "-XX:+UseStringDeduplication",
            "-XX:+UseG1GC",
            "-XX:+ParallelRefProcEnabled",
            "-XX:MaxGCPauseMillis=80",
            "-XX:+DisableExplicitGC",
        ).apply {
            addAll(gcFlags)
            addAll(listOf(
                "-XX:+PerfDisableSharedMem",
                "-XX:-UsePerfData",
                "-XX:-UseContainerSupport",
                "-XX:ErrorFile=$errorFilePattern",
                "-Dio.netty.allocator.maxOrder=9",
                "-Dio.netty.recycler.maxCapacity=262144",
                "-Dio.netty.recycler.maxCapacityPerThread=1024",
                "-Dio.netty.recycler.linkCapacity=1024",
                "-Dio.netty.allocator.type=pooled",
                "-Dio.netty.leakDetection.level=disabled",
                "-Dio.netty.noPreferDirect=false",
                "-Dio.netty.noUnsafe=false",
                "-Djdk.lang.Process.launchMechanism=FORK",
            ))
            when (launchMode) {
                ServerFileManager.LaunchMode.JAR -> {
                    add("-jar")
                    add(launchTargetPath)
                    add("nogui")
                    add("--port")
                    add(resolveServerPort(worldName).toString())
                }
                ServerFileManager.LaunchMode.ARG_FILE -> {
                    add("@$launchTargetPath")
                    add("nogui")
                }
            }
        }

        // Use Os.chmod (real syscall) instead of File.setExecutable which silently fails under SELinux.
        // 0x1ED = octal 0755 = rwxr-xr-x
        runCatching { android.system.Os.chmod(javaBin.absolutePath, 0x1ED) }
            .onFailure { android.util.Log.w("ServerLauncher", "chmod java failed: ${it.message}") }
        // Also ensure the whole bin/ directory has correct execute bits.
        javaBin.parentFile?.walkTopDown()?.filter { it.isFile }?.forEach { f ->
            runCatching { android.system.Os.chmod(f.absolutePath, 0x1ED) }
        }

        val launcherPrefix = selectLaunchCommandPrefix(
            wrapperBin = wrapperBin,
            javaBin = javaBin,
            jrePath = jrePath,
            tmpDir = tmpDir,
            homeDir = serverDir,
            ldLibraryPath = ldLibraryPath
        )
        val launcherName = if (launcherPrefix.firstOrNull() == wrapperBin.absolutePath) "serverwrap" else "java"
        onOutput("[PocketCraft] Launching dedicated Java process on Android ${Build.VERSION.RELEASE} via $launcherName.")

        // On Android 10+ some vendors block direct execve from code_cache via SELinux.
        // Routing through /system/bin/sh bypasses this: the shell runs in a trusted domain
        // that IS permitted to exec app-owned binaries.
        val rawCommand = buildList<String> {
            addAll(launcherPrefix)
            addAll(vmArgs)
        }
        // Build a shell-quoted command string so we can pass it to sh -c.
        // Prepend "exec " so the shell process replaces itself with the JVM wrapper,
        // making the JVM wrapper/process the direct child of ProcessBuilder.
        val shellCmd = "exec " + rawCommand.joinToString(" ") { arg ->
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
        val pid = runCatching {
            val method = process.javaClass.getMethod("pid")
            method.invoke(process) as Long
        }.getOrElse {
            runCatching {
                val field = process.javaClass.getDeclaredField("pid")
                field.isAccessible = true
                (field.get(process) as Number).toLong()
            }.getOrDefault(-1L)
        }
        if (pid > 0) {
            ServerHostService.persistExternalJvmPid(context, pid)
        }

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
        ServerHostService.persistExternalJvmPid(context, -1L)
        if (exitCode != 0) {
            reportHotspotCrash(serverDir, onError)
        }
        return exitCode
    }

    private fun selectLaunchCommandPrefix(
        wrapperBin: File,
        javaBin: File,
        jrePath: String,
        tmpDir: String,
        homeDir: String,
        ldLibraryPath: String
    ): List<String> {
        if (wrapperBin.exists()) {
            val wrapperProbe = probeJavaCommand(
                commandPrefix = listOf(wrapperBin.absolutePath, javaBin.absolutePath),
                jrePath = jrePath,
                tmpDir = tmpDir,
                homeDir = homeDir,
                ldLibraryPath = ldLibraryPath,
                javaBinDir = javaBin.parent.orEmpty(),
                usedWrapper = true
            )
            if (wrapperProbe.success) {
                return listOf(wrapperBin.absolutePath, javaBin.absolutePath)
            }
            android.util.Log.w(
                "ServerLauncher",
                "serverwrap preflight failed (exit=${wrapperProbe.exitCode}): ${wrapperProbe.detail}. Falling back to direct java."
            )
        }
        return listOf(javaBin.absolutePath)
    }

    private fun ensureSystemShims(
        shimDir: File,
        tmpShimDir: File,
        onOutput: (String) -> Unit
    ) {
        val libs = if (Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()) "/system/lib64" else "/system/lib"
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
    }

    private fun buildLdLibraryPath(
        archLibDir: File,
        jvmDir: File,
        shimDir: File,
        nativeLibDir: String
    ): String = buildString {
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

    private fun computeRelayChunkSendBudget(
        cellularRelay: Boolean,
        viewDistance: Int,
        flightModeEnabled: Boolean
    ): Int {
        val vd = viewDistance.coerceIn(4, 32)
        return if (!cellularRelay) {
            // Wi-Fi: High speed for fast chunk loading
            if (flightModeEnabled) 90 else 60
        } else {
            // Cellular: Responsive chunk loading under 180ms ping
            if (flightModeEnabled) 40 else 25
        }
    }

    private fun computeRelayChunkConcurrency(
        cellularRelay: Boolean,
        flightModeEnabled: Boolean
    ): Triple<Int, Int, Int> {
        return if (cellularRelay) {
            if (flightModeEnabled) {
                Triple(4, 6, 4) // generate, load, send
            } else {
                Triple(3, 4, 3)
            }
        } else {
            // Wi-Fi: High-throughput async chunk loading pipeline
            if (flightModeEnabled) {
                Triple(6, 10, 6)
            } else {
                Triple(4, 6, 4)
            }
        }
    }

    private fun computeRelayChunkPipelineRates(
        chunkSendRate: Int,
        flightModeEnabled: Boolean
    ): Pair<Int, Int> {
        return if (flightModeEnabled) {
            Pair(
                (chunkSendRate * 1.5).toInt().coerceIn(24, 300),
                (chunkSendRate * 2.0).toInt().coerceIn(36, 400)
            )
        } else {
            Pair(
                (chunkSendRate * 1.2).toInt().coerceIn(16, 200),
                (chunkSendRate * 1.5).toInt().coerceIn(24, 300)
            )
        }
    }

    // Relay runtime tuning applied at server start. Java ping is dominated by bridge
    // buffer sizes in RelayManager — see RelayManager KDoc before changing compression.
    private fun applyRelayReadyRuntimeProfile(
        serverDir: File,
        onOutput: (String) -> Unit
    ) {
        val props = ServerPropertiesHelper.readProperties(serverDir)

        val flightModeEnabled = runBlocking { AppPreferencesStore.isFlightModeEnabledFlow(context).first() }
        
        val currentCompression = props.getProperty(
            "network-compression-threshold",
            ServerPropertiesHelper.RELAY_READY_COMPRESSION_THRESHOLD.toString()
        ).toIntOrNull()
        val currentEntityBroadcast = props.getProperty(
            "entity-broadcast-range-percentage",
            ServerPropertiesHelper.RELAY_READY_ENTITY_BROADCAST_PERCENT.toString()
        ).toIntOrNull()
        val currentAllowFlight = props.getProperty("allow-flight", "false").toBoolean()

        var tunedAllowFlight = currentAllowFlight

        if (flightModeEnabled && !currentAllowFlight) {
            tunedAllowFlight = true
            onOutput("[PocketCraft] Flight Mode active: enabling allow-flight.")
        }

        val tunedCompression = ServerPropertiesHelper.RELAY_READY_COMPRESSION_THRESHOLD
        val tunedEntityBroadcast = when {
            currentEntityBroadcast == null -> 40
            currentEntityBroadcast <= 0 -> 40
            else -> currentEntityBroadcast.coerceIn(10, 40)
        }

        var changed = false
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
        if (props.getProperty("use-native-transport") != "false") {
            props["use-native-transport"] = "false"
            changed = true
        }

        val desiredView = props.getProperty(ServerPropertiesHelper.DESIRED_VIEW_DISTANCE_KEY)?.toIntOrNull()
            ?: props.getProperty("view-distance")?.toIntOrNull()
            ?: ServerPropertiesHelper.DEFAULT_VIEW_DISTANCE
        val desiredSimulation = props.getProperty(ServerPropertiesHelper.DESIRED_SIMULATION_DISTANCE_KEY)?.toIntOrNull()
            ?: props.getProperty("simulation-distance")?.toIntOrNull()
            ?: ServerPropertiesHelper.DEFAULT_SIMULATION_DISTANCE

        // Preserve the configured distances. Relay latency is handled by bounded
        // socket queues and Paper's chunk send budget, not by shrinking the world.
        val tunedView = desiredView.coerceIn(3, 32)
        val tunedSimulation = desiredSimulation.coerceIn(3, 32)

        if (props.getProperty("view-distance")?.toIntOrNull() != tunedView) {
            props["view-distance"] = tunedView.toString()
            changed = true
        }
        if (props.getProperty("simulation-distance")?.toIntOrNull() != tunedSimulation) {
            props["simulation-distance"] = tunedSimulation.toString()
            changed = true
        }
        if (props.getProperty(ServerPropertiesHelper.DESIRED_VIEW_DISTANCE_KEY)?.toIntOrNull() != desiredView) {
            props[ServerPropertiesHelper.DESIRED_VIEW_DISTANCE_KEY] = desiredView.toString()
            changed = true
        }
        if (props.getProperty(ServerPropertiesHelper.DESIRED_SIMULATION_DISTANCE_KEY)?.toIntOrNull() != desiredSimulation) {
            props[ServerPropertiesHelper.DESIRED_SIMULATION_DISTANCE_KEY] = desiredSimulation.toString()
            changed = true
        }

        if (changed) {
            ServerPropertiesHelper.saveProperties(serverDir, props)
            onOutput("[PocketCraft] Internet relay profile applied.")
        }
        onOutput(
            "[PocketCraft] Relay runtime profile: compression=$tunedCompression, view=$tunedView (desired=$desiredView), simulation=$tunedSimulation (desired=$desiredSimulation), entity-range=$tunedEntityBroadcast%"
        )
        val viewDistance = props.getProperty("view-distance")?.toIntOrNull()
            ?: ServerPropertiesHelper.DEFAULT_VIEW_DISTANCE
        val chunkBudget = computeRelayChunkSendBudget(
            cellularRelay = NetworkUtils.isCellular(context),
            viewDistance = viewDistance,
            flightModeEnabled = flightModeEnabled
        )
        onOutput("[PocketCraft] Relay chunk send budget: $chunkBudget/s (view=$viewDistance, reserves uplink for ping)")
    }

    private fun applyRelayReadyPaperGlobalConfig(
        serverDir: File,
        onOutput: (String) -> Unit
    ) {
        val configDir = File(serverDir, "config").also { it.mkdirs() }
        val paperGlobal = File(configDir, "paper-global.yml")
        val original = runCatching { paperGlobal.readText() }.getOrDefault("")
        val cellularRelay = NetworkUtils.isCellular(context)
        val flightModeEnabled = runBlocking { AppPreferencesStore.isFlightModeEnabledFlow(context).first() }
        val props = ServerPropertiesHelper.readProperties(serverDir, persistDefaults = false)
        val viewDistance = props.getProperty("view-distance")?.toIntOrNull()
            ?: ServerPropertiesHelper.DEFAULT_VIEW_DISTANCE
        val chunkSendRate = computeRelayChunkSendBudget(cellularRelay, viewDistance, flightModeEnabled)
        val (chunkGenerateRate, chunkLoadRate) = computeRelayChunkPipelineRates(chunkSendRate, flightModeEnabled)
        val (concurrentGenerates, concurrentLoads, concurrentSends) =
            computeRelayChunkConcurrency(cellularRelay, flightModeEnabled)
        val loadingPriority = "5"
        val profileLabel = if (flightModeEnabled) "flight" else "walking"

        var updated = original

        // Clean up legacy chunk-loading paths if they exist
        updated = removeYamlPathKey(updated, listOf("chunk-loading"), "player-max-chunk-generate-rate")
        updated = removeYamlPathKey(updated, listOf("chunk-loading"), "player-max-chunk-load-rate")
        updated = removeYamlPathKey(updated, listOf("chunk-loading"), "player-max-chunk-send-rate")
        updated = removeYamlPathKey(updated, listOf("chunk-loading"), "target-player-chunk-send-rate")
        updated = removeYamlPathKey(updated, listOf("chunk-loading"), "player-max-concurrent-sends")
        updated = removeYamlPathKey(updated, listOf("chunk-loading-basic"), "target-player-chunk-send-rate")
        updated = removeYamlPathKey(updated, listOf("chunk-loading-advanced"), "player-max-concurrent-loads")

        // Relay hosting: cap sustained chunk bandwidth so keepalives are not queued on the phone uplink.
        updated = ensureYamlPathValue(updated, listOf("chunk-loading-basic"), "player-max-chunk-generate-rate", chunkGenerateRate.toString())
        updated = ensureYamlPathValue(updated, listOf("chunk-loading-basic"), "player-max-chunk-load-rate", chunkLoadRate.toString())
        updated = ensureYamlPathValue(updated, listOf("chunk-loading-basic"), "player-max-chunk-send-rate", chunkSendRate.toString())
        updated = ensureYamlPathValue(updated, listOf("chunk-loading-basic"), "target-player-chunk-send-rate", chunkSendRate.toString())

        // Geyser reports ~0ms loopback ping; auto-config ramps send rate and destroys relay latency.
        updated = ensureYamlSectionValue(updated, "chunk-loading-advanced", "auto-config-send-distance", "false")
        updated = ensureYamlSectionValue(updated, "chunk-loading-advanced", "player-loading-priority-override", loadingPriority)
        updated = ensureYamlSectionValue(updated, "chunk-loading-advanced", "player-max-concurrent-chunk-generates", concurrentGenerates.toString())
        updated = ensureYamlSectionValue(updated, "chunk-loading-advanced", "player-max-concurrent-chunk-loads", concurrentLoads.toString())
        updated = ensureYamlSectionValue(updated, "chunk-loading-advanced", "player-max-concurrent-chunk-sends", concurrentSends.toString())

        val cores = Runtime.getRuntime().availableProcessors()
        val threads = (cores / 2).coerceIn(2, 3)

        updated = removeYamlPathKey(updated, listOf("misc"), "io-threads")
        updated = removeYamlPathKey(updated, listOf("misc"), "worker-threads")
        updated = ensureYamlSectionValue(updated, "chunk-system", "io-threads", threads.toString())
        updated = ensureYamlSectionValue(updated, "chunk-system", "worker-threads", threads.toString())
        updated = ensureYamlSectionValue(updated, "misc", "max-joins-per-tick", "3")

        // Disable bundled Spark profiler (fails to load native libraries on Android)
        updated = ensureYamlSectionValue(updated, "spark", "enabled", "false")
        updated = ensureYamlSectionValue(updated, "spark", "enable-immediately", "false")

        // Disable Timings — not needed on Android, saves CPU and IO per tick.
        updated = ensureYamlPathValue(updated, listOf("timings"), "enabled", "false")
        updated = ensureYamlPathValue(updated, listOf("timings"), "really-enabled", "false")
        updated = ensureYamlPathValue(updated, listOf("timings"), "server-name-privacy", "true")

        // Keep-alive: extend timeout so high-latency relay players aren't kicked,
        // and ensure keep-alives are sent on time even under chunk load.
        updated = ensureYamlSectionValue(updated, "misc", "keep-alive-timeout", "60")

        // Disable updater and metrics submission checks to prevent slow network lookup stalls on startup
        updated = ensureYamlPathValue(updated, listOf("updater"), "updater-status", "none")
        updated = ensureYamlPathValue(updated, listOf("updater"), "submit-metrics-status", "none")

        if (updated != original) {
            paperGlobal.writeText(updated)
            onOutput("[PocketCraft] Paper global tuning applied ($profileLabel): $chunkSendRate chunk/s send (gen=$chunkGenerateRate, load=$chunkLoadRate, view=$viewDistance), concurrent=$concurrentSends. Disabled update/metrics check.")
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
        updated = ensureYamlPathValue(updated, listOf("settings"), "user-suggest-updater", "false")

        // Optimize entity activation ranges to save tick CPU
        updated = ensureYamlPathValue(updated, listOf("world-settings", "default", "entity-activation-range"), "animals", "12")
        updated = ensureYamlPathValue(updated, listOf("world-settings", "default", "entity-activation-range"), "monsters", "16")
        updated = ensureYamlPathValue(updated, listOf("world-settings", "default", "entity-activation-range"), "raiders", "24")
        updated = ensureYamlPathValue(updated, listOf("world-settings", "default", "entity-activation-range"), "misc", "4")
        updated = ensureYamlPathValue(updated, listOf("world-settings", "default", "entity-activation-range"), "tick-inactive-villagers", "false")

        // Optimize entity tracking ranges to save bandwith and cpu
        updated = ensureYamlPathValue(updated, listOf("world-settings", "default", "entity-tracking-range"), "players", "48")
        updated = ensureYamlPathValue(updated, listOf("world-settings", "default", "entity-tracking-range"), "animals", "24")
        updated = ensureYamlPathValue(updated, listOf("world-settings", "default", "entity-tracking-range"), "monsters", "32")
        updated = ensureYamlPathValue(updated, listOf("world-settings", "default", "entity-tracking-range"), "misc", "16")

        if (updated != original) {
            spigotFile.writeText(updated)
            onOutput("[PocketCraft] Spigot optimizations applied: view distance overrides cleared + low-latency entity tracking/activation.")
        }
    }

    private fun applyRelayReadyPaperWorldDefaults(
        serverDir: File,
        onOutput: (String) -> Unit
    ) {
        val configDir = File(serverDir, "config").also { it.mkdirs() }
        val paperWorldDefaults = File(configDir, "paper-world-defaults.yml")
        val original = runCatching { paperWorldDefaults.readText() }.getOrDefault("")

        val sanitized = original
            .replace("validatenearbypoi", "validate-nearby-poi")
            .replace("secondarypoisensor", "secondary-poi-sensor")

        var updated = sanitized

        // paper-world-defaults.yml uses top-level sections. Nesting these under a
        // synthetic "world-defaults" key makes Paper ignore every optimization.
        updated = removeYamlTopLevelSection(updated, "world-defaults")
        updated = ensureYamlPathValue(updated, listOf("chunks"), "delay-chunk-unloads-by", "10s")
        updated = ensureYamlPathValue(updated, listOf("chunks"), "max-auto-save-chunks-per-tick", "4")
        updated = ensureYamlPathValue(updated, listOf("chunks"), "prevent-moving-into-unloaded-chunks", "true")
        updated = ensureYamlPathValue(updated, listOf("collisions"), "max-entity-collisions", "2")

        // Entity save limits to reduce chunk I/O overhead
        updated = ensureYamlPathValue(updated, listOf("chunks", "entity-per-chunk-save-limit"), "arrow", "16")
        updated = ensureYamlPathValue(updated, listOf("chunks", "entity-per-chunk-save-limit"), "ender_pearl", "8")
        updated = ensureYamlPathValue(updated, listOf("chunks", "entity-per-chunk-save-limit"), "experience_orb", "8")
        updated = ensureYamlPathValue(updated, listOf("chunks", "entity-per-chunk-save-limit"), "fireball", "8")
        updated = ensureYamlPathValue(updated, listOf("chunks", "entity-per-chunk-save-limit"), "small_fireball", "8")
        updated = ensureYamlPathValue(updated, listOf("chunks", "entity-per-chunk-save-limit"), "snowball", "8")

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

    private fun removeYamlTopLevelSection(original: String, section: String): String {
        val lines = original.split('\n').toMutableList()
        val start = lines.indexOfFirst { leadingYamlIndent(it) == 0 && it.trim() == "$section:" }
        if (start == -1) return original
        var end = lines.size
        for (index in (start + 1) until lines.size) {
            val line = lines[index]
            if (line.isBlank() || line.trimStart().startsWith("#")) continue
            if (leadingYamlIndent(line) == 0) {
                end = index
                break
            }
        }
        lines.subList(start, end).clear()
        return lines.joinToString("\n").trimEnd() + "\n"
    }

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
        val marker = File(jreDir, ".chmod_applied_v1")
        if (marker.exists()) {
            android.util.Log.d("ServerLauncher", "Skipping JRE chmod — already applied for ${runtime.displayName}")
            return
        }
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
        runCatching { marker.writeText(runtime.displayName) }
        android.util.Log.d("ServerLauncher", "Finished chmod on jre-runtime (Android 10 compat)")
    }

    private fun extractAndPatchJnaLibrary(paperJarPath: String, serverDir: File, shimDir: File): Boolean {
        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        val packagedJna = File(nativeLibDir, "libjnidispatch.so")
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
        if (patched) {
            return true
        }

        if (!packagedJna.exists()) {
            return false
        }

        val dest = File(shimDir, "libjnidispatch.so")
        val stampFile = File(shimDir, "libjnidispatch.meta")
        val expectedStamp = "packaged|${packagedJna.length()}|${packagedJna.lastModified()}"
        val currentStamp = runCatching { stampFile.readText(Charsets.UTF_8).trim() }.getOrDefault("")
        if (currentStamp == expectedStamp && dest.exists()) {
            return false
        }
        packagedJna.copyTo(dest, overwrite = true)
        dest.setExecutable(true)
        stampFile.writeText(expectedStamp, Charsets.UTF_8)
        return true
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
            ),
            // __xpg_strerror_r -> strerror_r + 6 zero pads
            "__xpg_strerror_r".toByteArray() to byteArrayOf(
                's'.code.toByte(), 't'.code.toByte(), 'r'.code.toByte(),
                'e'.code.toByte(), 'r'.code.toByte(), 'r'.code.toByte(),
                'o'.code.toByte(), 'r'.code.toByte(), '_'.code.toByte(),
                'r'.code.toByte(), 0, 0, 0, 0, 0, 0
            ),
            // __strdup -> strdup + 2 zero pads
            "__strdup".toByteArray() to byteArrayOf(
                's'.code.toByte(), 't'.code.toByte(), 'r'.code.toByte(),
                'd'.code.toByte(), 'u'.code.toByte(), 'p'.code.toByte(),
                0, 0
            ),
            // __errno_location -> __errno + 9 zero pads
            "__errno_location".toByteArray() to byteArrayOf(
                '_'.code.toByte(), '_'.code.toByte(), 'e'.code.toByte(),
                'r'.code.toByte(), 'r'.code.toByte(), 'n'.code.toByte(),
                'o'.code.toByte(), 0, 0, 0, 0, 0, 0, 0, 0, 0
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

    private fun validateAndRecoverLevelDat(serverDir: File, props: java.util.Properties, onOutput: (String) -> Unit) {
        val levelName = props.getProperty("level-name", "world")
        val worldDir = File(serverDir, levelName)
        val levelDat = File(worldDir, "level.dat")
        val levelDatOld = File(worldDir, "level.dat_old")

        if (!levelDat.exists()) {
            return
        }

        fun isValidGzipFile(file: File): Boolean {
            if (!file.exists() || file.length() == 0L) return false
            return try {
                java.util.zip.GZIPInputStream(file.inputStream()).use { gzip ->
                    gzip.read()
                    true
                }
            } catch (e: Exception) {
                false
            }
        }

        if (isValidGzipFile(levelDat)) {
            return
        }

        onOutput("[PocketCraft] ALERT: Detected corrupted level.dat! Length: ${levelDat.length()} bytes.")

        if (isValidGzipFile(levelDatOld)) {
            onOutput("[PocketCraft] Attempting to restore level.dat from level.dat_old...")
            try {
                val corruptBackup = File(worldDir, "level.dat.corrupt_${System.currentTimeMillis()}")
                levelDat.renameTo(corruptBackup)
                levelDatOld.copyTo(levelDat, overwrite = true)
                onOutput("[PocketCraft] Success: Restored level.dat from backup.")
                return
            } catch (e: Exception) {
                onOutput("[PocketCraft] ERROR: Failed to restore level.dat from backup: ${e.message}")
            }
        }

        onOutput("[PocketCraft] Both level.dat and level.dat_old are corrupt/missing. Moving them aside to allow server to boot...")
        val timestamp = System.currentTimeMillis()
        if (levelDat.exists()) {
            val movedLevelDat = File(worldDir, "level.dat.corrupt_$timestamp")
            levelDat.renameTo(movedLevelDat)
            onOutput("[PocketCraft] Moved corrupt level.dat to ${movedLevelDat.name}")
        }
        if (levelDatOld.exists()) {
            val movedLevelDatOld = File(worldDir, "level.dat_old.corrupt_$timestamp")
            levelDatOld.renameTo(movedLevelDatOld)
            onOutput("[PocketCraft] Moved corrupt level.dat_old to ${movedLevelDatOld.name}")
        }
    }
}

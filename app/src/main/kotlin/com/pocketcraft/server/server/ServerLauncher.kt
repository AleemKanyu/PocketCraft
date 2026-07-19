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
        val brand = Build.BRAND.orEmpty().lowercase(Locale.US)
        val model = Build.MODEL.orEmpty().lowercase(Locale.US)
        val device = Build.DEVICE.orEmpty().lowercase(Locale.US)
        val product = Build.PRODUCT.orEmpty().lowercase(Locale.US)
        val fingerprint = Build.FINGERPRINT.orEmpty().lowercase(Locale.US)
        val xiaomiMarkers = listOf(manufacturer, brand, model, device, product, fingerprint)
        val isXiaomiFamily = xiaomiMarkers.any { value ->
            value.contains("xiaomi") || value.contains("redmi") || value.contains("poco")
        }
        val isGalaxyA12Family = manufacturer.contains("samsung") && listOf(model, device, product).any { value ->
            value.contains("a12") || value.contains("sm-a125") || value.contains("sm-a127")
        }
        // Galaxy M13 (SM-M135/SM-M136/SM-M137) silently stalls during in-process JVM
        // initialisation on Android 14 — force external JVM to avoid the startup timeout.
        val isGalaxyM13Family = manufacturer.contains("samsung") && listOf(model, device, product).any { value ->
            value.contains("m13") || value.contains("sm-m135") ||
            value.contains("sm-m136") || value.contains("sm-m137")
        }
        // Xiaomi/Redmi/POCO devices on Android 14+ have shown silent stalls at
        // "Preparing spawn area" while using the in-process JNI launcher.
        // Force the external JVM path on this family until the root cause is isolated.
        val isXiaomiAndroid14PlusFamily = isXiaomiFamily && Build.VERSION.SDK_INT >= 34
        val constrainedHeap = isGalaxyA12Family || totalRamMb <= 4096
        val targetHeapCap = when {
            isGalaxyA12Family -> minOf((availableRamMb * 0.52f).toInt(), 896)
            totalRamMb <= 3072 -> minOf((availableRamMb * 0.58f).toInt(), 768)
            totalRamMb <= 4096 -> minOf((availableRamMb * 0.62f).toInt(), 1024)
            else -> minOf((availableRamMb * 0.72f).toInt(), (totalRamMb * 0.90f).toInt())
        }
        val minHeapFloor = if (isGalaxyA12Family || totalRamMb <= 3072) 384 else 512
        val reason = when {
            isXiaomiAndroid14PlusFamily -> "Xiaomi/Redmi/POCO Android 14+ device detected. Using external JVM to avoid in-process startup stalls during world preparation."
            isGalaxyM13Family -> "Samsung Galaxy M13 detected. Using external JVM to prevent startup stall on Android 14."
            isGalaxyA12Family -> "Samsung Galaxy A12 low-memory profile active. Using safer heap limits to reduce short crash loops."
            constrainedHeap -> "Low-memory device profile active. Heap is capped to reduce background crash risk."
            else -> null
        }
        return DeviceStabilityProfile(
            forceExternalJvm = isGalaxyM13Family || isXiaomiAndroid14PlusFamily,
            constrainedHeap = constrainedHeap,
            maxHeapCapMb = targetHeapCap.coerceAtLeast(minHeapFloor),
            minHeapFloorMb = minHeapFloor,
            reason = reason
        )
    }

    companion object {
        @Volatile
        private var activeExternalProcess: Process? = null

        /** Cached result of noexec mount detection; null = not yet checked. */
        @Volatile
        private var noexecCacheResult: Boolean? = null

        /** Cached result of canonical Java binary execution safety; null = not yet checked. */
        @Volatile
        private var execSafeCache: Boolean? = null

        /**
         * Set to true when ALL writable directories (files/, code_cache/, external) are
         * noexec — meaning this device (e.g. Samsung Knox lockdown) prevents execution
         * of any user-placed binary. Used to show a device-specific error message.
         */
        @Volatile
        var allStorageNoexecDetected: Boolean = false
            private set

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
        
        if (levelDat.exists()) {
            try {
                val difficultyStr = props.getProperty("difficulty", "normal")
                com.pocketcraft.server.service.NBTParser.updateDifficultyInLevelDat(levelDat, difficultyStr)
            } catch (e: Exception) {
                onOutput("[PocketCraft] Failed to sync difficulty to level.dat: ${e.message}")
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
        applyRelayReadyFabricConfig(serverDirFile, serverType, onOutput)

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
            // For full and manual modes, default to a conservative initial heap size (512MB)
            // to avoid immediate startup OOM kills by the OS when launching.
            else -> 512
        }
        val maxRamMb = requestedMaxRamMb.coerceIn(deviceProfile.minHeapFloorMb, maxAllowedRam)
        val minRamMb = maxRamMb // Set initial heap equal to max heap to prevent runtime dynamic resizing pauses

        deviceProfile.reason?.let { reason ->
            onOutput("[PocketCraft] Stability mode enabled: $reason")
        }
        onOutput("[PocketCraft] JVM memory: mode=$ramModeFromProps, heap=${minRamMb}MB..${maxRamMb}MB, available=${availRam}MB, total=${totalRam}MB")

        // Use exec-safe java binary path — falls back to codeCacheDir copy on noexec devices.
        val javaBin = ensureExecSafeJavaBin(context, resolvedRuntime)
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
                        runtime = resolvedRuntime,
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
                            runtime = resolvedRuntime,
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
        // Use exec-safe path: on Samsung M13/A13x (Android 14) the files/ dir is noexec.
        // ensureExecSafeJavaBin() transparently falls back to a codeCacheDir copy if needed.
        val javaBin = ensureExecSafeJavaBin(context, runtime)
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
        runtime: JreExtractor.RuntimeSpec,
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
        onError: (String) -> Unit,
        isRetry: Boolean = false
    ): Int {
        val javaBin = ensureExecSafeJavaBin(context, runtime)
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
        val totalRam = getTotalRamMb(context)
        val nettyThreads = (cores / 2).coerceIn(2, if (totalRam >= 5000) 6 else 4)

        // Scale G1 heap region size with available RAM to avoid region fragmentation stalls.
        // 8MB regions are appropriate for heaps 2GB+; smaller heaps stay at 4MB.
        val g1RegionSizeMb = if (maxRamMb >= 2048) 8 else 4
        // On high-RAM devices pre-touch heap pages at JVM startup to eliminate page-fault
        // latency spikes during GC. Only enable when we have >5GB total RAM so startup cost
        // doesn't hurt low-memory devices.
        val gcFlags = buildList {
            add("-XX:G1NewSizePercent=30")
            add("-XX:G1MaxNewSizePercent=40")
            add("-XX:G1ReservePercent=20")
            add("-XX:InitiatingHeapOccupancyPercent=15")
            add("-XX:G1HeapWastePercent=5")
            add("-XX:G1MixedGCCountTarget=4")
            add("-XX:G1MixedGCLiveThresholdPercent=90")
            add("-XX:G1RSetUpdatingPauseTimePercent=5")
            add("-XX:G1HeapRegionSize=${g1RegionSizeMb}m")
            add("-XX:SurvivorRatio=32")
            add("-XX:MaxTenuringThreshold=1")
            add("-XX:-AlwaysPreTouch")
        }

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
            "-XX:+UseStringDeduplication",
            "-XX:+UseG1GC",
            "-XX:+ParallelRefProcEnabled",
            // 40ms GC pause target ensures garbage collection pauses do not cause ping spikes
            "-XX:MaxGCPauseMillis=40",
            "-XX:+DisableExplicitGC",
        ).apply {
            addAll(gcFlags)
            addAll(buildList {
                add("-XX:-UsePerfData")
                add("-XX:-UseContainerSupport")
                add("-XX:ErrorFile=$errorFilePattern")
                // maxOrder=8 → max pooled buffer = 256KB * 2^8 = 2MB (was 4MB with order=9).
                // Smaller max allocation reduces swap page-in stalls on the Netty I/O path.
                add("-Dio.netty.allocator.maxOrder=8")
                // Tighten recycler pools: the default 262144 cap was holding ~100MB of
                // pooled byte buffers in swap, causing page-in latency on packet sends.
                add("-Dio.netty.recycler.maxCapacity=4096")
                add("-Dio.netty.recycler.maxCapacityPerThread=256")
                add("-Dio.netty.recycler.linkCapacity=256")
                add("-Dio.netty.allocator.type=pooled")
                add("-Dio.netty.leakDetection.level=disabled")
                add("-Dio.netty.noPreferDirect=false")
                add("-Dio.netty.noUnsafe=false")
                add("-Djdk.lang.Process.launchMechanism=FORK")
            })
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
 
        val startTime = System.currentTimeMillis()
        val processResult = runCatching {
            ProcessBuilder(command)
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
        }

        if (processResult.isFailure) {
            val error = processResult.exceptionOrNull()
            if (!isRetry && !javaBin.name.contains("libjava_exec")) {
                onOutput("[PocketCraft] Out-of-process JVM execution failed to start: ${error?.message}. Retrying with Knox/noexec fallback...")
                noexecCacheResult = true
                execSafeCache = false
                return launchExternalJvm(
                    runtime = runtime,
                    jrePath = jrePath,
                    launchTargetPath = launchTargetPath,
                    launchMode = launchMode,
                    serverDir = serverDir,
                    tmpDir = tmpDir,
                    shimDir = shimDir,
                    minRamMb = minRamMb,
                    maxRamMb = maxRamMb,
                    worldName = worldName,
                    onOutput = onOutput,
                    onError = onError,
                    isRetry = true
                )
            }
            throw error ?: Exception("Process start failed")
        }

        val process = processResult.getOrThrow()
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

        val duration = System.currentTimeMillis() - startTime
        if (!isRetry && 
            !javaBin.name.contains("libjava_exec") && 
            (exitCode == 126 || exitCode == 127 || (exitCode != 0 && duration < 2500))
        ) {
            onOutput("[PocketCraft] Out-of-process JVM execution failed (code=$exitCode, duration=${duration}ms). Retrying with Knox/noexec fallback...")
            noexecCacheResult = true
            execSafeCache = false
            return launchExternalJvm(
                runtime = runtime,
                jrePath = jrePath,
                launchTargetPath = launchTargetPath,
                launchMode = launchMode,
                serverDir = serverDir,
                tmpDir = tmpDir,
                shimDir = shimDir,
                minRamMb = minRamMb,
                maxRamMb = maxRamMb,
                worldName = worldName,
                onOutput = onOutput,
                onError = onError,
                isRetry = true
            )
        }

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
        if (flightModeEnabled) {
            val base = if (cellularRelay) 20 else 24
            val viewScale = (7.0 / vd).pow(0.7).coerceIn(0.45, 1.0)
            return (base * viewScale + 2).toInt().coerceIn(16, 26)
        }
        val base = if (cellularRelay) 17 else 22
        val viewScale = (7.0 / vd).pow(0.65).coerceIn(0.55, 1.0)
        return (base * viewScale).toInt().coerceIn(13, 20)
    }

    private fun computeRelayChunkConcurrency(
        cellularRelay: Boolean,
        flightModeEnabled: Boolean
    ): Triple<Int, Int, Int> {
        // Returns Triple(concurrentGenerates, concurrentLoads, concurrentSends)
        return if (flightModeEnabled) {
            if (cellularRelay) Triple(3, 4, 1) else Triple(4, 5, 1)
        } else {
            if (cellularRelay) Triple(3, 4, 1) else Triple(4, 5, 1)
        }
    }

    private fun computeRelayChunkPipelineRates(
        chunkSendRate: Int,
        flightModeEnabled: Boolean
    ): Pair<Int, Int> {
        return if (flightModeEnabled) {
            Pair(
                (chunkSendRate * 4).coerceIn(40, 78),
                (chunkSendRate * 5).coerceIn(52, 96)
            )
        } else {
            Pair(
                (chunkSendRate * 4).coerceIn(42, 72),
                (chunkSendRate * 6).coerceIn(56, 90)
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
        val tunedEntityBroadcast = currentEntityBroadcast?.coerceIn(70, 100) ?: ServerPropertiesHelper.RELAY_READY_ENTITY_BROADCAST_PERCENT

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

        // Preserve gameplay settings exactly as the user configured them. Ping optimization
        // must come from pacing and transport, not from silently shrinking distances.
        val tunedView = desiredView
        val tunedSimulation = desiredSimulation

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
            cellularRelay = com.pocketcraft.server.util.NetworkUtils.isCellular(context),
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
        val cellularRelay = com.pocketcraft.server.util.NetworkUtils.isCellular(context)
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

        // Capping to 2 threads prevents CPU core oversaturation, leaving cores open for main thread, GC, and bridge.
        val threads = 2

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

        // Keep-alive: extend timeout so high-latency relay players aren't kicked.
        // Use a moderate 10-tick interval (500ms): lower than the Paper default so idle
        // ping reporting has less scheduling jitter, but not so low that keepalives flood
        // the uplink during chunk bursts like the old 1-tick setting did.
        updated = ensureYamlSectionValue(updated, "misc", "keep-alive-timeout", "60")
        updated = ensureYamlSectionValue(updated, "misc", "keep-alive-interval", "10")
        // Compression level 4 is a better latency/CPU tradeoff than 6 for mobile servers.
        // Higher levels add measurable CPU overhead on the server tick thread per packet.
        updated = ensureYamlSectionValue(updated, "misc", "compression-level", "4")

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
        val props = ServerPropertiesHelper.readProperties(serverDir, persistDefaults = false)
        val activeView = props.getProperty("view-distance")?.toIntOrNull()
            ?: ServerPropertiesHelper.DEFAULT_VIEW_DISTANCE
        val activeSimulation = props.getProperty("simulation-distance")?.toIntOrNull()
            ?: ServerPropertiesHelper.DEFAULT_SIMULATION_DISTANCE
        val viewTrackingBlocks = activeView.coerceIn(3, 32) * 16
        val simulationTrackingBlocks = activeSimulation.coerceIn(3, 32) * 16

        var updated = original
        // Spigot can override both distances; keep them on "default" so the current
        // server.properties value is always the one Paper actually uses.
        updated = ensureYamlPathValue(updated, listOf("world-settings", "default"), "view-distance", "default")
        updated = ensureYamlPathValue(updated, listOf("world-settings", "default"), "simulation-distance", "default")
        updated = ensureYamlPathValue(updated, listOf("world-settings", "default"), "mob-spawn-range", activeSimulation.coerceIn(3, 32).toString())
        updated = ensureYamlPathValue(updated, listOf("settings"), "moved-too-quickly-multiplier", "1000.0")
        updated = ensureYamlPathValue(updated, listOf("settings"), "moved-wrongly-threshold", "1000.0")
        updated = ensureYamlPathValue(updated, listOf("settings"), "user-suggest-updater", "false")

        // Match Spigot activation and tracking limits to the configured world distances
        // so entities stay active and visible across the full simulation range.
        // Use optimized entity activation ranges to save CPU overhead and prevent lag spikes
        updated = ensureYamlPathValue(updated, listOf("world-settings", "default", "entity-activation-range"), "animals", "32")
        updated = ensureYamlPathValue(updated, listOf("world-settings", "default", "entity-activation-range"), "monsters", "32")
        updated = ensureYamlPathValue(updated, listOf("world-settings", "default", "entity-activation-range"), "raiders", "48")
        updated = ensureYamlPathValue(updated, listOf("world-settings", "default", "entity-activation-range"), "misc", "16")
        updated = ensureYamlPathValue(updated, listOf("world-settings", "default", "entity-activation-range"), "water", "16")
        updated = ensureYamlPathValue(updated, listOf("world-settings", "default", "entity-activation-range"), "villagers", "32")
        updated = ensureYamlPathValue(updated, listOf("world-settings", "default", "entity-activation-range"), "flying-monsters", "32")
        updated = ensureYamlPathValue(updated, listOf("world-settings", "default", "entity-activation-range"), "tick-inactive-villagers", "false")

        updated = ensureYamlPathValue(updated, listOf("world-settings", "default", "entity-tracking-range"), "players", viewTrackingBlocks.toString())
        updated = ensureYamlPathValue(updated, listOf("world-settings", "default", "entity-tracking-range"), "animals", "48")
        updated = ensureYamlPathValue(updated, listOf("world-settings", "default", "entity-tracking-range"), "monsters", "48")
        updated = ensureYamlPathValue(updated, listOf("world-settings", "default", "entity-tracking-range"), "misc", "32")

        if (updated != original) {
            spigotFile.writeText(updated)
            onOutput("[PocketCraft] Spigot entity ranges synced to active view=$activeView and simulation=$activeSimulation.")
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
        // On high-RAM devices keep chunks warm much longer to prevent re-load flicker.
        // 30s gives players time to backtrack without chunks being evicted.
        val totalRamMb = com.pocketcraft.server.util.RamUtils.getAvailableRamMb(context) +
            (Runtime.getRuntime().totalMemory() / 1024 / 1024).toInt()
        val chunkUnloadDelay = if (totalRamMb >= 4000) "30s" else "10s"
        // More chunks saved per tick on high-RAM = spread I/O evenly, no big auto-save spike.
        val autoSavePerTick = if (totalRamMb >= 4000) 8 else 4

        updated = removeYamlTopLevelSection(updated, "world-defaults")
        updated = ensureYamlPathValue(updated, listOf("chunks"), "delay-chunk-unloads-by", chunkUnloadDelay)
        updated = ensureYamlPathValue(updated, listOf("chunks"), "max-auto-save-chunks-per-tick", autoSavePerTick.toString())
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

    /**
     * Applies Fabric/Vanilla-specific relay optimizations that have no equivalent in
     * paper-global.yml.  Since Fabric exposes very few config knobs, we tune the two
     * levers that matter most on Android:
     *
     * 1. `fabric-server-launcher.properties` – controls worker and IO thread counts used
     *    by Fabric's built-in chunk executor.  Capping at 2 keeps the JVM off the main
     *    thread and GC off-core, matching the Paper chunk-system tuning we apply above.
     *
     * 2. `server.properties` extras – max-tick-time, rate-limit, and op-permission-level
     *    mirror the values Paper benefits from via paper-global.yml.  On Vanilla/Fabric
     *    these must live in server.properties instead.
     */
    private fun applyRelayReadyFabricConfig(
        serverDir: File,
        serverType: com.pocketcraft.server.data.model.ServerType,
        onOutput: (String) -> Unit
    ) {
        if (serverType != com.pocketcraft.server.data.model.ServerType.FABRIC) return

        // --- fabric-server-launcher.properties ----------------------------------
        // Fabric reads this file to configure the built-in chunk pipeline thread pool.
        // 2 workers keeps chunk generation off the main thread without over-committing
        // the small core count available on mid-range Android SoCs.
        val launcherProps = File(serverDir, "fabric-server-launcher.properties")
        val originalLauncher = runCatching { launcherProps.readText() }.getOrDefault("")
        val launcherLines = originalLauncher.lines().toMutableList()

        fun setLauncherProp(key: String, value: String) {
            val idx = launcherLines.indexOfFirst { it.trimStart().startsWith("$key=") }
            if (idx >= 0) {
                if (launcherLines[idx] != "$key=$value") launcherLines[idx] = "$key=$value"
            } else {
                launcherLines.add("$key=$value")
            }
        }

        // Disable Fabric's auto-open GUI — it tries to open a Swing window on Android.
        // gui=false is the only real property the Fabric server launcher reads here.
        setLauncherProp("gui", "false")

        val updatedLauncher = launcherLines.joinToString("\n").trimEnd() + "\n"
        if (updatedLauncher != originalLauncher) {
            runCatching { launcherProps.writeText(updatedLauncher) }
            onOutput("[PocketCraft] Fabric launcher config: gui=false applied.")
        }

        // --- server.properties extras (Fabric / Vanilla only) -------------------
        // Paper surfaces these through paper-global.yml; on Fabric they must go in
        // server.properties.  Only write props that aren't already at the right value.
        val props = ServerPropertiesHelper.readProperties(serverDir)
        var changed = false

        // Watchdog: disable the default 60-second hang detector.  On Android the main
        // thread legitimately pauses during GC / chunk I/O spikes, causing false kills.
        if (props.getProperty("max-tick-time") != "-1") {
            props["max-tick-time"] = "-1"
            changed = true
        }
        // Rate-limit: 0 = no packet flood protection (we handle this at the relay layer).
        if (props.getProperty("rate-limit") != "0") {
            props["rate-limit"] = "0"
            changed = true
        }
        // Op level 4: allows all commands, required for RCON ping/data-get to work.
        if (props.getProperty("op-permission-level") != "4") {
            props["op-permission-level"] = "4"
            changed = true
        }
        // Prevent Vanilla from sending a "Too many packets" kick during chunk bursts.
        if (props.getProperty("player-idle-timeout") == null) {
            props["player-idle-timeout"] = "0"
            changed = true
        }

        if (changed) {
            ServerPropertiesHelper.saveProperties(serverDir, props)
            onOutput("[PocketCraft] Fabric/Vanilla relay server.properties tuned (tick watchdog, rate-limit, op-level).")
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
        val javaBin = JreExtractor.getJavaBinary(context, runtime)

        // Always ensure the java binary is executable as a fast-path guard.
        // On Samsung Knox / Android 14, execute bits in files/ can be silently reset
        // after a cold boot, app update, or SELinux policy reload — even if chmod was
        // previously applied. If the binary is not executable, delete the marker so the
        // full chmod pass below is unconditionally re-applied.
        if (javaBin.exists() && !javaBin.canExecute()) {
            android.util.Log.w("ServerLauncher",
                "java binary lost execute bit (Samsung Knox/SELinux reset?) — forcing full re-chmod for ${runtime.displayName}")
            runCatching { marker.delete() }
        }

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

    /**
     * Returns true if the app's `files/` directory is on a `noexec`-mounted filesystem.
     *
     * On some Samsung Galaxy devices running Android 14 (e.g. M13/A13x with Knox),
     * `/data/data/<pkg>/files/` is mount-flagged `noexec`. `Os.chmod(0755)` succeeds
     * at the syscall level but the kernel still refuses `execve()` — producing
     * `Permission denied` (exit 127). We detect this by attempting to execute
     * a temporary script directly.
     */
    private fun isFilesdirNoexec(): Boolean {
        // Return cached result if available.
        noexecCacheResult?.let { return it }

        // Strategy: write a minimal shell script to files/, mark it executable,
        // then try to exec it via /system/bin/sh. If sh reports "Permission denied",
        // the directory is noexec. This is more reliable than parsing /proc/self/mountinfo
        // because Samsung Knox uses bind mounts that inherit noexec without listing it.
        val result = runCatching {
            val probe = java.io.File(normalizeAndroidPath(context.filesDir.absolutePath), ".noexec_probe")
            probe.writeText("#!/system/bin/sh\nexit 0\n")
            runCatching { android.system.Os.chmod(probe.absolutePath, 0x1ED) } // 0755
            val p = ProcessBuilder("/system/bin/sh", "-c",
                "'${probe.absolutePath}' 2>&1; echo \"rc:\$?\""
            ).redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            val timedOut = !p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)
            if (timedOut) p.destroyForcibly()
            probe.delete()
            // If output contains "Permission denied" or rc is non-zero due to exec failure, it's noexec.
            out.contains("Permission denied") || out.contains("cannot execute") || timedOut
        }.getOrDefault(false)

        noexecCacheResult = result
        android.util.Log.w("ServerLauncher",
            if (result) "Detected noexec on files/ — java binary will be copied to codeCacheDir"
            else "files/ dir is exec-safe (probe passed)"
        )
        return result
    }

    /**
     * Returns an exec-safe path to the java binary for the given runtime.
     *
     * On Samsung Galaxy M13/A13x (Android 14 with Knox), the entire /data/data/<pkg>/
     * partition can be mounted noexec — including both `files/` AND `code_cache/`.
     * We probe each candidate location in order and return the first one that can
     * actually execute a script:
     *   1. Canonical path (`files/jre-runtime/bin/java`) — fast path if exec-safe.
     *   2. Copy to `code_cache/jre-bin/<id>/java` — usually exec-safe on normal Android.
     *   3. Copy to `getExternalFilesDir("jre-bin")/<id>/java` — last resort.
     * If all three fail, `allStorageNoexecDetected` is set to true so callers can
     * surface a device-specific error (Samsung Knox restriction) to the user.
     */
    private fun ensureExecSafeJavaBin(
        context: Context,
        runtime: JreExtractor.RuntimeSpec
    ): File {
        val canonical = File(normalizeAndroidPath(JreExtractor.getJavaBinary(context, runtime).absolutePath))

        // 1. If we have already verified that canonical is exec-safe, use it.
        execSafeCache?.let { isSafe ->
            if (isSafe) {
                allStorageNoexecDetected = false
                return canonical
            }
        }

        // 2. If the canonical path exists, probe it directly.
        if (canonical.exists()) {
            val isSafe = runCatching {
                android.system.Os.chmod(canonical.absolutePath, 0x1ED) // 0755
                val p = ProcessBuilder("/system/bin/sh", "-c",
                    "'${canonical.absolutePath}' -Xshare:off -version 2>&1"
                ).redirectErrorStream(true).start()
                val out = p.inputStream.bufferedReader().readText()
                val finished = p.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)
                if (!finished) p.destroyForcibly()
                finished && p.exitValue() == 0 && out.contains("version", ignoreCase = true)
            }.getOrDefault(false)

            if (isSafe) {
                execSafeCache = true
                allStorageNoexecDetected = false
                return canonical
            } else {
                android.util.Log.w("ServerLauncher", "Canonical java binary failed execution probe (permission or ELF error). Triggering fallback.")
            }
        } else {
            // If the canonical path doesn't exist yet, we check the general noexec status.
            // If the files directory is generally exec-safe, we assume canonical will be safe once extracted.
            if (!isFilesdirNoexec()) {
                allStorageNoexecDetected = false
                return canonical
            }
        }

        // Try the bundled native library wrapper (libjava_exec.so) first.
        // Since it is extracted to nativeLibraryDir by the package manager, it is guaranteed
        // to be executable even on Knox-hardened devices where all writable directories are noexec.
        val nativeJavaBin = File(context.applicationInfo.nativeLibraryDir, "libjava_exec.so")
        if (nativeJavaBin.exists() && nativeJavaBin.canExecute()) {
            allStorageNoexecDetected = false
            android.util.Log.i("ServerLauncher", "Using bundled exec-safe java from nativeLibraryDir: ${nativeJavaBin.absolutePath}")
            return nativeJavaBin
        }

        // files/ is noexec — try candidate directories in priority order.
        val candidates = buildList {
            // 1. code_cache/ (ART JIT cache dir, usually exec-safe on stock Android)
            add(File(normalizeAndroidPath(context.codeCacheDir.absolutePath), "jre-bin/${runtime.id}"))
            // 2. External files dir (app-scoped, no permission needed on Android 10+)
            context.getExternalFilesDir("jre-bin/${runtime.id}")?.let { add(it) }
        }

        for (execDir in candidates) {
            runCatching { execDir.mkdirs() }
            val copy = File(execDir, "java")

            // Copy only if source changed (re-extraction) or copy missing.
            val needsCopy = !copy.exists()
                || copy.length() != canonical.length()
                || copy.lastModified() < canonical.lastModified()

            var copyOk = true
            if (needsCopy && canonical.exists()) {
                copyOk = runCatching {
                    canonical.inputStream().use { i -> copy.outputStream().use { o -> i.copyTo(o) } }
                    android.system.Os.chmod(copy.absolutePath, 0x1ED) // 0755
                }.onFailure {
                    android.util.Log.e("ServerLauncher", "Failed to copy java to ${execDir.absolutePath}: ${it.message}")
                }.isSuccess
            } else if (copy.exists()) {
                runCatching { android.system.Os.chmod(copy.absolutePath, 0x1ED) }
            }

            if (!copyOk || !copy.exists()) continue

            // Verify the copy is actually executable (probe with a quick exec test).
            val execSafe = runCatching {
                val p = ProcessBuilder("/system/bin/sh", "-c",
                    "'${copy.absolutePath}' -Xshare:off -version 2>&1 | head -c 256; echo \"rc:\$?\""
                ).redirectErrorStream(true).start()
                val out = p.inputStream.bufferedReader().readText()
                p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
                // Success if we get java version output; fail if Permission denied
                !out.contains("Permission denied") && !out.contains("cannot execute")
            }.getOrDefault(false)

            if (execSafe) {
                allStorageNoexecDetected = false
                android.util.Log.i("ServerLauncher",
                    "Using exec-safe java copy at: ${copy.absolutePath} (noexec workaround for ${runtime.displayName})")
                return copy
            } else {
                android.util.Log.w("ServerLauncher",
                    "${execDir.absolutePath} is also noexec — trying next candidate")
            }
        }

        // All candidate locations are noexec — device has full Knox storage lockdown.
        allStorageNoexecDetected = true
        android.util.Log.e("ServerLauncher",
            "ALL storage locations are noexec — Samsung Knox total lockdown detected. Cannot exec java binary.")
        return canonical // Return canonical so error surfaces from the actual exec attempt
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

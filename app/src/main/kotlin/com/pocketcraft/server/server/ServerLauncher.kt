package com.pocketcraft.server.server

import android.content.Context
import android.os.Build
import com.pocketcraft.server.NativeLauncher
import com.pocketcraft.server.service.PluginManager
import com.pocketcraft.server.service.ServerFileManager
import com.pocketcraft.server.service.ServerPropertiesHelper
import com.pocketcraft.server.setup.JreExtractor
import java.io.File
import java.io.InputStream
import android.app.ActivityManager
import com.pocketcraft.server.data.preferences.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class ServerLauncher(private val context: Context) {

    companion object {
        @Volatile
        private var activeExternalProcess: Process? = null

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
        onOutput : (String) -> Unit,
        onError  : (String) -> Unit,
        onStopped: (Int) -> Unit
    ) {
        if (!ServerFileManager.isServerJarReady(context, versionId)) {
            onError("Server JAR not found for $versionId"); return
        }

        ServerFileManager.prepareEula(context, versionId)
        ServerFileManager.prepareServerProperties(context, versionId)
        ServerFileManager.prepareRuntimeArtifacts(context, versionId)
        runBlocking(Dispatchers.IO) {
            PluginManager.ensureBedrockBridgePlugins(context, versionId).onFailure { error ->
                onOutput("[PocketCraft] Warning: Could not refresh Bedrock bridge plugins: ${error.message}")
            }
        }
        PluginManager.enforceBedrockBridgeLocalConfig(context, versionId)

        val jrePath   = JreExtractor.getJreDir(context).absolutePath
        val serverDirFile = ServerFileManager.getServerDir(context, versionId)
        val jarPath   = ServerFileManager.getServerJarFile(context, versionId).absolutePath
        val serverDir = serverDirFile.absolutePath
        val tmpDir    = File(context.filesDir, "runtime-tmp").also { it.mkdirs() }.absolutePath
        val totalRam = getTotalRamMb(context)
        val prefs = AppPreferences(context)
        val reservedForSystemMb = when {
            totalRam >= 8192 -> 1536
            totalRam >= 6144 -> 1024
            totalRam >= 4096 -> 768
            else -> 512
        }
        val hardSafeMaxMb = (totalRam - reservedForSystemMb).coerceAtLeast(768)
        val defaultSafeMaxMb = hardSafeMaxMb.coerceAtMost(3072)

        val (minRamMb, maxRamMb) = when (prefs.ramMode) {
            "full" -> {
                val max = defaultSafeMaxMb
                val min = (max * 0.5).toInt().coerceAtLeast(512)
                Pair(min, max)
            }
            "manual" -> {
                val requested = prefs.manualRamMb.coerceAtLeast(512)
                val max = requested.coerceAtMost(hardSafeMaxMb)
                val min = (max * 0.5).toInt().coerceAtLeast(512)
                if (requested > max) {
                    onOutput("[PocketCraft] RAM request capped to ${max}MB to keep Android stable.")
                }
                Pair(min, max)
            }
            else -> {
                val max = 512.coerceAtMost(defaultSafeMaxMb)
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

        val jnaBootPath = context.applicationInfo.nativeLibraryDir
        val jnaLibraryPath = "$jnaBootPath:${shimDir.absolutePath}"
 
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
            "-XX:-UsePerfData",
            "-XX:-UseContainerSupport",
            "-XX:ErrorFile=$errorFilePattern",
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

    private fun applyAdaptiveDistances(
        serverDir: File,
        totalRamMb: Int,
        onOutput: (String) -> Unit
    ) {
        val props = ServerPropertiesHelper.readProperties(serverDir)
        val worldName = props.getProperty("level-name", "world")
        val isNewWorld = !worldDirExists(serverDir, worldName)

        // For new worlds, use lower initial distances to speed up generation
        val viewDistance = if (isNewWorld) {
            resolveAdaptiveViewDistanceNew(totalRamMb)
        } else {
            resolveAdaptiveViewDistance(totalRamMb)
        }

        val simulationDistance = if (isNewWorld) {
            resolveAdaptiveSimulationDistanceNew(totalRamMb)
        } else {
            resolveAdaptiveSimulationDistance(totalRamMb)
        }

        props["view-distance"] = viewDistance.toString()
        props["simulation-distance"] = simulationDistance.toString()
        ServerPropertiesHelper.saveProperties(serverDir, props)

        val worldType = if (isNewWorld) "new world" else "existing world"
        onOutput(
            "[PocketCraft] Adaptive distance profile applied for ${totalRamMb}MB RAM ($worldType): view=$viewDistance, simulation=$simulationDistance"
        )
    }

    private fun worldDirExists(serverDir: File, worldName: String): Boolean {
        val candidates = listOf(
            File(serverDir, worldName),
            File(serverDir, "${worldName}_nether"),
            File(serverDir, "${worldName}_the_end")
        )
        return candidates.any { it.isDirectory && File(it, "level.dat").exists() }
    }

    private fun resolveAdaptiveViewDistanceNew(totalRamMb: Int): Int {
        // Lower initial view distance for new world generation
        return when {
            totalRamMb >= 7168 -> 16
            totalRamMb >= 6144 -> 12
            totalRamMb >= 4096 -> 8
            totalRamMb >= 3072 -> 6
            else -> 4
        }
    }

    private fun resolveAdaptiveSimulationDistanceNew(totalRamMb: Int): Int {
        // Lower initial simulation distance for new world generation
        return when {
            totalRamMb >= 7168 -> 12
            totalRamMb >= 6144 -> 8
            totalRamMb >= 4096 -> 6
            totalRamMb >= 3072 -> 4
            else -> 3
        }
    }

    private fun resolveAdaptiveViewDistance(totalRamMb: Int): Int {
        return when {
            totalRamMb >= 7168 -> 32
            totalRamMb >= 6144 -> 24
            totalRamMb >= 4096 -> 16
            totalRamMb >= 3072 -> 12
            else -> 8
        }
    }

    private fun resolveAdaptiveSimulationDistance(totalRamMb: Int): Int {
        return when {
            totalRamMb >= 7168 -> 24
            totalRamMb >= 6144 -> 16
            totalRamMb >= 4096 -> 12
            totalRamMb >= 3072 -> 8
            else -> 6
        }
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

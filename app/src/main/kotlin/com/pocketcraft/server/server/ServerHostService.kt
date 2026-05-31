package com.pocketcraft.server.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.pocketcraft.server.MainActivity
import com.pocketcraft.server.R
import com.pocketcraft.server.RelayManager
import com.pocketcraft.server.data.preferences.AppPreferences
import com.pocketcraft.server.server.ServerLauncher
import com.pocketcraft.server.service.ConsoleParser
import com.pocketcraft.server.service.PluginManager
import com.pocketcraft.server.service.ServerFileManager
import com.pocketcraft.server.service.ServerVersionMigrator
import com.pocketcraft.server.setup.JreExtractor
import java.io.File
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.ArrayDeque
import java.util.Properties
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.pocketcraft.server.data.preferences.AppPreferencesStore
import androidx.annotation.Keep

class ServerHostService : Service() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.pocketcraft.server.util.LocaleUtils.wrapContext(newBase))
    }

    private enum class ServerStage(val notificationText: String) {
        DOWNLOADING_SERVER("Downloading server..."),
        EXTRACTING_JRE("Preparing Java runtime..."),
        CHECKING_PLUGINS("Checking Bedrock bridge..."),
        STARTING_SERVER("Starting server... (30-60s)"),
        RUNNING("Server is running"),
        STOPPING("Stopping server...")
    }

    private var currentVersionId: String? = null
    private var currentWorldName: String? = null
    private var serverProcess: java.lang.Process? = null
    private var isLaunching = false
    private var launchJob: kotlinx.coroutines.Job? = null
    private var isNewWorld = false
    private var logcatThread: Thread? = null
    private var logTailThread: Thread? = null
    private var portProbeThread: Thread? = null
    private val logcatRunning = AtomicBoolean(false)
    private val logTailRunning = AtomicBoolean(false)
    private val portProbeRunning = AtomicBoolean(false)
    private val tunnelStarted = AtomicBoolean(false)
    private val serverReadyHandled = AtomicBoolean(false)
    private val stopInProgress = AtomicBoolean(false)
    private val logBuffer = ArrayDeque<String>(1000)
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var relayJob: Job? = null
    private var currentServerPort: Int = 25565
    private val relayManager by lazy { RelayManager(this) }
    private var wakeLock: PowerManager.WakeLock? = null
    private var stopReason: String = "unknown"
    private var autoRecoverWindowStartMs: Long = 0L
    private var autoRecoverAttempts: Int = 0
    private var autoRestartEnabled: Boolean = false
    private var lastNotificationText: String = ""
    private var lastNotificationUpdateMs: Long = 0L
    private var serverReadyNotificationShown = false
    private val relayStatusPlayerCount = AtomicInteger(0)
    private val relayOnlinePlayers = linkedSetOf<String>()
    private val relayHealthFailures = AtomicInteger(0)
    private var relayStatusJob: Job? = null
    private var serverReadyFallbackJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        autoRestartEnabled = AppPreferences(applicationContext).autoRestart
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun onTimeout(startId: Int) {
        currentVersionId?.let {
            sendEvent(it, EVENT_OUTPUT, "[PocketCraft] Background time limit reached (6h). Stopping server gracefully to comply with Android 15 policies...")
        }
        // Force immediate stop sequence
        stopServer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // ALWAYS call startForeground immediately to prevent ForegroundServiceDidNotStartInTimeException
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, createForegroundNotification(ServerStage.STARTING_SERVER.notificationText), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, createForegroundNotification(ServerStage.STARTING_SERVER.notificationText))
        }

        val versionId = intent?.getStringExtra(EXTRA_VERSION_ID)
        if (intent?.action == ACTION_STOP) {
            stopReason = "user"
            autoRecoverAttempts = 0
            autoRecoverWindowStartMs = 0L
            updateNotification(ServerStage.STOPPING, force = true)
            currentVersionId?.let {
                sendEvent(it, EVENT_OUTPUT, "[PocketCraft] Stop requested.")
            }
            stopServer()
            return START_NOT_STICKY
        }

        if (intent?.action == ACTION_RECONNECT_RELAY) {
            reconnectRelay()
            return START_STICKY
        }

        // If service restarts without explicit action but server was running, restart it
        val hasExplicitStart = intent?.action == ACTION_START && !versionId.isNullOrBlank()
        val worldName = (intent?.getStringExtra(EXTRA_WORLD_NAME)
            ?: getPersistedActiveWorld(applicationContext))
            .trim()
            .ifBlank { "world" }
        val activeVersion = if (!hasExplicitStart) getPersistedActiveVersion(applicationContext) else versionId

        if (!hasExplicitStart && activeVersion.isNotBlank() && worldName.isNotBlank()) {
            // Service restarted after being killed, resume running server
            return resumeServer(activeVersion, worldName)
        }

        if (!hasExplicitStart || versionId.isNullOrBlank()) {
            return START_STICKY
        }

        if (!isEulaAccepted(worldName)) {
            return abortStartForEula(versionId, worldName)
        }

        if (isLaunching) {
            sendEvent(versionId, EVENT_OUTPUT, "[PocketCraft] Server is already starting.")
            return START_STICKY
        }

        val existingServerPort = resolveServerPort(worldName)
        if (isLocalServerPortOpen(existingServerPort)) {
            return attachToExistingServer(versionId, worldName, existingServerPort, "start request")
        }

        isLaunching = true
        currentVersionId = versionId
        currentWorldName = worldName
        stopReason = "unknown"
        serverReadyNotificationShown = false
        serverReadyHandled.set(false)
        setServerReadyState(false)
        relayStatusPlayerCount.set(0)
        synchronized(relayOnlinePlayers) { relayOnlinePlayers.clear() }
        persistRuntimeState(applicationContext, versionId, worldName, RUNTIME_STATE_STARTING)
        resetNotificationState(ServerStage.STARTING_SERVER.notificationText)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, createForegroundNotification(ServerStage.STARTING_SERVER.notificationText), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, createForegroundNotification(ServerStage.STARTING_SERVER.notificationText))
        }
        // On Android 12+ the JVM runs in-process via JNI (NativeLauncher).
        // launcher.c pipes JVM stdout/stderr into logcat at full native speed.
        // startLogcatBridge() would re-broadcast every one of those lines on the
        // main thread, causing a broadcast flood (~100s of events/sec).
        // startServerLogTail() already tails logs/latest.log and handles all output,
        // so the logcat bridge is redundant on this path.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            startLogcatBridge(versionId)
        }

        // Clear old log if starting fresh so we don't read "Done" from previous session
        val latestLogFile = java.io.File(com.pocketcraft.server.service.ServerFileManager.getServerDir(applicationContext, worldName), "logs/latest.log")
        if (latestLogFile.exists()) {
            runCatching { latestLogFile.delete() }
        }

        startServerLogTail(versionId)
        val serverPort = resolveServerPort(worldName)
        currentServerPort = serverPort
        startPortProbe(versionId, serverPort)
        scheduleServerReadyFallback(versionId)
        acquireWakeLock()

        val serverDir = com.pocketcraft.server.service.ServerFileManager.getServerDir(applicationContext, worldName)
        val worldDir = java.io.File(serverDir, resolveConfiguredLevelName(serverDir))
        isNewWorld = !worldDir.exists()



        launchJob = serviceScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            migrateLegacyStorageIfNeeded(versionId, worldName)
            val configRepo = com.pocketcraft.server.data.repository.ServerConfigRepository(applicationContext)
            val config = configRepo.loadConfig()
            // Use versionId as fallback if pocketcraft-game-version was never written
            val resolvedGameVersion = config.gameVersion.ifBlank { versionId }
            val runtime = JreExtractor.runtimeForVersion(resolvedGameVersion)
            val serverDir = com.pocketcraft.server.service.ServerFileManager.getServerDir(applicationContext, worldName)
            val targetFile = com.pocketcraft.server.service.ServerFileManager.getServerJarFile(applicationContext, resolvedGameVersion, config.serverType)
            // Ensure parent directory exists before resolveJar tries to write the file
            targetFile.parentFile?.mkdirs()
            android.util.Log.d("ServerHostService", "[startServer] versionId=$versionId gameVersion=${config.gameVersion} resolvedGameVersion=$resolvedGameVersion serverType=${config.serverType} targetFile=${targetFile.absolutePath} exists=${targetFile.exists()} isDir=${targetFile.isDirectory}")

            try {
                updateNotification(ServerStage.EXTRACTING_JRE, force = true)
                ensureRuntimeExtracted(versionId, runtime)
                com.pocketcraft.server.server.ServerJarManager.resolveJar(
                    serverType = config.serverType,
                    gameVersion = resolvedGameVersion,
                    customJarPath = config.customJarPath,
                    targetFile = targetFile,
                    onProgress = { pct -> 
                        updateNotification("${ServerStage.DOWNLOADING_SERVER.notificationText} $pct%", force = false)
                        sendEvent(versionId, EVENT_OUTPUT, "Downloading server: $pct%")
                    }
                ).collect { jarFile ->
                    updateNotification(ServerStage.CHECKING_PLUGINS, force = true)
                    updateNotification(ServerStage.STARTING_SERVER, force = true)

                    withContext(kotlinx.coroutines.Dispatchers.IO) {
                        ServerLauncher(applicationContext).startServer(
                            worldName = worldName,
                            versionId = versionId,
                            jarPath = jarFile.absolutePath,
                            runtime = runtime,
            onOutput = { line ->
                handleObservedOutputLine(versionId, line)
            },
            onError = { line ->
                sendEvent(versionId, EVENT_ERROR, line)
                // Don't update notification status
                if (!stopInProgress.get() && stopReason != "user") {
                    stopReason = if (line.contains("outofmemory", ignoreCase = true) || line.contains("oom", ignoreCase = true)) {
                        "oom"
                    } else {
                        "crash"
                    }
                }
            },
            onStopped = { exitCode ->
                logJvmCrash(exitCode)
                isLaunching = false
                stopInProgress.set(false)
                relayJob?.cancel()
                relayJob = null
                serverReadyFallbackJob?.cancel()
                serverReadyFallbackJob = null
                relayManager.stopBedrockBridge()
                tunnelStarted.set(false)
                serverReadyHandled.set(false)
                setServerReadyState(false)
                stopLogcatBridge()
                stopServerLogTail()
                stopPortProbe()

                if (exitCode != 0 && stopReason != "user") {
                    if (exitCode == 127) {
                        sendEvent(versionId, EVENT_SERVER_CRASHED, "exit_code=127")
                        sendEvent(versionId, EVENT_ERROR, "[PocketCraft] Exit code 127: Java binary not executable on this device.")
                        sendEvent(versionId, EVENT_ERROR, "[PocketCraft] [HINT] Go to Settings → Apps → PocketCraft → Storage → Clear Cache, then restart.")
                    } else {
                        sendEvent(versionId, EVENT_SERVER_CRASHED, "exit_code=$exitCode")
                        sendEvent(versionId, EVENT_ERROR, "[PocketCraft] Server exited unexpectedly (code $exitCode).")
                    }
                }

                val shouldAutoRecover = exitCode != 0 && shouldScheduleAutoRecover(versionId)
                if (shouldAutoRecover) {
                    val attempt = autoRecoverAttempts
                    val delayMs = (attempt * 4000L).coerceAtMost(15_000L)
                    sendEvent(
                        versionId,
                        EVENT_OUTPUT,
                        "[PocketCraft] Server exited unexpectedly. Auto-restarting in ${delayMs / 1000}s (attempt $attempt/$AUTO_RECOVER_MAX_ATTEMPTS)..."
                    )
                    serviceScope.launch {
                        kotlinx.coroutines.delay(delayMs)
                        if (stopReason != "user") {
                            start(applicationContext, versionId, activeWorldNameOrDefault())
                        }
                    }
                }

                persistPublicAddress(applicationContext, "")
                persistRuntimeState(applicationContext, versionId, activeWorldNameOrDefault(), RUNTIME_STATE_OFFLINE)
                serverReadyNotificationShown = false
                serverReadyHandled.set(false)
                setServerReadyState(false)
                releaseWakeLock()
                if (shouldAutoRecover) {
                    updateNotification("Recovering server...", force = true)
                } else {
                    updateNotification(ServerStage.STOPPING, force = true)
                }
            }
        )
                    }
                }
            } catch (e: Exception) {
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    sendEvent(versionId, EVENT_ERROR, "[PocketCraft] Failed to resolve JAR: ${e.message}")
                    updateNotification("Server error", force = true)
                    isLaunching = false
                    stopInProgress.set(false)
                    persistPublicAddress(applicationContext, "")
                    persistRuntimeState(applicationContext, versionId, activeWorldNameOrDefault(), RUNTIME_STATE_OFFLINE)
                    serverReadyNotificationShown = false
                    serverReadyHandled.set(false)
                    setServerReadyState(false)
                    releaseWakeLock()
                    updateNotification(ServerStage.STOPPING, force = true)
                }
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        // If this service is being destroyed unexpectedly, avoid leaving
        // a detached JVM process running without relay/control.
        forceTerminateHostedServer()
        val inProcessRuntime = !ServerLauncher.hasActiveExternalProcess()
        if (inProcessRuntime && isLaunching) {
            android.util.Log.e("PocketCraft", "Service destroyed while JVM thread active. Killing :server process to prevent leak.")
            android.os.Process.killProcess(android.os.Process.myPid())
        }
        persistPublicAddress(applicationContext, "")
        currentVersionId?.let { persistRuntimeState(applicationContext, it, activeWorldNameOrDefault(), RUNTIME_STATE_OFFLINE) }
        serverReadyFallbackJob?.cancel()
        serverReadyFallbackJob = null
        setServerReadyState(false)
        releaseWakeLock()
        // Don't stop server on app close - only stop if explicitly requested by user
        // The service will keep running in background
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Keep service lifecycle independent from recent-task UI removal.
        // This avoids races where user-initiated shutdown is misread as a crash/restart flow.
    }

    private fun stopServer() {
        if (!stopInProgress.compareAndSet(false, true)) return
        launchJob?.cancel()
        launchJob = null
        serverReadyFallbackJob?.cancel()
        serverReadyFallbackJob = null
        CoroutineScope(Dispatchers.IO).launch {
            val inProcessRuntime = !ServerLauncher.hasActiveExternalProcess()
            try {
                // Start the grace period timer before we attempt any blocking RCON commands.
                val deadline = SystemClock.elapsedRealtime() + STOP_GRACE_PERIOD_MS

                if (inProcessRuntime) {
                    // Native in-process JVM has no Process handle.
                    // Signal the companion plugin to perform graceful shutdown first.
                    try {
                        currentWorldName?.let { worldName ->
                            val serverDir = ServerFileManager.getServerDir(applicationContext, worldName)
                            val signalFile = File(serverDir, "plugins/PocketCraftCompanion/graceful_stop.signal")
                            signalFile.parentFile?.mkdirs()
                            signalFile.createNewFile()
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("PocketCraft", "Failed to write stop signal: " + e.message)
                    }
                    runCatching { sendRconStop() }
                } else {
                    ServerLauncher.sendCommand("stop")
                }

                // Keep shutdown responsive; the app already requested a save before stopping.
                while (SystemClock.elapsedRealtime() < deadline) {
                    if (!isLaunching) { // isLaunching is set to false in onStopped
                        break
                    }
                    delay(250)
                }

            } catch (e: Exception) {
                android.util.Log.e("PocketCraft", "Error during stop: ${e.message}")
            } finally {
                forceTerminateHostedServer()
                try {
                    relayManager.stopBedrockBridge()
                    kotlinx.coroutines.withTimeout(3000L) {
                        relayManager.disconnect()
                    }
                } catch (_: Exception) {}

                relayJob?.cancel()
                relayJob = null
                relayStatusJob?.cancel()
                relayStatusJob = null
                serverReadyFallbackJob?.cancel()
                serverReadyFallbackJob = null
                tunnelStarted.set(false)
                serverReadyHandled.set(false)
                setServerReadyState(false)
                stopLogcatBridge()
                stopServerLogTail()
                stopPortProbe()
                releaseWakeLock()

                isLaunching = false
                currentVersionId?.let { versionId ->
                    persistPublicAddress(applicationContext, "")
                    persistRuntimeState(applicationContext, versionId, activeWorldNameOrDefault(), RUNTIME_STATE_OFFLINE)
                    sendEvent(versionId, EVENT_STOPPED, "[INFO] Server stopped.")
                }
                relayStatusPlayerCount.set(0)
                synchronized(relayOnlinePlayers) { relayOnlinePlayers.clear() }

                sendBroadcast(Intent(EVENT_STOPPED).setPackage(packageName))
                try {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } catch (e: Exception) {
                    android.util.Log.e("PocketCraft", "Error stopping foreground: ${e.message}")
                }

                if (inProcessRuntime) {
                    android.util.Log.d("PocketCraft", "In-process runtime stopped. Killing :server process to ensure clean resource release.")
                    stopSelf()
                    android.os.Process.killProcess(android.os.Process.myPid())
                } else {
                    stopSelf()
                }

                stopInProgress.set(false)
            }
        }
    }

    private fun isLocalServerPortOpen(port: Int): Boolean {
        return runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", port), 250)
                true
            }
        }.getOrDefault(false)
    }

    private suspend fun waitForLocalServerPort(port: Int, timeoutMs: Long): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (isLocalServerPortOpen(port)) return true
            delay(500)
        }
        return isLocalServerPortOpen(port)
    }

    private fun forceTerminateHostedServer() {
        runCatching { ServerLauncher.requestForceStop() }
        runCatching { serverProcess?.destroyForcibly() }
        serverProcess = null
    }

    private fun logJvmCrash(exitCode: Int) {
        android.util.Log.e("ServerHostService", "JVM process exited with code: $exitCode")

        // Capture last 50 lines of log buffer as crash context
        val stderrLog = File(filesDir, "logs/last_crash_stderr.txt")
        runCatching {
            stderrLog.parentFile?.mkdirs()
            val tail = synchronized(logBuffer) {
                logBuffer.toList().takeLast(50)
            }
            stderrLog.writeText(tail.joinToString("\n"))
        }

        // Log to Crashlytics if exit was abnormal
        if (exitCode != 0 && exitCode != 130 && exitCode != 143) { // 130/143 are SIGINT/SIGTERM
            val exception = RuntimeException("JVM exited abnormally: code=$exitCode")
            FirebaseCrashlytics.getInstance().recordException(exception)
        }
    }

    private fun updateNotification(text: String, force: Boolean = false) {
        val cleanText = ConsoleParser.stripAnsi(text).trim()
        if (cleanText.isBlank()) return

        // Skip further notification updates after server is ready
        if (serverReadyNotificationShown && !force) return

        val now = SystemClock.elapsedRealtime()
        val normalized = cleanText.lowercase()
        val isImportant = force ||
            normalized.contains("starting") ||
            normalized.contains("stopping") ||
            normalized.contains("error") ||
            normalized.contains("crash") ||
            normalized.contains("recover") ||
            normalized.contains("done (") ||
            normalized.contains("internet relay") ||
            normalized.contains("internet address") ||
            normalized.contains("accepting connections")

        val minIntervalMs = if (isImportant) IMPORTANT_NOTIFICATION_UPDATE_INTERVAL_MS else NOTIFICATION_UPDATE_INTERVAL_MS
        val isDuplicate = cleanText == lastNotificationText
        val isThrottled = (now - lastNotificationUpdateMs) < minIntervalMs

        if (!force && (isDuplicate || isThrottled)) return

        lastNotificationText = cleanText
        lastNotificationUpdateMs = now

        runCatching {
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, createForegroundNotification(cleanText))
        }
    }

    private fun updateNotification(stage: ServerStage, force: Boolean = false) {
        updateNotification(stage.notificationText, force)
    }

    private fun resetNotificationState(initialText: String = "") {
        lastNotificationText = initialText
        lastNotificationUpdateMs = SystemClock.elapsedRealtime()
    }

    private fun createForegroundNotification(text: String, playSound: Boolean = false): Notification {
        ensureNotificationChannel()

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("PocketCraft Server Running")
            .setContentText(text.take(100).ifBlank { "Tap to manage your server" })
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(pendingIntent)

        if (playSound) {
            builder.setSound(android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION))
        } else {
            builder.setSilent(true)
        }

        return builder.build()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = getSystemService(NotificationManager::class.java)
        
        // 1. Server Running (Ongoing)
        val runningChannel = NotificationChannel(
            CHANNEL_SERVER_RUNNING,
            "Server Status",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows while your Minecraft server is running"
            setShowBadge(false)
        }
        manager.createNotificationChannel(runningChannel)

        // 2. Alerts (Players joining, etc.)
        val alertChannel = NotificationChannel(
            CHANNEL_SERVER_ALERTS,
            "Server Alerts",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Alerts for player activity and server events"
        }
        manager.createNotificationChannel(alertChannel)

        // 3. Timeout Warnings
        val timeoutChannel = NotificationChannel(
            CHANNEL_SERVER_TIMEOUT,
            "System Notifications",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Critical alerts about service lifecycle and timeouts"
        }
        manager.createNotificationChannel(timeoutChannel)
    }

    private suspend fun ensureRuntimeExtracted(
        versionId: String,
        runtime: JreExtractor.RuntimeSpec
    ) {
        var lastPercent = -1
        runCatching {
            JreExtractor.extractIfNeeded(applicationContext, runtime) { percent, status ->
                if (percent == 0 || percent == 100 || percent - lastPercent >= 15) {
                    lastPercent = percent
                    sendEvent(
                        versionId,
                        EVENT_OUTPUT,
                        "[PocketCraft] ${status} (${runtime.displayName})"
                    )
                }
            }
        }.onFailure { error ->
            sendEvent(
                versionId,
                EVENT_ERROR,
                "[PocketCraft] Runtime setup failed: ${error.message}"
            )
            throw error
        }
    }

    private fun sendEvent(versionId: String, type: String, line: String) {
        sendBroadcast(
            Intent(ACTION_SERVER_EVENT).apply {
                setPackage(packageName)
                putExtra(EXTRA_VERSION_ID, versionId)
                putExtra(EXTRA_EVENT_TYPE, type)
                putExtra(EXTRA_LINE, line)
            }
        )
    }

    private fun startLogcatBridge(versionId: String) {
        if (logcatRunning.getAndSet(true)) return

        logcatThread = Thread {
            val process = ProcessBuilder(
                "logcat",
                "--pid=${Process.myPid()}",
                "-v",
                "brief",
                "*:V"
            ).redirectErrorStream(true).start()

            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { raw ->
                    if (!logcatRunning.get()) return@forEach
                    val line = raw.substringAfter(": ", raw).trim()
                    if (line.isBlank()) return@forEach
                    if (line.startsWith("JAR:") ||
                        line.startsWith("JRE:") ||
                        line.startsWith("Dir:") ||
                        line.startsWith("Tmp:") ||
                        line.contains("type=1400 audit(") ||
                        line.contains(" avc: ") ||
                        line.contains("GraphicsEnvironment") ||
                        line.contains("ziparchive") ||
                        line.contains("ProfileInstaller") ||
                        line.contains("AdrenoGLES") ||
                        line.contains("OpenGLRenderer") ||
                        line.contains("Compat change id reported")
                    ) {
                        return@forEach
                    }
                    addLogLine(line)
                    sendEvent(versionId, EVENT_OUTPUT, line)
                    if (looksLikeServerReady(line)) {
                        onServerReady()
                    }
                }
            }
        }.apply {
            name = "server-logcat-bridge"
            isDaemon = true
            start()
        }
    }

    private fun startServerLogTail(versionId: String) {
        if (logTailRunning.getAndSet(true)) return

        val latestLog = File(ServerFileManager.getServerDir(applicationContext, activeWorldNameOrDefault()), "logs/latest.log")
        // Read up to 128KB of backlog so the user sees the start-up logs even if the tailer starts a bit late.
        val initialOffset = latestLog.takeIf { it.exists() }?.let { (it.length() - 131072).coerceAtLeast(0L) } ?: 0L

        logTailThread = Thread {
            var offset = initialOffset
            while (logTailRunning.get() && !Thread.currentThread().isInterrupted) {
                try {
                    if (!latestLog.exists()) {
                        Thread.sleep(400)
                        continue
                    }

                    if (latestLog.length() < offset) {
                        offset = 0L
                    }

                    RandomAccessFile(latestLog, "r").use { raf ->
                        raf.seek(offset)
                        while (logTailRunning.get()) {
                            val raw = raf.readLine() ?: break
                            offset = raf.filePointer

                            val line = decodeLogLine(raw)
                                ?.let(ConsoleParser::stripAnsi)
                                ?.trim()
                                .orEmpty()

                            if (line.isBlank()) continue
                            handleObservedOutputLine(versionId, line)
                        }
                        offset = raf.filePointer
                    }

                    Thread.sleep(250)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                } catch (_: Exception) {
                    Thread.sleep(500)
                }
            }
        }.apply {
            name = "server-file-tail"
            isDaemon = true
            start()
        }
    }

    private fun stopServerLogTail() {
        logTailRunning.set(false)
        logTailThread?.interrupt()
        logTailThread = null
    }

    private fun startPortProbe(versionId: String, port: Int) {
        if (portProbeRunning.getAndSet(true)) return

        portProbeThread = Thread {
            try {
                while (portProbeRunning.get() && !Thread.currentThread().isInterrupted) {
                    try {
                        Socket().use { socket ->
                            socket.connect(InetSocketAddress("127.0.0.1", port), 750)
                        }
                        val line = "[PocketCraft] Server port $port is open. Finalizing startup..."
                        sendEvent(versionId, EVENT_OUTPUT, line)
                        updateNotification("Finalizing server startup...", force = true)
                        break
                    } catch (_: Exception) {
                        Thread.sleep(1500)
                    }
                }
            } catch (e: InterruptedException) {
                // Thread was interrupted, exit gracefully
                Thread.currentThread().interrupt()
            } finally {
                portProbeRunning.set(false)
            }
        }.apply {
            name = "server-port-probe"
            isDaemon = true
            start()
        }
    }

    private fun resolveServerPort(worldName: String): Int {
        val serverDir = ServerFileManager.getServerDir(applicationContext, worldName.trim().ifBlank { activeWorldNameOrDefault() })
        val propsFile = File(serverDir, "server.properties")
        if (!propsFile.exists()) return 25565

        return runCatching {
            propsFile.inputStream().use { input ->
                Properties().apply { load(input) }
            }.getProperty("server-port", "25565").toIntOrNull() ?: 25565
        }.getOrDefault(25565)
    }

    private fun resolveConfiguredLevelName(serverDir: File): String {
        return runCatching {
            val propsFile = File(serverDir, "server.properties")
            val props = Properties()
            if (propsFile.exists()) {
                propsFile.inputStream().use { props.load(it) }
            }
            props.getProperty("level-name", "world").trim().ifBlank { "world" }
        }.getOrDefault("world")
    }

    @Keep
    private fun looksLikeServerReady(line: String): Boolean {
        val normalized = line.lowercase()
        return normalized.contains("done (") &&
            normalized.contains("for help, type \"help\"")
    }

    private fun stopPortProbe() {
        portProbeRunning.set(false)
        portProbeThread?.interrupt()
        portProbeThread = null
    }

    private fun onRelayReadyToStart(versionId: String) {
        if (tunnelStarted.getAndSet(true)) {
            android.util.Log.d("ServerHostService", "onRelayReadyToStart: Tunnel already started, skipping.")
            return
        }

        resolveLanEndpoint(currentServerPort)?.let { endpoint ->
            sendEvent(versionId, EVENT_OUTPUT, "[PocketCraft] LAN address: $endpoint")
        }
        sendEvent(versionId, EVENT_TUNNEL_CONNECTING, "[PocketCraft] Opening internet relay...")

        relayJob = serviceScope.launch(Dispatchers.IO) {
            var registrationAttempts = 0
            val maxRegistrationAttempts = 5
            relayManager.disconnect()
            relayManager.startBedrockBridge()

            while (isActive) {
                try {
                    val alreadyKnownPort = relayManager.assignedPort

                    registrationAttempts++
                    android.util.Log.i("ServerHostService", "Relay registration attempt $registrationAttempts/$maxRegistrationAttempts")
                    sendEvent(
                        versionId,
                        EVENT_OUTPUT,
                        "[PocketCraft] Relay attempt $registrationAttempts/$maxRegistrationAttempts..."
                    )

                    val address = relayManager.register()
                    sendEvent(
                        versionId,
                        EVENT_OUTPUT,
                        "[PocketCraft] Relay registered: $address"
                    )
                    publishRelayStatus(versionId)
                    relayManager.initPool(currentServerPort)
                    if (!relayManager.isPoolReady.value) {
                        sendEvent(
                            versionId,
                            EVENT_OUTPUT,
                            "[PocketCraft] Relay registered, but tunnel sockets did not become ready."
                        )
                        throw java.io.IOException("Relay tunnel pool did not become ready")
                    }
                    sendEvent(
                        versionId,
                        EVENT_OUTPUT,
                        "[PocketCraft] Relay tunnel is ready."
                    )

                    persistPublicAddress(applicationContext, address.toString())
                    relayHealthFailures.set(0)

                    registrationAttempts = 0

                    val intent = Intent(ACTION_SERVER_EVENT).apply {
                        setPackage(packageName)
                        putExtra(EXTRA_VERSION_ID, currentVersionId ?: "unknown")
                        putExtra(EXTRA_EVENT_TYPE, EVENT_TUNNEL_CONNECTED)
                        putExtra(EXTRA_LINE, address.toString())
                        putExtra(EXTRA_IS_FALLBACK, address.isFallback)
                    }
                    sendBroadcast(intent)
                    publishRelayStatus(versionId)
                    startRelayStatusHeartbeat(versionId)



                    while (isActive) {
                        delay(60_000)
                    }
                } catch (e: Exception) {
                    if (!isActive) break

                    android.util.Log.e("ServerHostService", "Relay Error (attempt $registrationAttempts): ${e.message}")
                    sendEvent(
                        versionId,
                        EVENT_OUTPUT,
                        "[PocketCraft] Relay attempt $registrationAttempts failed: ${e.message ?: "unknown error"}"
                    )
                    relayStatusJob?.cancel()
                    relayStatusJob = null

                    // Ensure failed attempts do not keep stale sockets around.
                    relayManager.disconnect()
                    relayManager.startBedrockBridge()

                    if (registrationAttempts >= maxRegistrationAttempts) {
                        android.util.Log.e("ServerHostService", "Max registration attempts reached. Stopping retry loop.")
                        currentVersionId?.let {
                            sendEvent(
                                it,
                                EVENT_TUNNEL_FAILED,
                                "Internet relay is unavailable right now. Players on the same Wi-Fi can still join with the LAN address."
                            )
                        }
                        updateNotification("LAN only: internet relay unavailable", force = true)
                        break
                    }

                    delay(5000)
                }
            }
            relayManager.stopBedrockBridge()
            android.util.Log.i("ServerHostService", "Relay job ended (isActive=$isActive)")
        }
    }

    private fun reconnectRelay() {
        val versionId = currentVersionId ?: return
        relayHealthFailures.set(0)
        relayJob?.cancel()
        relayJob = null
        relayStatusJob?.cancel()
        relayStatusJob = null
        relayManager.stopBedrockBridge()
        relayManager.disconnect()
        tunnelStarted.set(false)
        sendEvent(versionId, EVENT_OUTPUT, "[PocketCraft] Reconnecting internet relay...")
        serviceScope.launch(Dispatchers.IO) {
            delay(500)
            onRelayReadyToStart(versionId)
        }
    }

    @Keep
    private fun onServerReady() {
        if (serverReadyHandled.getAndSet(true)) {
            android.util.Log.d("ServerHostService", "onServerReady: Ready state already handled, skipping.")
            return
        }

        currentVersionId?.let(::onRelayReadyToStart)
        setServerReadyState(true)
        serverReadyFallbackJob?.cancel()
        serverReadyFallbackJob = null

        val versionId = currentVersionId ?: return

        // 1. Update disk state FIRST so UI refreshes read the correct value BEFORE the broadcast is received
        persistRuntimeState(applicationContext, versionId, activeWorldNameOrDefault(), RUNTIME_STATE_RUNNING)

        // 2. Broadcast a direct server-ready event so the UI can transition to ONLINE
        // without depending on log-parsing, which may be affected by R8 in release builds.
        val readyIntent = Intent(ACTION_SERVER_EVENT).apply {
            setPackage(packageName)
            putExtra(EXTRA_VERSION_ID, versionId)
            putExtra(EXTRA_EVENT_TYPE, EVENT_SERVER_READY)
        }
        sendBroadcast(readyIntent)
        android.util.Log.d("ServerHostService", "onServerReady: Sent EVENT_SERVER_READY broadcast for $versionId")

        // 3. Show "Server is Online" notification once with sound
        if (!serverReadyNotificationShown) {
            serverReadyNotificationShown = true
            val notificationText = ServerStage.RUNNING.notificationText
            runCatching {
                val manager = getSystemService(NotificationManager::class.java)
                manager.notify(NOTIFICATION_ID, createForegroundNotification(notificationText, playSound = true))
            }
            // Send the push notification
            com.pocketcraft.server.notification.NotificationHelper.notifyServerOnline(applicationContext, versionId)
        }

        autoRecoverAttempts = 0
        autoRecoverWindowStartMs = 0L
    }

    private fun scheduleServerReadyFallback(versionId: String) {
        serverReadyFallbackJob?.cancel()
        serverReadyFallbackJob = serviceScope.launch {
            delay(90_000)
            val shouldPromote = currentVersionId == versionId &&
                !serverReadyHandled.get() &&
                (serverProcess?.isAlive == true ||
                    ServerLauncher.hasActiveExternalProcess() ||
                    isLocalServerPortOpen(currentServerPort))
            if (shouldPromote) {
                android.util.Log.w(
                    "ServerHostService",
                    "Server ready signal was missed for $versionId. Promoting readiness via fallback poll."
                )
                setServerReadyState(true)
                onServerReady()
            }
        }
    }

    @Keep
    private fun setServerReadyState(ready: Boolean) {
        serviceScope.launch {
            _serverReadyState.value = ready
        }
    }

    private fun shouldScheduleAutoRecover(versionId: String): Boolean {
        return false
    }

    private fun acquireWakeLock() {
        if (wakeLock != null) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PocketCraft:ServerWakeLock").apply {
            setReferenceCounted(false)
            acquire(Long.MAX_VALUE)
        }
        android.util.Log.i("ServerHostService", "WakeLock acquired.")
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) it.release()
                android.util.Log.i("ServerHostService", "WakeLock released.")
            }
        } catch (e: Exception) {
            android.util.Log.e("ServerHostService", "Error releasing WakeLock: ${e.message}")
        } finally {
            wakeLock = null
        }
    }

    private fun activeWorldNameOrDefault(): String {
        return currentWorldName
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: getPersistedActiveWorld(applicationContext).trim().ifBlank { "world" }
    }

    private fun attachToExistingServer(
        versionId: String,
        worldName: String,
        serverPort: Int,
        reason: String
    ): Int {
        isLaunching = true
        currentVersionId = versionId
        currentWorldName = worldName
        currentServerPort = serverPort
        stopReason = "unknown"
        serverReadyNotificationShown = false
        serverReadyHandled.set(false)
        relayStatusPlayerCount.set(0)
        synchronized(relayOnlinePlayers) { relayOnlinePlayers.clear() }
        persistRuntimeState(applicationContext, versionId, worldName, RUNTIME_STATE_RUNNING)
        resetNotificationState(ServerStage.RUNNING.notificationText)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, createForegroundNotification(ServerStage.RUNNING.notificationText), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, createForegroundNotification(ServerStage.RUNNING.notificationText))
        }
        startLogcatBridge(versionId)
        startServerLogTail(versionId)
        acquireWakeLock()
        sendEvent(
            versionId,
            EVENT_OUTPUT,
            "[PocketCraft] Found an existing Minecraft server on localhost:$serverPort during $reason. Reattaching relay instead of starting another server."
        )
        onServerReady()
        return START_STICKY
    }

    private fun migrateLegacyStorageIfNeeded(versionId: String, worldName: String) {
        val result = ServerVersionMigrator.migrateLegacyVersionStorageIfNeeded(
            context = applicationContext,
            versionId = versionId,
            worldName = worldName
        )
        if (!result.migrated) return

        sendEvent(
            versionId,
            EVENT_OUTPUT,
            "[PocketCraft] Restored existing world data from the legacy version folder."
        )
        result.backupDir?.let { backup ->
            sendEvent(
                versionId,
                EVENT_OUTPUT,
                "[PocketCraft] A newer generated world was moved aside before restore: $backup"
            )
        }
    }

    private fun isEulaAccepted(worldName: String): Boolean {
        val prefs = AppPreferences(applicationContext)
        // If the user has already accepted the EULA globally, write the file for
        // this world (if missing) and proceed without asking again.
        if (prefs.eulaAccepted) {
            ServerFileManager.prepareEula(applicationContext, worldName)
            return true
        }
        // First-time check: see if the file was accepted externally (e.g. manual import).
        val accepted = ServerFileManager.isEulaAccepted(applicationContext, worldName)
        if (accepted) {
            // Promote the file acceptance to the preference so we never ask again.
            prefs.eulaAccepted = true
        }
        return accepted
    }

    private fun abortStartForEula(versionId: String, worldName: String): Int {
        sendEvent(versionId, EVENT_ERROR, "[PocketCraft] Accept the Minecraft EULA to start the server.")
        persistRuntimeState(
            applicationContext,
            versionId,
            worldName.ifBlank { "world" },
            RUNTIME_STATE_OFFLINE
        )
        stopSelf()
        return START_NOT_STICKY
    }

    private fun resumeServer(versionId: String, worldName: String): Int {
        if (isLaunching || currentVersionId != null) {
            return START_STICKY
        }

        if (!isEulaAccepted(worldName)) {
            return abortStartForEula(versionId, worldName)
        }

        isLaunching = true
        currentVersionId = versionId
        currentWorldName = worldName
        stopReason = "unknown"
        serverReadyNotificationShown = false
        serverReadyHandled.set(false)
        setServerReadyState(false)
        relayStatusPlayerCount.set(0)
        synchronized(relayOnlinePlayers) { relayOnlinePlayers.clear() }
        persistRuntimeState(applicationContext, versionId, worldName, RUNTIME_STATE_STARTING)
        resetNotificationState(ServerStage.STARTING_SERVER.notificationText)
        startLogcatBridge(versionId)
        val serverPort = resolveServerPort(worldName)
        currentServerPort = serverPort
        val existingServerDetected = runBlocking(Dispatchers.IO) {
            waitForLocalServerPort(serverPort, timeoutMs = 12_000L)
        }
        if (existingServerDetected) {
            isLaunching = false
            currentVersionId = null
            currentWorldName = null
            return attachToExistingServer(versionId, worldName, serverPort, "service resume")
        }

        // If we reach here, we are starting fresh. Clear old log and start tailing.
        val latestLogFile = java.io.File(com.pocketcraft.server.service.ServerFileManager.getServerDir(applicationContext, worldName), "logs/latest.log")
        if (latestLogFile.exists()) {
            runCatching { latestLogFile.delete() }
        }
        startServerLogTail(versionId)

        startPortProbe(versionId, serverPort)
        scheduleServerReadyFallback(versionId)
        acquireWakeLock()

        val serverDirPre = com.pocketcraft.server.service.ServerFileManager.getServerDir(applicationContext, worldName)
        val worldDir = java.io.File(serverDirPre, resolveConfiguredLevelName(serverDirPre))
        isNewWorld = !worldDir.exists()

        launchJob = serviceScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            migrateLegacyStorageIfNeeded(versionId, worldName)
            val configRepo = com.pocketcraft.server.data.repository.ServerConfigRepository(applicationContext)
            val config = configRepo.loadConfig()
            // Use versionId as fallback if pocketcraft-game-version was never written
            val resolvedGameVersion = config.gameVersion.ifBlank { versionId }
            val runtime = JreExtractor.runtimeForVersion(resolvedGameVersion)
            val serverDir = com.pocketcraft.server.service.ServerFileManager.getServerDir(applicationContext, worldName)
            val targetFile = com.pocketcraft.server.service.ServerFileManager.getServerJarFile(applicationContext, resolvedGameVersion, config.serverType)
            // Ensure parent directory exists before resolveJar tries to write the file
            targetFile.parentFile?.mkdirs()
            android.util.Log.d("ServerHostService", "[resumeServer] versionId=$versionId gameVersion=${config.gameVersion} resolvedGameVersion=$resolvedGameVersion serverType=${config.serverType} targetFile=${targetFile.absolutePath} exists=${targetFile.exists()} isDir=${targetFile.isDirectory}")

            try {
                updateNotification(ServerStage.EXTRACTING_JRE, force = true)
                ensureRuntimeExtracted(versionId, runtime)
                com.pocketcraft.server.server.ServerJarManager.resolveJar(
                    serverType = config.serverType,
                    gameVersion = resolvedGameVersion,
                    customJarPath = config.customJarPath,
                    targetFile = targetFile,
                    onProgress = { pct -> 
                        updateNotification("${ServerStage.DOWNLOADING_SERVER.notificationText} $pct%", force = false)
                        sendEvent(versionId, EVENT_OUTPUT, "Downloading server: $pct%")
                    }
                ).collect { jarFile ->
                    updateNotification(ServerStage.CHECKING_PLUGINS, force = true)
                    updateNotification(ServerStage.STARTING_SERVER, force = true)
                    withContext(kotlinx.coroutines.Dispatchers.IO) {
                        ServerLauncher(applicationContext).startServer(
                            worldName = worldName,
                            versionId = versionId,
                            jarPath = jarFile.absolutePath,
                            runtime = runtime,
            onOutput = { line ->
                handleObservedOutputLine(versionId, line)
            },
            onError = { line ->
                sendEvent(versionId, EVENT_ERROR, line)
                updateNotification("Server error", force = true)
                if (!stopInProgress.get() && stopReason != "user") {
                    stopReason = if (line.contains("outofmemory", ignoreCase = true) || line.contains("oom", ignoreCase = true)) {
                        "oom"
                    } else {
                        "crash"
                    }
                }
            },
            onStopped = { exitCode ->
                logJvmCrash(exitCode)
                isLaunching = false
                stopInProgress.set(false)
                relayJob?.cancel()
                relayJob = null
                relayStatusJob?.cancel()
                relayStatusJob = null
                serverReadyFallbackJob?.cancel()
                serverReadyFallbackJob = null
                relayManager.stopBedrockBridge()
                tunnelStarted.set(false)
                serverReadyHandled.set(false)
                setServerReadyState(false)
                stopLogcatBridge()
                stopServerLogTail()
                stopPortProbe()

                if (exitCode != 0 && stopReason != "user") {
                    if (exitCode == 127) {
                        sendEvent(versionId, EVENT_SERVER_CRASHED, "exit_code=127")
                        sendEvent(versionId, EVENT_ERROR, "[PocketCraft] Exit code 127: Java binary not executable on this device.")
                        sendEvent(versionId, EVENT_ERROR, "[PocketCraft] [HINT] Go to Settings → Apps → PocketCraft → Storage → Clear Cache, then restart.")
                    } else {
                        sendEvent(versionId, EVENT_SERVER_CRASHED, "exit_code=$exitCode")
                        sendEvent(versionId, EVENT_ERROR, "[PocketCraft] Server exited unexpectedly (code $exitCode).")
                    }
                }

                val shouldAutoRecover = exitCode != 0 && shouldScheduleAutoRecover(versionId)
                if (shouldAutoRecover) {
                    val attempt = autoRecoverAttempts
                    val delayMs = (attempt * 4000L).coerceAtMost(15_000L)
                    sendEvent(
                        versionId,
                        EVENT_OUTPUT,
                        "[PocketCraft] Server exited unexpectedly. Auto-restarting in ${delayMs / 1000}s (attempt $attempt/$AUTO_RECOVER_MAX_ATTEMPTS)..."
                    )
                    serviceScope.launch {
                        kotlinx.coroutines.delay(delayMs)
                        if (stopReason != "user") {
                            start(applicationContext, versionId, activeWorldNameOrDefault())
                        }
                    }
                }

                persistPublicAddress(applicationContext, "")
                persistRuntimeState(applicationContext, versionId, activeWorldNameOrDefault(), RUNTIME_STATE_OFFLINE)
                serverReadyNotificationShown = false
                serverReadyHandled.set(false)
                relayStatusPlayerCount.set(0)
                synchronized(relayOnlinePlayers) { relayOnlinePlayers.clear() }
                releaseWakeLock()
                if (shouldAutoRecover) {
                    updateNotification("Recovering server...", force = true)
                } else {
                    updateNotification("Server stopped", force = true)
                }
            }
        )
                    }
                }
            } catch (e: Exception) {
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    sendEvent(versionId, EVENT_ERROR, "[PocketCraft] Failed to resolve JAR: ${e.message}")
                    updateNotification("Server error", force = true)
                    isLaunching = false
                    stopInProgress.set(false)
                    relayStatusJob?.cancel()
                    relayStatusJob = null
                    serverReadyFallbackJob?.cancel()
                    serverReadyFallbackJob = null
                    persistPublicAddress(applicationContext, "")
                    persistRuntimeState(applicationContext, versionId, activeWorldNameOrDefault(), RUNTIME_STATE_OFFLINE)
                    serverReadyNotificationShown = false
                    serverReadyHandled.set(false)
                    setServerReadyState(false)
                    relayStatusPlayerCount.set(0)
                    synchronized(relayOnlinePlayers) { relayOnlinePlayers.clear() }
                    releaseWakeLock()
                    updateNotification("Server stopped", force = true)
                }
            }
        }

        return START_STICKY
    }

    private fun handleObservedOutputLine(versionId: String, line: String) {
        addLogLine(line)
        sendEvent(versionId, EVENT_OUTPUT, line)
        ConsoleParser.parseJoin(line)?.let { (name, _) ->
            synchronized(relayOnlinePlayers) {
                relayOnlinePlayers.add(name.lowercase())
                relayStatusPlayerCount.set(relayOnlinePlayers.size)
                com.pocketcraft.server.service.PlayerDataManager.updateActivePlayers(relayOnlinePlayers.toSet())
            }
            ServerLauncher.sendCommand(POCKETCRAFT_JOIN_TELLRAW)
            serviceScope.launch(Dispatchers.IO) {
                publishRelayStatus(versionId)
            }
            scheduleChunkResendBurst(name)
        }
        ConsoleParser.parseLeave(line)?.let { name ->
            val remainingPlayers = synchronized(relayOnlinePlayers) {
                relayOnlinePlayers.remove(name.lowercase())
                relayStatusPlayerCount.set(relayOnlinePlayers.size)
                com.pocketcraft.server.service.PlayerDataManager.updateActivePlayers(relayOnlinePlayers.toSet())
                relayOnlinePlayers.size
            }
            serviceScope.launch(Dispatchers.IO) {
                publishRelayStatus(versionId)
            }
        }
        ConsoleParser.parseCommand(line)?.let { (name, cmd) ->
            if (cmd.equals("/ram", ignoreCase = true) || cmd.equals("/memory", ignoreCase = true)) {
                val runtime = Runtime.getRuntime()
                val max = runtime.maxMemory() / (1024 * 1024)
                val total = runtime.totalMemory() / (1024 * 1024)
                val free = runtime.freeMemory() / (1024 * 1024)
                val used = total - free

                val msg = "[PocketCraft] Server RAM: ${used}MB used of ${max}MB max"
                ServerLauncher.sendCommand("tellraw $name {\"text\":\"$msg\",\"color\":\"green\"}")
            }
        }

        if (looksLikeServerReady(line)) {
            onServerReady()
        }

        if (ConsoleParser.isPreparingStartRegion(line)) {
            sendEvent(versionId, EVENT_CHUNKS_LOADING, line)
        }
    }

    private fun scheduleChunkResendBurst(name: String) {
        // Bedrock/Geyser clients sometimes finish login before all chunks have
        // been fully acknowledged over the relay. A few staggered resend passes
        // recover most partial/wireframe chunk cases without needing a rejoin.
        serviceScope.launch {
            listOf(3_000L, 8_000L, 15_000L).forEach { delayMs ->
                delay(delayMs)
                ServerLauncher.sendCommand("send-chunks $name")
            }
        }
    }

    private fun startRelayStatusHeartbeat(versionId: String) {
        relayStatusJob?.cancel()
        relayStatusJob = serviceScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(30_000)
                publishRelayStatus(versionId)
                ensureRelayTunnelHealthy(versionId)
            }
        }
    }

    private suspend fun ensureRelayTunnelHealthy(versionId: String) {
        if (!tunnelStarted.get()) return
        if (!isLocalServerPortOpen(currentServerPort)) return

        val health = relayManager.snapshotTunnelHealth()
        if (health.healthy) {
            relayHealthFailures.set(0)
            return
        }

        val failures = relayHealthFailures.incrementAndGet()
        android.util.Log.w(
            "ServerHostService",
            "Relay health degraded (pool=${health.poolSize}, ready=${health.poolReady}, heartbeat=${health.heartbeatActive}, localPortBound=${health.hasLocalPort}, failures=$failures)."
        )
        if (failures < 2) return

        relayHealthFailures.set(0)
        sendEvent(
            versionId,
            EVENT_OUTPUT,
            "[PocketCraft] Relay health degraded. Reconnecting internet relay..."
        )
        serviceScope.launch(Dispatchers.Main.immediate) {
            reconnectRelay()
        }
    }

    private suspend fun publishRelayStatus(versionId: String) {
        val serverDir = ServerFileManager.getServerDir(applicationContext, activeWorldNameOrDefault())
        val propsFile = File(serverDir, "server.properties")
        val props = Properties().apply {
            if (propsFile.exists()) {
                propsFile.inputStream().use(::load)
            }
        }
        val motd = props.getProperty("motd", "A PocketCraft Server").trim()
        val maxPlayers = props.getProperty("max-players", "20").toIntOrNull() ?: 20
        relayManager.postServerStatus(
            motd = motd,
            players = relayStatusPlayerCount.get().coerceAtLeast(0),
            maxPlayers = maxPlayers.coerceAtLeast(1),
            version = versionId
        )
    }

    private fun resolveLanEndpoint(port: Int): String? {
        val ip = runCatching {
            NetworkInterface.getNetworkInterfaces()
                .toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }
                ?.hostAddress
        }.onFailure { e ->
            android.util.Log.e("ServerHostService", "Failed to get network interfaces (SELinux?): ${e.message}")
        }.getOrNull()
        return if (ip.isNullOrBlank()) "0.0.0.0:$port" else "$ip:$port"
    }

    private fun decodeLogLine(raw: String?): String? {
        if (raw == null) return null
        return String(raw.toByteArray(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8)
    }

    private fun addLogLine(line: String) {
        synchronized(logBuffer) {
            if (logBuffer.size >= 1000) {
                logBuffer.removeFirst()
            }
            logBuffer.addLast(line)
        }
    }

    private fun stopLogcatBridge() {
        logcatRunning.set(false)
        logcatThread?.interrupt()
        logcatThread = null
    }

    private fun sendRconStop() {
        try {
            val password = "pocketcraft-internal-rcon"
            java.net.Socket().use { socket ->
                socket.connect(java.net.InetSocketAddress("127.0.0.1", 25575), 2000)
                socket.soTimeout = 3000
                val out = java.io.DataOutputStream(socket.getOutputStream())
                val inp = java.io.DataInputStream(socket.getInputStream())

                fun writeIntLE(v: Int) { out.write(v and 0xFF); out.write((v shr 8) and 0xFF); out.write((v shr 16) and 0xFF); out.write((v shr 24) and 0xFF) }
                fun readIntLE(): Int { val b0=inp.read();val b1=inp.read();val b2=inp.read();val b3=inp.read(); return (b0 and 0xFF) or ((b1 and 0xFF) shl 8) or ((b2 and 0xFF) shl 16) or ((b3 and 0xFF) shl 24) }
                fun sendPkt(id: Int, type: Int, payload: String) {
                    val pb = payload.toByteArray(Charsets.UTF_8)
                    writeIntLE(4 + 4 + pb.size + 2); writeIntLE(id); writeIntLE(type); out.write(pb); out.write(0); out.write(0); out.flush()
                }
                fun readPkt(): Int { val len=readIntLE(); val id=readIntLE(); readIntLE(); val payLen=(len-10).coerceAtLeast(0); if(payLen>0) inp.skipBytes(payLen); inp.read(); inp.read(); return id }

                sendPkt(1, 3, password)
                val authId = readPkt()
                if (authId != -1) {
                    sendPkt(2, 2, "stop")
                    readPkt()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ServerHostService", "RCON stop failed: ${e.message}")
        }
    }

    companion object {
        private const val AUTO_RECOVER_WINDOW_MS = 20 * 60 * 1000L
        private const val AUTO_RECOVER_MAX_ATTEMPTS = 3
        const val CHANNEL_SERVER_RUNNING = "server_running"
        const val CHANNEL_SERVER_ALERTS = "server_alerts"
        const val CHANNEL_SERVER_TIMEOUT = "server_timeout"
        private const val CHANNEL_ID = CHANNEL_SERVER_RUNNING
        private const val NOTIFICATION_ID = 1111
        private const val NOTIFICATION_UPDATE_INTERVAL_MS = 2500L
        private const val IMPORTANT_NOTIFICATION_UPDATE_INTERVAL_MS = 750L
        private const val STOP_GRACE_PERIOD_MS = 15_000L

        const val ACTION_START = "com.pocketcraft.server.action.START_SERVER"
        const val ACTION_STOP = "com.pocketcraft.server.action.STOP_SERVER"
        const val ACTION_RECONNECT_RELAY = "com.pocketcraft.server.action.RECONNECT_RELAY"
        const val ACTION_SERVER_EVENT = "com.pocketcraft.server.action.SERVER_EVENT"

        const val EXTRA_VERSION_ID = "version_id"
        const val EXTRA_WORLD_NAME = "world_name"
        const val EXTRA_EVENT_TYPE = "event_type"
        const val EXTRA_LINE = "line"

        const val EXTRA_IS_FALLBACK = "is_fallback"

        const val EVENT_OUTPUT = "output"
        const val EVENT_ERROR = "error"
        const val EVENT_STOPPED = "stopped"
        const val EVENT_TUNNEL_CONNECTING = "tunnel_connecting"
        const val EVENT_TUNNEL_CONNECTED = "tunnel_connected"
        const val EVENT_TUNNEL_FAILED = "tunnel_failed"
        const val EVENT_SERVER_CRASHED = "server_crashed"
        const val EVENT_CHUNKS_LOADING = "chunks_loading"
        const val EVENT_CHUNKY_PROGRESS = "chunky_progress"
        const val EXTRA_CHUNKY_PERCENT = "chunky_percent"
        const val EVENT_SERVER_READY = "server_ready"
        private const val PREFS_NAME = "pocketcraft_runtime_state"
        private const val KEY_ACTIVE_VERSION = "active_version"
        private const val KEY_ACTIVE_WORLD = "active_world"
        private const val KEY_RUNTIME_STATE = "runtime_state"
        private const val KEY_PUBLIC_ADDRESS = "public_address"
        private const val POCKETCRAFT_JOIN_TELLRAW =
            """tellraw @a ["",{"text":"hosted on Pocketcraft","color":"green","bold":true},{"text":"\nJoin our Discord: ","color":"white"},{"text":"https://discord.gg/nc7ceYWVfT","color":"aqua","underlined":true}]"""
        const val RUNTIME_STATE_OFFLINE = "offline"
        const val RUNTIME_STATE_STARTING = "starting"
        const val RUNTIME_STATE_RUNNING = "running"
        @Keep
        private val _serverReadyState = MutableStateFlow(false)
        @Keep
        val serverReadyState: StateFlow<Boolean> = _serverReadyState.asStateFlow()

        fun start(context: Context, versionId: String, worldName: String) {
            val intent = Intent(context, ServerHostService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_VERSION_ID, versionId)
                putExtra(EXTRA_WORLD_NAME, worldName)
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                android.util.Log.e("ServerHostService", "Failed to start service: ${e.message}")
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ServerHostService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                android.util.Log.e("ServerHostService", "Failed to stop service: ${e.message}")
            }
        }

        fun reconnectRelay(context: Context) {
            val intent = Intent(context, ServerHostService::class.java).apply {
                action = ACTION_RECONNECT_RELAY
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                android.util.Log.e("ServerHostService", "Failed to reconnect relay: ${e.message}")
            }
        }

        fun getPersistedActiveVersion(context: Context): String {
            return readStateFile(context).optString(KEY_ACTIVE_VERSION, "")
        }

        fun getPersistedActiveWorld(context: Context): String {
            return readStateFile(context).optString(KEY_ACTIVE_WORLD, "")
        }

        fun getPersistedRuntimeState(context: Context, versionId: String): String {
            val state = readStateFile(context)
            val activeVersion = state.optString(KEY_ACTIVE_VERSION, "")
            if (activeVersion != versionId) {
                return RUNTIME_STATE_OFFLINE
            }
            return state.optString(KEY_RUNTIME_STATE, RUNTIME_STATE_OFFLINE)
        }

        fun getPersistedPublicAddress(context: Context, versionId: String): String? {
            val state = readStateFile(context)
            val activeVersion = state.optString(KEY_ACTIVE_VERSION, "")
            if (activeVersion != versionId) {
                return null
            }
            return state.optString(KEY_PUBLIC_ADDRESS, "").trim().ifBlank { null }
        }

        fun persistRuntimeState(context: Context, versionId: String, worldName: String, state: String) {
            val obj = readStateFile(context)
            obj.put(KEY_ACTIVE_VERSION, if (state == RUNTIME_STATE_OFFLINE) "" else versionId)
            obj.put(KEY_ACTIVE_WORLD, if (state == RUNTIME_STATE_OFFLINE) "" else worldName)
            obj.put(KEY_RUNTIME_STATE, state)
            obj.put("server_pid", android.os.Process.myPid())
            writeStateFile(context, obj)
        }

        fun getServerPid(context: Context): Int {
            return readStateFile(context).optInt("server_pid", -1)
        }

        private fun persistPublicAddress(context: Context, address: String?) {
            val obj = readStateFile(context)
            obj.put(KEY_PUBLIC_ADDRESS, address?.trim().orEmpty())
            writeStateFile(context, obj)
        }

        private fun getStateFile(context: Context): File {
            return File(context.filesDir, "runtime_state.json")
        }

        private fun readStateFile(context: Context): org.json.JSONObject {
            val file = getStateFile(context)
            if (!file.exists()) return org.json.JSONObject()
            return runCatching { org.json.JSONObject(file.readText()) }.getOrDefault(org.json.JSONObject())
        }

        private fun writeStateFile(context: Context, obj: org.json.JSONObject) {
            val file = getStateFile(context)
            runCatching { file.writeText(obj.toString()) }
        }

        fun isWhitelistEnabled(context: Context, worldName: String): Boolean {
            val propsFile = File(com.pocketcraft.server.service.ServerFileManager.getServerDir(context, worldName), "server.properties")
            if (!propsFile.exists()) return false
            return propsFile.readLines().any { it.trim() == "white-list=true" }
        }
    }
}

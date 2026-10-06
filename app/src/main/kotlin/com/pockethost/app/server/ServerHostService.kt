/**
 * Core foreground service supervising Minecraft server JVM lifecycles,
 * console log tailing, process monitoring, and networking bridges.
 */
package com.pockethost.app.server

import com.pockethost.app.NativeLauncher
import android.app.ActivityManager
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.net.wifi.WifiManager
import android.os.IBinder
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import com.pockethost.app.MainActivity
import com.pockethost.app.R
import com.pockethost.app.RelayManager
import com.pockethost.app.data.preferences.AppPreferences
import com.pockethost.app.server.ServerLauncher
import com.pockethost.app.service.ConsoleParser
import com.pockethost.app.service.PluginManager
import com.pockethost.app.service.ServerFileManager
import com.pockethost.app.service.ServerVersionMigrator
import com.pockethost.app.network.RconClient
import com.pockethost.app.setup.JreExtractor
import com.pockethost.app.widget.ServerWidgetUpdater
import java.io.File
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.ArrayDeque
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.Timestamp
import com.pockethost.app.data.model.PlayerInfo
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.pockethost.app.data.preferences.AppPreferencesStore
import androidx.annotation.Keep
import com.pockethost.app.afk.AfkDummyPluginSync

class ServerHostService : Service() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.pockethost.app.util.LocaleUtils.wrapContext(newBase))
    }

    private enum class ServerStage(val notificationText: String) {
        DOWNLOADING_SERVER("Checking server JAR..."),
        EXTRACTING_JRE("Preparing Java runtime..."),
        CHECKING_PLUGINS("Checking Bedrock bridge..."),
        STARTING_SERVER("Starting server... (30-60s)"),
        RUNNING("Server is running"),
        STOPPING("Stopping server..."),
        DASHBOARD_LISTENER_ACTIVE("PocketHost service is active")
    }

    private var isForegroundServiceStarted = false
    private var currentVersionId: String? = null
    private var currentWorldName: String? = null
    private var serverProcess: java.lang.Process? = null
    // Volatile: written from onStopped's plain background Thread (mc-server-thread /
    // ServerLauncher's finally block) and read/written from coroutines on other
    // dispatchers — needs cross-thread visibility, not just atomic reference swap.
    @Volatile private var isLaunching = false
    private var launchJob: kotlinx.coroutines.Job? = null
    private var isNewWorld = false
    private var logcatThread: Thread? = null
    // The `logcat` child process backing the bridge. interrupt() cannot unblock a read on a
    // process stream, so stopping the bridge has to destroy the process: otherwise the thread
    // stays parked on read() forever, the child keeps running, and the next start() spawns a
    // second bridge whose output interleaves with the first one's.
    // Fully qualified: this file imports android.os.Process, which shadows java.lang.Process.
    @Volatile private var logcatProcess: java.lang.Process? = null
    private var logTailThread: Thread? = null
    private var portProbeThread: Thread? = null
    private val logcatRunning = AtomicBoolean(false)
    private val logcatGeneration = java.util.concurrent.atomic.AtomicInteger(0)
    // Set once the latest.log tail has delivered a line: the server's logger is up, so the
    // early logcat bridge hands the console over to the tail.
    @Volatile private var latestLogHasOutput = false
    private val logTailRunning = AtomicBoolean(false)
    private val portProbeRunning = AtomicBoolean(false)
    private val tunnelStarted = AtomicBoolean(false)
    private val serverReadyHandled = AtomicBoolean(false)
    private val stopInProgress = AtomicBoolean(false)
    private var keepListenerRunningManual = false
    private val keepListenerRunning: Boolean
        get() = com.pockethost.app.data.preferences.AppPreferences(applicationContext).alwaysAliveBackground || keepListenerRunningManual
    private val logBuffer = ArrayDeque<String>(1000)
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val localPortCheckExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "local-port-check").apply { isDaemon = true }
    }
    @Volatile private var relayJob: Job? = null
    private var relayReconnectJob: Job? = null
    private var currentServerPort: Int = 25565
    private val relayManager by lazy { RelayManager(this) }
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var wifiLowLatencyLock: WifiManager.WifiLock? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    @Volatile private var stopReason: String = "unknown"
    private var autoRecoverWindowStartMs: Long = 0L
    private var autoRecoverAttempts: Int = 0
    private var autoRestartEnabled: Boolean = false
    private var lastNotificationText: String = ""
    private var lastNotificationUpdateMs: Long = 0L
    private var serverReadyNotificationShown = false
    private var serverStartTimeMillis: Long = 0L
    @Volatile private var serviceLaunchRealtimeMs: Long = 0L
    @Volatile private var hasSeenServerStarting = false
    @Volatile private var bedrockBridgeFailureReported = false

    private var pendingRestartVersionId: String? = null
    private var pendingRestartWorldName: String? = null
    private val relayStatusPlayerCount = AtomicInteger(0)
    private val relayOnlinePlayers = linkedSetOf<String>()
    private val relayHealthFailures = AtomicInteger(0)
    @Volatile private var relayStatusJob: Job? = null
    @Volatile private var widgetUpdateJob: Job? = null
    private val chunkResendJobs = ConcurrentHashMap<String, Job>()
    private val currentPlayersList = mutableListOf<PlayerInfo>()
    private var dashboardStatusJob: Job? = null
    private var dashboardCommandListener: com.pockethost.app.broadcast.DashboardCommandListener? = null
    private var currentServerTps: Float = 20.0f
    @Volatile private var serverReadyFallbackJob: Job? = null
    private var bootStartedAt: Long = 0L
    /** Set to true when MIUI's socket permission check message is seen in the output. */
    private var miuiSocketCheckSeen = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        autoRestartEnabled = AppPreferences(applicationContext).autoRestart
        serverStartTimeMillis = AppPreferences(applicationContext).lastStartTimestamp
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun onTimeout(startId: Int) {
        currentVersionId?.let {
            sendEvent(it, EVENT_OUTPUT, "[PocketHost] Background time limit reached (6h). Stopping server gracefully to comply with Android 15 policies...")
        }
        // Force immediate stop sequence
        stopServer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val initialText = if (action == ACTION_START_LISTENER) {
            ServerStage.DASHBOARD_LISTENER_ACTIVE.notificationText
        } else {
            ServerStage.STARTING_SERVER.notificationText
        }

        if (!isForegroundServiceStarted) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(NOTIFICATION_ID, createForegroundNotification(initialText), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                } else {
                    startForeground(NOTIFICATION_ID, createForegroundNotification(initialText))
                }
                isForegroundServiceStarted = true
            } catch (e: Exception) {
                android.util.Log.e("ServerHostService", "startForeground failed: ${e.message}")
            }
        } else {
            updateNotification(initialText, force = true)
        }

        val appPrefs = AppPreferences(applicationContext)

        if (action == ACTION_START_LISTENER) {
            keepListenerRunningManual = false
            val requestedVersionId = intent?.getStringExtra(EXTRA_VERSION_ID).orEmpty().trim()
            val versionId = requestedVersionId.ifBlank { getPersistedActiveVersion(applicationContext) }
            val worldName = intent?.getStringExtra(EXTRA_WORLD_NAME).orEmpty().trim().ifBlank { appPrefs.selectedWorld.ifBlank { "world" } }
            currentVersionId = versionId
            currentWorldName = worldName
            
            if (!serverReadyHandled.get()) {
                startDashboardStatusHeartbeat(versionId)
            }
            updateNotification(ServerStage.DASHBOARD_LISTENER_ACTIVE, force = true)
            return START_STICKY
        }

        if (intent?.action == ACTION_START) {
            keepListenerRunningManual = com.pockethost.app.data.preferences.AppPreferences(applicationContext).alwaysAliveBackground
            com.pockethost.app.data.preferences.AppPreferences(applicationContext).isUserStopped = false
        }
        val requestedVersionId = intent?.getStringExtra(EXTRA_VERSION_ID).orEmpty().trim()
        val versionId = if (intent?.action == ACTION_START && requestedVersionId.isBlank()) {
            com.pockethost.app.data.preferences.AppPreferences(applicationContext).selectedVersion
        } else {
            requestedVersionId
        }
        if (intent?.action == ACTION_START && versionId.isNotBlank()) {
            val startWorld = intent.getStringExtra(EXTRA_WORLD_NAME).orEmpty().trim().ifBlank { appPrefs.selectedWorld.ifBlank { "world" } }
            persistRuntimeState(applicationContext, versionId, startWorld, RUNTIME_STATE_STARTING)
        }

        if (intent?.action == ACTION_STOP) {
            keepListenerRunningManual = intent.getBooleanExtra("keep_listener_alive", false)
            stopReason = "user"
            val prefs = com.pockethost.app.data.preferences.AppPreferences(applicationContext)
            prefs.isUserStopped = true
            autoRecoverAttempts = 0
            autoRecoverWindowStartMs = 0L
            pendingRestartVersionId = null
            pendingRestartWorldName = null
            val activeVer = currentVersionId?.takeIf { it.isNotBlank() } ?: getPersistedActiveVersion(applicationContext)
            if (activeVer.isNotBlank()) {
                persistRuntimeState(applicationContext, activeVer, activeWorldNameOrDefault(), RUNTIME_STATE_OFFLINE)
            }
            updateNotification(ServerStage.STOPPING, force = true)
            pushWidgetUpdate("stopping")
            if (activeVer.isNotBlank()) {
                sendEvent(activeVer, EVENT_OUTPUT, "[PocketHost] Stop requested.")
            }
            stopServer()
            return START_NOT_STICKY
        }


        if (intent?.action == ACTION_RESTART) {
            val activeVersionId = currentVersionId ?: getPersistedActiveVersion(applicationContext)
            val activeWorldName = currentWorldName ?: getPersistedActiveWorld(applicationContext)
            val runtimeState = if (activeVersionId.isBlank()) {
                RUNTIME_STATE_OFFLINE
            } else {
                getPersistedRuntimeState(applicationContext, activeVersionId)
            }
            if (activeVersionId.isBlank() ||
                activeWorldName.isBlank() ||
                (runtimeState != RUNTIME_STATE_RUNNING && runtimeState != RUNTIME_STATE_STARTING)
            ) {
                return START_NOT_STICKY
            }
            stopReason = "restart"
            autoRecoverAttempts = 0
            autoRecoverWindowStartMs = 0L
            pendingRestartVersionId = activeVersionId
            pendingRestartWorldName = activeWorldName
            updateNotification(ServerStage.STOPPING, force = true)
            pushWidgetUpdate("stopping")
            sendEvent(activeVersionId, EVENT_OUTPUT, "[PocketHost] Restart requested.")
            stopServer()
            return START_NOT_STICKY
        }

        if (intent?.action == ACTION_CONSOLE_COMMAND) {
            val command = intent.getStringExtra(EXTRA_CONSOLE_COMMAND).orEmpty().trim()
            if (command.isNotBlank()) {
                if (ServerLauncher.hasActiveExternalProcess()) {
                    ServerLauncher.sendCommand(command)
                } else {
                    val targetVersion = currentVersionId?.takeIf { it.isNotBlank() } ?: getPersistedActiveVersion(applicationContext)
                    serviceScope.launch {
                        val resp = sendRconCommandSuspended(command)
                        if (resp.isNotBlank() && resp != "[OK]") {
                            sendEvent(targetVersion, EVENT_OUTPUT, resp)
                        } else if (resp == "[OK]") {
                            sendEvent(targetVersion, EVENT_OUTPUT, "[Server] Command executed.")
                        }
                    }
                }
            }
            return START_STICKY
        }

        if (intent?.action == ACTION_RECONNECT_RELAY) {
            val skipUnregister = intent.getBooleanExtra(EXTRA_SKIP_RELAY_UNREGISTER, false)
            val requestedRelayHost = intent.getStringExtra(EXTRA_RELAY_HOST).orEmpty().trim()
            reconnectRelay(
                skipUnregister = skipUnregister,
                requestedRelayHost = requestedRelayHost.takeIf { it.isNotBlank() }
            )
            return START_STICKY
        }

        // If service restarts without explicit action but server was running, restart it
        val hasExplicitStart = intent?.action == ACTION_START && versionId.isNotBlank()
        val requestedWorldName = intent?.getStringExtra(EXTRA_WORLD_NAME).orEmpty().trim()
        val selectedWorld = if (intent?.action == ACTION_START && requestedWorldName.isBlank()) {
            appPrefs.selectedWorld
        } else {
            ""
        }
        val worldName = (requestedWorldName.takeIf { it.isNotBlank() }
            ?: selectedWorld.takeIf { it.isNotBlank() }
            ?: getPersistedActiveWorld(applicationContext))
            .trim()
            .ifBlank { "world" }
        val activeVersion = if (!hasExplicitStart) getPersistedActiveVersion(applicationContext) else versionId

        if (hasExplicitStart) {
            val prefs = AppPreferences(applicationContext)
            prefs.isUserStopped = false
            prefs.consecutiveCrashCount = 0
            prefs.lastStartTimestamp = System.currentTimeMillis()
        }

        if (!hasExplicitStart) {
            // Service restarted after being killed, check if server was actually active
            val prefs = AppPreferences(applicationContext)
            val now = System.currentTimeMillis()
            val previousRuntimeState = if (activeVersion.isNotBlank()) getPersistedRuntimeState(applicationContext, activeVersion) else RUNTIME_STATE_OFFLINE
            if (activeVersion.isBlank() ||
                worldName.isBlank() ||
                previousRuntimeState == RUNTIME_STATE_OFFLINE ||
                stopReason == "user" ||
                stopInProgress.get() ||
                prefs.isUserStopped
            ) {
                android.util.Log.i("ServerHostService", "Service restarted without explicit START but server is offline, user-stopped, or stopping. Suppressing auto-resume.")
                try {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } catch (e: Exception) {
                    android.util.Log.e("PocketHost", "Error stopping foreground: ${e.message}")
                }
                stopSelf()
                return START_NOT_STICKY
            }
            if (previousRuntimeState == RUNTIME_STATE_STARTING && now - prefs.lastStartTimestamp < 60_000L) {
                prefs.consecutiveCrashCount += 1
            } else if (previousRuntimeState != RUNTIME_STATE_STARTING) {
                prefs.consecutiveCrashCount = 0
            }
            prefs.lastStartTimestamp = now
            if (prefs.consecutiveCrashCount > 2) {
                persistRuntimeState(applicationContext, activeVersion, worldName, RUNTIME_STATE_OFFLINE)
                sendEvent(activeVersion, EVENT_SERVER_CRASHED, "Server crashed repeatedly during startup.")
                com.pockethost.app.notification.NotificationHelper.notifyServerCrashLoop(applicationContext)
                try {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } catch (e: Exception) {
                    android.util.Log.e("PocketHost", "Error stopping foreground: ${e.message}")
                }
                stopSelf()
                return START_NOT_STICKY
            }
            return resumeServer(activeVersion, worldName)
        }

        if (!hasExplicitStart || versionId.isBlank()) {
            return START_STICKY
        }

        if (!isEulaAccepted(worldName)) {
            return abortStartForEula(versionId, worldName)
        }

        if (isLaunching || stopInProgress.get()) {
            sendEvent(versionId, EVENT_OUTPUT, if (stopInProgress.get()) "[PocketHost] Server is stopping. Please wait for shutdown to finish." else "[PocketHost] Server is already starting.")
            return START_STICKY
        }

        val existingServerPort = resolveServerPort(worldName)
        val hasLiveProcess = serverProcess?.isAlive == true || ServerLauncher.hasActiveExternalProcess()
        if (hasLiveProcess && isLocalServerPortOpen(existingServerPort) && serverReadyHandled.get()) {
            return attachToExistingServer(versionId, worldName, existingServerPort, "start request")
        }

        isLaunching = true
        serviceLaunchRealtimeMs = SystemClock.elapsedRealtime()
        bedrockBridgeFailureReported = false
        hasSeenServerStarting = false
        currentVersionId = versionId
        currentWorldName = worldName
        stopReason = "unknown"
        com.pockethost.app.data.preferences.AppPreferences(applicationContext).isUserStopped = false
        serverReadyNotificationShown = false
        serverReadyHandled.set(false)
        setServerReadyState(false)
        miuiSocketCheckSeen = false
        relayStatusPlayerCount.set(0)
        synchronized(relayOnlinePlayers) { relayOnlinePlayers.clear() }
        cancelChunkResendJobs()
        persistRuntimeState(applicationContext, versionId, worldName, RUNTIME_STATE_STARTING)
        persistPlayerCount(applicationContext, 0)
        serviceScope.launch { AppPreferencesStore.setServerStartedAtMillis(applicationContext, 0L) }
        serverStartTimeMillis = 0L
        resetNotificationState(ServerStage.STARTING_SERVER.notificationText)
        pushWidgetUpdate()
        startWidgetUpdateHeartbeat()
        synchronized(currentPlayersList) { currentPlayersList.clear() }
        startDashboardStatusHeartbeat(versionId)
        sendEvent(versionId, EVENT_OUTPUT, "[PocketHost] Starting server...")
        if (!isForegroundServiceStarted) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(NOTIFICATION_ID, createForegroundNotification(ServerStage.STARTING_SERVER.notificationText), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                } else {
                    startForeground(NOTIFICATION_ID, createForegroundNotification(ServerStage.STARTING_SERVER.notificationText))
                }
                isForegroundServiceStarted = true
            } catch (e: Exception) {
                android.util.Log.e("ServerHostService", "startForeground failed: ${e.message}")
            }
        } else {
            updateNotification(ServerStage.STARTING_SERVER.notificationText, force = true)
        }
        // On Android 12+ the JVM runs in-process via JNI (NativeLauncher).
        // launcher.c pipes JVM stdout/stderr into logcat at full native speed.
        // A full startLogcatBridge() would re-broadcast every one of those lines on the
        // main thread, causing a broadcast flood (~100s of events/sec), and
        // startServerLogTail() delivers the server's output once its logger is writing
        // logs/latest.log. The early bridge covers only the gap before that.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            startLogcatBridge(versionId)
        } else {
            startLogcatBridge(versionId, earlyStartupOnly = true)
        }



        val serverDir = com.pockethost.app.service.ServerFileManager.getServerDir(applicationContext, worldName)
        isNewWorld = !resolveLevelDir(serverDir).exists()

        runCatching {
            val nukkitLog = File(serverDir, "logs/server.log")
            if (nukkitLog.exists()) nukkitLog.delete()
            val latestLog = File(serverDir, "logs/latest.log")
            if (latestLog.exists()) {
                // Start the tail on an empty log so an old "Done" line can't mark this launch
                // ready, but keep the last session (often the crash being reported) around.
                val previousLog = File(serverDir, "logs/previous-session.log")
                if (!latestLog.renameTo(previousLog)) {
                    runCatching { latestLog.copyTo(previousLog, overwrite = true) }
                    latestLog.delete()
                }
                if (latestLog.exists()) {
                    runCatching { java.io.FileOutputStream(latestLog).close() }
                }
            }
        }

        startServerLogTail(versionId, worldName)
        val serverPort = resolveServerPort(worldName)
        currentServerPort = serverPort
        startPortProbe(versionId, serverPort)
        scheduleServerReadyFallback(versionId)
        acquireWakeLock()



        launchJob = serviceScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            migrateLegacyStorageIfNeeded(versionId, worldName)
            AfkDummyPluginSync.syncWorldFromDatabase(applicationContext, worldName)
            val configRepo = com.pockethost.app.data.repository.ServerConfigRepository(applicationContext).apply {
                setWorldNameOverride(worldName)
            }
            val config = configRepo.loadConfig()
            // Use versionId as fallback if pocketcraft-game-version was never written
            val resolvedGameVersion = config.gameVersion.ifBlank { versionId }
            val runtime = JreExtractor.runtimeForServer(config.serverType, resolvedGameVersion)
            val serverDir = com.pockethost.app.service.ServerFileManager.getServerDir(applicationContext, worldName)
            val targetFile = com.pockethost.app.service.ServerFileManager.getServerJarFile(applicationContext, resolvedGameVersion, config.serverType)
            // Ensure parent directory exists before resolveJar tries to write the file
            targetFile.parentFile?.mkdirs()
            android.util.Log.d("ServerHostService", "[startServer] versionId=$versionId gameVersion=${config.gameVersion} resolvedGameVersion=$resolvedGameVersion serverType=${config.serverType} targetFile=${targetFile.absolutePath} exists=${targetFile.exists()} isDir=${targetFile.isDirectory}")

            try {
                val jarFile = prepareRuntimeAndJar(
                    versionId = versionId,
                    runtime = runtime,
                    config = config,
                    resolvedGameVersion = resolvedGameVersion,
                    targetFile = targetFile,
                    serverDir = serverDir
                )
                val launchTarget = com.pockethost.app.service.ServerFileManager.readLaunchTarget(serverDir)
                updateNotification(ServerStage.CHECKING_PLUGINS, force = true)
                updateNotification(ServerStage.STARTING_SERVER, force = true)

                withContext(kotlinx.coroutines.Dispatchers.IO) {
                    ServerLauncher(applicationContext).startServer(
                        worldName = worldName,
                        versionId = versionId,
                        jarPath = jarFile.absolutePath,
                        launchMode = launchTarget?.mode ?: com.pockethost.app.service.ServerFileManager.LaunchMode.JAR,
                        runtime = runtime,
            onOutput = { line ->
                handleObservedOutputLine(versionId, line)
            },
            onError = { line ->
                sendEvent(versionId, EVENT_OUTPUT, line)
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
                relayJob?.cancel()
                relayJob = null
                relayStatusJob?.cancel()
                relayStatusJob = null
                widgetUpdateJob?.cancel()
                widgetUpdateJob = null
                serverReadyFallbackJob?.cancel()
                serverReadyFallbackJob = null

                val shouldAutoRecover = exitCode != 0 && shouldScheduleAutoRecover(versionId)
                if (shouldAutoRecover) {
                    relayManager.disconnect()
                } else if (!stopInProgress.get()) {
                    markServerOfflineImmediately()
                    serviceScope.launch(Dispatchers.IO) {
                        try {
                            kotlinx.coroutines.withTimeout(5_000L) {
                                relayManager.unregister()
                            }
                        } catch (e: Exception) {
                            relayManager.disconnect()
                        }
                    }
                }

                tunnelStarted.set(false)
                serverReadyHandled.set(false)
                setServerReadyState(false)
                stopLogcatBridge()
                stopServerLogTail()
                stopPortProbe()

                if (exitCode != 0 && stopReason != "user") {
                    if (exitCode == 127) {
                        sendEvent(versionId, EVENT_SERVER_CRASHED, "exit_code=127")
                        sendEvent(versionId, EVENT_ERROR, "[PocketHost] Exit code 127: Java binary not executable on this device.")
                        if (ServerLauncher.allStorageNoexecDetected) {
                            sendEvent(versionId, EVENT_ERROR, "[PocketHost] [DEVICE RESTRICTION] Samsung Knox security policy on this device marks ALL app storage as non-executable. PocketCraft cannot launch a Java server under these restrictions. Clearing cache will not help — this is a device-level OS policy.")
                        } else {
                            sendEvent(versionId, EVENT_ERROR, "[PocketHost] [HINT] Go to Settings \u2192 Apps \u2192 PocketCraft \u2192 Storage \u2192 Clear Cache, then restart.")
                        }
                    } else {
                        sendEvent(versionId, EVENT_SERVER_CRASHED, "exit_code=$exitCode")
                        sendEvent(versionId, EVENT_ERROR, "[PocketHost] Server exited unexpectedly (code $exitCode).")
                    }
                }

                if (shouldAutoRecover) {
                    val attempt = autoRecoverAttempts
                    val delayMs = (attempt * 4000L).coerceAtMost(15_000L)
                    sendEvent(
                        versionId,
                        EVENT_OUTPUT,
                        "[PocketHost] Server exited unexpectedly. Auto-restarting in ${delayMs / 1000}s (attempt $attempt/$AUTO_RECOVER_MAX_ATTEMPTS)..."
                    )
                    val recycledForRecover = NativeLauncher.hasInProcessJvmRunInThisProcess &&
                        scheduleProcessRestart(versionId, activeWorldNameOrDefault(), delayMs)
                    if (recycledForRecover) {
                        android.util.Log.i("ServerHostService", "Recycling :server process for auto-recover in ${delayMs}ms.")
                        android.os.Process.killProcess(android.os.Process.myPid())
                    } else {
                        if (NativeLauncher.hasInProcessJvmRunInThisProcess) {
                            sendEvent(
                                versionId,
                                EVENT_OUTPUT,
                                "[PocketHost] Could not schedule an automatic restart. Reopen PocketHost to start the server again."
                            )
                        }
                        serviceScope.launch {
                            kotlinx.coroutines.delay(delayMs)
                            if (stopReason != "user") {
                                start(applicationContext, versionId, activeWorldNameOrDefault())
                            }
                        }
                    }
                }

                persistPublicAddress(applicationContext, "")
                persistRuntimeState(applicationContext, versionId, activeWorldNameOrDefault(), RUNTIME_STATE_OFFLINE)
                pushWidgetUpdate()
                serverReadyNotificationShown = false
                serverReadyHandled.set(false)
                setServerReadyState(false)
                releaseWakeLock()
                if (shouldAutoRecover) {
                    updateNotification("Recovering server...", force = true)
                } else {
                    updateNotification(ServerStage.STOPPING, force = true)
                }
                if (!stopInProgress.get()) {
                    stopInProgress.set(false)
                    if (NativeLauncher.hasInProcessJvmRunInThisProcess) {
                        // Killing the process is only safe once something is scheduled to bring it
                        // back; without that the listener would simply disappear.
                        val relaunchPending = if (keepListenerRunning) {
                            scheduleKeepAliveRestart(500L).also { scheduled ->
                                android.util.Log.i(
                                    "ServerHostService",
                                    if (scheduled) "Recycling :server process to reset JVM state after server exit."
                                    else "Keep-alive restart could not be scheduled; leaving process alive."
                                )
                            }
                        } else {
                            try { stopSelf() } catch (_: Throwable) {}
                            true
                        }
                        if (relaunchPending) {
                            android.os.Process.killProcess(android.os.Process.myPid())
                        }
                    }
                }
            }
        )
                }
            } catch (e: Exception) {
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    sendEvent(versionId, EVENT_ERROR, "[PocketHost] Server JAR is not ready: ${e.message}")
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
                    pushWidgetUpdate()
                }
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        isServiceRunning = false
        stopDashboardStatusAndClear()
        relayReconnectJob?.cancel()
        relayReconnectJob = null
        widgetUpdateJob?.cancel()
        widgetUpdateJob = null
        localPortCheckExecutor.shutdownNow()
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {}
        // Capture this BEFORE forceTerminateHostedServer(), which calls
        // ServerLauncher.requestForceStop() and nulls its activeExternalProcess —
        // after that, hasActiveExternalProcess() always reports false, which would
        // make inProcessRuntime always true below regardless of how the server
        // actually ran.
        val inProcessRuntime = !ServerLauncher.hasActiveExternalProcess()
        // If this service is being destroyed unexpectedly, avoid leaving
        // a detached JVM process running without relay/control.
        forceTerminateHostedServer()
        // Clean up relay registration on service destruction
        runBlocking {
            runCatching {
                kotlinx.coroutines.withTimeout(2000L) {
                    relayManager.unregister()
                }
            }.onFailure {
                relayManager.disconnect()
            }
        }

        persistPublicAddress(applicationContext, "")
        val activeVer = currentVersionId?.takeIf { it.isNotBlank() } ?: getPersistedActiveVersion(applicationContext)
        persistRuntimeState(applicationContext, activeVer, activeWorldNameOrDefault(), RUNTIME_STATE_OFFLINE)
        serverReadyFallbackJob?.cancel()
        serverReadyFallbackJob = null
        setServerReadyState(false)
        releaseWakeLock()
        // Ensure widget is updated to OFFLINE when the service is destroyed
        pushWidgetUpdate(applicationContext)

        if (inProcessRuntime) {
            android.util.Log.i("PocketHost", "Service destroyed. Killing :server process to allow fresh JVM launch on next start.")
            android.os.Process.killProcess(android.os.Process.myPid())
        }

        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Keep service lifecycle independent from recent-task UI removal.
        // This avoids races where user-initiated shutdown is misread as a crash/restart flow.
        val isUserStopped = AppPreferences(applicationContext).isUserStopped
        val isServerActive = (serverProcess?.isAlive == true || serverReadyHandled.get()) && !stopInProgress.get() && stopReason != "user" && !isUserStopped
        if (isServerActive || (keepListenerRunning && !isUserStopped)) {
            val restartServiceIntent = Intent(applicationContext, ServerHostService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_VERSION_ID, currentVersionId.orEmpty())
                putExtra(EXTRA_WORLD_NAME, currentWorldName.orEmpty())
            }
            val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_ONE_SHOT
            }
            val pendingIntent = PendingIntent.getService(
                applicationContext,
                1002,
                restartServiceIntent,
                pendingIntentFlags
            )
            val alarmManager = applicationContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            alarmManager?.set(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                android.os.SystemClock.elapsedRealtime() + 1000L,
                pendingIntent
            )
            android.util.Log.i("ServerHostService", "Scheduled alarm to auto-restart service after task removal.")
        } else {
            android.util.Log.i("ServerHostService", "Task removed while server is offline or user stopped. Skipping auto-restart alarm.")
        }
    }

    private fun stopServer() {
        if (!stopInProgress.compareAndSet(false, true)) return
        runCatching {
            if (NativeLauncher.loadLibrary()) {
                NativeLauncher.notifyShutdownStarted()
            }
        }
        stopDashboardStatusAndClear()

        launchJob?.cancel()
        launchJob = null
        serverReadyFallbackJob?.cancel()
        serverReadyFallbackJob = null
        markServerStoppingState()
        CoroutineScope(Dispatchers.IO).launch {
            val inProcessRuntime = !ServerLauncher.hasActiveExternalProcess()
            try {
                try {
                    kotlinx.coroutines.withTimeout(5_000L) {
                        relayManager.unregister()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ServerHostService", "Failed to unregister relay before JVM stop: ${e.message}")
                    relayManager.disconnect()
                }

                // Start the grace period timer before we attempt any blocking RCON commands.
                val isServerReady = serverReadyHandled.get()
                val deadline = SystemClock.elapsedRealtime() + (if (isServerReady) STOP_GRACE_PERIOD_MS else 1000L)

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
                        android.util.Log.e("PocketHost", "Failed to write stop signal: " + e.message)
                    }
                    runCatching { sendRconStop() }
                    ServerLauncher.sendCommand("stop")
                } else {
                    ServerLauncher.sendCommand("stop")
                }

                // Keep shutdown responsive; wait up to 15s for Paper to save chunks and exit cleanly.
                while (SystemClock.elapsedRealtime() < deadline) {
                    if (!isLaunching) { // isLaunching is set to false in onStopped
                        break
                    }
                    delay(250)
                }

            } catch (e: Exception) {
                android.util.Log.e("PocketHost", "Error during stop: ${e.message}")
            } finally {
                forceTerminateHostedServer()
                waitForLocalServerPortClosed(currentServerPort, 15_000L)

                try {
                    relayManager.disconnect()
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
                    persistPlayerCount(applicationContext, 0)
                    sendEvent(versionId, EVENT_STOPPED, "[INFO] Server stopped.")
                }
                AppPreferencesStore.setServerStartedAtMillis(applicationContext, 0L)
                serverStartTimeMillis = 0L
                relayStatusPlayerCount.set(0)
                synchronized(relayOnlinePlayers) { relayOnlinePlayers.clear() }
                cancelChunkResendJobs()
                try {
                    ServerHostService.pushWidgetUpdate(applicationContext)
                } catch (e: Exception) {
                    android.util.Log.e("ServerHostService", "Widget update failed on stop: ${e.message}")
                }

                sendBroadcast(Intent(EVENT_STOPPED).setPackage(packageName))

                val restartVersionId = pendingRestartVersionId
                val restartWorldName = pendingRestartWorldName
                pendingRestartVersionId = null
                pendingRestartWorldName = null

                if (!restartVersionId.isNullOrBlank() && !restartWorldName.isNullOrBlank()) {
                    android.util.Log.i("ServerHostService", "Restart requested: launching server $restartVersionId for world $restartWorldName...")
                    stopInProgress.set(false)
                    isLaunching = false
                    if (NativeLauncher.hasInProcessJvmRunInThisProcess) {
                        // The in-process JVM cannot be booted twice in one process, so a restart
                        // has to recycle — but only once the alarm that brings it back exists.
                        if (scheduleProcessRestart(restartVersionId, restartWorldName, 1000L)) {
                            android.util.Log.i("ServerHostService", "Recycling :server process on restart to allow clean JVM boot.")
                            android.os.Process.killProcess(android.os.Process.myPid())
                            return@launch
                        }
                        android.util.Log.e("ServerHostService", "Restart alarm could not be scheduled; not recycling.")
                        sendEvent(
                            restartVersionId,
                            EVENT_OUTPUT,
                            "[PocketHost] Restart could not be scheduled automatically. Start the server again from the app."
                        )
                    }
                    delay(500L)
                    start(applicationContext, restartVersionId, restartWorldName)
                    return@launch
                }

                if (keepListenerRunning) {
                    if (NativeLauncher.hasInProcessJvmRunInThisProcess) {
                        if (scheduleKeepAliveRestart(500L)) {
                            android.util.Log.i("ServerHostService", "Recycling :server process to reset JVM state while keeping listener alive.")
                            android.os.Process.killProcess(android.os.Process.myPid())
                            return@launch
                        }
                        android.util.Log.e("ServerHostService", "Keep-alive alarm could not be scheduled; keeping current process.")
                    }
                    val notification = createForegroundNotification(ServerStage.DASHBOARD_LISTENER_ACTIVE.notificationText)
                    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    manager?.notify(NOTIFICATION_ID, notification)
                } else {
                    try {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    } catch (e: Throwable) {
                        android.util.Log.e("PocketHost", "Error stopping foreground: ${e.message}")
                    }
                    try {
                        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                        manager?.cancel(NOTIFICATION_ID)
                    } catch (_: Throwable) {}

                    try {
                        stopSelf()
                    } catch (e: Throwable) {
                        android.util.Log.e("PocketHost", "Error in stopSelf: ${e.message}")
                    }
                    if (NativeLauncher.hasInProcessJvmRunInThisProcess) {
                        android.util.Log.i("ServerHostService", "Terminating :server process on stop to clear in-process JVM.")
                        android.os.Process.killProcess(android.os.Process.myPid())
                    }
                }

                stopInProgress.set(false)
            }
        }
    }

    private fun markServerStoppingState() {
        relayReconnectJob?.cancel()
        relayReconnectJob = null
        relayJob?.cancel()
        relayJob = null
        relayStatusJob?.cancel()
        relayStatusJob = null
        tunnelStarted.set(false)
        relayManager.disconnect()
        setServerReadyState(false)
        serverReadyHandled.set(false)
        stopReason = "user"
        AppPreferences(applicationContext).isUserStopped = true
        persistPublicAddress(applicationContext, "")
        currentVersionId?.let { versionId ->
            persistRuntimeState(applicationContext, versionId, activeWorldNameOrDefault(), RUNTIME_STATE_STOPPING)
            persistPlayerCount(applicationContext, 0)
            sendEvent(versionId, EVENT_OUTPUT, "[PocketHost] Server stopping...")
        }
        serviceScope.launch { AppPreferencesStore.setServerStartedAtMillis(applicationContext, 0L) }
        serverStartTimeMillis = 0L
        serviceLaunchRealtimeMs = 0L
        pushWidgetUpdate("stopping")
    }

    private fun markServerOfflineImmediately() {
        relayReconnectJob?.cancel()
        relayReconnectJob = null
        relayJob?.cancel()
        relayJob = null
        relayStatusJob?.cancel()
        relayStatusJob = null
        tunnelStarted.set(false)
        relayManager.disconnect()
        setServerReadyState(false)
        serverReadyHandled.set(false)
        // Unlike markServerStoppingState(), this runs from non-user-initiated paths
        // (crash / exhausted auto-recover) — do not mark it as a user stop, and keep
        // whatever stopReason the caller already set (e.g. "crashed") so downstream
        // checks like `if (stopReason != "user") restart(...)` still work correctly.
        persistPublicAddress(applicationContext, "")
        currentVersionId?.let { versionId ->
            persistRuntimeState(applicationContext, versionId, activeWorldNameOrDefault(), RUNTIME_STATE_OFFLINE)
            persistPlayerCount(applicationContext, 0)
            sendEvent(versionId, EVENT_OUTPUT, "[PocketHost] Server stopping...")
        }
        serviceScope.launch { AppPreferencesStore.setServerStartedAtMillis(applicationContext, 0L) }
        serverStartTimeMillis = 0L
        serviceLaunchRealtimeMs = 0L
        pushWidgetUpdate("stopping")
    }

    private suspend fun waitForLocalServerPortClosed(port: Int, timeoutMs: Long) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (!isLocalServerPortOpen(port)) return
            delay(200)
        }
    }

    private suspend fun prepareRuntimeAndJar(
        versionId: String,
        runtime: com.pockethost.app.setup.JreExtractor.RuntimeSpec,
        config: com.pockethost.app.data.model.ServerConfig,
        resolvedGameVersion: String,
        targetFile: File,
        serverDir: File
    ): File = coroutineScope {
        val diskProps = com.pockethost.app.service.ServerPropertiesHelper.readProperties(serverDir, persistDefaults = false)
        val diskLaunchTarget = com.pockethost.app.service.ServerFileManager.readLaunchTarget(serverDir)
        val effectiveServerType = if (
            com.pockethost.app.data.model.ServerType.fromString(diskProps.getProperty("pocketcraft-server-type")) ==
            com.pockethost.app.data.model.ServerType.MODPACK &&
            diskLaunchTarget != null
        ) {
            com.pockethost.app.data.model.ServerType.MODPACK
        } else {
            config.serverType
        }
        val effectiveCustomJarPath = diskProps.getProperty("pocketcraft-custom-jar-path")
            ?: diskProps.getProperty("pocketcraft-modpack-id")
            ?: config.customJarPath
        val effectiveGameVersion = diskProps.getProperty("pocketcraft-game-version")
            ?.takeIf { it.isNotBlank() }
            ?: resolvedGameVersion

        updateNotification(ServerStage.EXTRACTING_JRE, force = true)
        val runtimeJob = async { ensureRuntimeExtracted(versionId, runtime) }
        val jarJob = async {
            var resolvedJar: File? = null
            com.pockethost.app.server.ServerJarManager.resolveJar(
                serverType = effectiveServerType,
                gameVersion = effectiveGameVersion,
                customJarPath = effectiveCustomJarPath,
                targetFile = targetFile,
                serverDir = serverDir,
                onProgress = { pct ->
                    updateNotification("${ServerStage.DOWNLOADING_SERVER.notificationText} $pct%", force = false)
                    sendEvent(versionId, EVENT_OUTPUT, "Checking server JAR: $pct%")
                }
            ).collect { resolvedJar = it }
            resolvedJar ?: throw IllegalStateException("Server JAR could not be resolved")
        }
        runtimeJob.await()
        val jar = jarJob.await()
        val wName = currentWorldName ?: "world"
        com.pockethost.app.server.BundledPluginInstaller.installBundledPlugins(applicationContext, serverDir)
        com.pockethost.app.service.PluginManager.enforceBedrockBridgeLocalConfig(applicationContext, wName)
        jar
    }

    /**
     * Most callers run on [serviceScope], which is the main thread, and Android throws
     * NetworkOnMainThreadException for any socket there. runCatching used to turn that
     * into "port closed", so readiness was never promoted from the port probe or the
     * fallback poll and the internet relay only opened after a manual retry. Hop off
     * the main thread for the probe; a loopback connect answers in milliseconds.
     */
    private fun isLocalServerPortOpen(port: Int): Boolean {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            return probeLocalServerPort(port)
        }
        return runCatching {
            localPortCheckExecutor.submit<Boolean> { probeLocalServerPort(port) }
                .get(1_500L, java.util.concurrent.TimeUnit.MILLISECONDS)
        }.getOrDefault(false)
    }

    private fun probeLocalServerPort(port: Int): Boolean {
        val ipv4Ok = runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", port), 250)
                true
            }
        }.getOrDefault(false)
        if (ipv4Ok) return true

        return runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("::1", port), 250)
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

        val extPid = getExternalJvmPid(applicationContext)
        if (extPid > 0 && extPid != android.os.Process.myPid().toLong()) {
            android.util.Log.i("ServerHostService", "Force-killing persisted external JVM process: $extPid")
            runCatching { android.os.Process.killProcess(extPid.toInt()) }
            persistExternalJvmPid(applicationContext, -1L)
        }
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
            runCatching {
                // The exit code alone says nothing about why; the last console lines do.
                val crashlytics = FirebaseCrashlytics.getInstance()
                synchronized(logBuffer) { logBuffer.toList().takeLast(30) }.forEach { line ->
                    crashlytics.log(line.take(500))
                }
                crashlytics.recordException(exception)
            }
        }
    }

    private fun updateNotification(text: String, force: Boolean = false) {
        val cleanText = ConsoleParser.stripAnsi(text).trim()
        if (cleanText.isBlank()) return

        if (!shouldApplyNotificationText(cleanText, force)) return

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

    private fun shouldApplyNotificationText(cleanText: String, force: Boolean): Boolean {
        val normalized = cleanText.lowercase()
        val runtimeState = currentVersionId
            ?.let { getPersistedRuntimeState(applicationContext, it) }
            ?: getPersistedActiveVersion(applicationContext)
                .takeIf { it.isNotBlank() }
                ?.let { getPersistedRuntimeState(applicationContext, it) }
            ?: RUNTIME_STATE_OFFLINE

        val looksLikeStartupText =
            normalized.contains("starting") ||
                normalized.contains("checking") ||
                normalized.contains("preparing") ||
                normalized.contains("extracting") ||
                normalized.contains("downloading") ||
                normalized.contains("finalizing")

        if (runtimeState == RUNTIME_STATE_RUNNING && looksLikeStartupText) {
            return false
        }

        if (serverReadyHandled.get() && !force && looksLikeStartupText) {
            return false
        }

        return true
    }

    private fun currentPlayerCount(): Int = relayStatusPlayerCount.get().coerceAtLeast(0)

    private fun currentMaxPlayers(): Int {
        val worldName = activeWorldNameOrDefault()
        val serverDir = ServerFileManager.getServerDir(applicationContext, worldName)
        val propsFile = File(serverDir, "server.properties")
        val props = Properties()
        if (propsFile.exists()) {
            runCatching { propsFile.inputStream().use(props::load) }
        }
        return props.getProperty("max-players", "10").toIntOrNull()?.coerceIn(1, 50) ?: 10
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

        val stopIntent = Intent(this, ServerHostService::class.java).apply {
            action = ACTION_STOP
        }
        val restartIntent = Intent(this, ServerHostService::class.java).apply {
            action = ACTION_RESTART
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            100,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val restartPendingIntent = PendingIntent.getService(
            this,
            101,
            restartIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val activeVersion = currentVersionId ?: getPersistedActiveVersion(applicationContext)
        val runtimeState = activeVersion
            .takeIf { it.isNotBlank() }
            ?.let { getPersistedRuntimeState(applicationContext, it) }
            ?: RUNTIME_STATE_OFFLINE
        val isRunning = serverStartTimeMillis > 0L && runtimeState == RUNTIME_STATE_RUNNING
        val playerCount = currentPlayerCount()
        val maxPlayers = currentMaxPlayers()
        val playerSummary = "$playerCount/$maxPlayers players online"
        val title = when (runtimeState) {
            RUNTIME_STATE_RUNNING -> "PocketHost Server Online"
            RUNTIME_STATE_STARTING -> "PocketHost Server Starting"
            else -> "PocketHost Server"
        }
        val body = when (runtimeState) {
            RUNTIME_STATE_RUNNING -> playerSummary
            else -> text.take(100).ifBlank { "Tap to manage your server" }
        }
        val expandedText = when (runtimeState) {
            RUNTIME_STATE_RUNNING -> {
                buildString {
                    append(playerSummary)
                    if (activeVersion.isNotBlank()) {
                        append("\nMinecraft ")
                        append(activeVersion)
                    }
                    if (text.isNotBlank() && text != ServerStage.RUNNING.notificationText) {
                        append("\n")
                        append(text)
                    }
                }
            }
            else -> text
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setContentTitle(title)
            .setContentText(body)
            .setSubText(if (isRunning) activeWorldNameOrDefault() else null)
            .setStyle(NotificationCompat.BigTextStyle().bigText(expandedText))
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(pendingIntent)
            .addAction(0, "Stop", stopPendingIntent)
            .addAction(0, "Restart", restartPendingIntent)

        if (isRunning) {
            builder.setUsesChronometer(true)
            builder.setWhen(serverStartTimeMillis)
            builder.setShowWhen(true)
        }

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
                        "[PocketHost] ${status} (${runtime.displayName})"
                    )
                }
            }
        }.onFailure { error ->
            sendEvent(
                versionId,
                EVENT_ERROR,
                "[PocketHost] Runtime setup failed: ${error.message}"
            )
            throw error
        }
    }

    private val pendingOutputLines = java.util.concurrent.ConcurrentLinkedQueue<String>()
    @Volatile private var outputFlusherActive = false

    private fun sendEvent(versionId: String, type: String, line: String) {
        if (type == EVENT_OUTPUT) {
            pendingOutputLines.add(line)
            if (!outputFlusherActive) {
                outputFlusherActive = true
                serviceScope.launch(Dispatchers.IO) {
                    delay(250)
                    outputFlusherActive = false
                    val lines = mutableListOf<String>()
                    while (true) {
                        val l = pendingOutputLines.poll() ?: break
                        lines.add(l)
                    }
                    if (lines.isNotEmpty()) {
                        val joined = lines.joinToString("\n")
                        sendBroadcast(
                            Intent(ACTION_SERVER_EVENT).apply {
                                setPackage(packageName)
                                putExtra(EXTRA_VERSION_ID, versionId)
                                putExtra(EXTRA_EVENT_TYPE, EVENT_OUTPUT)
                                putExtra(EXTRA_LINE, joined)
                            }
                        )
                    }
                }
            }
            return
        }

        sendBroadcast(
            Intent(ACTION_SERVER_EVENT).apply {
                setPackage(packageName)
                putExtra(EXTRA_VERSION_ID, versionId)
                putExtra(EXTRA_EVENT_TYPE, type)
                putExtra(EXTRA_LINE, line)
            }
        )
        appendLineToFirestoreLogs(line)
    }

    private val firestoreLogQueue = java.util.concurrent.ConcurrentLinkedQueue<String>()
    private val firestoreRecentLines = java.util.Collections.synchronizedList(mutableListOf<String>())
    private var firestoreLogFlusherJob: Job? = null
    @Volatile private var firestoreQuotaExceededUntil = 0L

    private fun appendLineToFirestoreLogs(line: String) {
        // Web Dashboard feature removed in v1.6.0.
        // Disabled log syncing to prevent PERMISSION_DENIED errors and eliminate constant Firestore write quota usage.
    }

    private suspend fun flushLogsToFirestore() {
        firestoreLogQueue.clear()
    }

    /**
     * Forwards this process's logcat output into the console.
     *
     * With [earlyStartupOnly] (Android 12+, where the JVM runs in-process) only launcher.c's two
     * tags are read, and only until the server's own logs/latest.log starts producing lines or
     * the server is ready. Everything the JVM prints before its logger is up reaches logcat and
     * nowhere else: Paperclip downloading and patching, the vanilla bundler, "Error occurred
     * during initialization of VM". Without this a launch that stalls there leaves the console
     * silent until the startup watchdog gives up, and the support ticket shows nothing past
     * "Launching in-process JVM". Log4j console lines are skipped because the latest.log tail
     * delivers them, and stopping at the handover keeps the full-volume stream off this path.
     */
    private fun startLogcatBridge(versionId: String, earlyStartupOnly: Boolean = false) {
        if (logcatRunning.getAndSet(true)) return
        val bridgeStartTime = SystemClock.elapsedRealtime()
        val generation = logcatGeneration.incrementAndGet()
        if (earlyStartupOnly) latestLogHasOutput = false
        logcatThread = Thread {
            val filterSpecs = if (earlyStartupOnly) {
                listOf("$NATIVE_LAUNCHER_LOG_TAG:I", "$JVM_STDOUT_LOG_TAG:V", "*:S")
            } else {
                listOf("*:V")
            }
            val process = try {
                ProcessBuilder(
                    listOf(
                        "logcat",
                        "-T", "1",
                        "--pid=${Process.myPid()}",
                        "-v",
                        "brief"
                    ) + filterSpecs
                ).redirectErrorStream(true).start()
            } catch (e: Exception) {
                android.util.Log.e("ServerHostService", "Failed to start logcat bridge: ${e.message}")
                // Release the latch so a later start attempt is not permanently blocked.
                logcatRunning.set(false)
                return@Thread
            }
            logcatProcess = process
            var endedAtHandover = false

            try {
                process.inputStream.bufferedReader().useLines { lines ->
                    for (raw in lines) {
                        // A retired generation must stop forwarding even if its stream is still open.
                        if (!logcatRunning.get() || logcatGeneration.get() != generation) break
                        if (earlyStartupOnly) {
                            if (latestLogHasOutput || serverReadyHandled.get()) {
                                endedAtHandover = true
                                break
                            }
                            val earlyLine = earlyStartupConsoleLine(raw) ?: continue
                            val isBacklog = (SystemClock.elapsedRealtime() - bridgeStartTime) < 3000L
                            handleObservedOutputLine(versionId, earlyLine, isBacklog)
                            continue
                        }
                        val line = raw.substringAfter(": ", raw).trim()
                        if (line.isBlank()) continue
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
                            continue
                        }
                        val isBacklog = (SystemClock.elapsedRealtime() - bridgeStartTime) < 3000L
                        handleObservedOutputLine(versionId, line, isBacklog)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.d("ServerHostService", "Logcat bridge ended: ${e.message}")
            } finally {
                runCatching { process.destroy() }
                if (logcatGeneration.get() == generation) {
                    logcatProcess = null
                    // The early bridge ends itself at the handover with the latch still held;
                    // release it so a later start (a service resume) can open a bridge again.
                    // No newer bridge can have started while the latch was held.
                    if (endedAtHandover) logcatRunning.set(false)
                }
            }
        }.apply {
            name = "server-logcat-bridge"
            isDaemon = true
            start()
        }
    }

    /**
     * Turns one `-v brief` logcat line from the early bridge into a console line, or null to
     * drop it. JVM output passes through except log4j lines; launcher.c output is limited to
     * its launch steps and its warnings and errors.
     */
    private fun earlyStartupConsoleLine(raw: String): String? {
        // brief format: "I/PocketCraftJVM( 1234): message"
        val tag = raw.substringAfter('/', "").substringBefore('(').trim()
        val message = raw.substringAfter("): ", "").trimEnd()
        if (message.isBlank()) return null
        return when (tag) {
            JVM_STDOUT_LOG_TAG -> if (!latestLogHasOutput) message else message.takeUnless { LOG4J_CONSOLE_LINE.containsMatchIn(it) }
            NATIVE_LAUNCHER_LOG_TAG -> {
                val isProblem = raw.startsWith("E/") || raw.startsWith("W/")
                val isLaunchStep = EARLY_LAUNCHER_MILESTONES.any { message.startsWith(it) }
                if (isProblem || isLaunchStep) "[Launcher] $message" else null
            }
            else -> null
        }
    }

    private fun startServerLogTail(versionId: String, worldName: String = activeWorldNameOrDefault()) {
        if (logTailRunning.getAndSet(true)) return

        val resolvedWorld = worldName.trim().ifBlank { activeWorldNameOrDefault() }
        val serverDir = ServerFileManager.getServerDir(applicationContext, resolvedWorld)
        val latestLog = File(serverDir, "logs/latest.log")
        val tailStartLength = latestLog.takeIf { it.exists() }?.length() ?: 0L
        val initialOffset = tailStartLength

        logTailThread = Thread {
            var offset = initialOffset
            var effectiveTailStart = tailStartLength
            while (logTailRunning.get() && !Thread.currentThread().isInterrupted) {
                try {
                    if (!latestLog.exists()) {
                        Thread.sleep(400)
                        continue
                    }

                    if (latestLog.length() < offset) {
                        offset = 0L
                        effectiveTailStart = 0L
                    }

                    RandomAccessFile(latestLog, "r").use { raf ->
                        raf.seek(offset)
                        while (logTailRunning.get()) {
                            val raw = raf.readLine() ?: break
                            val currentOffset = raf.filePointer
                            offset = currentOffset

                            val line = decodeLogLine(raw)
                                ?.let(ConsoleParser::stripAnsi)
                                ?.trim()
                                .orEmpty()

                            if (line.isBlank()) continue
                            val isBacklog = currentOffset <= effectiveTailStart
                            if (!isBacklog) latestLogHasOutput = true
                            handleObservedOutputLine(versionId, line, isBacklog)
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
                        val connected = runCatching {
                            Socket().use { socket ->
                                socket.connect(InetSocketAddress("127.0.0.1", port), 350)
                                true
                            }
                        }.getOrElse {
                            runCatching {
                                Socket().use { socket ->
                                    socket.connect(InetSocketAddress("::1", port), 350)
                                    true
                                }
                            }.getOrDefault(false)
                        }
                        if (!connected) throw java.io.IOException("Port $port not open yet")
                        val line = "[PocketHost] Server port $port is open. Finalizing startup..."
                        sendEvent(versionId, EVENT_OUTPUT, line)
                        updateNotification("Finalizing server startup...", force = true)
                        hasSeenServerStarting = true

                        // If the log tail missed the "Done!" line or logcat bridge is inactive,
                        // promote readiness once the port is open and listening. Paper binds the
                        // port long before the world finishes loading, so give "Done" a chance
                        // first; promoting right away showed ONLINE while joins still failed.
                        serviceScope.launch {
                            delay(PORT_ONLY_READY_GRACE_MS)
                            val deadline = SystemClock.elapsedRealtime() + 300_000L
                            while (!serverReadyHandled.get() && currentVersionId == versionId && SystemClock.elapsedRealtime() < deadline) {
                                if (isLocalServerPortOpen(port)) {
                                    android.util.Log.i("ServerHostService", "Port $port verified open; promoting server readiness via port probe watchdog.")
                                    onServerReady()
                                    break
                                }
                                delay(3_000L)
                            }
                        }
                        break
                    } catch (_: Exception) {
                        Thread.sleep(400)
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

        val props = runCatching {
            propsFile.inputStream().use { input -> Properties().apply { load(input) } }
        }.getOrNull() ?: return 25565

        return props.getProperty("server-port", "25565").toIntOrNull() ?: 25565
    }

    /** Directory holding the level's chunk data. */
    private fun resolveLevelDir(serverDir: File): File =
        File(serverDir, resolveConfiguredLevelName(serverDir))

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
        return ConsoleParser.isDone(line)
    }

    private fun stopPortProbe() {
        portProbeRunning.set(false)
        portProbeThread?.interrupt()
        portProbeThread = null
    }

    /**
     * @param clearStaleSession drop any session the relay still holds for this phone
     *   before registering. A crash, process recycle or network change can leave the
     *   old tunnel alive on the relay with dead phone sockets filling its pool, and
     *   /register then hands that tunnel back. The manual retry already unregisters
     *   first ([reconnectRelay]), which is why only retrying used to work.
     */
    private fun onRelayReadyToStart(versionId: String, clearStaleSession: Boolean = true) {
        if (relayJob?.isActive == true) {
            android.util.Log.d("ServerHostService", "onRelayReadyToStart: Tunnel already running, skipping.")
            return
        }
        tunnelStarted.set(true)

        resolveLanEndpoint(currentServerPort)?.let { endpoint ->
            sendEvent(versionId, EVENT_OUTPUT, "[PocketHost] LAN address: $endpoint")
        }
        sendEvent(versionId, EVENT_TUNNEL_CONNECTING, "[PocketHost] Opening internet relay...")

        relayJob = serviceScope.launch(Dispatchers.IO) {
            try {
                var registrationAttempts = 0
                val maxRegistrationAttempts = 5
                var reportedUnavailable = false
                if (clearStaleSession) {
                    runCatching {
                        kotlinx.coroutines.withTimeout(5_000L) {
                            relayManager.unregister()
                        }
                    }.onFailure { error ->
                        android.util.Log.w("ServerHostService", "Clearing stale relay session failed: ${error.message}")
                    }
                }
                relayManager.disconnect()
                relayManager.startBedrockBridge()

                while (isActive) {
                    try {
                        val alreadyKnownPort = relayManager.assignedPort

                        registrationAttempts++
                        android.util.Log.i("ServerHostService", "Relay registration attempt $registrationAttempts/$maxRegistrationAttempts")
                        if (!reportedUnavailable) {
                            sendEvent(
                                versionId,
                                EVENT_OUTPUT,
                                "[PocketHost] Relay attempt $registrationAttempts/$maxRegistrationAttempts..."
                            )
                        }

                        val address = relayManager.register()
                        sendEvent(
                            versionId,
                            EVENT_OUTPUT,
                            "[PocketHost] Relay registered: $address"
                        )
                        publishRelayStatus(versionId)
                        relayManager.initPool(currentServerPort)
                        if (!relayManager.isPoolReady.value) {
                            sendEvent(
                                versionId,
                                EVENT_OUTPUT,
                                "[PocketHost] Relay registered, but tunnel sockets did not become ready."
                            )
                            throw java.io.IOException("Relay tunnel pool did not become ready")
                        }
                        sendEvent(
                            versionId,
                            EVENT_OUTPUT,
                            "[PocketHost] Relay tunnel is ready."
                        )

                        val publicAddress = resolvePublicRelayAddress(address)
                        persistPublicAddress(applicationContext, publicAddress)
                        relayHealthFailures.set(0)

                        registrationAttempts = 0

                        val intent = Intent(ACTION_SERVER_EVENT).apply {
                            setPackage(packageName)
                            putExtra(EXTRA_VERSION_ID, currentVersionId ?: "unknown")
                            putExtra(EXTRA_EVENT_TYPE, EVENT_TUNNEL_CONNECTED)
                            putExtra(EXTRA_LINE, publicAddress)
                            putExtra(EXTRA_IS_FALLBACK, address.isFallback)
                        }
                        sendBroadcast(intent)
                        publishRelayStatus(versionId)
                        startRelayStatusHeartbeat(versionId)
                        if (reportedUnavailable) {
                            reportedUnavailable = false
                            updateNotification(ServerStage.RUNNING.notificationText, force = true)
                        }

                        while (isActive) {
                            delay(60_000)
                        }
                    } catch (e: Exception) {
                        if (!isActive) break

                        android.util.Log.e("ServerHostService", "Relay Error (attempt $registrationAttempts): ${e.message}")
                        if (!reportedUnavailable) {
                            sendEvent(
                                versionId,
                                EVENT_OUTPUT,
                                "[PocketHost] Relay attempt $registrationAttempts failed: ${e.message ?: "unknown error"}"
                            )
                        }
                        relayStatusJob?.cancel()
                        relayStatusJob = null

                        // Ensure failed attempts do not keep stale sockets around, here or on the relay.
                        runCatching {
                            kotlinx.coroutines.withTimeout(5_000L) {
                                relayManager.unregister()
                            }
                        }
                        relayManager.disconnect()
                        relayManager.startBedrockBridge()

                        if (registrationAttempts >= maxRegistrationAttempts) {
                            if (!reportedUnavailable) {
                                reportedUnavailable = true
                                android.util.Log.e("ServerHostService", "Max registration attempts reached. Retrying in the background.")
                                currentVersionId?.let {
                                    sendEvent(
                                        it,
                                        EVENT_TUNNEL_FAILED,
                                        "Internet relay is unavailable right now. Players on the same Wi-Fi can still join with the LAN address."
                                    )
                                }
                                updateNotification("LAN only: internet relay unavailable", force = true)
                            }
                            // Keep trying quietly so the relay comes back on its own once the
                            // network or the relay recovers, instead of waiting for a manual retry.
                            delay(30_000L)
                            continue
                        }

                        delay(5000)
                    }
                }
            } finally {
                tunnelStarted.set(false)
                relayManager.disconnect()
                android.util.Log.i("ServerHostService", "Relay job ended (isActive=$isActive)")
            }
        }
    }

    private fun reconnectRelay(
        skipUnregister: Boolean = false,
        requestedRelayHost: String? = null
    ) {
        val versionId = currentVersionId ?: return
        requestedRelayHost?.let { relayHost ->
            // SharedPreferences are cached per Android process. Mirror the manual
            // selection into :server before RelayManager reads its local cache.
            AppPreferences(applicationContext).setManualRelayHost(relayHost)
            android.util.Log.i("ServerHostService", "Switching relay registration to $relayHost")
        }
        relayHealthFailures.set(0)
        relayReconnectJob?.cancel()
        relayJob?.cancel()
        relayJob = null
        relayStatusJob?.cancel()
        relayStatusJob = null
        tunnelStarted.set(false)
        persistPublicAddress(applicationContext, "")
        sendEvent(versionId, EVENT_TUNNEL_CONNECTING, "[PocketHost] Opening internet relay...")
        relayReconnectJob = serviceScope.launch(Dispatchers.IO) {
            if (!skipUnregister) {
                runCatching {
                    kotlinx.coroutines.withTimeout(5_000L) {
                        relayManager.unregister()
                    }
                }.onFailure { error ->
                    android.util.Log.w("ServerHostService", "Relay unregister before reconnect failed: ${error.message}")
                    relayManager.disconnect()
                }
            } else {
                relayManager.disconnect()
            }
            delay(500)
            if (!isActive || currentVersionId != versionId || stopInProgress.get()) return@launch
            // Already unregistered above (or deliberately skipped for a region switch).
            onRelayReadyToStart(versionId, clearStaleSession = false)
        }
    }

    @Keep
    private fun onServerReady() {
        if (serverReadyHandled.getAndSet(true)) {
            android.util.Log.d("ServerHostService", "onServerReady: Ready state already handled, skipping.")
            return
        }

        currentVersionId?.let { onRelayReadyToStart(it) }
        setServerReadyState(true)
        serverReadyFallbackJob?.cancel()
        serverReadyFallbackJob = null

        val versionId = currentVersionId ?: return

        // 1. Update disk state FIRST so UI refreshes read the correct value BEFORE the broadcast is received
        persistRuntimeState(applicationContext, versionId, activeWorldNameOrDefault(), RUNTIME_STATE_RUNNING)
        val readyAt = System.currentTimeMillis()
        serverStartTimeMillis = readyAt
        serviceScope.launch {
            AppPreferencesStore.setServerStartedAtMillis(applicationContext, readyAt)
            pushWidgetUpdate(applicationContext)
        }
        AppPreferences(applicationContext).consecutiveCrashCount = 0

        // 2. Persist RUNTIME_STATE_RUNNING & broadcast a direct server-ready event so the UI can transition to ONLINE
        persistRuntimeState(applicationContext, versionId, activeWorldNameOrDefault(), RUNTIME_STATE_RUNNING)
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
            com.pockethost.app.notification.NotificationHelper.notifyServerOnline(applicationContext, versionId)
        }
        lastNotificationText = ServerStage.RUNNING.notificationText
        lastNotificationUpdateMs = SystemClock.elapsedRealtime()

        // Optimize spawn chunk radius to save CPU and RAM on subsequent starts
        runCatching {
            ServerLauncher.sendCommand("gamerule spawnChunkRadius 1")
        }

        autoRecoverAttempts = 0
        autoRecoverWindowStartMs = 0L
        startWidgetUpdateHeartbeat()
        afkHelperManager.onServerStateChanged(true)
        triggerDashboardStatusUpdate()
    }

    /**
     * Whether this build may set an exact alarm.
     *
     * From Android 12 setExactAndAllowWhileIdle throws SecurityException unless the app holds
     * SCHEDULE_EXACT_ALARM or USE_EXACT_ALARM. PocketHost declares neither — USE_EXACT_ALARM is
     * reserved for alarm and calendar apps under Play policy — so the call has to be guarded
     * rather than attempted and allowed to blow up.
     */
    private fun canScheduleExactAlarm(alarmManager: AlarmManager?): Boolean {
        if (alarmManager == null) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return runCatching { alarmManager.canScheduleExactAlarms() }.getOrDefault(false)
    }

    /**
     * Schedules the alarm that brings the `:server` process back after it is recycled.
     *
     * Returns false when nothing could be scheduled. That return value matters: the callers kill
     * their own process immediately afterwards, so a silently failed alarm leaves the server dead
     * with nothing left running to restart it.
     */
    private fun scheduleProcessRestart(versionId: String, worldName: String, delayMs: Long): Boolean {
        val restartIntent = Intent(applicationContext, ServerHostService::class.java).apply {
            action = ACTION_START
            putExtra(EXTRA_VERSION_ID, versionId)
            putExtra(EXTRA_WORLD_NAME, worldName)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_ONE_SHOT
        }
        val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(applicationContext, 9101, restartIntent, flags)
        } else {
            PendingIntent.getService(applicationContext, 9101, restartIntent, flags)
        }
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val triggerAt = SystemClock.elapsedRealtime() + delayMs.coerceAtLeast(500L)
        return setRecycleAlarm(alarmManager, triggerAt, pendingIntent, "restart")
    }

    /**
     * Sets the wake-up alarm, preferring an exact one and degrading to an inexact one rather than
     * throwing. Returns whether an alarm is actually pending.
     */
    private fun setRecycleAlarm(
        alarmManager: AlarmManager?,
        triggerAt: Long,
        pendingIntent: PendingIntent,
        label: String
    ): Boolean {
        if (alarmManager == null) {
            android.util.Log.e("ServerHostService", "No AlarmManager; cannot schedule $label.")
            return false
        }
        if (canScheduleExactAlarm(alarmManager)) {
            val exact = runCatching {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
            }.isSuccess
            if (exact) return true
            android.util.Log.w("ServerHostService", "Exact alarm for $label was refused; falling back to inexact.")
        }
        return runCatching {
            alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
        }.onFailure { error ->
            android.util.Log.e("ServerHostService", "Could not schedule $label alarm: ${error.message}")
        }.isSuccess
    }

    private fun scheduleKeepAliveRestart(delayMs: Long): Boolean {
        val keepAliveIntent = Intent(applicationContext, ServerHostService::class.java).apply {
            action = ACTION_START_LISTENER
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_ONE_SHOT
        }
        val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(applicationContext, 9102, keepAliveIntent, flags)
        } else {
            PendingIntent.getService(applicationContext, 9102, keepAliveIntent, flags)
        }
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val triggerAt = SystemClock.elapsedRealtime() + delayMs.coerceAtLeast(300L)
        return setRecycleAlarm(alarmManager, triggerAt, pendingIntent, "keep-alive restart")
    }

    private fun scheduleServerReadyFallback(versionId: String) {
        serverReadyFallbackJob?.cancel()
        serverReadyFallbackJob = serviceScope.launch {
            val deadline = SystemClock.elapsedRealtime() + 600_000L
            var portOpenSinceMs = 0L
            while (isActive && SystemClock.elapsedRealtime() < deadline) {
                delay(5_000L)
                if (currentVersionId == versionId && !serverReadyHandled.get()) {
                    val portOpen = isLocalServerPortOpen(currentServerPort)
                    val hasProc = serverProcess?.isAlive == true ||
                        ServerLauncher.hasActiveExternalProcess() ||
                        NativeLauncher.hasInProcessJvmRunInThisProcess
                    // Only fall back to "port is open" once it has stayed open for a while;
                    // the "Done" log line is the real ready signal and usually lands first.
                    portOpenSinceMs = if (portOpen && hasProc) {
                        portOpenSinceMs.takeIf { it > 0L } ?: SystemClock.elapsedRealtime()
                    } else {
                        0L
                    }
                    val openLongEnough = portOpenSinceMs > 0L &&
                        SystemClock.elapsedRealtime() - portOpenSinceMs >= PORT_ONLY_READY_GRACE_MS
                    if (openLongEnough) {
                        android.util.Log.w(
                            "ServerHostService",
                            "Port $currentServerPort verified open during fallback poll for $versionId. Promoting readiness."
                        )
                        setServerReadyState(true)
                        onServerReady()
                        return@launch
                    }
                }
            }
            val shouldPromote = currentVersionId == versionId &&
                !serverReadyHandled.get() &&
                (serverProcess?.isAlive == true ||
                    ServerLauncher.hasActiveExternalProcess() ||
                    NativeLauncher.hasInProcessJvmRunInThisProcess ||
                    isLocalServerPortOpen(currentServerPort))
            if (shouldPromote) {
                android.util.Log.w(
                    "ServerHostService",
                    "Server ready signal was missed for $versionId. Promoting readiness via fallback poll after timeout."
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
        if (stopReason == "user" || stopInProgress.get() || AppPreferences(applicationContext).isUserStopped) {
            return false
        }
        val now = SystemClock.elapsedRealtime()
        if (autoRecoverWindowStartMs == 0L || (now - autoRecoverWindowStartMs) > AUTO_RECOVER_WINDOW_MS) {
            autoRecoverWindowStartMs = now
            autoRecoverAttempts = 0
        }
        if (autoRecoverAttempts >= AUTO_RECOVER_MAX_ATTEMPTS) {
            android.util.Log.w("ServerHostService", "Auto-recover limit reached ($AUTO_RECOVER_MAX_ATTEMPTS attempts in 20m).")
            return false
        }
        autoRecoverAttempts++
        return true
    }

    private fun acquireWakeLock() {
        if (wakeLock != null) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PocketHost:ServerWakeLock").apply {
            setReferenceCounted(false)
            acquire(Long.MAX_VALUE)
        }
        android.util.Log.i("ServerHostService", "WakeLock acquired.")

        // Keep WiFi out of power-save between game packets. Without this, the radio
        // parks after ~50ms idle and adds a 20–150ms wake penalty on the next packet.
        // We acquire BOTH WifiManager.WIFI_MODE_FULL_HIGH_PERF (to keep Wi-Fi awake when screen is off)
        // and WifiManager.WIFI_MODE_FULL_LOW_LATENCY (on Android 10+, to minimize jitter when screen is on).
        if (wifiLock == null) {
            runCatching {
                val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                @Suppress("DEPRECATION")
                wifiLock = wm.createWifiLock(
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                    "PocketHost:ServerWifiLockHighPerf"
                ).also {
                    it.setReferenceCounted(false)
                    it.acquire()
                    android.util.Log.i("ServerHostService", "WiFi high-performance lock acquired.")
                }
            }.onFailure { e ->
                android.util.Log.w("ServerHostService", "WiFi high-performance lock unavailable: ${e.message}")
            }
        }

        if (wifiLowLatencyLock == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                wifiLowLatencyLock = wm.createWifiLock(
                    WifiManager.WIFI_MODE_FULL_LOW_LATENCY,
                    "PocketHost:ServerWifiLockLowLatency"
                ).also {
                    it.setReferenceCounted(false)
                    it.acquire()
                    android.util.Log.i("ServerHostService", "WiFi low-latency lock acquired.")
                }
            }.onFailure { e ->
                android.util.Log.w("ServerHostService", "WiFi low-latency lock unavailable: ${e.message}")
            }
        }

        // Lift system-level multicast and broadcast filters so the server can receive Bedrock LAN pings.
        if (multicastLock == null) {
            runCatching {
                val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                multicastLock = wm.createMulticastLock("PocketHost:ServerMulticastLock").also {
                    it.setReferenceCounted(false)
                    it.acquire()
                    android.util.Log.i("ServerHostService", "MulticastLock acquired.")
                }
            }.onFailure { e ->
                android.util.Log.w("ServerHostService", "Multicast lock unavailable: ${e.message}")
            }
        }
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

        try {
            wifiLock?.let {
                if (it.isHeld) it.release()
                android.util.Log.i("ServerHostService", "WiFi high-performance lock released.")
            }
        } catch (e: Exception) {
            android.util.Log.e("ServerHostService", "Error releasing WiFi lock: ${e.message}")
        } finally {
            wifiLock = null
        }

        try {
            wifiLowLatencyLock?.let {
                if (it.isHeld) it.release()
                android.util.Log.i("ServerHostService", "WiFi low-latency lock released.")
            }
        } catch (e: Exception) {
            android.util.Log.e("ServerHostService", "Error releasing WiFi low-latency lock: ${e.message}")
        } finally {
            wifiLowLatencyLock = null
        }

        try {
            multicastLock?.let {
                if (it.isHeld) it.release()
                android.util.Log.i("ServerHostService", "MulticastLock released.")
            }
        } catch (e: Exception) {
            android.util.Log.e("ServerHostService", "Error releasing MulticastLock: ${e.message}")
        } finally {
            multicastLock = null
        }
    }

    private fun activeWorldNameOrDefault(): String {
        val existing = currentWorldName?.trim()?.takeIf { it.isNotBlank() }
        if (existing != null) {
            return existing
        }
        val world = AppPreferences(applicationContext).selectedWorld.trim().ifBlank { "world" }
        currentWorldName = world
        return world
    }

    private fun attachToExistingServer(
        versionId: String,
        worldName: String,
        serverPort: Int,
        reason: String
    ): Int {
        isLaunching = true
        serviceLaunchRealtimeMs = SystemClock.elapsedRealtime()
        bedrockBridgeFailureReported = false
        hasSeenServerStarting = true
        currentVersionId = versionId
        currentWorldName = worldName
        currentServerPort = serverPort
        stopReason = "unknown"
        serverReadyNotificationShown = false
        serverReadyHandled.set(false)
        relayStatusPlayerCount.set(0)
        synchronized(relayOnlinePlayers) { relayOnlinePlayers.clear() }
        // The JVM has only just been launched here — the server is not accepting players
        // yet. Persisting RUNNING at this point made the state file claim readiness for the
        // whole boot, which the UI then adopted on launch and treated as "already ready".
        // onServerReady() writes RUNNING once the server actually reports Done.
        persistRuntimeState(applicationContext, versionId, worldName, RUNTIME_STATE_STARTING)
        val attachedAt = System.currentTimeMillis()
        serverStartTimeMillis = attachedAt
        serviceScope.launch { AppPreferencesStore.setServerStartedAtMillis(applicationContext, attachedAt) }
        persistPlayerCount(applicationContext, 0)
        resetNotificationState(ServerStage.RUNNING.notificationText)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, createForegroundNotification(ServerStage.RUNNING.notificationText), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, createForegroundNotification(ServerStage.RUNNING.notificationText))
        }
        startLogcatBridge(versionId)
        startServerLogTail(versionId, worldName)
        acquireWakeLock()
        sendEvent(
            versionId,
            EVENT_OUTPUT,
            "[PocketHost] Found an existing Minecraft server on localhost:$serverPort during $reason. Reattaching relay instead of starting another server."
        )
        pushWidgetUpdate()
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
            "[PocketHost] Restored existing world data from the legacy version folder."
        )
        result.backupDir?.let { backup ->
            sendEvent(
                versionId,
                EVENT_OUTPUT,
                "[PocketHost] A newer generated world was moved aside before restore: $backup"
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
        sendEvent(versionId, EVENT_ERROR, "[PocketHost] Accept the Minecraft EULA to start the server.")
        persistRuntimeState(
            applicationContext,
            versionId,
            worldName.ifBlank { "world" },
            RUNTIME_STATE_OFFLINE
        )
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            android.util.Log.e("PocketHost", "Error stopping foreground: ${e.message}")
        }
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
        serviceLaunchRealtimeMs = SystemClock.elapsedRealtime()
        bedrockBridgeFailureReported = false
        hasSeenServerStarting = false
        currentVersionId = versionId
        currentWorldName = worldName
        runCatching {
            val serverDir = ServerFileManager.getServerDir(applicationContext, worldName)
            val nukkitLog = File(serverDir, "logs/server.log")
            if (nukkitLog.exists()) nukkitLog.delete()
            val latestLog = File(serverDir, "logs/latest.log")
            if (latestLog.exists()) {
                // Start the tail on an empty log so an old "Done" line can't mark this launch
                // ready, but keep the last session (often the crash being reported) around.
                val previousLog = File(serverDir, "logs/previous-session.log")
                if (!latestLog.renameTo(previousLog)) {
                    runCatching { latestLog.copyTo(previousLog, overwrite = true) }
                    latestLog.delete()
                }
                if (latestLog.exists()) {
                    runCatching { java.io.FileOutputStream(latestLog).close() }
                }
            }
        }
        stopReason = "unknown"
        serverReadyNotificationShown = false
        serverReadyHandled.set(false)
        setServerReadyState(false)
        relayStatusPlayerCount.set(0)
        synchronized(relayOnlinePlayers) { relayOnlinePlayers.clear() }
        cancelChunkResendJobs()
        persistRuntimeState(applicationContext, versionId, worldName, RUNTIME_STATE_STARTING)
        resetNotificationState(ServerStage.STARTING_SERVER.notificationText)
        pushWidgetUpdate()
        startWidgetUpdateHeartbeat()
        synchronized(currentPlayersList) { currentPlayersList.clear() }
        startDashboardStatusHeartbeat(versionId)
        sendEvent(versionId, EVENT_OUTPUT, "[PocketHost] Starting server...")
        startLogcatBridge(versionId)
        val serverPort = resolveServerPort(worldName)
        currentServerPort = serverPort
        val existingServerDetected = isLocalServerPortOpen(serverPort)
        if (existingServerDetected) {
            isLaunching = false
            currentVersionId = null
            currentWorldName = null
            return attachToExistingServer(versionId, worldName, serverPort, "service resume")
        }


        startServerLogTail(versionId, worldName)

        startPortProbe(versionId, serverPort)
        scheduleServerReadyFallback(versionId)
        acquireWakeLock()

        val serverDirPre = com.pockethost.app.service.ServerFileManager.getServerDir(applicationContext, worldName)
        isNewWorld = !resolveLevelDir(serverDirPre).exists()

        launchJob = serviceScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            migrateLegacyStorageIfNeeded(versionId, worldName)
            val configRepo = com.pockethost.app.data.repository.ServerConfigRepository(applicationContext).apply {
                setWorldNameOverride(worldName)
            }
            val config = configRepo.loadConfig()
            // Use versionId as fallback if pocketcraft-game-version was never written
            val resolvedGameVersion = config.gameVersion.ifBlank { versionId }
            val runtime = JreExtractor.runtimeForServer(config.serverType, resolvedGameVersion)
            val serverDir = com.pockethost.app.service.ServerFileManager.getServerDir(applicationContext, worldName)
            val targetFile = com.pockethost.app.service.ServerFileManager.getServerJarFile(applicationContext, resolvedGameVersion, config.serverType)
            // Ensure parent directory exists before resolveJar tries to write the file
            targetFile.parentFile?.mkdirs()
            android.util.Log.d("ServerHostService", "[resumeServer] versionId=$versionId gameVersion=${config.gameVersion} resolvedGameVersion=$resolvedGameVersion serverType=${config.serverType} targetFile=${targetFile.absolutePath} exists=${targetFile.exists()} isDir=${targetFile.isDirectory}")

            try {
                val jarFile = prepareRuntimeAndJar(
                    versionId = versionId,
                    runtime = runtime,
                    config = config,
                    resolvedGameVersion = resolvedGameVersion,
                    targetFile = targetFile,
                    serverDir = serverDir
                )
                val launchTarget = com.pockethost.app.service.ServerFileManager.readLaunchTarget(serverDir)
                updateNotification(ServerStage.CHECKING_PLUGINS, force = true)
                updateNotification(ServerStage.STARTING_SERVER, force = true)
                withContext(kotlinx.coroutines.Dispatchers.IO) {
                    ServerLauncher(applicationContext).startServer(
                        worldName = worldName,
                        versionId = versionId,
                        jarPath = jarFile.absolutePath,
                        launchMode = launchTarget?.mode ?: com.pockethost.app.service.ServerFileManager.LaunchMode.JAR,
                        runtime = runtime,
            onOutput = { line ->
                handleObservedOutputLine(versionId, line)
            },
            onError = { line ->
                sendEvent(versionId, EVENT_OUTPUT, line)
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
                relayJob?.cancel()
                relayJob = null
                relayStatusJob?.cancel()
                relayStatusJob = null
                serverReadyFallbackJob?.cancel()
                serverReadyFallbackJob = null

                val shouldAutoRecover = exitCode != 0 && shouldScheduleAutoRecover(versionId)
                if (shouldAutoRecover) {
                    relayManager.disconnect()
                } else if (!stopInProgress.get()) {
                    markServerOfflineImmediately()
                    serviceScope.launch(Dispatchers.IO) {
                        try {
                            kotlinx.coroutines.withTimeout(5_000L) {
                                relayManager.unregister()
                            }
                        } catch (e: Exception) {
                            relayManager.disconnect()
                        }
                    }
                }

                tunnelStarted.set(false)
                serverReadyHandled.set(false)
                setServerReadyState(false)
                stopLogcatBridge()
                stopServerLogTail()
                stopPortProbe()

                if (exitCode != 0 && stopReason != "user") {
                    if (exitCode == 127) {
                        sendEvent(versionId, EVENT_SERVER_CRASHED, "exit_code=127")
                        sendEvent(versionId, EVENT_ERROR, "[PocketHost] Exit code 127: Java binary not executable on this device.")
                        if (ServerLauncher.allStorageNoexecDetected) {
                            sendEvent(versionId, EVENT_ERROR, "[PocketHost] [DEVICE RESTRICTION] Samsung Knox security policy on this device marks ALL app storage as non-executable. PocketCraft cannot launch a Java server under these restrictions. Clearing cache will not help — this is a device-level OS policy.")
                        } else {
                            sendEvent(versionId, EVENT_ERROR, "[PocketHost] [HINT] Go to Settings \u2192 Apps \u2192 PocketCraft \u2192 Storage \u2192 Clear Cache, then restart.")
                        }
                    } else {
                        sendEvent(versionId, EVENT_SERVER_CRASHED, "exit_code=$exitCode")
                        sendEvent(versionId, EVENT_ERROR, "[PocketHost] Server exited unexpectedly (code $exitCode).")
                    }
                }

                if (shouldAutoRecover) {
                    val attempt = autoRecoverAttempts
                    val delayMs = (attempt * 4000L).coerceAtMost(15_000L)
                    sendEvent(
                        versionId,
                        EVENT_OUTPUT,
                        "[PocketHost] Server exited unexpectedly. Auto-restarting in ${delayMs / 1000}s (attempt $attempt/$AUTO_RECOVER_MAX_ATTEMPTS)..."
                    )
                    val recycledForRecover = NativeLauncher.hasInProcessJvmRunInThisProcess &&
                        scheduleProcessRestart(versionId, activeWorldNameOrDefault(), delayMs)
                    if (recycledForRecover) {
                        android.util.Log.i("ServerHostService", "Recycling :server process for auto-recover in ${delayMs}ms.")
                        android.os.Process.killProcess(android.os.Process.myPid())
                    } else {
                        if (NativeLauncher.hasInProcessJvmRunInThisProcess) {
                            sendEvent(
                                versionId,
                                EVENT_OUTPUT,
                                "[PocketHost] Could not schedule an automatic restart. Reopen PocketHost to start the server again."
                            )
                        }
                        serviceScope.launch {
                            kotlinx.coroutines.delay(delayMs)
                            if (stopReason != "user") {
                                start(applicationContext, versionId, activeWorldNameOrDefault())
                            }
                        }
                    }
                }

                persistPublicAddress(applicationContext, "")
                persistRuntimeState(applicationContext, versionId, activeWorldNameOrDefault(), RUNTIME_STATE_OFFLINE)
                persistPlayerCount(applicationContext, 0)
                serverReadyNotificationShown = false
                serverReadyHandled.set(false)
                relayStatusPlayerCount.set(0)
                synchronized(relayOnlinePlayers) { relayOnlinePlayers.clear() }
                serviceScope.launch { AppPreferencesStore.setServerStartedAtMillis(applicationContext, 0L) }
                serverStartTimeMillis = 0L
                releaseWakeLock()
                if (shouldAutoRecover) {
                    updateNotification("Recovering server...", force = true)
                } else {
                    updateNotification("Server stopped", force = true)
                }
                pushWidgetUpdate()
                if (!stopInProgress.get()) {
                    stopInProgress.set(false)
                    if (NativeLauncher.hasInProcessJvmRunInThisProcess) {
                        if (keepListenerRunning) {
                            android.util.Log.i("ServerHostService", "Recycling :server process to reset JVM state after server exit.")
                            scheduleKeepAliveRestart(500L)
                        } else {
                            try { stopSelf() } catch (_: Throwable) {}
                        }
                        android.os.Process.killProcess(android.os.Process.myPid())
                    }
                }
            }
        )
                }
            } catch (e: Exception) {
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    sendEvent(versionId, EVENT_ERROR, "[PocketHost] Server JAR is not ready: ${e.message}")
                    updateNotification("Server error", force = true)
                    isLaunching = false
                    stopInProgress.set(false)
                    relayStatusJob?.cancel()
                    relayStatusJob = null
                    serverReadyFallbackJob?.cancel()
                    serverReadyFallbackJob = null
                    persistPublicAddress(applicationContext, "")
                    persistRuntimeState(applicationContext, versionId, activeWorldNameOrDefault(), RUNTIME_STATE_OFFLINE)
                    persistPlayerCount(applicationContext, 0)
                    serverReadyNotificationShown = false
                    serverReadyHandled.set(false)
                    setServerReadyState(false)
                    relayStatusPlayerCount.set(0)
                    synchronized(relayOnlinePlayers) { relayOnlinePlayers.clear() }
                    AppPreferencesStore.setServerStartedAtMillis(applicationContext, 0L)
                    serverStartTimeMillis = 0L
                    releaseWakeLock()
                    updateNotification("Server stopped", force = true)
                    pushWidgetUpdate()
                }
            }
        }

        return START_STICKY
    }

    private fun handleObservedOutputLine(versionId: String, line: String, isBacklog: Boolean = false) {
        // Suppress Moonrise duplicate UUID warning loop spam to prevent CPU saturation, Binder IPC bottlenecks, and high player ping
        if (line.contains("Entity uuid already exists", ignoreCase = true) ||
            line.contains("Failed to spawn player ender pearl in level", ignoreCase = true)) {
            return
        }

        // ── MIUI Security kill detection ────────────────────────────────────────
        // MIUI's security framework prints "[socket]:check permission begin!" when
        // it intercepts socket creation in a child process and may terminate it.
        // We suppress this noisy line from the console, but track it so we can
        // give more targeted advice if the process is subsequently killed.
        if (line.contains("[socket]:check permission begin", ignoreCase = true)) {
            miuiSocketCheckSeen = true
            // Don't forward this internal MIUI message to the user console.
            return
        }
        // "unloaded from com.pockethost.app:server" is MIUI's signal that it
        // has terminated the :server child process (the external JVM).
        if (line.contains("unloaded from com.pockethost.app:server", ignoreCase = true)
            || line.contains("unloaded from com.pockethost.app", ignoreCase = true) && line.contains(":server", ignoreCase = true)) {
            if (miuiSocketCheckSeen) {
                sendEvent(versionId, EVENT_OUTPUT, "[PocketHost] ⚠ MIUI Security blocked the server process.")
                sendEvent(versionId, EVENT_OUTPUT, "[PocketHost] Fix: Open Security app → Permissions → Autostart → enable PocketCraft.")
                sendEvent(versionId, EVENT_OUTPUT, "[PocketHost] Also try: Settings → Developer Options → turn off MIUI Optimization.")
                sendEvent(versionId, EVENT_OUTPUT, "[PocketHost] Then force-stop PocketCraft and start the server again.")
            } else {
                sendEvent(versionId, EVENT_OUTPUT, "[PocketHost] ⚠ Server process was terminated by the system (MIUI Security or OEM battery saver).")
                sendEvent(versionId, EVENT_OUTPUT, "[PocketHost] Fix: Open Security app → enable Autostart for PocketCraft, or disable battery restrictions.")
            }
            miuiSocketCheckSeen = false
            return
        }
        if (line.contains("MixinApplyError", ignoreCase = true) && line.contains("carpet", ignoreCase = true)) {
            val serverDir = com.pockethost.app.service.ServerFileManager.getServerDir(applicationContext, activeWorldNameOrDefault())
            val modsDir = File(serverDir, "mods")
            // A Carpet mixin crash means the installed Carpet build is wrong for this server, so
            // here the blanket purge is the right response — no version is known to be good.
            CarpetModManager.purgeAllCarpetJars(modsDir) { msg ->
                sendEvent(versionId, EVENT_OUTPUT, msg)
            }
        }
        if (line.contains("Starting minecraft server", ignoreCase = true) ||
            line.contains("Loading Paper", ignoreCase = true) ||
            line.contains("Preparing level", ignoreCase = true) ||
            line.contains("Running Java", ignoreCase = true) ||
            line.contains("Initializing plugins", ignoreCase = true) ||
            ConsoleParser.isPreparingStartRegion(line)) {
            hasSeenServerStarting = true
        }

        val elapsedSinceLaunch = if (serviceLaunchRealtimeMs > 0L) {
            SystemClock.elapsedRealtime() - serviceLaunchRealtimeMs
        } else {
            3000L
        }
        if (elapsedSinceLaunch >= 2000L || hasSeenServerStarting) {
            if (looksLikeServerReady(line)) {
                android.util.Log.i("ServerHostService", "Server ready signal detected on log line (isBacklog=$isBacklog): $line")
                onServerReady()
            }
        }

        addLogLine(line)
        sendEvent(versionId, EVENT_OUTPUT, line)
        if (isBacklog) return
        // Geyser sometimes lags behind brand-new Paper builds and refuses to enable. The
        // crossplay toggle still reads ENABLED then, so say plainly that Bedrock is down.
        if (!bedrockBridgeFailureReported &&
            line.contains("Error occurred while enabling Geyser", ignoreCase = true)
        ) {
            bedrockBridgeFailureReported = true
            sendEvent(
                versionId,
                EVENT_OUTPUT,
                "[PocketHost] Bedrock crossplay could not start: Geyser does not support this Minecraft version yet. " +
                    "Java players can still join. For Bedrock players, switch to an older version such as 1.21.x."
            )
        }
        ConsoleParser.parseTps(line)?.let { parsedTps ->
            currentServerTps = parsedTps
            triggerDashboardStatusUpdate()
        }
        ConsoleParser.parseJoin(line)?.let { (name, uuid) ->
            synchronized(currentPlayersList) {
                if (currentPlayersList.none { it.name.equals(name, ignoreCase = true) }) {
                    currentPlayersList.add(PlayerInfo(name = name, uuid = uuid))
                }
            }
            triggerDashboardStatusUpdate()
            val isNewJoin = synchronized(relayOnlinePlayers) {
                val added = relayOnlinePlayers.add(name.lowercase())
                relayStatusPlayerCount.set(relayOnlinePlayers.size)
                if (added) {
                    persistPlayerCount(applicationContext, relayOnlinePlayers.size)
                    com.pockethost.app.service.PlayerDataManager.updateActivePlayers(relayOnlinePlayers.toSet())
                }
                added
            }
            if (!isNewJoin) return@let
            pushWidgetUpdate()
            updateNotification(ServerStage.RUNNING.notificationText, force = true)
            ServerLauncher.sendCommand(POCKETCRAFT_JOIN_TELLRAW)
            serviceScope.launch(Dispatchers.IO) {
                publishRelayStatus(versionId)
            }
            if (name.startsWith(".")) {
                scheduleChunkResendBurst(name)
            }
        }
        ConsoleParser.parseLeave(line)?.let { name ->
            chunkResendJobs.remove(name.lowercase())?.cancel()
            synchronized(currentPlayersList) {
                currentPlayersList.removeAll { it.name.equals(name, ignoreCase = true) }
            }
            triggerDashboardStatusUpdate()
            val remainingPlayers = synchronized(relayOnlinePlayers) {
                relayOnlinePlayers.remove(name.lowercase())
                relayStatusPlayerCount.set(relayOnlinePlayers.size)
                persistPlayerCount(applicationContext, relayOnlinePlayers.size)
                com.pockethost.app.service.PlayerDataManager.updateActivePlayers(relayOnlinePlayers.toSet())
                relayOnlinePlayers.size
            }
            pushWidgetUpdate()
            updateNotification(ServerStage.RUNNING.notificationText, force = true)
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

                val msg = "[PocketHost] Server RAM: ${used}MB used of ${max}MB max"
                ServerLauncher.sendCommand("tellraw $name {\"text\":\"$msg\",\"color\":\"green\"}")
            }
        }

        if (ConsoleParser.isPreparingStartRegion(line)) {
            sendEvent(versionId, EVENT_CHUNKS_LOADING, line)
        }

        ConsoleParser.parseChunkyProgress(line)?.let { progress ->
            sendBroadcast(
                Intent(ACTION_SERVER_EVENT).apply {
                    setPackage(packageName)
                    putExtra(EXTRA_VERSION_ID, versionId)
                    putExtra(EXTRA_EVENT_TYPE, EVENT_CHUNKY_PROGRESS)
                    putExtra(EXTRA_CHUNKY_PERCENT, progress.percent.toInt())
                }
            )
        }
    }

    private fun scheduleChunkResendBurst(name: String) {
        // Bedrock/Geyser clients sometimes finish login before all chunks have
        // been fully acknowledged over the relay. Keep this to a single debounced
        // resend so recovery does not flood the same uplink as keepalives.
        val key = name.lowercase()
        chunkResendJobs.remove(key)?.cancel()
        val job = serviceScope.launch {
            delay(7_000L)
            ServerLauncher.sendCommand("send-chunks $name")
        }
        chunkResendJobs[key] = job
        job.invokeOnCompletion {
            chunkResendJobs.remove(key, job)
        }
    }

    private fun cancelChunkResendJobs() {
        chunkResendJobs.values.forEach { it.cancel() }
        chunkResendJobs.clear()
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

    private fun startWidgetUpdateHeartbeat() {
        if (widgetUpdateJob?.isActive == true) return
        widgetUpdateJob = serviceScope.launch {
            while (isActive) {
                delay(3_000L)
                pushWidgetUpdate(applicationContext)
            }
        }
    }

    private suspend fun resolvePublicRelayAddress(address: RelayManager.RelayAddress): String {
        val region = relayRegionWireValue(address.host)
        val defaultDomain = when (region) {
            "as" -> "mine.pocketcraft.online"
            "eu" -> "eu.pocketcraft.online"
            "us" -> "us.pocketcraft.online"
            else -> address.host
        }

        val prefs = AppPreferences(applicationContext)
        val isPremium = prefs.isPremiumUser || prefs.debugPremiumOverride
        if (!isPremium) {
            return "$defaultDomain:${address.port}"
        }

        val subdomain = prefs.customSubdomain?.trim()?.lowercase().orEmpty()
        if (subdomain.isBlank()) {
            return "$defaultDomain:${address.port}"
        }

        val host = when (region) {
            "as" -> "$subdomain.as.pocketcraft.online"
            "eu" -> "$subdomain.eu.pocketcraft.online"
            "us" -> "$subdomain.us.pocketcraft.online"
            else -> defaultDomain
        }
        return "$host:${address.port}"
    }

    private fun relayRegionWireValue(host: String): String? = when (host.trim().lowercase()) {
        "mine.pocketcraft.online", "13.233.131.236", "13.201.57.41" -> "as"
        "eu.pocketcraft.online", "3.72.235.245", "54.93.247.2" -> "eu"
        "us.pocketcraft.online", "98.83.40.35", "18.225.223.45" -> "us"
        else -> null
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
            "[PocketHost] Relay health degraded. Reconnecting internet relay..."
        )
        serviceScope.launch(Dispatchers.Main.immediate) {
            reconnectRelay()
        }
    }

    private suspend fun publishRelayStatus(versionId: String) {
        runCatching {
            val serverDir = ServerFileManager.getServerDir(applicationContext, activeWorldNameOrDefault())
            val propsFile = File(serverDir, "server.properties")
            val props = Properties()
            if (propsFile.exists()) {
                propsFile.inputStream().use { props.load(it) }
            }
            val motd = props.getProperty("motd", "A PocketCraft Server").trim()
            val maxPlayers = props.getProperty("max-players", "10").toIntOrNull() ?: 10
            relayManager.postServerStatus(
                motd = motd,
                players = relayStatusPlayerCount.get().coerceAtLeast(0),
                maxPlayers = maxPlayers.coerceIn(1, 50),
                version = versionId
            )
        }
    }

    private fun pushWidgetUpdate(statusOverride: String? = null) {
        serviceScope.launch {
            pushWidgetUpdate(applicationContext, statusOverride)
        }
    }

    private fun resolveLanEndpoint(port: Int): String? {
        val ip = ServerAddressResolver.getLocalIpAddress()
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
        // Destroying the child closes its stdout, which is the only thing that unblocks the
        // bridge thread's read() and lets it exit.
        runCatching { logcatProcess?.destroy() }
        logcatProcess = null
        logcatThread?.interrupt()
        logcatThread = null
    }

    private fun sendRconStop() {
        // Shares RconClient's framing (whole packets per write) and the per-install
        // password. The server often closes the socket before answering "stop", so an
        // empty reply here does not mean the stop was lost.
        val reply = com.pockethost.app.network.RconClient.sendCommand("stop", timeoutMs = 3000)
        if (reply.isEmpty()) {
            android.util.Log.w("ServerHostService", "RCON stop sent without a reply (server may already be shutting down)")
        }
    }

    val afkHelperManager by lazy {
        com.pockethost.app.afk.AfkHelperManager(
            context = this,
            scope = serviceScope,
            currentWorldProvider = { currentWorldName ?: "world" },
            isServerRunningProvider = { serverReadyHandled.get() },
            onlinePlayersProvider = { synchronized(currentPlayersList) { currentPlayersList.toList() } },
            knownPlayersProvider = { emptyList() },
            sendRconCommand = ::sendRconCommandSuspended,
            appendLog = { line -> sendEvent(currentVersionId ?: "", EVENT_OUTPUT, line) },
            notifyStateChanged = {
                triggerDashboardStatusUpdate()
            }
        )
    }

    private suspend fun sendRconCommandSuspended(command: String): String = withContext(Dispatchers.IO) {
        val rconResponse = RconClient.sendCommand(command)
        if (rconResponse.isNotBlank()) {
            return@withContext rconResponse
        }
        if (com.pockethost.app.server.ServerLauncher.hasActiveExternalProcess()) {
            com.pockethost.app.server.ServerLauncher.sendCommand(command)
            return@withContext "[OK]"
        }
        "[OK]"
    }

    private fun startDashboardStatusHeartbeat(versionId: String) {
        // Disabled
    }

    private fun triggerDashboardStatusUpdate() {
        // Disabled: avoid unnecessary status payload construction
    }

    private suspend fun updateDashboardStatus(versionId: String) {
        // Web dashboard status is disabled/removed. Immediate no-op to prevent OutOfMemoryError.
    }

    private fun listWorlds(): List<String> {
        val worldsDir = java.io.File(filesDir, "servers/worlds")
        if (!worldsDir.exists() || !worldsDir.isDirectory) return listOf("world")
        val list = worldsDir.listFiles()
            ?.filter { dir ->
                dir.isDirectory && (
                    dir.name.equals("world", ignoreCase = true) ||
                    java.io.File(dir, "level.dat").exists() ||
                    java.io.File(dir, "region").isDirectory ||
                    dir.listFiles()?.any { sub -> sub.name == "level.dat" || sub.name == "region" } == true
                )
            }
            ?.map { it.name }
            ?.sorted()
            .orEmpty()
        return if (list.isEmpty()) listOf("world") else list
    }

    private fun readServerProperties(worldName: String): Map<String, String> {
        val serverDir = com.pockethost.app.service.ServerFileManager.getServerDir(this, worldName)
        val file = java.io.File(serverDir, "server.properties")
        val propsMap = try {
            if (file.exists()) {
                val props = java.util.Properties()
                file.inputStream().use { props.load(it) }
                mapOf(
                    "difficulty" to props.getProperty("difficulty", "normal"),
                    "gamemode" to props.getProperty("gamemode", "survival"),
                    "pvp" to props.getProperty("pvp", "true"),
                    "maxPlayers" to props.getProperty("max-players", "10"),
                    "viewDistance" to props.getProperty("view-distance", "10"),
                    "simulationDistance" to props.getProperty("simulation-distance", "10"),
                    "allowNether" to props.getProperty("allow-nether", "true"),
                    "whiteList" to props.getProperty("white-list", "false"),
                    "spawnProtection" to props.getProperty("spawn-protection", "16"),
                    "levelSeed" to props.getProperty("level-seed", ""),
                    "hardcore" to props.getProperty("hardcore", "false"),
                    "spawnMonsters" to props.getProperty("spawn-monsters", "true"),
                    "generateStructures" to props.getProperty("generate-structures", "true"),
                    "worldDisplayName" to props.getProperty("pocketcraft-world-display.$worldName", worldName),
                    "worldDescription" to props.getProperty("pocketcraft-world-description.$worldName", "Hosted on PocketHost")
                )
            } else {
                emptyMap()
            }
        } catch (e: Exception) {
            emptyMap()
        }
        
        val gamerulesMap = readWorldGamerules(worldName)
        return propsMap + gamerulesMap
    }

    private fun readWorldGamerules(worldName: String): Map<String, String> {
        val serverDir = com.pockethost.app.service.ServerFileManager.getServerDir(this, worldName)
        val worldDir = java.io.File(serverDir, worldName)
        val file = java.io.File(worldDir, "level.dat")
        if (!file.exists()) return emptyMap()
        return try {
            val bytes = java.util.zip.GZIPInputStream(java.io.FileInputStream(file)).use { it.readBytes() }
            
            val keepInvIdx = indexOfTagForGamerule(bytes, "keepInventory")
            val keepInventoryVal = if (keepInvIdx != -1) findStringValueAt(bytes, keepInvIdx, "keepInventory") else "false"
            
            val daylightIdx = indexOfTagForGamerule(bytes, "doDaylightCycle")
            val daylightVal = if (daylightIdx != -1) findStringValueAt(bytes, daylightIdx, "doDaylightCycle") else "true"
            
            val griefingIdx = indexOfTagForGamerule(bytes, "mobGriefing")
            val mobGriefingVal = if (griefingIdx != -1) findStringValueAt(bytes, griefingIdx, "mobGriefing") else "true"
            
            mapOf(
                "keepInventory" to keepInventoryVal,
                "doDaylightCycle" to daylightVal,
                "mobGriefing" to mobGriefingVal
            )
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun indexOfTagForGamerule(data: ByteArray, name: String): Int {
        val nameBytes = name.toByteArray(Charsets.UTF_8)
        for (i in 0 until data.size - nameBytes.size - 3) {
            if (data[i] == 8.toByte()) { // type 8 is String tag
                val len = ((data[i + 1].toInt() and 0xFF) shl 8) or (data[i + 2].toInt() and 0xFF)
                if (len == nameBytes.size) {
                    var match = true
                    for (j in nameBytes.indices) {
                        if (data[i + 3 + j] != nameBytes[j]) {
                            match = false
                            break
                        }
                    }
                    if (match) return i
                }
            }
        }
        return -1
    }

    private fun findStringValueAt(data: ByteArray, idx: Int, name: String): String {
        val start = idx + 1 + 2 + name.length
        if (start + 2 > data.size) return "false"
        val len = ((data[start].toInt() and 0xFF) shl 8) or (data[start + 1].toInt() and 0xFF)
        return if (start + 2 + len <= data.size) String(data, start + 2, len, Charsets.UTF_8) else "false"
    }

    private fun readWhitelistNames(context: Context, worldName: String): List<String> {
        val serverDir = com.pockethost.app.service.ServerFileManager.getServerDir(context, worldName)
        val file = File(serverDir, "whitelist.json")
        if (!file.exists()) return emptyList()
        return try {
            val arr = org.json.JSONArray(file.readText())
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val name = obj.optString("name").trim()
                if (name.isNotEmpty()) {
                    list.add(name)
                }
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun readOpsNames(context: Context, worldName: String): List<String> {
        val serverDir = com.pockethost.app.service.ServerFileManager.getServerDir(context, worldName)
        val file = File(serverDir, "ops.json")
        if (!file.exists()) return emptyList()
        return try {
            val arr = org.json.JSONArray(file.readText())
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val name = obj.optString("name").trim()
                if (name.isNotEmpty()) {
                    list.add(name)
                }
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun stopDashboardStatusAndClear() {
        if (!keepListenerRunning) {
            dashboardStatusJob?.cancel()
            dashboardStatusJob = null
            dashboardCommandListener?.stop()
            dashboardCommandListener = null
        }
        synchronized(currentPlayersList) { currentPlayersList.clear() }
    }

    private fun getRegisteredPlayers(worldName: String): List<Map<String, Any>> {
        val serverDir = com.pockethost.app.service.ServerFileManager.getServerDir(this, worldName)
        val worldDir = java.io.File(serverDir, worldName)
        
        val uuidToName = mutableMapOf<String, String>()
        val userCacheFile = java.io.File(serverDir, "usercache.json")
        if (userCacheFile.exists()) {
            try {
                val arr = org.json.JSONArray(userCacheFile.readText())
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val name = obj.optString("name")
                    val uuid = obj.optString("uuid")
                    if (name.isNotBlank() && uuid.isNotBlank()) {
                        uuidToName[uuid.lowercase()] = name
                    }
                }
            } catch (e: Exception) {}
        }
        
        val playerdataDir = com.pockethost.app.service.PlayerDataManager.playerDirs(worldDir).data
        if (playerdataDir.exists() && playerdataDir.isDirectory) {
            playerdataDir.listFiles()?.filter { it.isFile && it.extension == "dat" }?.forEach { file ->
                val uuid = file.nameWithoutExtension.lowercase()
                if (!uuidToName.containsKey(uuid)) {
                    try {
                        val bytes = java.util.zip.GZIPInputStream(file.inputStream()).use { it.readBytes() }
                        val name = findLastKnownNameForService(bytes)
                        if (!name.isNullOrBlank()) {
                            uuidToName[uuid] = name
                        } else {
                            uuidToName[uuid] = "OfflinePlayer_${uuid.take(5)}"
                        }
                    } catch (e: Exception) {
                        uuidToName[uuid] = "OfflinePlayer_${uuid.take(5)}"
                    }
                }
            }
        }
        
        return uuidToName.map { (uuid, name) ->
            val datFile = java.io.File(playerdataDir, "$uuid.dat")
            val snapshot = if (datFile.exists()) {
                com.pockethost.app.service.NBTParser.parsePlayerData(datFile)
            } else null
            
            val isOnline = synchronized(currentPlayersList) {
                currentPlayersList.any { it.name.equals(name, ignoreCase = true) }
            }

            // Parse playtime and deaths from stats file if available
            val statsFile = File(worldDir, "stats/$uuid.json")
            var playtimeSeconds = 0L
            var deaths = 0L
            if (statsFile.exists()) {
                runCatching {
                    val json = org.json.JSONObject(statsFile.readText())
                    val stats = json.optJSONObject("stats")
                    val custom = stats?.optJSONObject("minecraft:custom")
                    val playTicks = custom?.optLong("minecraft:play_time", 0L)
                        ?: custom?.optLong("minecraft:play_one_minute", 0L)
                        ?: stats?.optLong("playOneMinute", 0L)
                        ?: stats?.optLong("playTime", 0L)
                        ?: stats?.optLong("timePlayed", 0L)
                        ?: 0L
                    playtimeSeconds = playTicks / 20L
                    
                    deaths = custom?.optLong("minecraft:deaths", 0L)
                        ?: stats?.optLong("deaths", 0L)
                        ?: 0L
                }
            }
            
            mapOf(
                "name" to name,
                "uuid" to uuid,
                "x" to (snapshot?.currentPos?.x ?: 0.0),
                "y" to (snapshot?.currentPos?.y ?: 0.0),
                "z" to (snapshot?.currentPos?.z ?: 0.0),
                "dimension" to (snapshot?.currentPos?.dimension ?: "minecraft:overworld"),
                "deathX" to (snapshot?.lastDeathPos?.x ?: 0.0),
                "deathY" to (snapshot?.lastDeathPos?.y ?: 0.0),
                "deathZ" to (snapshot?.lastDeathPos?.z ?: 0.0),
                "deathDim" to (snapshot?.lastDeathPos?.dimension ?: ""),
                "health" to (snapshot?.health ?: 20.0f),
                "hunger" to (snapshot?.hunger ?: 20),
                "online" to isOnline,
                "playtime" to playtimeSeconds,
                "deaths" to deaths
            )
        }
    }

    private fun findLastKnownNameForService(bytes: ByteArray): String? {
        val tags = listOf("lastKnownName", "last_known_name", "Name", "playerName", "author")
        for (tag in tags) {
            val idx = indexOfTagInRangeForService(bytes, tag, 8, 0, bytes.size.coerceAtMost(100000))
            if (idx != -1) {
                val start = idx + 1 + 2 + tag.length
                if (start + 2 <= bytes.size) {
                    val len = ((bytes[start].toInt() and 0xFF) shl 8) or (bytes[start + 1].toInt() and 0xFF)
                    if (start + 2 + len <= bytes.size) {
                        val name = String(bytes, start + 2, len, Charsets.UTF_8)
                        if (name.isNotBlank()) return name
                    }
                }
            }
        }
        return null
    }

    private fun indexOfTagInRangeForService(data: ByteArray, name: String, type: Byte, start: Int, end: Int): Int {
        val nameBytes = name.toByteArray(Charsets.UTF_8)
        for (i in start until end - nameBytes.size - 3) {
            if (data[i] == type) {
                val len = ((data[i + 1].toInt() and 0xFF) shl 8) or (data[i + 2].toInt() and 0xFF)
                if (len == nameBytes.size && nameBytes.indices.all { data[i + 3 + it] == nameBytes[it] }) return i
            }
        }
        return -1
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
        /** How long the game port must stay open before it alone counts as "ready". */
        private const val PORT_ONLY_READY_GRACE_MS = 60_000L

        /** logcat tag of launcher.c's own messages (its TAG define). */
        private const val NATIVE_LAUNCHER_LOG_TAG = "PocketCraft"
        /** logcat tag launcher.c writes the in-process JVM's stdout/stderr under (jvm_log_tag). */
        private const val JVM_STDOUT_LOG_TAG = "PocketCraftJVM"
        /**
         * launcher.c messages worth showing in the console: one per launch step, so a stalled
         * launch shows the last step it reached. The rest (argv, PLT hooks, every preloaded
         * library) is noise to a player.
         */
        private val EARLY_LAUNCHER_MILESTONES = listOf(
            "NativeLauncher starting",
            "libjli.so loaded",
            "libjvm.so loaded",
            "Calling JLI_Launch",
            "JLI_Launch returned",
            "Intercepted"
        )
        /** Log4j console lines start with a [HH:MM:SS timestamp; latest.log delivers those. */
        private val LOG4J_CONSOLE_LINE = Regex("^\\[\\d{2}:\\d{2}:\\d{2}")

        const val ACTION_START = "com.pockethost.app.action.START"
        const val ACTION_START_LISTENER = "com.pockethost.app.action.START_LISTENER"
        const val ACTION_STOP = "com.pockethost.app.action.STOP"
        const val ACTION_RESTART = "com.pockethost.app.action.RESTART"
        const val ACTION_RECONNECT_RELAY = "com.pockethost.app.action.RECONNECT_RELAY"
        const val ACTION_CONSOLE_COMMAND = "com.pockethost.app.action.CONSOLE_COMMAND"
        const val EXTRA_CONSOLE_COMMAND = "console_command"
        const val EXTRA_SKIP_RELAY_UNREGISTER = "skip_relay_unregister"
        const val EXTRA_RELAY_HOST = "relay_host"
        const val ACTION_SERVER_EVENT = "com.pockethost.app.action.SERVER_EVENT"

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
        private const val KEY_PLAYER_COUNT = "player_count"
        private const val POCKETCRAFT_JOIN_TELLRAW =
            """tellraw @a ["",{"text":"hosted on PocketHost","color":"green","bold":true},{"text":"\nJoin our Discord: ","color":"white"},{"text":"https://discord.gg/7xw3Rd2vs2","color":"aqua","underlined":true}]"""
        const val RUNTIME_STATE_OFFLINE = "offline"
        const val RUNTIME_STATE_STARTING = "starting"
        const val RUNTIME_STATE_RUNNING = "running"
        const val RUNTIME_STATE_STOPPING = "stopping"
        @Keep
        var isServiceRunning = false

        @JvmStatic
        fun isServiceRunning(context: Context): Boolean {
            if (ServerLauncher.isServerProcessAlive(context)) return true
            if (!isServiceRunning) return false
            val pid = getExternalJvmPid(context)
            if (pid <= 0) return false
            return try {
                android.system.Os.kill(pid.toInt(), 0)
                true
            } catch (e: Exception) {
                false
            }
        }

        @Keep
        private val _serverReadyState = MutableStateFlow(false)
        @Keep
        val serverReadyState: StateFlow<Boolean> = _serverReadyState.asStateFlow()

        fun start(context: Context, versionId: String, worldName: String): Boolean {
            val intent = Intent(context, ServerHostService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_VERSION_ID, versionId)
                putExtra(EXTRA_WORLD_NAME, worldName)
            }
            return try {
                ContextCompat.startForegroundService(context, intent)
                true
            } catch (e: Exception) {
                try {
                    context.startService(intent)
                    true
                } catch (e2: Exception) {
                    android.util.Log.e("ServerHostService", "Failed to start service: ${e2.message}")
                    false
                }
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

        fun restart(context: Context): Boolean {
            val intent = Intent(context, ServerHostService::class.java).apply {
                action = ACTION_RESTART
            }
            return try {
                if (isServiceRunning(context)) {
                    context.startService(intent)
                } else {
                    ContextCompat.startForegroundService(context, intent)
                }
                true
            } catch (e: Exception) {
                try {
                    context.startService(intent)
                    true
                } catch (e2: Exception) {
                    android.util.Log.e("ServerHostService", "Failed to restart service: ${e2.message}")
                    false
                }
            }
        }

        /**
         * Writes a command to the running server's stdin.
         *
         * The server JVM is a child of the `:server` process, so `ServerLauncher.sendCommand`
         * only reaches it from inside that process — calling it from the UI process is a silent
         * no-op. Used as a fallback when RCON is not reachable.
         */
        fun sendConsoleCommand(context: Context, command: String) {
            val clean = command.trim()
            if (clean.isBlank()) return
            val intent = Intent(context, ServerHostService::class.java).apply {
                action = ACTION_CONSOLE_COMMAND
                putExtra(EXTRA_CONSOLE_COMMAND, clean)
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                android.util.Log.e("ServerHostService", "Failed to deliver console command: ${e.message}")
            }
        }

        fun reconnectRelay(
            context: Context,
            skipUnregister: Boolean = false,
            relayHost: String? = null
        ) {
            val intent = Intent(context, ServerHostService::class.java).apply {
                action = ACTION_RECONNECT_RELAY
                putExtra(EXTRA_SKIP_RELAY_UNREGISTER, skipUnregister)
                relayHost?.trim()?.takeIf { it.isNotBlank() }?.let {
                    putExtra(EXTRA_RELAY_HOST, it)
                }
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

        fun getPersistedPlayerCount(context: Context): Int {
            return readStateFile(context).optInt(KEY_PLAYER_COUNT, 0).coerceAtLeast(0)
        }

        fun persistRuntimeState(context: Context, versionId: String, worldName: String, state: String) {
            val obj = readStateFile(context)
            obj.put(KEY_ACTIVE_VERSION, if (state == RUNTIME_STATE_OFFLINE) "" else versionId)
            obj.put(KEY_ACTIVE_WORLD, if (state == RUNTIME_STATE_OFFLINE) "" else worldName)
            obj.put(KEY_RUNTIME_STATE, state)
            if (state == RUNTIME_STATE_OFFLINE) {
                obj.put(KEY_PLAYER_COUNT, 0)
                obj.put("server_pid", -1)
                // Do NOT set isUserStopped here — this runs on every transition to
                // offline (crashes, exhausted auto-recover, etc.), not just explicit
                // user stops. isUserStopped is the ACTION_STOP handler's job (above).
            } else {
                obj.put("server_pid", android.os.Process.myPid())
            }
            writeStateFile(context, obj)
        }

        fun persistPlayerCount(context: Context, players: Int) {
            val obj = readStateFile(context)
            obj.put(KEY_PLAYER_COUNT, players.coerceAtLeast(0))
            writeStateFile(context, obj)
        }

        fun getServerPid(context: Context): Int {
            return readStateFile(context).optInt("server_pid", -1)
        }

        fun persistExternalJvmPid(context: Context, pid: Long) {
            val obj = readStateFile(context)
            obj.put("external_jvm_pid", pid)
            writeStateFile(context, obj)
        }

        fun getExternalJvmPid(context: Context): Long {
            return readStateFile(context).optLong("external_jvm_pid", -1L)
        }

        private fun persistPublicAddress(context: Context, address: String?) {
            val obj = readStateFile(context)
            obj.put(KEY_PUBLIC_ADDRESS, address?.trim().orEmpty())
            writeStateFile(context, obj)
        }

        private val stateFileLock = Any()

        private fun getStateFile(context: Context): File {
            return File(context.filesDir, "runtime_state.json")
        }

        private fun readStateFile(context: Context): org.json.JSONObject {
            synchronized(stateFileLock) {
                val file = getStateFile(context)
                if (!file.exists()) return org.json.JSONObject()
                return runCatching { org.json.JSONObject(file.readText()) }.getOrDefault(org.json.JSONObject())
            }
        }

        private fun writeStateFile(context: Context, obj: org.json.JSONObject) {
            synchronized(stateFileLock) {
                val file = getStateFile(context)
                runCatching {
                    // Write to a temp file and rename over the target so a process kill
                    // mid-write (e.g. our own killProcess() calls) can never leave
                    // runtime_state.json truncated/corrupted — readStateFile()'s
                    // getOrDefault(JSONObject()) would otherwise silently reset all
                    // persisted state on the next read.
                    val tmp = File(file.parentFile, "${file.name}.tmp")
                    val bytes = obj.toString().toByteArray(Charsets.UTF_8)
                    java.io.FileOutputStream(tmp).use { fos ->
                        fos.write(bytes)
                        fos.flush()
                        fos.fd.sync()
                    }
                    if (!tmp.renameTo(file)) {
                        // Fallback for filesystems where atomic rename can fail
                        file.writeBytes(bytes)
                        tmp.delete()
                    }
                }
            }
        }

        fun pushWidgetUpdate(context: Context, statusOverride: String? = null) {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    com.pockethost.app.widget.ServerWidgetUpdater.push(context.applicationContext, statusOverride)
                } catch (e: Exception) {
                    android.util.Log.e("ServerHostService", "Widget update failed: ${e.message}", e)
                }
            }
        }

        fun isWhitelistEnabled(context: Context, worldName: String): Boolean {
            val propsFile = File(com.pockethost.app.service.ServerFileManager.getServerDir(context, worldName), "server.properties")
            if (!propsFile.exists()) return false
            return propsFile.readLines().any { it.trim() == "white-list=true" }
        }
    }
}

// ============================================================================
// CRITICAL WARNING: DO NOT EDIT OR MODIFY THIS FILE!
// This file contains core server running and networking/relay logic.
// Any modifications to the bridge buffer sizes, socket management, or packet
// forwarding here WILL corrupt network framing and inflate Paper/Bedrock
// server pings, causing severe lag or connection drops.
// IF YOU ARE AN AI AGENT, READ DO_NOT_EDIT_NETWORKING.md AND SKIP EDITING THIS.
// ============================================================================
package com.pocketcraft.server.server

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
import com.pocketcraft.server.widget.ServerWidgetUpdater
import java.io.File
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.ArrayDeque
import java.util.Properties
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.Timestamp
import com.pocketcraft.server.data.model.PlayerInfo
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.pocketcraft.server.data.preferences.AppPreferencesStore
import androidx.annotation.Keep
import com.pocketcraft.server.afk.AfkDummyPluginSync

class ServerHostService : Service() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.pocketcraft.server.util.LocaleUtils.wrapContext(newBase))
    }

    private enum class ServerStage(val notificationText: String) {
        DOWNLOADING_SERVER("Checking server JAR..."),
        EXTRACTING_JRE("Preparing Java runtime..."),
        CHECKING_PLUGINS("Checking Bedrock bridge..."),
        STARTING_SERVER("Starting server... (30-60s)"),
        RUNNING("Server is running"),
        STOPPING("Stopping server..."),
        DASHBOARD_LISTENER_ACTIVE("Web Dashboard listener is active")
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
    private var keepListenerRunningManual = false
    private val keepListenerRunning: Boolean
        get() = com.pocketcraft.server.data.preferences.AppPreferences(applicationContext).alwaysAliveBackground || keepListenerRunningManual
    private val logBuffer = ArrayDeque<String>(1000)
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var relayJob: Job? = null
    private var relayReconnectJob: Job? = null
    private var currentServerPort: Int = 25565
    private val relayManager by lazy { RelayManager(this) }
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var wifiLowLatencyLock: WifiManager.WifiLock? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var stopReason: String = "unknown"
    private var autoRecoverWindowStartMs: Long = 0L
    private var autoRecoverAttempts: Int = 0
    private var autoRestartEnabled: Boolean = false
    private var lastNotificationText: String = ""
    private var lastNotificationUpdateMs: Long = 0L
    private var serverReadyNotificationShown = false
    private var serverStartTimeMillis: Long = 0L
    private var pendingRestartVersionId: String? = null
    private var pendingRestartWorldName: String? = null
    private val relayStatusPlayerCount = AtomicInteger(0)
    private val relayOnlinePlayers = linkedSetOf<String>()
    private val relayHealthFailures = AtomicInteger(0)
    private var relayStatusJob: Job? = null
    private var widgetUpdateJob: Job? = null
    private val currentPlayersList = mutableListOf<PlayerInfo>()
    private var dashboardStatusJob: Job? = null
    private var dashboardCommandListener: com.pocketcraft.server.broadcast.DashboardCommandListener? = null
    private var currentServerTps: Float = 20.0f
    private var serverReadyFallbackJob: Job? = null
    private var bootStartedAt: Long = 0L
    /** Set to true when MIUI's socket permission check message is seen in the output. */
    private var miuiSocketCheckSeen = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        autoRestartEnabled = AppPreferences(applicationContext).autoRestart
        serverStartTimeMillis = runBlocking {
            AppPreferencesStore.getServerStartedAtMillis(applicationContext)
        }
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
        val action = intent?.action
        val initialText = if (action == ACTION_START_LISTENER) {
            ServerStage.DASHBOARD_LISTENER_ACTIVE.notificationText
        } else {
            ServerStage.STARTING_SERVER.notificationText
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, createForegroundNotification(initialText), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, createForegroundNotification(initialText))
        }

        if (action == ACTION_START_LISTENER) {
            keepListenerRunningManual = false
            val requestedVersionId = intent?.getStringExtra(EXTRA_VERSION_ID).orEmpty().trim()
            val versionId = if (requestedVersionId.isBlank()) {
                runBlocking { AppPreferencesStore.getSelectedVersionFlow(applicationContext).first().orEmpty() }
            } else {
                requestedVersionId
            }
            val worldName = intent?.getStringExtra(EXTRA_WORLD_NAME).orEmpty().trim().takeIf { it.isNotBlank() }
                ?: runBlocking { AppPreferencesStore.getSelectedWorldFlow(applicationContext).first() }
                ?: "world"
            currentVersionId = versionId
            currentWorldName = worldName
            
            if (!serverReadyHandled.get()) {
                startDashboardStatusHeartbeat(versionId)
            }
            updateNotification(ServerStage.DASHBOARD_LISTENER_ACTIVE, force = true)
            return START_STICKY
        }

        if (intent?.action == ACTION_START) {
            keepListenerRunningManual = com.pocketcraft.server.data.preferences.AppPreferences(applicationContext).alwaysAliveBackground
        }
        val requestedVersionId = intent?.getStringExtra(EXTRA_VERSION_ID).orEmpty().trim()
        val versionId = if (intent?.action == ACTION_START && requestedVersionId.isBlank()) {
            runBlocking { AppPreferencesStore.getSelectedVersionFlow(applicationContext).first().orEmpty() }
        } else {
            requestedVersionId
        }
        if (intent?.action == ACTION_STOP) {
            keepListenerRunningManual = intent.getBooleanExtra("keep_listener_alive", false)
            stopReason = "user"
            autoRecoverAttempts = 0
            autoRecoverWindowStartMs = 0L
            pendingRestartVersionId = null
            pendingRestartWorldName = null
            updateNotification(ServerStage.STOPPING, force = true)
            pushWidgetUpdate("stopping")
            currentVersionId?.let {
                sendEvent(it, EVENT_OUTPUT, "[PocketCraft] Stop requested.")
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
            stopReason = "user"
            autoRecoverAttempts = 0
            autoRecoverWindowStartMs = 0L
            pendingRestartVersionId = activeVersionId
            pendingRestartWorldName = activeWorldName
            updateNotification(ServerStage.STOPPING, force = true)
            pushWidgetUpdate("stopping")
            sendEvent(activeVersionId, EVENT_OUTPUT, "[PocketCraft] Restart requested.")
            stopServer()
            return START_NOT_STICKY
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
            runBlocking { AppPreferencesStore.getSelectedWorldFlow(applicationContext).first() }
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
            prefs.consecutiveCrashCount = 0
            prefs.lastStartTimestamp = System.currentTimeMillis()
        }

        if (!hasExplicitStart && activeVersion.isNotBlank() && worldName.isNotBlank()) {
            // Service restarted after being killed, resume running server
            val prefs = AppPreferences(applicationContext)
            val now = System.currentTimeMillis()
            val previousRuntimeState = getPersistedRuntimeState(applicationContext, activeVersion)
            if (previousRuntimeState == RUNTIME_STATE_STARTING && now - prefs.lastStartTimestamp < 60_000L) {
                prefs.consecutiveCrashCount += 1
            } else if (previousRuntimeState != RUNTIME_STATE_STARTING) {
                prefs.consecutiveCrashCount = 0
            }
            prefs.lastStartTimestamp = now
            if (prefs.consecutiveCrashCount > 2) {
                persistRuntimeState(applicationContext, activeVersion, worldName, RUNTIME_STATE_OFFLINE)
                sendEvent(activeVersion, EVENT_SERVER_CRASHED, "Server crashed repeatedly during startup.")
                com.pocketcraft.server.notification.NotificationHelper.notifyServerCrashLoop(applicationContext)
                try {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } catch (e: Exception) {
                    android.util.Log.e("PocketCraft", "Error stopping foreground: ${e.message}")
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
        miuiSocketCheckSeen = false
        relayStatusPlayerCount.set(0)
        synchronized(relayOnlinePlayers) { relayOnlinePlayers.clear() }
        persistRuntimeState(applicationContext, versionId, worldName, RUNTIME_STATE_STARTING)
        persistPlayerCount(applicationContext, 0)
        serviceScope.launch { AppPreferencesStore.setServerStartedAtMillis(applicationContext, 0L) }
        serverStartTimeMillis = 0L
        resetNotificationState(ServerStage.STARTING_SERVER.notificationText)
        pushWidgetUpdate()
        startWidgetUpdateHeartbeat()
        synchronized(currentPlayersList) { currentPlayersList.clear() }
        startDashboardStatusHeartbeat(versionId)
        sendEvent(versionId, EVENT_OUTPUT, "[PocketCraft] Starting server...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, createForegroundNotification(ServerStage.STARTING_SERVER.notificationText), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
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
            AfkDummyPluginSync.syncWorldFromDatabase(applicationContext, worldName)
            val configRepo = com.pocketcraft.server.data.repository.ServerConfigRepository(applicationContext).apply {
                setWorldNameOverride(worldName)
            }
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
                val jarFile = prepareRuntimeAndJar(
                    versionId = versionId,
                    runtime = runtime,
                    config = config,
                    resolvedGameVersion = resolvedGameVersion,
                    targetFile = targetFile,
                    serverDir = serverDir
                )
                val launchTarget = com.pocketcraft.server.service.ServerFileManager.readLaunchTarget(serverDir)
                updateNotification(ServerStage.CHECKING_PLUGINS, force = true)
                updateNotification(ServerStage.STARTING_SERVER, force = true)

                withContext(kotlinx.coroutines.Dispatchers.IO) {
                    ServerLauncher(applicationContext).startServer(
                        worldName = worldName,
                        versionId = versionId,
                        jarPath = jarFile.absolutePath,
                        launchMode = launchTarget?.mode ?: com.pocketcraft.server.service.ServerFileManager.LaunchMode.JAR,
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
                        sendEvent(versionId, EVENT_ERROR, "[PocketCraft] Exit code 127: Java binary not executable on this device.")
                        if (ServerLauncher.allStorageNoexecDetected) {
                            sendEvent(versionId, EVENT_ERROR, "[PocketCraft] [DEVICE RESTRICTION] Samsung Knox security policy on this device marks ALL app storage as non-executable. PocketCraft cannot launch a Java server under these restrictions. Clearing cache will not help — this is a device-level OS policy.")
                        } else {
                            sendEvent(versionId, EVENT_ERROR, "[PocketCraft] [HINT] Go to Settings \u2192 Apps \u2192 PocketCraft \u2192 Storage \u2192 Clear Cache, then restart.")
                        }
                    } else {
                        sendEvent(versionId, EVENT_SERVER_CRASHED, "exit_code=$exitCode")
                        sendEvent(versionId, EVENT_ERROR, "[PocketCraft] Server exited unexpectedly (code $exitCode).")
                    }
                }

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
                }
            }
        )
                }
            } catch (e: Exception) {
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    sendEvent(versionId, EVENT_ERROR, "[PocketCraft] Server JAR is not ready: ${e.message}")
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
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {}
        // If this service is being destroyed unexpectedly, avoid leaving
        // a detached JVM process running without relay/control.
        forceTerminateHostedServer()
        val inProcessRuntime = !ServerLauncher.hasActiveExternalProcess()
        if (inProcessRuntime && isLaunching) {
            android.util.Log.e("PocketCraft", "Service destroyed while JVM thread active. Killing :server process to prevent leak.")
            currentVersionId?.let { persistRuntimeState(applicationContext, it, activeWorldNameOrDefault(), RUNTIME_STATE_OFFLINE) }
            android.os.Process.killProcess(android.os.Process.myPid())
        }

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
        currentVersionId?.let { persistRuntimeState(applicationContext, it, activeWorldNameOrDefault(), RUNTIME_STATE_OFFLINE) }
        serverReadyFallbackJob?.cancel()
        serverReadyFallbackJob = null
        setServerReadyState(false)
        releaseWakeLock()
        // Ensure widget is updated to OFFLINE when the service is destroyed
        pushWidgetUpdate(applicationContext)
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Keep service lifecycle independent from recent-task UI removal.
        // This avoids races where user-initiated shutdown is misread as a crash/restart flow.
        if (keepListenerRunning) {
            val isServerActive = serverProcess?.isAlive == true || serverReadyHandled.get()
            val restartAction = if (isServerActive) ACTION_START else ACTION_START_LISTENER
            
            val restartServiceIntent = Intent(applicationContext, ServerHostService::class.java).apply {
                action = restartAction
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
        }
    }

    private fun stopServer() {
        if (!stopInProgress.compareAndSet(false, true)) return
        stopDashboardStatusAndClear()
        launchJob?.cancel()
        launchJob = null
        serverReadyFallbackJob?.cancel()
        serverReadyFallbackJob = null
        markServerOfflineImmediately()
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
                        android.util.Log.e("PocketCraft", "Failed to write stop signal: " + e.message)
                    }
                    if (isServerReady) {
                        runCatching { sendRconStop() }
                    }
                } else {
                    if (isServerReady) {
                        ServerLauncher.sendCommand("stop")
                    }
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
                waitForLocalServerPortClosed(currentServerPort, 2_500L)
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
                try {
                    com.pocketcraft.server.widget.ServerWidgetUpdater.push(applicationContext)
                } catch (e: Exception) {
                    android.util.Log.e("ServerHostService", "Widget update failed on stop: ${e.message}")
                }

                sendBroadcast(Intent(EVENT_STOPPED).setPackage(packageName))
                if (keepListenerRunning) {
                    val notification = createForegroundNotification(ServerStage.DASHBOARD_LISTENER_ACTIVE.notificationText)
                    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    manager?.notify(NOTIFICATION_ID, notification)
                } else {
                    try {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    } catch (e: Exception) {
                        android.util.Log.e("PocketCraft", "Error stopping foreground: ${e.message}")
                    }

                    val restartVersionId = pendingRestartVersionId
                    val restartWorldName = pendingRestartWorldName
                    pendingRestartVersionId = null
                    pendingRestartWorldName = null
                    if (!restartVersionId.isNullOrBlank() && !restartWorldName.isNullOrBlank()) {
                        val restartIntent = Intent(applicationContext, ServerHostService::class.java).apply {
                            action = ACTION_START
                            putExtra(EXTRA_VERSION_ID, restartVersionId)
                            putExtra(EXTRA_WORLD_NAME, restartWorldName)
                        }
                        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
                        } else {
                            PendingIntent.FLAG_ONE_SHOT
                        }
                        val pendingIntent = PendingIntent.getService(
                            applicationContext,
                            1001,
                            restartIntent,
                            pendingIntentFlags
                        )
                        val alarmManager = applicationContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                        alarmManager?.set(
                            AlarmManager.ELAPSED_REALTIME_WAKEUP,
                            SystemClock.elapsedRealtime() + 1200L,
                            pendingIntent
                        )
                    }

                    if (inProcessRuntime) {
                        android.util.Log.d("PocketCraft", "In-process runtime stopped. Killing :server process to ensure clean resource release.")
                        stopSelf()
                        // Allow the EVENT_STOPPED broadcast to propagate before we terminate our PID
                        delay(500)
                        android.os.Process.killProcess(android.os.Process.myPid())
                    } else {
                        stopSelf()
                    }
                }

                stopInProgress.set(false)
            }
        }
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
        persistPublicAddress(applicationContext, "")
        currentVersionId?.let { versionId ->
            persistRuntimeState(applicationContext, versionId, activeWorldNameOrDefault(), RUNTIME_STATE_OFFLINE)
            persistPlayerCount(applicationContext, 0)
            sendEvent(versionId, EVENT_OUTPUT, "[PocketCraft] Server stopping...")
        }
        serviceScope.launch { AppPreferencesStore.setServerStartedAtMillis(applicationContext, 0L) }
        serverStartTimeMillis = 0L
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
        runtime: com.pocketcraft.server.setup.JreExtractor.RuntimeSpec,
        config: com.pocketcraft.server.data.model.ServerConfig,
        resolvedGameVersion: String,
        targetFile: File,
        serverDir: File
    ): File = coroutineScope {
        updateNotification(ServerStage.EXTRACTING_JRE, force = true)
        val runtimeJob = async { ensureRuntimeExtracted(versionId, runtime) }
        val jarJob = async {
            var resolvedJar: File? = null
            com.pocketcraft.server.server.ServerJarManager.resolveJar(
                serverType = config.serverType,
                gameVersion = resolvedGameVersion,
                customJarPath = config.customJarPath,
                targetFile = targetFile,
                serverDir = serverDir,
                onProgress = { pct ->
                    updateNotification("${ServerStage.DOWNLOADING_SERVER.notificationText} $pct%", force = false)
                    sendEvent(versionId, EVENT_OUTPUT, "Checking imported server JAR: $pct%")
                }
            ).collect { resolvedJar = it }
            resolvedJar ?: throw IllegalStateException("Server JAR could not be resolved")
        }
        runtimeJob.await()
        jarJob.await()
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

        val extPid = getExternalJvmPid(applicationContext)
        if (extPid > 0) {
            android.util.Log.i("ServerHostService", "Force-killing persisted external JVM process: $extPid")
            runCatching { android.os.Process.killProcess(extPid.toInt()) }
            persistExternalJvmPid(applicationContext, -1L)
        }

        killOrphanedJvmProcesses(applicationContext)
    }

    private fun killOrphanedJvmProcesses(context: Context) {
        runCatching {
            val process = Runtime.getRuntime().exec(arrayOf("ps"))
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.contains("java") || trimmed.contains("serverwrap")) {
                        val parts = trimmed.split(Regex("\\s+"))
                        for (part in parts) {
                            val pid = part.toIntOrNull()
                            if (pid != null && pid != android.os.Process.myPid() && pid > 0) {
                                android.util.Log.i("ServerHostService", "Killing orphaned process from ps: $trimmed ($pid)")
                                android.os.Process.killProcess(pid)
                                break
                            }
                        }
                    }
                }
            }
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
            runCatching { FirebaseCrashlytics.getInstance().recordException(exception) }
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
            RUNTIME_STATE_RUNNING -> "PocketCraft Server Online"
            RUNTIME_STATE_STARTING -> "PocketCraft Server Starting"
            else -> "PocketCraft Server"
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
            .setSmallIcon(R.mipmap.ic_launcher)
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
                "-T", "1",
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
        val tailStartLength = latestLog.takeIf { it.exists() }?.length() ?: 0L
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
                            val currentOffset = raf.filePointer
                            offset = currentOffset

                            val line = decodeLogLine(raw)
                                ?.let(ConsoleParser::stripAnsi)
                                ?.trim()
                                .orEmpty()

                            if (line.isBlank()) continue
                            val isBacklog = currentOffset <= tailStartLength
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
                        Socket().use { socket ->
                            socket.connect(InetSocketAddress("127.0.0.1", port), 350)
                        }
                        val line = "[PocketCraft] Server port $port is open. Finalizing startup..."
                        sendEvent(versionId, EVENT_OUTPUT, line)
                        updateNotification("Finalizing server startup...", force = true)
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
        return ConsoleParser.isDone(line)
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
            relayManager.disconnect()
            android.util.Log.i("ServerHostService", "Relay job ended (isActive=$isActive)")
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
        sendEvent(versionId, EVENT_TUNNEL_CONNECTING, "[PocketCraft] Opening internet relay...")
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
        val readyAt = System.currentTimeMillis()
        serverStartTimeMillis = readyAt
        serviceScope.launch {
            AppPreferencesStore.setServerStartedAtMillis(applicationContext, readyAt)
            pushWidgetUpdate(applicationContext)
        }
        AppPreferences(applicationContext).consecutiveCrashCount = 0

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
        lastNotificationText = ServerStage.RUNNING.notificationText
        lastNotificationUpdateMs = SystemClock.elapsedRealtime()

        autoRecoverAttempts = 0
        autoRecoverWindowStartMs = 0L
        startWidgetUpdateHeartbeat()
        afkHelperManager.onServerStateChanged(true)
        triggerDashboardStatusUpdate()
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
                    "PocketCraft:ServerWifiLockHighPerf"
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
                    "PocketCraft:ServerWifiLockLowLatency"
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
                multicastLock = wm.createMulticastLock("PocketCraft:ServerMulticastLock").also {
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
        val serverRunning = serverReadyHandled.get()
        if (serverRunning) {
            return currentWorldName
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: "world"
        }
        val world = kotlinx.coroutines.runBlocking {
            AppPreferencesStore.getSelectedWorldFlow(applicationContext).first().trim().ifBlank { "world" }
        }
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
        currentVersionId = versionId
        currentWorldName = worldName
        currentServerPort = serverPort
        stopReason = "unknown"
        serverReadyNotificationShown = false
        serverReadyHandled.set(false)
        relayStatusPlayerCount.set(0)
        synchronized(relayOnlinePlayers) { relayOnlinePlayers.clear() }
        persistRuntimeState(applicationContext, versionId, worldName, RUNTIME_STATE_RUNNING)
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
        startServerLogTail(versionId)
        acquireWakeLock()
        sendEvent(
            versionId,
            EVENT_OUTPUT,
            "[PocketCraft] Found an existing Minecraft server on localhost:$serverPort during $reason. Reattaching relay instead of starting another server."
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
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            android.util.Log.e("PocketCraft", "Error stopping foreground: ${e.message}")
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
        pushWidgetUpdate()
        startWidgetUpdateHeartbeat()
        synchronized(currentPlayersList) { currentPlayersList.clear() }
        startDashboardStatusHeartbeat(versionId)
        sendEvent(versionId, EVENT_OUTPUT, "[PocketCraft] Starting server...")
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
            val configRepo = com.pocketcraft.server.data.repository.ServerConfigRepository(applicationContext).apply {
                setWorldNameOverride(worldName)
            }
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
                val jarFile = prepareRuntimeAndJar(
                    versionId = versionId,
                    runtime = runtime,
                    config = config,
                    resolvedGameVersion = resolvedGameVersion,
                    targetFile = targetFile,
                    serverDir = serverDir
                )
                val launchTarget = com.pocketcraft.server.service.ServerFileManager.readLaunchTarget(serverDir)
                updateNotification(ServerStage.CHECKING_PLUGINS, force = true)
                updateNotification(ServerStage.STARTING_SERVER, force = true)
                withContext(kotlinx.coroutines.Dispatchers.IO) {
                    ServerLauncher(applicationContext).startServer(
                        worldName = worldName,
                        versionId = versionId,
                        jarPath = jarFile.absolutePath,
                        launchMode = launchTarget?.mode ?: com.pocketcraft.server.service.ServerFileManager.LaunchMode.JAR,
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
                        sendEvent(versionId, EVENT_ERROR, "[PocketCraft] Exit code 127: Java binary not executable on this device.")
                        if (ServerLauncher.allStorageNoexecDetected) {
                            sendEvent(versionId, EVENT_ERROR, "[PocketCraft] [DEVICE RESTRICTION] Samsung Knox security policy on this device marks ALL app storage as non-executable. PocketCraft cannot launch a Java server under these restrictions. Clearing cache will not help — this is a device-level OS policy.")
                        } else {
                            sendEvent(versionId, EVENT_ERROR, "[PocketCraft] [HINT] Go to Settings \u2192 Apps \u2192 PocketCraft \u2192 Storage \u2192 Clear Cache, then restart.")
                        }
                    } else {
                        sendEvent(versionId, EVENT_SERVER_CRASHED, "exit_code=$exitCode")
                        sendEvent(versionId, EVENT_ERROR, "[PocketCraft] Server exited unexpectedly (code $exitCode).")
                    }
                }

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
                }
            }
        )
                }
            } catch (e: Exception) {
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    sendEvent(versionId, EVENT_ERROR, "[PocketCraft] Server JAR is not ready: ${e.message}")
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
        // "unloaded from com.pocketcraft.server:server" is MIUI's signal that it
        // has terminated the :server child process (the external JVM).
        if (line.contains("unloaded from com.pocketcraft.server:server", ignoreCase = true)
            || line.contains("unloaded from com.pocketcraft.server", ignoreCase = true) && line.contains(":server", ignoreCase = true)) {
            if (miuiSocketCheckSeen) {
                sendEvent(versionId, EVENT_OUTPUT, "[PocketCraft] ⚠ MIUI Security blocked the server process.")
                sendEvent(versionId, EVENT_OUTPUT, "[PocketCraft] Fix: Open Security app → Permissions → Autostart → enable PocketCraft.")
                sendEvent(versionId, EVENT_OUTPUT, "[PocketCraft] Also try: Settings → Developer Options → turn off MIUI Optimization.")
                sendEvent(versionId, EVENT_OUTPUT, "[PocketCraft] Then force-stop PocketCraft and start the server again.")
            } else {
                sendEvent(versionId, EVENT_OUTPUT, "[PocketCraft] ⚠ Server process was terminated by the system (MIUI Security or OEM battery saver).")
                sendEvent(versionId, EVENT_OUTPUT, "[PocketCraft] Fix: Open Security app → enable Autostart for PocketCraft, or disable battery restrictions.")
            }
            miuiSocketCheckSeen = false
            return
        }
        // ── end MIUI detection ──────────────────────────────────────────────────
        addLogLine(line)
        sendEvent(versionId, EVENT_OUTPUT, line)
        if (isBacklog) return
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
                    com.pocketcraft.server.service.PlayerDataManager.updateActivePlayers(relayOnlinePlayers.toSet())
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
            synchronized(currentPlayersList) {
                currentPlayersList.removeAll { it.name.equals(name, ignoreCase = true) }
            }
            triggerDashboardStatusUpdate()
            val remainingPlayers = synchronized(relayOnlinePlayers) {
                relayOnlinePlayers.remove(name.lowercase())
                relayStatusPlayerCount.set(relayOnlinePlayers.size)
                persistPlayerCount(applicationContext, relayOnlinePlayers.size)
                com.pocketcraft.server.service.PlayerDataManager.updateActivePlayers(relayOnlinePlayers.toSet())
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

    private fun startWidgetUpdateHeartbeat() {
        if (widgetUpdateJob?.isActive == true) return
        widgetUpdateJob = serviceScope.launch {
            while (isActive) {
                delay(60_000L)
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
        "mine.pocketcraft.online", "13.201.57.41" -> "as"
        "eu.pocketcraft.online", "54.93.247.2" -> "eu"
        "us.pocketcraft.online", "18.225.223.45" -> "us"
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
        val maxPlayers = props.getProperty("max-players", "10").toIntOrNull() ?: 10
        relayManager.postServerStatus(
            motd = motd,
            players = relayStatusPlayerCount.get().coerceAtLeast(0),
            maxPlayers = maxPlayers.coerceIn(1, 50),
            version = versionId
        )
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

    val afkHelperManager by lazy {
        com.pocketcraft.server.afk.AfkHelperManager(
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
        val password = "pocketcraft-internal-rcon"
        val port = 25575
        runCatching {
            Socket().use { socket ->
                socket.connect(java.net.InetSocketAddress("127.0.0.1", port), 3000)
                socket.soTimeout = 5000
                val out = java.io.DataOutputStream(socket.getOutputStream().buffered())
                val inp = java.io.DataInputStream(socket.getInputStream().buffered())

                fun sendPacket(id: Int, type: Int, payload: String) {
                    val payloadBytes = payload.toByteArray(java.nio.charset.StandardCharsets.UTF_8)
                    val length = 4 + 4 + payloadBytes.size + 2
                    out.write(length and 0xFF)
                    out.write((length shr 8) and 0xFF)
                    out.write((length shr 16) and 0xFF)
                    out.write((length shr 24) and 0xFF)

                    out.write(id and 0xFF)
                    out.write((id shr 8) and 0xFF)
                    out.write((id shr 16) and 0xFF)
                    out.write((id shr 24) and 0xFF)

                    out.write(type and 0xFF)
                    out.write((type shr 8) and 0xFF)
                    out.write((type shr 16) and 0xFF)
                    out.write((type shr 24) and 0xFF)

                    out.write(payloadBytes)
                    out.write(0)
                    out.write(0)
                    out.flush()
                }

                fun readIntLE(): Int {
                    val b0 = inp.read(); val b1 = inp.read(); val b2 = inp.read(); val b3 = inp.read()
                    if (b0 == -1 || b1 == -1 || b2 == -1 || b3 == -1) throw java.io.IOException("EOF")
                    return (b0 and 0xFF) or ((b1 and 0xFF) shl 8) or ((b2 and 0xFF) shl 16) or ((b3 and 0xFF) shl 24)
                }

                fun readPacket(): Triple<Int, Int, String> {
                    val length = readIntLE()
                    val id = readIntLE()
                    val type = readIntLE()
                    val payloadLen = (length - 10).coerceAtLeast(0)
                    val payload = if (payloadLen > 0) ByteArray(payloadLen).also { inp.readFully(it) } else ByteArray(0)
                    inp.read()
                    inp.read()
                    return Triple(id, type, payload.toString(java.nio.charset.StandardCharsets.UTF_8))
                }

                // Auth
                sendPacket(1, 3, password)
                val (authId, _, _) = readPacket()
                if (authId == -1) return@use "[RCON] Authentication failed."

                // Command
                sendPacket(2, 2, command)
                val (_, _, response) = readPacket()
                response.ifBlank { "[OK]" }
            }
        }.getOrElse { error ->
            when (error) {
                is java.net.SocketTimeoutException -> "[RCON] Timed out waiting for response."
                is java.net.ConnectException -> "[RCON] Connection refused."
                is java.io.IOException -> "[RCON] Connection failed: ${error.message ?: error.javaClass.simpleName}"
                else -> "[RCON] Failed: ${error.message ?: error.javaClass.simpleName}"
            }
        }
    }

    private fun startDashboardStatusHeartbeat(versionId: String) {
        dashboardStatusJob?.cancel()
        
        dashboardCommandListener?.stop()
        dashboardCommandListener = com.pocketcraft.server.broadcast.DashboardCommandListener(
            context = this,
            scope = serviceScope,
            isMainProcess = false,
            sendRconCommand = ::sendRconCommandSuspended,
            toggleAfkBot = { enabled ->
                val dbDao = com.pocketcraft.server.afk.AfkHelperDatabase.getInstance(this).afkFarmLocationDao()
                val currentWorld = currentWorldName ?: "world"
                val worldFarms = dbDao.getAll().filter { it.worldName.equals(currentWorld, ignoreCase = true) }
                for (farm in worldFarms) {
                    if (farm.isActive != enabled) {
                        afkHelperManager.toggleFarm(farm.id)
                    }
                }
            },
            onPropertyUpdated = {
                triggerDashboardStatusUpdate()
            }
        )
        dashboardCommandListener?.start()

        dashboardStatusJob = serviceScope.launch(Dispatchers.IO) {
            while (isActive) {
                updateDashboardStatus(versionId)
                delay(5_000)
            }
        }
    }

    private fun triggerDashboardStatusUpdate() {
        val versionId = currentVersionId ?: return
        serviceScope.launch(Dispatchers.IO) {
            updateDashboardStatus(versionId)
        }
    }

    private suspend fun updateDashboardStatus(versionId: String) {
        val prefs = com.pocketcraft.server.data.preferences.AppPreferences(this)
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            ?: prefs.firebaseUserUid
            ?: return
        
        val serverRunning = serverReadyHandled.get()
        val processAlive = serverProcess?.isAlive == true ||
            com.pocketcraft.server.server.ServerLauncher.hasActiveExternalProcess() ||
            isLocalServerPortOpen(currentServerPort)
            
        val serverState = when {
            serverRunning -> "running"
            isLaunching -> "starting"
            else -> "stopped"
        }

        if (serverState == "starting") {
            if (bootStartedAt == 0L) {
                bootStartedAt = System.currentTimeMillis()
            }
        } else {
            bootStartedAt = 0L
        }

        val progressPercent = if (serverRunning) {
            100
        } else if (serverState == "starting") {
            val elapsedMs = (System.currentTimeMillis() - bootStartedAt).coerceAtLeast(0L)
            when {
                elapsedMs < 8_000L -> ((elapsedMs / 8_000f) * 20f)
                elapsedMs < 20_000L -> 20f + (((elapsedMs - 8_000L) / 12_000f) * 32f)
                elapsedMs < 35_000L -> 52f + (((elapsedMs - 20_000L) / 15_000f) * 26f)
                elapsedMs < 55_000L -> 78f + (((elapsedMs - 35_000L) / 20_000f) * 20f)
                else -> 98f
            }.toInt().coerceIn(0, 98)
        } else {
            0
        }
        val bootProgress = if (serverState == "starting") {
            lastNotificationText.ifBlank { ServerStage.STARTING_SERVER.notificationText }
        } else {
            ""
        }
        
        val playersOnline = synchronized(currentPlayersList) {
            currentPlayersList.map { player ->
                mapOf(
                    "name" to player.name,
                    "uuid" to player.uuid,
                    "ping" to player.pingMs,
                    "pingText" to player.pingText()
                )
            }
        }
        
        val startedAt = serverStartTimeMillis
        val uptimeSeconds = if (startedAt > 0L && serverRunning) {
            ((System.currentTimeMillis() - startedAt) / 1000L).coerceAtLeast(0L)
        } else {
            0L
        }
        
        val currentTps = if (serverRunning) {
            val tpsVal = currentServerTps
            if (tpsVal > 0f) tpsVal.toDouble() else 20.0
        } else {
            null
        }
        
        val dbDao = com.pocketcraft.server.afk.AfkHelperDatabase.getInstance(this).afkFarmLocationDao()
        val currentWorld = currentWorldName ?: "world"
        val rawBots = dbDao.getAll().filter { it.worldName.equals(currentWorld, ignoreCase = true) }
        val afkBotEnabled = rawBots.any { it.isActive }
        val afkBotsList = rawBots.map { bot ->
            mapOf(
                "id" to bot.id,
                "name" to bot.name,
                "dummyName" to bot.dummyEntityName,
                "x" to bot.x,
                "y" to bot.y,
                "z" to bot.z,
                "world" to bot.worldName,
                "active" to bot.isActive,
                "owner" to bot.ownerPlayerName,
                "ownerUuid" to bot.ownerPlayerUuid
            )
        }
            
        val subdomain = prefs.customSubdomain
        val subdomainRegion = prefs.customSubdomainRegion
        val relayAddress = getPersistedPublicAddress(this, versionId) ?: ""
        
        val whitelist = readWhitelistNames(this, currentWorld)
        val ops = readOpsNames(this, currentWorld)
        val consoleLines = synchronized(logBuffer) {
            logBuffer.toList().takeLast(50)
        }
        val allPlayers = getRegisteredPlayers(currentWorld)

        val statusDoc = mapOf(
            "serverRunning" to serverRunning,
            "serverState" to serverState,
            "bootProgress" to bootProgress,
            "bootProgressPercent" to progressPercent,
            "playersOnline" to playersOnline,
            "allPlayers" to allPlayers,
            "uptimeSeconds" to uptimeSeconds,
            "tps" to currentTps,
            "afkBotEnabled" to afkBotEnabled,
            "afkBots" to afkBotsList,
            "subdomain" to subdomain,
            "subdomainRegion" to subdomainRegion,
            "relayAddress" to relayAddress,
            "whitelist" to whitelist,
            "ops" to ops,
            "consoleLines" to consoleLines,
            "lastSeen" to com.google.firebase.Timestamp.now(),
            "usedRam" to com.pocketcraft.server.util.RamUtils.getUsedRamMb(this),
            "totalRam" to com.pocketcraft.server.util.RamUtils.getTotalRamMb(this),
            "currentWorld" to activeWorldNameOrDefault(),
            "worlds" to listWorlds(),
            "properties" to readServerProperties(currentWorld),
            "secret" to (prefs.dashboardSecret ?: ""),
            "localIp" to (com.pocketcraft.server.server.ServerAddressResolver.getLocalIpAddress() ?: ""),
            "serverPort" to currentServerPort,
            "isPremium" to (prefs.isPremiumUser || prefs.debugPremiumOverride)
        )

        try {
            FirebaseFirestore.getInstance().collection("users").document(uid)
                .collection("dashboard_status").document("status")
                .set(statusDoc, SetOptions.merge())
        } catch (e: Exception) {
            android.util.Log.e("ServerHostService", "Failed to update dashboard status: ${e.message}")
        }
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
        val serverDir = com.pocketcraft.server.service.ServerFileManager.getServerDir(this, worldName)
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
                    "worldDescription" to props.getProperty("pocketcraft-world-description.$worldName", "Hosted on Pocketcraft")
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
        val serverDir = com.pocketcraft.server.service.ServerFileManager.getServerDir(this, worldName)
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
        val serverDir = com.pocketcraft.server.service.ServerFileManager.getServerDir(context, worldName)
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
        val serverDir = com.pocketcraft.server.service.ServerFileManager.getServerDir(context, worldName)
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

        val prefs = com.pocketcraft.server.data.preferences.AppPreferences(this)
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            ?: prefs.firebaseUserUid
        if (uid != null) {
            val currentWorld = currentWorldName ?: "world"
            serviceScope.launch(Dispatchers.IO) {
                try {
                    val properties = readServerProperties(currentWorld)
                    val dbDao = com.pocketcraft.server.afk.AfkHelperDatabase.getInstance(this@ServerHostService).afkFarmLocationDao()
                    val rawBots = dbDao.getAll().filter { it.worldName.equals(currentWorld, ignoreCase = true) }
                    val afkBotEnabled = rawBots.any { it.isActive }
                    val afkBotsList = rawBots.map { bot ->
                        mapOf(
                            "id" to bot.id,
                            "name" to bot.name,
                            "dummyName" to bot.dummyEntityName,
                            "x" to bot.x,
                            "y" to bot.y,
                            "z" to bot.z,
                            "world" to bot.worldName,
                            "active" to bot.isActive,
                            "owner" to bot.ownerPlayerName,
                            "ownerUuid" to bot.ownerPlayerUuid
                        )
                    }
                    val localIp = com.pocketcraft.server.server.ServerAddressResolver.getLocalIpAddress() ?: ""

                    val statusDoc = mapOf(
                        "serverRunning" to false,
                        "serverState" to "stopped",
                        "bootProgress" to "",
                        "bootProgressPercent" to 0,
                        "playersOnline" to emptyList<Map<String, String>>(),
                        "uptimeSeconds" to 0L,
                        "tps" to null,
                        "lastSeen" to com.google.firebase.Timestamp.now(),
                        "secret" to (prefs.dashboardSecret ?: ""),
                        "properties" to properties,
                        "relayAddress" to "",
                        "afkBotEnabled" to afkBotEnabled,
                        "afkBots" to afkBotsList,
                        "localIp" to localIp,
                        "isPremium" to (prefs.isPremiumUser || prefs.debugPremiumOverride)
                    )

                    FirebaseFirestore.getInstance().collection("users").document(uid)
                        .collection("dashboard_status").document("status")
                        .set(statusDoc, SetOptions.merge()).await()
                } catch (e: Exception) {
                    android.util.Log.e("ServerHostService", "Failed to update offline status: ${e.message}")
                }
            }
        }
    }

    private fun getRegisteredPlayers(worldName: String): List<Map<String, Any>> {
        val serverDir = com.pocketcraft.server.service.ServerFileManager.getServerDir(this, worldName)
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
        
        val playerdataDir = java.io.File(worldDir, "playerdata")
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
                com.pocketcraft.server.service.NBTParser.parsePlayerData(datFile)
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

        const val ACTION_START = "com.pocketcraft.server.action.START"
        const val ACTION_START_LISTENER = "com.pocketcraft.server.action.START_LISTENER"
        const val ACTION_STOP = "com.pocketcraft.server.action.STOP"
        const val ACTION_RESTART = "com.pocketcraft.server.action.RESTART"
        const val ACTION_RECONNECT_RELAY = "com.pocketcraft.server.action.RECONNECT_RELAY"
        const val EXTRA_SKIP_RELAY_UNREGISTER = "skip_relay_unregister"
        const val EXTRA_RELAY_HOST = "relay_host"
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
        private const val KEY_PLAYER_COUNT = "player_count"
        private const val POCKETCRAFT_JOIN_TELLRAW =
            """tellraw @a ["",{"text":"hosted on Pocketcraft","color":"green","bold":true},{"text":"\nJoin our Discord: ","color":"white"},{"text":"https://discord.gg/7xw3Rd2vs2","color":"aqua","underlined":true}]"""
        const val RUNTIME_STATE_OFFLINE = "offline"
        const val RUNTIME_STATE_STARTING = "starting"
        const val RUNTIME_STATE_RUNNING = "running"
        @Keep
        var isServiceRunning = false

        @JvmStatic
        fun isServiceRunning(context: Context): Boolean {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
            @Suppress("DEPRECATION")
            val runningServices = try {
                manager.getRunningServices(Integer.MAX_VALUE)
            } catch (e: Exception) {
                null
            } ?: return isServiceRunning
            for (service in runningServices) {
                if (ServerHostService::class.java.name == service.service.className) {
                    return true
                }
            }
            return false
        }

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

        fun restart(context: Context) {
            val intent = Intent(context, ServerHostService::class.java).apply {
                action = ACTION_RESTART
            }
            try {
                if (isServiceRunning(context)) {
                    context.startService(intent)
                } else {
                    ContextCompat.startForegroundService(context, intent)
                }
            } catch (e: Exception) {
                try {
                    context.startService(intent)
                } catch (e2: Exception) {
                    android.util.Log.e("ServerHostService", "Failed to restart service: ${e2.message}")
                }
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
            }
            obj.put("server_pid", android.os.Process.myPid())
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

        fun pushWidgetUpdate(context: Context, statusOverride: String? = null) {
            val intent = Intent(context, com.pocketcraft.server.widget.ServerWidgetReceiver::class.java).apply {
                action = com.pocketcraft.server.widget.ServerWidgetReceiver.ACTION_TRIGGER_WIDGET_UPDATE
                statusOverride?.let { putExtra("status_override", it) }
            }
            context.sendBroadcast(intent)
        }

        fun isWhitelistEnabled(context: Context, worldName: String): Boolean {
            val propsFile = File(com.pocketcraft.server.service.ServerFileManager.getServerDir(context, worldName), "server.properties")
            if (!propsFile.exists()) return false
            return propsFile.readLines().any { it.trim() == "white-list=true" }
        }
    }
}

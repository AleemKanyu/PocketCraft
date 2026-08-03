package com.pocketcraft.server.ui.screens

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.derivedStateOf
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.pocketcraft.server.analytics.FirebaseAnalyticsManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.tasks.await
import com.pocketcraft.server.BuildConfig
import com.pocketcraft.server.RelayManager
import com.pocketcraft.server.WorldImporter
import com.pocketcraft.server.afk.AfkFarmLocation
import com.pocketcraft.server.afk.AfkHelperManager
import com.pocketcraft.server.data.model.PlayerInfo
import com.pocketcraft.server.data.model.ServerConfig
import com.pocketcraft.server.data.model.ServerType
import com.pocketcraft.server.data.preferences.AppPreferences
import com.pocketcraft.server.data.preferences.AppPreferencesStore
import com.pocketcraft.server.data.repository.ServerConfigRepository
import com.pocketcraft.server.notification.NotificationHelper
import com.pocketcraft.server.network.RconClient
import com.pocketcraft.server.service.PluginManager
import com.pocketcraft.server.service.ConsoleParser
import com.pocketcraft.server.service.ParsedPlayerPing
import com.pocketcraft.server.service.DimensionMigrator
import com.pocketcraft.server.service.ServerFileManager
import com.pocketcraft.server.service.AutoBackupReceiver
import com.pocketcraft.server.service.ServerPropertiesHelper
import com.pocketcraft.server.server.ServerPropertiesWriter
import com.pocketcraft.server.server.ServerHostService
import com.pocketcraft.server.server.ServerAddressResolver
import com.pocketcraft.server.sound.SoundManager
import com.pocketcraft.server.service.PlayerDataManager
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.NetworkInterface
import java.net.Socket
import java.net.InetSocketAddress
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.Properties
import java.util.TimeZone
import java.util.UUID
import java.nio.ByteBuffer
import java.util.zip.GZIPInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.flow.first

enum class ServerStatus { ONLINE, STARTING, RESTARTING, OFFLINE }

enum class ServerUiState {
    IDLE,       // not running, show Start Server button
    STARTING,   // process launched, waiting for "Done" log line
    RUNNING     // fully ready, show address card + Stop/Restart
}

data class BackupEntry(
    val name: String,
    val sizeMb: Long,
    val date: String,
    val file: File
)

data class WorldEntry(
    val name: String,
    val sizeMb: Long,
    val isActive: Boolean,
    val photoUrl: String = ""
)

class ServerStateHolder(
    private val context: Context,
    val versionId: String,
    initialWorld: String = "world"
) {
    companion object {
        const val DEFAULT_SERVER_DESCRIPTION = "Hosted on Pocketcraft"
        private const val POCKETCRAFT_JOIN_MESSAGE_TEXT =
            "hosted on Pocketcraft"
        private const val POCKETCRAFT_JOIN_MESSAGE_URL = "https://discord.gg/7xw3Rd2vs2"
        private const val MAX_REALISTIC_WIFI_PING_MS = 5_000

        val applicationScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
        
        val isBackingUpState = mutableStateOf(false)
        val backupProgressPercentState = mutableStateOf(0)
        val backupStatusMessageState = mutableStateOf("")
        val backupSaveLocationState = mutableStateOf("")

        val isRestoringBackupState = mutableStateOf(false)
        val restoreProgressPercentState = mutableStateOf(0)
        val restoreStatusMessageState = mutableStateOf("")

        val isDownloadingBackupState = mutableStateOf(false)
        val downloadBackupProgressPercentState = mutableStateOf(0)
        val downloadBackupStatusMessageState = mutableStateOf("")

        var manualBackupJob: kotlinx.coroutines.Job? = null
        val manualBackupStateState = mutableStateOf(com.pocketcraft.server.service.BackupProgressTracker.State.IDLE)
    }

    var manualBackupState: com.pocketcraft.server.service.BackupProgressTracker.State
        get() = manualBackupStateState.value
        set(value) { manualBackupStateState.value = value }

    fun cancelManualBackup() {
        manualBackupJob?.cancel()
        manualBackupJob = null
        isBackingUp = false
        backupProgressPercent = 0
        backupStatusMessage = ""
        manualBackupState = com.pocketcraft.server.service.BackupProgressTracker.State.FAILED
    }

    fun resetManualBackupState() {
        manualBackupState = com.pocketcraft.server.service.BackupProgressTracker.State.IDLE
    }

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    var activeWorld: String = initialWorld.ifBlank { "world" }
        private set(value) {
            field = value.ifBlank { "world" }
            afkHelperManager.onWorldChanged()
        }
    private val serverDir: File
        get() = ServerFileManager.getServerDir(appContext, activeWorld.ifBlank { "world" })
    private val serverPhotosDir: File
        get() = File(serverDir, "server_photos").also { it.mkdirs() }
    private val backupsDir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "PocketCraft Server Backups").also { it.mkdirs() }
    private val logsQueue = ArrayDeque<String>(2000)
    private var receiverRegistered = false
    private var startedAtRealtime: Long? = null
    private var startupStartedAtRealtime: Long? = null
    private var jvmStartedTracking = false
    private var startupProgressJob: Job? = null
    private var startupLaunchJob: Job? = null
    private var stopWatchdogJob: Job? = null
    private var restartFallbackJob: Job? = null
    private var periodicWorldSaveJob: Job? = null
    private var periodicLocationJob: Job? = null
    private var periodicPingJob: Job? = null
    @Volatile
    private var lastPingLogReceivedTimeMs = 0L
    private var lastRequestedServerType: ServerType? = null
    private var consoleVisibleAfterStart = false
    private var pendingRestart by mutableStateOf(false)
    private var hasAnnouncedServerOnline = false
    private val namedListLock = Any()
    private val worldRegistryKey = "pocketcraft-world-list"
    private val worldSetupRegistryKey = "pocketcraft-world-setup-list"
    private val singleServerPort = 25565
    private val serverSlotSystemFolderNames = setOf(
        "plugins", "mods", "resourcepacks", "jre", "jre-21", "jre-runtime",
        "logs", "cache", "config", "libraries", "binaries", "backups",
        "crash-reports", "bundler", "versions", "world_plugin_profiles",
        "server_photos"
    )

    // Directories excluded from backups — all are re-downloadable or regenerated automatically.
    // plugins/, mods/, resourcepacks/, config/, and all world folders are NOT in this list
    // and will always be included in the backup.
    private val backupExcludeDirs = setOf(
        "jre", "jre-21", "jre-runtime",      // Java runtime — re-downloaded on next start
        "libraries", "bundler", "versions",   // Paper/Fabric internals — re-downloaded
        "binaries",                            // Cached server JARs — re-downloaded
        "cache",                               // Runtime caches — regenerated
        "logs",                                // Server logs — not needed for restore
        "crash-reports",                       // Crash logs — not needed for restore
        "world_plugin_profiles",               // Internal profile cache — already excluded
        "backups"                              // Backups folder — prevent zipping existing backups
    )
    private val stopWatchdogTimeoutMs = 25_000L
    private val restartFallbackDelayMs = 10_000L
    private val worldPluginProfilesDir: File
        get() = File(serverDir, "world_plugin_profiles").also { it.mkdirs() }
    private val totalRamGb by lazy {
        val manager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(info)
        (info.totalMem / (1024L * 1024L * 1024L)).toInt().coerceAtLeast(1)
    }
    private val afkHelperManager by lazy {
        AfkHelperManager(
            context = appContext,
            scope = scope,
            currentWorldProvider = { activeWorld },
            isServerRunningProvider = { isRunning && !isStopping },
            onlinePlayersProvider = { onlinePlayers.toList() },
            knownPlayersProvider = { knownPlayers.toList() },
            sendRconCommand = ::sendRconCommand,
            appendLog = ::appendLog,
            notifyStateChanged = ::notifyStateChanged
        )
    }

    private val recentlyWelcomedPlayers = ConcurrentHashMap<String, Long>()

    private val _stateUpdateTrigger = MutableStateFlow(0)
    val stateUpdateTrigger: StateFlow<Int> = _stateUpdateTrigger.asStateFlow()

    fun notifyStateChanged() {
        _stateUpdateTrigger.value = _stateUpdateTrigger.value + 1
    }

    private val _isRunning = mutableStateOf(false)
    var isRunning: Boolean
        get() = _isRunning.value
        private set(value) {
            _isRunning.value = value
            if (value) {
                _isStarting.value = false
                _isRestartingCycle.value = false
            }
            updateServerUiState()
            afkHelperManager.onServerStateChanged(value)
            notifyStateChanged()
        }

    private val _isStarting = mutableStateOf(false)
    var isStarting: Boolean
        get() = _isStarting.value
        private set(value) {
            if (value && _isRunning.value) return
            _isStarting.value = value
            updateServerUiState()
            notifyStateChanged()
        }

    private val _serverUiState = mutableStateOf(ServerUiState.IDLE)
    var serverUiState: ServerUiState
        get() = _serverUiState.value
        private set(value) {
            _serverUiState.value = value
            notifyStateChanged()
        }

    private fun updateServerUiState() {
        serverUiState = when {
            isStopping && !isRestartingCycle && !pendingRestart -> ServerUiState.RUNNING
            isRunning && isJavaServerDone && serverJoinable -> ServerUiState.RUNNING
            isStarting || isRestartingCycle || isRunning -> ServerUiState.STARTING
            else -> ServerUiState.IDLE
        }
        ServerHostService.pushWidgetUpdate(appContext)
    }

    private val _isRestartingCycle = mutableStateOf(false)
    var isRestartingCycle: Boolean
        get() = _isRestartingCycle.value
        private set(value) {
            _isRestartingCycle.value = value
            notifyStateChanged()
        }
    private var lastStartRequestedRealtime: Long = 0L
    var config by mutableStateOf(loadConfig())
        private set
    var localIp by mutableStateOf("127.0.0.1")
        private set
    var serverName by mutableStateOf("PocketCraft")
        private set
    var serverPhotoUrl by mutableStateOf("")
        private set
    var serverDescription by mutableStateOf("")
        private set
    var tps by mutableStateOf(0f)
        private set
    var worldSizeMb by mutableStateOf(0L)
        private set
    var isRefreshing by mutableStateOf(false)
        private set
    private val _publicAddress = mutableStateOf<String?>(null)
    var publicAddress: String?
        get() = if (status == ServerStatus.OFFLINE) null else _publicAddress.value
        private set(value) {
            _publicAddress.value = value
            notifyStateChanged()
        }
    
    private val _tunnelConnecting = mutableStateOf(false)
    private val _relaySwitchInProgress = mutableStateOf(false)
    var relaySwitchInProgress: Boolean
        get() = _relaySwitchInProgress.value
        private set(value) {
            _relaySwitchInProgress.value = value
            notifyStateChanged()
        }

    var tunnelConnecting: Boolean
        get() = _tunnelConnecting.value
        private set(value) {
            _tunnelConnecting.value = value
            if (!value) {
                _relaySwitchInProgress.value = false
            }
            notifyStateChanged()
        }
    
    private val _tunnelError = mutableStateOf<String?>(null)
    var tunnelError: String?
        get() = _tunnelError.value
        private set(value) {
            _tunnelError.value = value
            notifyStateChanged()
        }
    var relayHost by mutableStateOf("play.pocketcraft.online")
        private set
    var relayFallbackActive by mutableStateOf(false)
        private set
    private val _isStopping = mutableStateOf(false)
    var isStopping: Boolean
        get() = _isStopping.value
        private set(value) {
            _isStopping.value = value
            notifyStateChanged()
        }
    var isBackingUp: Boolean
        get() = isBackingUpState.value
        private set(value) { isBackingUpState.value = value }
    var backupProgressPercent: Int
        get() = backupProgressPercentState.value
        private set(value) { backupProgressPercentState.value = value }
    var backupStatusMessage: String
        get() = backupStatusMessageState.value
        private set(value) { backupStatusMessageState.value = value }
    var backupSaveLocation: String
        get() = backupSaveLocationState.value
        private set(value) { backupSaveLocationState.value = value }
    var activeWorldNeedsSetup by mutableStateOf(false)
        private set
    var isRestoringBackup: Boolean
        get() = isRestoringBackupState.value
        private set(value) { isRestoringBackupState.value = value }
    var restoreProgressPercent: Int
        get() = restoreProgressPercentState.value
        private set(value) { restoreProgressPercentState.value = value }
    var restoreStatusMessage: String
        get() = restoreStatusMessageState.value
        private set(value) { restoreStatusMessageState.value = value }
    var isDownloadingBackup: Boolean
        get() = isDownloadingBackupState.value
        private set(value) { isDownloadingBackupState.value = value }
    var downloadBackupProgressPercent: Int
        get() = downloadBackupProgressPercentState.value
        private set(value) { downloadBackupProgressPercentState.value = value }
    var downloadBackupStatusMessage: String
        get() = downloadBackupStatusMessageState.value
        private set(value) { downloadBackupStatusMessageState.value = value }

    fun startCreateBackup(onResult: (String) -> Unit = {}) {
        applicationScope.launch {
            val result = createBackup()
            withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(appContext, result, android.widget.Toast.LENGTH_LONG).show()
                onResult(result)
            }
        }
    }

    fun startImportBackup(uri: android.net.Uri, onResult: (String) -> Unit = {}) {
        applicationScope.launch {
            val result = importBackup(uri)
            withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(appContext, result, android.widget.Toast.LENGTH_LONG).show()
                onResult(result)
            }
        }
    }

    fun startRestoreBackup(backup: BackupEntry, onResult: (String) -> Unit = {}) {
        applicationScope.launch {
            val result = restoreBackup(backup)
            withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(appContext, result, android.widget.Toast.LENGTH_LONG).show()
                onResult(result)
            }
        }
    }

    fun startRestoreOverworldOnly(backup: BackupEntry, onResult: (String) -> Unit = {}) {
        applicationScope.launch {
            val result = restoreOverworldOnly(backup)
            withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(appContext, result, android.widget.Toast.LENGTH_LONG).show()
                onResult(result)
            }
        }
    }

    fun startRestoreDimensionsOnly(backup: BackupEntry, onResult: (String) -> Unit = {}) {
        applicationScope.launch {
            val result = restoreDimensionsOnly(backup)
            withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(appContext, result, android.widget.Toast.LENGTH_LONG).show()
                onResult(result)
            }
        }
    }

    fun startDownloadBackup(backup: BackupEntry, onResult: (String) -> Unit = {}) {
        applicationScope.launch {
            val result = downloadBackup(backup)
            withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(appContext, result, android.widget.Toast.LENGTH_LONG).show()
                onResult(result)
            }
        }
    }
    var startupProgressPercent by mutableStateOf(0)
        private set
    var startupStatusMessage by mutableStateOf("")
        private set
    var activePlayersTab by mutableStateOf(0)
    var openServerRiskAcknowledged by mutableStateOf(false)
        private set
    var bedrockBridgeEnabled by mutableStateOf(true)
        private set
    var chunkyProgressPercent by mutableStateOf<Int?>(null)
        private set
    var chunksLoadingTipShown by mutableStateOf(false)
        private set
    var firstBootComplete by mutableStateOf(false)
        private set
    var areSpawnChunksLoaded by mutableStateOf(false)
        private set
    var isJavaServerDone by mutableStateOf(false)
        private set
    var isGeyserDone by mutableStateOf(false)
        private set
    private var isRelayDone = false
    private val _serverJoinable = mutableStateOf(false)
    var serverJoinable: Boolean
        get() = _serverJoinable.value
        private set(value) {
            _serverJoinable.value = value
            notifyStateChanged()
        }
    val isServerFullyReady: Boolean get() = serverJoinable
    var isImportingWorld by mutableStateOf(false)
        private set
    var importProgressPercent by mutableStateOf(0f)
        private set
    var importProgressMessage by mutableStateOf("")
        private set

    val logs = mutableStateListOf<String>()
    val onlinePlayers = mutableStateListOf<PlayerInfo>()
    val sessionPlayers = mutableStateListOf<PlayerInfo>()
    val knownPlayers = mutableStateListOf<PlayerInfo>()
    val whitelistPlayers = mutableStateListOf<PlayerInfo>()
    val opPlayers = mutableStateListOf<PlayerInfo>()
    val bannedPlayers = mutableStateListOf<PlayerInfo>()
    val backups = mutableStateListOf<BackupEntry>()
    val worlds = mutableStateListOf<WorldEntry>()
    val afkFarms: List<AfkFarmLocation>
        get() = afkHelperManager.farms
    val isAfkHelperBusy: Boolean
        get() = afkHelperManager.isBusy
    var lastAfkEnabledTime by mutableStateOf(0L)
        private set

    private fun attemptTransitionToOnline() {
        if (isJavaServerDone && areSpawnChunksLoaded) {
            isGeyserDone = true
            val bridgeEnabled = try { PluginManager.isBedrockBridgeEnabled(appContext, activeWorld.ifBlank { "world" }) } catch (e: Exception) { false }
            
            // Transition state from STARTING to RUNNING
            isStarting = false
            isRunning = true
            isStopping = false
            startupProgressPercent = 100
            startupStatusMessage = "Server ready!"
            stopStartupProgressTracking(reset = false)

            markServerReady()
            markJoinable()
            bedrockBridgeEnabled = bridgeEnabled

            // Force UI state to RUNNING immediately so the loading card dismisses without
            // waiting for the async refreshAll() disk read to complete.
            updateServerUiState()
            refreshAll()
        }
    }


    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val type = intent?.getStringExtra(ServerHostService.EXTRA_EVENT_TYPE).orEmpty()
            val line = intent?.getStringExtra(ServerHostService.EXTRA_LINE).orEmpty()
            val intentVersionId = intent?.getStringExtra(ServerHostService.EXTRA_VERSION_ID)
            
            android.util.Log.d("ServerStateHolder", "onReceive: action=${intent?.action}, type=$type, versionId=$intentVersionId")
            
            if (intent?.action != ServerHostService.ACTION_SERVER_EVENT) return
            if (intentVersionId != versionId) return

            // Bug fix: Only re-enter STARTING state for events that genuinely signal
            // an active server process (not plain output lines). Plain EVENT_OUTPUT
            // events buffered after the server has already stopped would otherwise
            // briefly flip the UI back to "Starting server…".
            val isLaunchSignal = type == ServerHostService.EVENT_SERVER_READY ||
                type == ServerHostService.EVENT_TUNNEL_CONNECTING ||
                type == ServerHostService.EVENT_TUNNEL_CONNECTED ||
                type == ServerHostService.EVENT_TUNNEL_FAILED ||
                type == ServerHostService.EVENT_CHUNKY_PROGRESS ||
                type == ServerHostService.EVENT_CHUNKS_LOADING
            val isStopSignal = type == ServerHostService.EVENT_STOPPED ||
                type == ServerHostService.EVENT_SERVER_CRASHED
            if (!isStopSignal && !isStopping) {
                if (!isRunning && !isStarting && isLaunchSignal) {
                    isStarting = true
                    isRunning = false
                }
            }

            if (type == ServerHostService.EVENT_STOPPED || type == ServerHostService.EVENT_SERVER_CRASHED) {
                val prefs = AppPreferences(appContext)
                if (prefs.hasStartedServer) {
                    prefs.hasCompletedStartStopCycle = true
                }
                // If a real player joined this session and the server stopped (not restarting),
                // mark the rating popup as pending — it will show on the next app launch.
                val isRestartingNow = isRestartingCycle || pendingRestart
                val shouldRestart = type == ServerHostService.EVENT_STOPPED && isRestartingNow
                if (!shouldRestart && sessionPlayers.isNotEmpty()) {
                    prefs.pendingRatingPopup = true
                }
                scope.launch {
                    val failedDuringStartup = isStarting && !shouldRestart
                    restartFallbackJob?.cancel()
                    restartFallbackJob = null
                    stopStartupProgressTracking(reset = !shouldRestart)
                    isStopping = false
                    isRunning = false
                    if (!shouldRestart) {
                        isStarting = false
                        isRestartingCycle = false
                        pendingRestart = false
                        isJavaServerDone = false
                        isGeyserDone = false
                        areSpawnChunksLoaded = false
                    } else {
                        isStarting = true
                        isRestartingCycle = true
                        isJavaServerDone = false
                        isGeyserDone = false
                        areSpawnChunksLoaded = false
                        startupStatusMessage = "Restarting server..."
                        startStartupProgressTracking()
                    }
                    updateServerUiState()
                    stopPeriodicLocationPolling()
                    tps = 0f
                    if (!shouldRestart) {
                        publicAddress = null
                        tunnelConnecting = false
                        tunnelError = null
                        startedAtRealtime = null
                        lastStartRequestedRealtime = 0L
                        resetJoinable()
                    }
                    onlinePlayers.clear()
                    if (type == ServerHostService.EVENT_SERVER_CRASHED) {
                        val reason = line.ifBlank { "The server process exited unexpectedly." }
                        appendLog("[ERROR] Server process crashed ($reason).")
                        FirebaseAnalyticsManager.logServerCrashed(versionId, line)
                        recordServerFailure(reason, failedDuringStartup)
                    } else {
                        appendLog("[INFO] Server stopped.")
                    }
                    stopWatchdogJob?.cancel()
                    stopWatchdogJob = null

                    // ── Auto-backup on stop (Bug fix: was dead code in the when-branch below) ──
                    val appPrefs = com.pocketcraft.server.data.preferences.AppPreferences(appContext)
                    val runOnStop = appPrefs.autoBackupOnStop && !shouldRestart
                    val runPendingAuto = appPrefs.pendingAutoBackup && !shouldRestart
                    if (runPendingAuto) {
                        appendLog("[PocketCraft] Running queued daily automatic backup...")
                        AutoBackupReceiver.startPendingBackup(appContext)
                    } else if (runOnStop) {
                        appendLog("[PocketCraft] Triggering automated backup on stop...")
                        val msg = createBackup()
                        appendLog("[PocketCraft] Auto Backup: $msg")
                    }

                    if (shouldRestart) {
                        isStarting = true
                        appendLog("[PocketCraft] Starting server again...")
                        delay(1500)
                        startServer(isRestart = true)
                    } else {
                        refreshAll()
                    }
                }
                return
            }

            scope.launch {
                when (type) {
                    ServerHostService.EVENT_SERVER_READY -> {
                        // Authoritative signal from the service that the server process is ready.
                        // This bypasses the log-parsing chain that can fail in release builds.
                        android.util.Log.d("ServerStateHolder", "EVENT_SERVER_READY received — setting ready flags")
                        isJavaServerDone = true
                        isGeyserDone = true
                        areSpawnChunksLoaded = true
                        isStarting = true
                        attemptTransitionToOnline()
                    }
                    ServerHostService.EVENT_OUTPUT -> appendLog(line)
                    ServerHostService.EVENT_TUNNEL_CONNECTING -> {
                        tunnelConnecting = true
                        tunnelError = null
                        appendLog("[PocketCraft] Opening internet relay...")
                    }
                    ServerHostService.EVENT_TUNNEL_CONNECTED -> {
                        publicAddress = line.trim().ifBlank { null }
                        tunnelConnecting = false
                        tunnelError = null
                        isRelayDone = true
                        relayFallbackActive = intent.getBooleanExtra(ServerHostService.EXTRA_IS_FALLBACK, false)
                        if (!publicAddress.isNullOrBlank()) {
                            appendLog("[PocketCraft] Internet address: $publicAddress")
                            if (relayFallbackActive) {
                                appendLog("[PocketCraft] NOTE: Selected relay is offline. Falling back to another relay for connection.")
                            }
                        }
                        if (isStarting) {
                            attemptTransitionToOnline()
                        }
                    }
                    ServerHostService.EVENT_TUNNEL_FAILED -> {
                        tunnelConnecting = false
                        // Keep the last known address if one exists; relay failures can be transient.
                        if (publicAddress.isNullOrBlank()) {
                            publicAddress = null
                        }
                        tunnelError = line.ifBlank { "Internet relay unavailable." }
                        isRelayDone = true
                        appendLog("[WARN] ${tunnelError.orEmpty()}")
                        if (isStarting) {
                            attemptTransitionToOnline()
                        }
                    }
                    ServerHostService.EVENT_SERVER_CRASHED -> {
                        val failedDuringStartup = isStarting
                        val reason = line.ifBlank { "The server exited unexpectedly." }
                        appendLog("[ERROR] Server process crashed ($reason).")
                        FirebaseAnalyticsManager.logServerCrashed(versionId, line)
                        stopStartupProgressTracking(reset = true)
                        isStopping = false
                        pendingRestart = false
                        isStarting = false
                        isRunning = false
                        tps = 0f
                        resetJoinable()
                        recordServerFailure(reason, failedDuringStartup)
                    }
                    ServerHostService.EVENT_ERROR -> {
                        appendLog("[ERROR] $line")
                    }
                    ServerHostService.EVENT_STOPPED -> {
                        // Note: This branch is unreachable because EVENT_STOPPED is handled
                        // by the early-return block above. Kept as a no-op safety fallback.
                    }
                    ServerHostService.EVENT_CHUNKY_PROGRESS -> {
                        val percent = intent.getIntExtra(ServerHostService.EXTRA_CHUNKY_PERCENT, -1)
                        if (percent in 0..100) {
                            chunkyProgressPercent = percent
                        } else {
                            chunkyProgressPercent = null
                        }
                    }
                    ServerHostService.EVENT_CHUNKS_LOADING -> {
                        chunksLoadingTipShown = true
                    }
                }
            }
        }
    }

    init {
        NotificationHelper.createChannel(appContext)
        registerReceiver()
        observeOpenServerRiskAcknowledgement()
        consoleVisibleAfterStart = false
        refreshAll()
        ensureBedrockBridgeProvisioned()
        performOneTimeEmergencyRecovery()
        
        scope.launch {
            AppPreferencesStore.isFirstBootCompleteFlow(appContext).collect { complete ->
                firstBootComplete = complete
            }
        }
    }

    private fun performOneTimeEmergencyRecovery() {
        scope.launch(Dispatchers.IO) {
            val marker = File(appContext.filesDir, ".emergency_playerdata_recovery_done")
            if (marker.exists()) return@launch

            android.util.Log.i("PocketCraft", "Running emergency playerdata recovery...")
            val backupDir = File("/sdcard/Download/PocketCraftWorldBackups/Mainworld")
            if (!backupDir.exists()) {
                android.util.Log.w("PocketCraft", "Emergency recovery: Backup dir not found")
                return@launch
            }

            val backupFiles = backupDir.listFiles()?.filter { it.name.endsWith(".zip") }
                ?.sortedByDescending { it.lastModified() }
            if (backupFiles.isNullOrEmpty()) {
                android.util.Log.w("PocketCraft", "Emergency recovery: No backup zips found")
                return@launch
            }

            // Find the 20260718-202719 backup or fall back to the most recent zip
            val targetZip = backupFiles.firstOrNull { it.name.contains("20260718-202719") }
                ?: backupFiles.first()

            android.util.Log.i("PocketCraft", "Emergency recovery: Extracting playerdata from zip: ${targetZip.absolutePath}")
            val targetServerDir = ServerFileManager.getServerDir(appContext, "Mainworld")
            val baseWorldDir = File(targetServerDir, "Mainworld")

            val pdDir = File(baseWorldDir, "playerdata")
            val statsDir = File(baseWorldDir, "stats")
            val advDir = File(baseWorldDir, "advancements")

            pdDir.mkdirs()
            statsDir.mkdirs()
            advDir.mkdirs()

            runCatching {
                java.util.zip.ZipFile(targetZip).use { zip ->
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        if (entry.isDirectory) continue

                        val name = entry.name
                        val isPd = name.startsWith("world/playerdata/") || name.startsWith("Mainworld/playerdata/")
                        val isStats = name.startsWith("world/stats/") || name.startsWith("Mainworld/stats/")
                        val isAdv = name.startsWith("world/advancements/") || name.startsWith("Mainworld/advancements/")

                        if (isPd || isStats || isAdv) {
                            val fileName = name.substringAfterLast('/')
                            if (fileName.isBlank()) continue

                            val destFolder = when {
                                isPd -> pdDir
                                isStats -> statsDir
                                else -> advDir
                            }

                            val destFile = File(destFolder, fileName)
                            // Extract file
                            zip.getInputStream(entry).use { input ->
                                destFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                        }
                    }
                }
                marker.createNewFile()
                android.util.Log.i("PocketCraft", "Emergency playerdata recovery completed successfully!")
                refreshAll()
            }.onFailure {
                android.util.Log.e("PocketCraft", "Emergency recovery failed: ${it.message}", it)
            }
        }
    }

    private fun ensureBedrockBridgeProvisioned() {
        val world = activeWorld.ifBlank { "world" }
        scope.launch(Dispatchers.IO) {
            try {
                PluginManager.ensureBedrockBridgePlugins(appContext, world)
                    .onSuccess {
                        PluginManager.enforceBedrockBridgeLocalConfig(appContext, world)
                    }
                    .onFailure { error ->
                        android.util.Log.w("ServerStateHolder", "Failed to provision Bedrock bridge: ${error.message}")
                    }
            } catch (e: Exception) {
                android.util.Log.e("ServerStateHolder", "Bedrock bridge provisioning crashed: ${e.message}", e)
            }
            withContext(Dispatchers.Main) {
                try {
                    bedrockBridgeEnabled = PluginManager.isBedrockBridgeEnabled(appContext, world)
                } catch (e: Exception) {
                    android.util.Log.e("ServerStateHolder", "Failed to check Bedrock bridge status: ${e.message}")
                    bedrockBridgeEnabled = false
                }
            }
        }
    }

    private fun observeOpenServerRiskAcknowledgement() {
        scope.launch {
            AppPreferencesStore.isOpenServerRiskAcknowledgedFlow(appContext).collect { acknowledged ->
                openServerRiskAcknowledged = acknowledged
            }
        }
    }

    fun acknowledgeOpenServerRisk() {
        if (openServerRiskAcknowledged) return
        scope.launch {
            AppPreferencesStore.setOpenServerRiskAcknowledged(appContext, true)
        }
    }

    val status: ServerStatus
        get() = when {
            isRestarting -> ServerStatus.RESTARTING
            isRunning && isJavaServerDone && serverJoinable -> ServerStatus.ONLINE
            isStarting || isRunning -> ServerStatus.STARTING
            else -> ServerStatus.OFFLINE
        }

    val versionLabel: String
        get() = versionId

    val runtimeVersionLabel: String
        get() = config.gameVersion

    val isNavigationLocked: Boolean
        get() = isStarting || isRunning || isStopping

    val isRestarting: Boolean
        get() = isRestartingCycle || (isStopping && pendingRestart)

    val healthPercent: Float
        get() = when (status) {
            ServerStatus.OFFLINE -> 0f
            ServerStatus.STARTING, ServerStatus.RESTARTING -> 38f
            ServerStatus.ONLINE -> ((tps.coerceIn(0f, 20f) / 20f) * 100f).coerceIn(32f, 100f)
        }

    fun refreshAll() {
        scope.launch {
            isRefreshing = true
            withContext(Dispatchers.IO) { flattenWorldStructure() }
            val persistedRuntimeState = withContext(Dispatchers.IO) { readPersistedRuntimeState() }
            withContext(Dispatchers.IO) { syncActiveWorldServerPresentation() }
            val snapshot = withContext(Dispatchers.IO) { readSnapshot() }
            config = snapshot.config
            localIp = snapshot.localIp
            serverName = snapshot.serverName
            serverPhotoUrl = snapshot.serverPhotoUrl
            serverDescription = snapshot.serverDescription
            worldSizeMb = snapshot.worldSizeMb
            replaceAll(knownPlayers, snapshot.knownPlayers)
            replaceAll(whitelistPlayers, snapshot.whitelist)
            replaceAll(opPlayers, snapshot.ops)
            replaceAll(bannedPlayers, snapshot.banned)
            replaceAll(backups, snapshot.backups)
            replaceAll(worlds, snapshot.worlds)
            relayHost = snapshot.relayHost
            activeWorldNeedsSetup = snapshot.activeWorldNeedsSetup
            bedrockBridgeEnabled = snapshot.bedrockBridgeEnabled
            applyPersistedRuntimeState(persistedRuntimeState)
            if (!isStarting && !isRunning) {
                tps = 0f
            }
            isRefreshing = false

            // Check if a crash loop was detected and the server is offline
            val prefs = AppPreferences(appContext)
            if (prefs.consecutiveCrashCount > 2 && status == ServerStatus.OFFLINE) {
                val crashReasonText = "The server repeatedly crashed during startup. JRE cache might be corrupt or an integrity check failed. Try clearing JRE cache in Settings."
                prefs.consecutiveCrashCount = 0
                recordServerFailure(crashReasonText, duringStartup = true)
            }

            withContext(Dispatchers.IO) {
                afkHelperManager.refreshNow()
            }
        }
    }

    suspend fun addAfkFarm(name: String, x: Int, y: Int, z: Int): String =
        afkHelperManager.addFarm(name = name, x = x, y = y, z = z)

    suspend fun toggleAfkFarm(id: String): String {
        val result = afkHelperManager.toggleFarm(id)
        lastAfkEnabledTime = System.currentTimeMillis()
        return result
    }

    suspend fun deleteAfkFarm(id: String): String =
        afkHelperManager.deleteFarm(id)

    suspend fun updateAfkFarm(id: String, name: String, x: Int, y: Int, z: Int): String =
        afkHelperManager.updateFarm(id, name, x, y, z)

    suspend fun refreshAfkHelpers() {
        afkHelperManager.refreshNow()
    }

    suspend fun suggestAfkFarmLocation(playerName: String? = null): Triple<Int, Int, Int>? =
        afkHelperManager.captureSuggestedLocation(playerName)

    var showEulaDialog by mutableStateOf(false)
        private set

    var showCrashDialog by mutableStateOf(false)
        private set
    var crashReason by mutableStateOf("")
        private set
    var crashDetails by mutableStateOf("")
        private set
    var crashWasDuringStartup by mutableStateOf(false)
        private set

    fun dismissCrashDialog() {
        showCrashDialog = false
        crashReason = ""
        crashDetails = ""
        crashWasDuringStartup = false
    }

    var showBatteryOptimizationDialog by mutableStateOf(false)
        private set

    fun dismissBatteryOptimizationDialog() {
        showBatteryOptimizationDialog = false
    }

    fun requestBatteryOptimization() {
        showBatteryOptimizationDialog = false
        val pm = appContext.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        if (pm != null && !pm.isIgnoringBatteryOptimizations(appContext.packageName)) {
            val opened = runCatching {
                val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${appContext.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                appContext.startActivity(intent)
                true
            }.getOrDefault(false)

            if (!opened) {
                // Fallback for iQOO / Vivo / OriginOS / FuntouchOS custom background & autostart managers
                runCatching {
                    val intent = Intent().apply {
                        setClassName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    appContext.startActivity(intent)
                }.onFailure {
                    runCatching {
                        val intent = Intent().apply {
                            setClassName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        appContext.startActivity(intent)
                    }.onFailure {
                        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:${appContext.packageName}")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        appContext.startActivity(intent)
                    }
                }
            }
        }
    }

    private fun recordServerFailure(reason: String, duringStartup: Boolean) {
        val cleanReason = reason.trim().ifBlank { "The server exited unexpectedly." }
        val serverTypeForDiagnostics = resolveServerTypeForDiagnostics()
        crashReason = cleanReason
        crashWasDuringStartup = duringStartup
        crashDetails = buildString {
            appendLine("PocketCraft server issue")
            appendLine("App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Server type: ${serverTypeForDiagnostics.displayName}")
            appendLine("Server version: $versionId")
            appendLine("World: ${activeWorld.ifBlank { "world" }}")
            appendLine("Phase: ${if (duringStartup) "startup" else "running"}")
            appendLine("Reason: $cleanReason")
            appendLine()
            appendLine("Recent console output:")
            logsQueue.toList().takeLast(80).forEach { line -> appendLine(line) }
        }.trim()
        showCrashDialog = true
    }

    fun dismissEulaDialog() {
        showEulaDialog = false
    }

    fun acceptEula() {
        showEulaDialog = false
        scope.launch(Dispatchers.IO) {
            // Persist acceptance globally so we never ask again
            AppPreferences(appContext).eulaAccepted = true
            ServerFileManager.acceptEula(appContext, activeWorld)
            withContext(Dispatchers.Main) {
                appendLog("[PocketCraft] EULA accepted. Starting server...")
                // Auto-start immediately — no second tap needed
                startServer()
            }
        }
    }

    fun startServer(isRestart: Boolean = false) {
        if (isBackingUp || isRestoringBackup || isDownloadingBackup) {
            val msg = "Cannot start server while a backup, restore, or download is in progress."
            appendLog("[ERROR] $msg")
            recordServerFailure(msg, duringStartup = true)
            return
        }
        if (versionId.isBlank()) {
            val reason = "No server version is selected."
            appendLog("[ERROR] $reason")
            recordServerFailure(reason, duringStartup = true)
            return
        }
        // Check persisted preference first — only ask once ever
        val prefs = AppPreferences(appContext)
        if (!prefs.eulaAccepted) {
            // Also check if the file is already present (e.g. from a manual import)
            val eulaFile = File(serverDir, "eula.txt")
            val acceptedByFile = eulaFile.exists() && eulaFile.readText().contains("eula=true")
            if (acceptedByFile) {
                // Promote the file acceptance to the preference so we don't ask again
                prefs.eulaAccepted = true
            } else {
                showEulaDialog = true
                return
            }
        }
        prefs.hasStartedServer = true
        // Ensure eula.txt is present in the active world dir (for new worlds or switched worlds)
        runCatching {
            ServerFileManager.prepareEula(appContext, activeWorld)
        }.onFailure { error ->
            val reason = error.message ?: "PocketCraft could not prepare the Minecraft EULA file."
            appendLog("[ERROR] Failed to prepare server files: $reason")
            recordServerFailure(reason, duringStartup = true)
            return
        }
        if (isRunning || (!isRestart && isStarting) || (isStopping && !isRestart)) return

        val pm = appContext.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        if (pm != null && !pm.isIgnoringBatteryOptimizations(appContext.packageName)) {
            if (!prefs.batteryOptimizationRequested) {
                prefs.batteryOptimizationRequested = true
                showBatteryOptimizationDialog = true
            }
        }

        lastStartRequestedRealtime = SystemClock.elapsedRealtime()
        stopWatchdogJob?.cancel()
        stopWatchdogJob = null
        pendingRestart = false
        isStopping = false
        isStarting = true
        isRunning = false
        areSpawnChunksLoaded = false
        isJavaServerDone = false
        isGeyserDone = false
        isRelayDone = false
        resetJoinable()
        ServerHostService.persistRuntimeState(appContext, versionId, activeWorld, ServerHostService.RUNTIME_STATE_STARTING)

        tps = 4f
        startedAtRealtime = SystemClock.elapsedRealtime()
        startupStartedAtRealtime = SystemClock.elapsedRealtime()
        jvmStartedTracking = false
        publicAddress = null
        tunnelConnecting = false
        tunnelError = null
        relayFallbackActive = false
        startupProgressPercent = 0
        startupStatusMessage = "Initializing..."
        hasAnnouncedServerOnline = false
        chunkyProgressPercent = null
        consoleVisibleAfterStart = true
        startStartupProgressTracking()
        if (logsQueue.isNotEmpty()) {
            appendLog("[PocketCraft] ----------------------------------------")
        }
        consoleVisibleAfterStart = true
        onlinePlayers.clear()
        sessionPlayers.clear()
        lastRequestedServerType = config.serverType
        appendLog("[PocketCraft] Booting ${config.serverType.displayName} $versionId...")
        appendLog("[PocketCraft] Checking Bedrock bridge plugins...")

        startupLaunchJob?.cancel()
        startupLaunchJob = scope.launch {
            try {
                val currentWorld = activeWorld.ifBlank { "world" }
                val uid = FirebaseAuth.getInstance().currentUser?.uid?.trim().orEmpty()
                if (uid.isNotBlank()) {
                    runCatching {
                        val snapshot = FirebaseFirestore.getInstance()
                            .collection("subdomains")
                            .whereEqualTo("ownerId", uid)
                            .limit(1)
                            .get()
                            .await()
                        val doc = snapshot.documents.firstOrNull()
                        val currentPrefs = AppPreferences(appContext)
                        val isPremium = com.pocketcraft.server.billing.BillingManager.getInstance(appContext).isPremium.value
                        if (doc != null && isPremium) {
                            currentPrefs.customSubdomain = doc.id.trim().lowercase()
                            currentPrefs.customSubdomainRegion = doc.getString("region")?.trim()?.lowercase()
                        } else {
                            currentPrefs.customSubdomain = null
                            currentPrefs.customSubdomainRegion = null
                        }
                    }.onFailure { error ->
                        android.util.Log.e("ServerStateHolder", "Failed to cache subdomain on startServer: ${error.message}")
                    }
                }
                val bridgeProvisionResult = withContext(Dispatchers.IO) {
                    PluginManager.ensureBedrockBridgePlugins(appContext, currentWorld)
                }

                bridgeProvisionResult.onFailure { error ->
                    appendLog("[PocketCraft] Bedrock bridge setup warning: ${error.message ?: "unknown error"}")
                }

                appendLog("[PocketCraft] Checking optimization plugins...")

                bedrockBridgeEnabled = withContext(Dispatchers.IO) {
                    runCatching { PluginManager.isBedrockBridgeEnabled(appContext, currentWorld) }
                        .getOrDefault(false)
                }

                appendLog("[PocketCraft] Starting server - this may take 30-60 seconds...")

                withContext(Dispatchers.IO) {
                    val currentActiveWorld = sanitizeWorldName(activeWorld.ifBlank { "world" })
                    // Ensure flat→nested migration runs before fixOfflineUuids and server boot
                    ensureWorldDirectories(currentActiveWorld)
                    PlayerDataManager.fixOfflineUuids(serverDir, currentActiveWorld)

                    val isPremium = prefs.isPremiumUser || prefs.debugPremiumOverride
                    val maxPlayersLimit = if (isPremium) 50 else 10
                    val propsFile = File(serverDir, "server.properties")
                    if (propsFile.exists()) {
                        try {
                            val props = java.util.Properties()
                            propsFile.inputStream().use { props.load(it) }
                            val currentMax = props.getProperty("max-players")?.toIntOrNull() ?: 10
                            if (currentMax > maxPlayersLimit) {
                                props["max-players"] = maxPlayersLimit.toString()
                                propsFile.outputStream().use { props.store(it, "Managed by PocketCraft") }
                                android.util.Log.d("ServerStateHolder", "Free tier player count capped to $maxPlayersLimit prior to boot.")
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("ServerStateHolder", "Failed to enforce player cap in properties: ${e.message}")
                        }
                    }
                }

                markActiveWorldSetupCompleted()

                FirebaseAnalyticsManager.logServerStarted(versionId, config.maxPlayers)
                ServerHostService.start(appContext, versionId, activeWorld)
                startupLaunchJob = null

                delay(1000)
                refreshAll()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                startupLaunchJob = null
                val reason = error.message ?: error::class.java.simpleName
                appendLog("[ERROR] Failed to start server: $reason")
                stopStartupProgressTracking(reset = true)
                isStarting = false
                isRunning = false
                isStopping = false
                resetJoinable()
                recordServerFailure(reason, duringStartup = true)
            }
        }
    }

    fun stopServer() {
        if (isStopping || (!isRunning && !isStarting && !isRestartingCycle)) return
        startupLaunchJob?.cancel()
        startupLaunchJob = null
        pendingRestart = false
        isRestartingCycle = false
        restartFallbackJob?.cancel()
        restartFallbackJob = null
        isStopping = true
        isJavaServerDone = false
        isGeyserDone = false
        areSpawnChunksLoaded = false
        stopStartupProgressTracking(reset = true)
        updateServerUiState()
        appendLog("[PocketCraft] Stopping server...")
        val durationSeconds = startedAtRealtime
            ?.let { ((SystemClock.elapsedRealtime() - it) / 1000L).coerceAtLeast(0L) }
            ?: 0L
        FirebaseAnalyticsManager.logServerStopped(versionId, durationSeconds)
        requestWorldSave(reason = "before stop")
        stopPeriodicWorldSave()
        stopPeriodicLocationPolling()
        startStopWatchdog()
        runCatching {
            ServerHostService.stop(appContext)
        }.onFailure { error ->
            isStopping = false
            stopWatchdogJob?.cancel()
            stopWatchdogJob = null
            appendLog("[ERROR] Failed to stop server: ${error.message}")
        }
    }

    fun reconnectRelay() {
        if (!isRunning && !isStarting) return
        appendLog("[PocketCraft] Reconnecting internet relay...")
        publicAddress = null
        relaySwitchInProgress = false
        tunnelConnecting = true
        tunnelError = null
        ServerHostService.reconnectRelay(appContext)
    }

    fun restartServer() {
        if (isStopping) return
        if (!isRunning && !isStarting) {
            startServer()
            return
        }
        if (pendingRestart) return

        isRestartingCycle = true
        pendingRestart = true
        isStarting = true
        isStopping = true
        isJavaServerDone = false
        isGeyserDone = false
        startupStatusMessage = "Restarting server..."
        updateServerUiState()
        appendLog("[PocketCraft] Restart requested...")
        requestWorldSave(reason = "before restart")
        stopPeriodicWorldSave()
        stopPeriodicLocationPolling()
        startStopWatchdog()
        runCatching {
            ServerHostService.restart(appContext)
        }.onFailure { error ->
            pendingRestart = false
            isStopping = false
            stopWatchdogJob?.cancel()
            stopWatchdogJob = null
            restartFallbackJob?.cancel()
            restartFallbackJob = null
            appendLog("[ERROR] Failed to restart server: ${error.message}")
        }

        restartFallbackJob?.cancel()
        restartFallbackJob = scope.launch {
            delay(3500L)
            if (!pendingRestart && !isRestartingCycle) return@launch
            if (isRunning) return@launch
            if (isServerProcessAlive()) return@launch

            pendingRestart = false
            isStopping = false
            isRestartingCycle = false
            appendLog("[PocketCraft] Starting server again...")
            startServer(isRestart = true)
        }
    }

    fun appendLog(line: String) {
        if (line.contains('\n')) {
            line.split('\n').forEach { appendLog(it) }
            return
        }
        val cleanLine = ConsoleParser.stripAnsi(line).trimEnd()
        if (cleanLine.isBlank()) return
        val parsedPings = ConsoleParser.parsePing(cleanLine)
        val isPingLine = parsedPings.isNotEmpty()
        
        // Filter out harmless oshi/JNA Android hardware probe warnings that flood log with false stacktraces
        if (cleanLine.contains("oshi.software.os.linux", ignoreCase = true) ||
            cleanLine.contains("com.sun.jna", ignoreCase = true) ||
            cleanLine.contains("Did not find udev library", ignoreCase = true) ||
            cleanLine.contains("Failed retrieving info for group", ignoreCase = true)) {
            return
        }

        // Debug: log important lines
        if (cleanLine.contains("Done", ignoreCase = true) || cleanLine.contains("Server port", ignoreCase = true)) {
            android.util.Log.d("ServerStateHolder", "appendLog received: $cleanLine")
        }


        // Track startup progress with exact Paper boot log milestones
        if (isStarting) {
            when {
                cleanLine.contains("Running Java 21", ignoreCase = true) ||
                    cleanLine.contains("Loading Paper", ignoreCase = true) -> {
                    startupStatusMessage = "Starting Paper 1.21.11..."
                    startupProgressPercent = maxOf(startupProgressPercent, 10)
                }
                cleanLine.contains("Environment: Environment", ignoreCase = true) -> {
                    startupStatusMessage = "Loading environment..."
                    startupProgressPercent = maxOf(startupProgressPercent, 20)
                }
                cleanLine.contains("recipes", ignoreCase = true) && cleanLine.contains("Loaded", ignoreCase = true) -> {
                    startupStatusMessage = "Loaded recipes & advancements..."
                    startupProgressPercent = maxOf(startupProgressPercent, 35)
                }
                cleanLine.contains("Initialising converters", ignoreCase = true) -> {
                    startupStatusMessage = "Initialising DataConverters..."
                    startupProgressPercent = maxOf(startupProgressPercent, 45)
                }
                cleanLine.contains("PluginInitializerManager] Initializing plugins", ignoreCase = true) -> {
                    startupStatusMessage = "Initializing plugins..."
                    startupProgressPercent = maxOf(startupProgressPercent, 55)
                }
                cleanLine.contains("Starting minecraft server version", ignoreCase = true) -> {
                    startupStatusMessage = "Starting Minecraft server..."
                    startupProgressPercent = maxOf(startupProgressPercent, 65)
                }
                cleanLine.contains("Loading server plugin floodgate", ignoreCase = true) ||
                    cleanLine.contains("boot Floodgate", ignoreCase = true) -> {
                    startupStatusMessage = "Loading Floodgate..."
                    startupProgressPercent = maxOf(startupProgressPercent, 75)
                }
                cleanLine.contains("Via-Mappingloader", ignoreCase = true) ||
                    cleanLine.contains("Loading server plugin ViaVersion", ignoreCase = true) -> {
                    startupStatusMessage = "Loading ViaVersion mappings..."
                    startupProgressPercent = maxOf(startupProgressPercent, 80)
                }
                cleanLine.contains("Loading server plugin Geyser", ignoreCase = true) ||
                    cleanLine.contains("Loaded 1 extension", ignoreCase = true) -> {
                    startupStatusMessage = "Loading Geyser Bedrock bridge..."
                    startupProgressPercent = maxOf(startupProgressPercent, 85)
                }
                cleanLine.contains("Preparing level", ignoreCase = true) -> {
                    startupStatusMessage = "Loading world level..."
                    startupProgressPercent = maxOf(startupProgressPercent, 90)
                }
                cleanLine.contains("Preparing spawn area", ignoreCase = true) -> {
                    val pctMatch = Regex("""Preparing spawn area:\s*(\d+)%""", RegexOption.IGNORE_CASE).find(cleanLine)
                    if (pctMatch != null) {
                        val pct = pctMatch.groupValues[1]
                        startupStatusMessage = "Preparing spawn area ($pct%)..."
                        val rawPct = pct.toIntOrNull() ?: 0
                        val mapped = 90 + ((rawPct * 8) / 100)
                        startupProgressPercent = maxOf(startupProgressPercent, mapped)
                    } else {
                        startupStatusMessage = "Preparing spawn area..."
                    }
                }
                cleanLine.contains("Connecting tunnel", ignoreCase = true) ||
                    cleanLine.contains("Opening internet relay", ignoreCase = true) -> {
                    startupStatusMessage = "Opening internet relay..."
                    startupProgressPercent = maxOf(startupProgressPercent, 95)
                }
            }
        }


        if (!isPingLine) {
            if (logsQueue.size >= 2000) {
                logsQueue.removeFirst()
                if (consoleVisibleAfterStart && logs.isNotEmpty()) {
                    logs.removeAt(0)
                }
            }
            logsQueue.addLast(cleanLine)
            if (consoleVisibleAfterStart) {
                logs.add(cleanLine)
            }
        }

        if (isLegacyRelayAuthError(cleanLine)) {
            val hint = "[PocketCraft] Legacy relay plugin auth failed (401). Disable/remove old Minekube/relay plugin from the server plugins folder."
            if (logsQueue.lastOrNull() != hint) {
                if (logsQueue.size >= 2000) {
                    logsQueue.removeFirst()
                    if (consoleVisibleAfterStart && logs.isNotEmpty()) {
                        logs.removeAt(0)
                    }
                }
                logsQueue.addLast(hint)
                if (consoleVisibleAfterStart) {
                    logs.add(hint)
                }
            }
        }

        parsedPings.takeIf { it.isNotEmpty() }?.let { pings ->
            lastPingLogReceivedTimeMs = SystemClock.elapsedRealtime()
            onlinePlayers.replaceAll { player ->
                val newPing = pings[player.name] ?: pings[player.name.lowercase()]
                if (newPing != null) {
                    val nextIp = newPing.ip.ifBlank { player.ip }
                    val updated = player.copy(
                        pingMs = sanitizeWifiPingSample(player, newPing, nextIp),
                        ip = nextIp,
                        uuid = newPing.uuid.ifBlank { player.uuid },
                        x = newPing.x ?: player.x,
                        y = newPing.y ?: player.y,
                        z = newPing.z ?: player.z,
                        worldName = newPing.world.ifBlank { player.worldName }
                    )
                    val sessionIdx = sessionPlayers.indexOfFirst { canonicalPlayerName(it.name) == canonicalPlayerName(player.name) }
                    if (sessionIdx >= 0) {
                        sessionPlayers[sessionIdx] = updated
                    }
                    updated
                } else {
                    player
                }
            }
        }

        ConsoleParser.parseTps(cleanLine)?.let { parsedTps ->
            tps = parsedTps
        }

        ConsoleParser.parseJoin(cleanLine)?.let { (name, uuid) ->
            if (isRunning && !isStopping) {
                sendCommand("chunky pause", showOfflineWarning = false)
                upsertOnlinePlayer(name = name, uuid = uuid)
                
                val now: Long = SystemClock.elapsedRealtime()
                val lastMsgTime: Long = recentlyWelcomedPlayers[name.lowercase()] ?: 0L
                if (now - lastMsgTime > 10_000L) {
                    recentlyWelcomedPlayers[name.lowercase()] = now
                    // Send branded welcome message
                    scope.launch {
                        delay(1500) // Ensure player is fully connected before sending message
                        if (isRunning && !isStopping) {
                            val shortName = serverName.take(12)
                            val prefs = com.pocketcraft.server.data.preferences.AppPreferences(appContext)
                            val isPremium = prefs.isPremiumUser || prefs.debugPremiumOverride
                            val joinText = if (isPremium && config.joinMessageText.isNotBlank()) config.joinMessageText else POCKETCRAFT_JOIN_MESSAGE_TEXT
                            val rawUrl = if (isPremium && config.joinMessageUrl.isNotBlank()) config.joinMessageUrl else POCKETCRAFT_JOIN_MESSAGE_URL
                            val displayUrl = rawUrl
                                .removePrefix("https://")
                                .removePrefix("http://")
                            val firstLine = """{"text":"\n[","color":"gray"},{"text":"$shortName","color":"green","bold":true},{"text":"] ","color":"gray"},{"text":"$joinText","color":"white"}"""
                            val urlSection = """,{"text":"\n[","color":"gray"},{"text":"$shortName","color":"green","bold":true},{"text":"] ","color":"gray"},{"text":"Join using ","color":"white"},{"text":"$displayUrl","color":"aqua","underlined":true,"clickEvent":{"action":"open_url","value":"$rawUrl"}},"""
                            val spacerLine = """{"text":"\n \n","color":"white"}"""
                            val tellrawArg = """[$firstLine$urlSection$spacerLine]"""
                            val escapedName = escapeSelectorName(name)
                            sendCommand("tellraw @a[name=\"$escapedName\"] $tellrawArg", showOfflineWarning = false)
                        }
                    }
                }
            }
        }
        ConsoleParser.parseLeave(cleanLine)?.let { name ->
            onlinePlayers.removeAll { it.name.equals(name, ignoreCase = true) }
            if (onlinePlayers.isEmpty() && isRunning && !isStopping) {
                sendCommand("chunky continue", showOfflineWarning = false)
            }
        }

        // Detect player death to store last dead location instantly
        if (isAnyPlayerDeathLog(cleanLine)) {
            val victim = extractDeathVictim(cleanLine)
            if (victim != null) {
                scope.launch(Dispatchers.IO) {
                    delay(250) // Minimal delay for server to update NBT
                    sendRconCommand("""data get entity @a[name="${escapeSelectorName(victim)}",limit=1] LastDeathLocation""")
                }
            }
        }

        if (cleanLine.contains("Preparing spawn area: 100%", ignoreCase = true) || 
            cleanLine.contains("Preparing start region for level", ignoreCase = true) && cleanLine.contains("100%", ignoreCase = true)) {
            areSpawnChunksLoaded = true
        }

        if ((isStarting || isRestartingCycle) && ConsoleParser.isDone(cleanLine)) {
            areSpawnChunksLoaded = true
            isJavaServerDone = true
            markServerReady()
            attemptTransitionToOnline()
        }

        if (cleanLine.contains("[Geyser-Spigot] Done (", ignoreCase = true) || 
            cleanLine.contains("Started Geyser on UDP port", ignoreCase = true)) {
            isGeyserDone = true
            if (isStarting && isJavaServerDone && areSpawnChunksLoaded) {
                attemptTransitionToOnline()
            }
        }

        if (cleanLine.contains("[Chunky] Task finished", ignoreCase = true)) {
            scope.launch {
                val firstBoot = !AppPreferencesStore.isFirstBootCompleteFlow(appContext).first()
                if (firstBoot) {
                    AppPreferencesStore.setFirstBootComplete(appContext, true)
                    appendLog("[PocketCraft] First boot pre-generation finished! You can now increase view distance.")
                }
                chunkyProgressPercent = null
            }
        }

    }

    private fun markServerReady() {
        stopStartupProgressTracking(reset = false)
        isStarting = false
        isRestartingCycle = false
        isRunning = true
        lastStartRequestedRealtime = 0L
        ServerHostService.persistRuntimeState(appContext, versionId, activeWorld, ServerHostService.RUNTIME_STATE_RUNNING)
        ServerHostService.pushWidgetUpdate(appContext)
        val prefs = com.pocketcraft.server.data.preferences.AppPreferences(appContext)
        prefs.successfulServerStarts = prefs.successfulServerStarts + 1
        android.util.Log.i("ServerStateHolder", "Server start count incremented: ${prefs.successfulServerStarts}")
        consoleVisibleAfterStart = true
        startPeriodicWorldSave()
        startPeriodicLocationPolling()
        startPeriodicPingPolling()
        startupProgressPercent = 100
        startupStatusMessage = "Server ready!"
        if (tps <= 0f) tps = 20f
        if (startedAtRealtime == null) startedAtRealtime = SystemClock.elapsedRealtime()
        if (!hasAnnouncedServerOnline) {
            hasAnnouncedServerOnline = true
        }
        if (areSpawnChunksLoaded) {
            markJoinable()
        }
        if (!firstBootComplete) {
            scope.launch {
                AppPreferencesStore.setFirstBootComplete(appContext, true)
            }
        }
    }

    fun markJoinable() {
        serverJoinable = true
    }

    fun resetJoinable() {
        serverJoinable = false
    }

    private fun isLegacyRelayAuthError(line: String): Boolean {
        val normalized = line.lowercase()
        if (normalized.contains("expected http 101 response but was '401 unauthorized'")) {
            return true
        }
        if (normalized.contains("trying to reconnect") && normalized.contains("minekube")) {
            return true
        }
        return false
    }

    fun clearLogs() {
        // Console logs are preserved permanently per user requirement.
    }

    fun currentLogLines(): List<String> = logsQueue.toList()

    private fun loadLogsFromDisk() {
        scope.launch(Dispatchers.IO) {
            val latestLogFile = File(serverDir, "logs/latest.log")
            if (latestLogFile.exists()) {
                val lines = runCatching {
                    latestLogFile.useLines { seq: Sequence<String> ->
                        seq.toList().takeLast(1000)
                    }
                }.getOrNull()
                if (!lines.isNullOrEmpty()) {
                    withContext(Dispatchers.Main) {
                        logsQueue.clear()
                        logsQueue.addAll(lines)
                        logs.clear()
                        logs.addAll(lines)
                        consoleVisibleAfterStart = true
                    }
                }
            }
        }
    }

    fun sendCommand(cmd: String, showOfflineWarning: Boolean = true) {
        val clean = cmd.trim()
        if (clean.isBlank()) return
        if (!isRunning) {
            if (showOfflineWarning) {
                appendLog("[RCON] Server is offline. Start the server before sending commands.")
            }
            return
        }
        appendLog("> $clean")
        com.pocketcraft.server.server.ServerLauncher.sendCommand(clean)
        scope.launch(Dispatchers.IO) {
            runCatching {
                val response = RconClient.sendCommand(clean)
                if (response.isNotBlank() && response != "[OK]") {
                    withContext(Dispatchers.Main) { appendLog(response) }
                }
            }
        }
    }

    // Source RCON client (RFC-compliant packet framing over TCP socket 25575)
    fun sendRconCommand(command: String): String {
        val rconResponse = RconClient.sendCommand(command)
        if (rconResponse.isNotBlank()) {
            return rconResponse
        }
        com.pocketcraft.server.server.ServerLauncher.sendCommand(command)
        return "[OK]"
    }

    fun sendRconCommands(commands: List<String>): List<String> {
        if (commands.isEmpty()) return emptyList()
        return RconClient.sendCommands(commands)
    }

    // Little-endian helpers for RCON protocol
    private fun java.io.DataOutputStream.writeIntLE(value: Int) {
        write(value and 0xFF)
        write((value shr 8) and 0xFF)
        write((value shr 16) and 0xFF)
        write((value shr 24) and 0xFF)
    }
    private fun java.io.DataInputStream.readIntLE(): Int {
        val b0 = read(); val b1 = read(); val b2 = read(); val b3 = read()
        return (b0 and 0xFF) or ((b1 and 0xFF) shl 8) or ((b2 and 0xFF) shl 16) or ((b3 and 0xFF) shl 24)
    }

    private fun upsertOnlinePlayer(name: String, uuid: String) {
        val normalizedName = name.trim()
        if (normalizedName.isBlank()) return
        val canonicalName = canonicalPlayerName(normalizedName)

        val normalizedUuid = uuid.trim().ifBlank {
            sequenceOf(
                onlinePlayers.firstOrNull { canonicalPlayerName(it.name) == canonicalName }?.uuid,
                knownPlayers.firstOrNull { canonicalPlayerName(it.name) == canonicalName }?.uuid,
                whitelistPlayers.firstOrNull { canonicalPlayerName(it.name) == canonicalName }?.uuid,
                opPlayers.firstOrNull { canonicalPlayerName(it.name) == canonicalName }?.uuid
            ).filterNotNull().firstOrNull().orEmpty()
        }

        val isPlayerOp = opPlayers.any { canonicalPlayerName(it.name) == canonicalName }
        val existingIndex = onlinePlayers.indexOfFirst {
            (normalizedUuid.isNotBlank() && it.uuid == normalizedUuid) ||
                canonicalPlayerName(it.name) == canonicalName
        }
        val existingPlayer = onlinePlayers.getOrNull(existingIndex)
        val initialPing = existingPlayer?.pingMs ?: 0
        val mergedPlayer = PlayerInfo(
            name = normalizedName,
            uuid = normalizedUuid.ifBlank { existingPlayer?.uuid.orEmpty() },
            pingMs = initialPing,
            ip = existingPlayer?.ip.orEmpty(),
            isOp = isPlayerOp || existingPlayer?.isOp == true
        )

        if (existingIndex >= 0) {
            onlinePlayers[existingIndex] = mergedPlayer
        } else {
            onlinePlayers.add(mergedPlayer)
            // Removed markServerReady() call to prevent premature transitions from offline to online status
            // Wait for proper startup progression handled by attemptTransitionToOnline
        }

        val sessionIdx = sessionPlayers.indexOfFirst { canonicalPlayerName(it.name) == canonicalName }
        if (sessionIdx >= 0) {
            sessionPlayers[sessionIdx] = mergedPlayer
        } else {
            sessionPlayers.add(mergedPlayer)
        }


    }

    private fun applyPersistedRuntimeState(state: PersistedRuntimeState) {
        if (isStopping) {
            if (isRestartingCycle || pendingRestart) {
                // Keep RESTARTING status active; do not override with OFFLINE during restart transition
                return
            }
            // Fallback: if service/runtime are already offline, unblock UI even if a stop event was missed.
            if (!state.isRunning && !state.isStarting) {
                isStopping = false
                pendingRestart = false
                isStarting = false
                isRunning = false
                stopPeriodicWorldSave()
                startedAtRealtime = null
                publicAddress = null
                tunnelConnecting = false
                tunnelError = null
                stopStartupProgressTracking(reset = true)
                tps = 0f
                stopWatchdogJob?.cancel()
                stopWatchdogJob = null
                resetJoinable()
            }
            return
        }

        if (isJavaServerDone && areSpawnChunksLoaded) {
            isStarting = false
            isRestartingCycle = false
            isRunning = true
            lastStartRequestedRealtime = 0L
            isRelayDone = true
            isJavaServerDone = true
            isGeyserDone = true
            startupProgressPercent = 100
            startupStatusMessage = "Server ready!"
            stopStartupProgressTracking(reset = false)
            startPeriodicWorldSave()
            startPeriodicLocationPolling()
            startPeriodicPingPolling()
            markJoinable()
        } else if (isStarting) {
            // During active server boot, NEVER adopt stale isRunning = true from disk state
            isRunning = false
            resetJoinable()
        } else if (!state.isStarting && isStarting && !isRunning && (lastStartRequestedRealtime > 0L && SystemClock.elapsedRealtime() - lastStartRequestedRealtime < 180000L)) {
            // Keep isStarting = true during startup grace period; prevent brief flickering to OFFLINE
            isStarting = true
            isRunning = false
        } else {
            isStarting = state.isStarting
            isRunning = state.isRunning
        }

        if (state.isRunning || state.isStarting) {
            consoleVisibleAfterStart = true
            if (logs.isEmpty()) {
                loadLogsFromDisk()
            }
        }
        if (!isRunning && !isStarting) {
            stopPeriodicWorldSave()
            stopPeriodicLocationPolling()
            resetJoinable()
        }
        val extPid = ServerHostService.getExternalJvmPid(appContext)
        if (isStarting && extPid > 0 && !jvmStartedTracking) {
            val processAlive = try {
                android.system.Os.kill(extPid.toInt(), 0)
                true
            } catch (e: android.system.ErrnoException) {
                e.errno != android.system.OsConstants.ESRCH
            } catch (e: Exception) {
                false
            }
            if (processAlive) {
                jvmStartedTracking = true
                startupStartedAtRealtime = SystemClock.elapsedRealtime()
                appendLog("[PocketCraft] JVM process launched (PID $extPid). Server boot timeout timer initialized.")
            }
        }

        if (isStarting && !isRunning && startupStartedAtRealtime == null) {
            startupStartedAtRealtime = SystemClock.elapsedRealtime()
            jvmStartedTracking = false
            startStartupProgressTracking()
        } else if (!isStarting || isRunning) {
            stopStartupProgressTracking(reset = false)
        }

        if (state.isRunning && tps <= 0f) {
            tps = 20f
        }

        if (state.isRunning && publicAddress.isNullOrBlank() && !state.publicAddress.isNullOrBlank() && !tunnelConnecting) {
            publicAddress = state.publicAddress
            tunnelError = null
        }

        if (state.isRunning && state.publicAddress.isNullOrBlank() && tunnelConnecting) {
            publicAddress = null
        }

        if (!state.isRunning && !state.isStarting) {
            startedAtRealtime = null
            publicAddress = null
            tps = 0f
            resetJoinable()
        } else if (startedAtRealtime == null) {
            startedAtRealtime = SystemClock.elapsedRealtime()
        }
    }

    private fun isServerPortOpen(port: Int): Boolean {
        return runCatching {
            java.net.Socket().use { socket ->
                socket.connect(java.net.InetSocketAddress("127.0.0.1", port), 1000)
                true
            }
        }.getOrDefault(false)
    }

    private fun isServiceActive(): Boolean {
        return isServerProcessAlive()
    }

    private fun readPersistedRuntimeState(): PersistedRuntimeState {
        val rawState = ServerHostService.getPersistedRuntimeState(appContext, versionId)
        val address = ServerHostService.getPersistedPublicAddress(appContext, versionId)
        val portOpen = isServerPortOpen(config.port)
        val processAlive = isServerProcessAlive()

        // Server is ONLY truly RUNNING if Java server boot is done AND spawn chunks are loaded AND process is alive.
        if (isJavaServerDone && areSpawnChunksLoaded && processAlive) {
            return PersistedRuntimeState(isRunning = true, publicAddress = address)
        }

        val startingGracePeriod = !isJavaServerDone && (isStarting || (lastStartRequestedRealtime > 0 && SystemClock.elapsedRealtime() - lastStartRequestedRealtime < 120000L))

        if (startingGracePeriod && processAlive) {
            return PersistedRuntimeState(isStarting = true, publicAddress = address)
        }

        if (rawState == ServerHostService.RUNTIME_STATE_RUNNING || isRunning) {
            if ((portOpen || processAlive) && isJavaServerDone && areSpawnChunksLoaded) {
                return PersistedRuntimeState(isRunning = true, publicAddress = address)
            }
            if (startingGracePeriod && processAlive) {
                return PersistedRuntimeState(isStarting = true, publicAddress = address)
            }
            ServerHostService.persistRuntimeState(appContext, versionId, activeWorld, ServerHostService.RUNTIME_STATE_OFFLINE)
            return PersistedRuntimeState()
        }

        if (rawState == ServerHostService.RUNTIME_STATE_STARTING || isStarting || rawState.startsWith("STARTING")) {
            if ((portOpen || processAlive) && isJavaServerDone && areSpawnChunksLoaded) {
                return PersistedRuntimeState(isRunning = true, publicAddress = address)
            }
            if (startingGracePeriod && processAlive) {
                return PersistedRuntimeState(isStarting = true, publicAddress = address)
            }
            // Dead state
            ServerHostService.persistRuntimeState(appContext, versionId, activeWorld, ServerHostService.RUNTIME_STATE_OFFLINE)
            return PersistedRuntimeState()
        }

        return PersistedRuntimeState()
    }


    fun kickPlayer(name: String) {
        val normalizedName = name.trim()
        if (normalizedName.isBlank()) return

        runCatching {
            sendCommand("""kick @a[name="${escapeSelectorName(normalizedName)}"] Removed by PocketCraft""")
            // Remove immediately for UI feedback; parser/refresh will reconcile authoritative state.
            onlinePlayers.removeAll { it.name.equals(normalizedName, ignoreCase = true) }
        }.onFailure { error ->
            appendLog("[PocketCraft] Failed to kick $normalizedName: ${error.message}")
        }

        scope.launch {
            // Give server time to emit leave/disconnect lines, then sync lists.
            delay(1200)
            if (isRunning && !isStopping) {
                sendCommand("list", showOfflineWarning = false)
            }
            delay(900)
            refreshAll()
        }
    }

    fun banPlayer(name: String) {
        val normalizedName = name.trim()
        if (normalizedName.isBlank()) return

        sendCommand("ban $normalizedName")
        
        val resolvedUuid = onlinePlayers.firstOrNull { it.name.equals(normalizedName, ignoreCase = true) }?.uuid
            ?: knownPlayers.firstOrNull { it.name.equals(normalizedName, ignoreCase = true) }?.uuid.orEmpty()

        scope.launch(Dispatchers.IO) {
            val error = runCatching {
                mutateNamedList("banned-players.json") { list ->
                    if (list.none { it.name.equals(normalizedName, ignoreCase = true) }) {
                        list + NamedPlayerRecord(
                            name = normalizedName,
                            uuid = resolvedUuid,
                            extra = JSONObject().apply {
                                put("created", isoNow())
                                put("source", "PocketCraft")
                                put("expires", "forever")
                                put("reason", "Banned from PocketCraft")
                            }
                        )
                    } else {
                        list
                    }
                }
            }.exceptionOrNull()

            withContext(Dispatchers.Main) {
                if (error != null) {
                    appendLog("[PocketCraft] Failed to ban $normalizedName: ${error.message}")
                } else {
                    onlinePlayers.removeAll { it.name.equals(normalizedName, ignoreCase = true) }
                }
                refreshAll()
            }
        }
    }

    fun opPlayer(name: String) {
        val normalizedName = name.trim()
        if (normalizedName.isBlank()) return

        sendCommand("op $normalizedName")

        val resolvedUuid = onlinePlayers.firstOrNull { it.name.equals(normalizedName, ignoreCase = true) }?.uuid
            ?: knownPlayers.firstOrNull { it.name.equals(normalizedName, ignoreCase = true) }?.uuid.orEmpty()

        scope.launch(Dispatchers.IO) {
            val error = runCatching {
                mutateNamedList("ops.json") { list ->
                    if (list.none { it.name.equals(normalizedName, ignoreCase = true) }) {
                        list + NamedPlayerRecord(
                            name = normalizedName,
                            uuid = resolvedUuid,
                            extra = JSONObject().apply {
                                put("level", 4)
                                put("bypassesPlayerLimit", false)
                            }
                        )
                    } else {
                        list
                    }
                }
            }.exceptionOrNull()

            withContext(Dispatchers.Main) {
                if (error != null) {
                    appendLog("[PocketCraft] Failed to grant OP to $normalizedName: ${error.message}")
                } else {
                    val normalizedUuid = onlinePlayers.firstOrNull { it.name.equals(normalizedName, ignoreCase = true) }?.uuid
                        ?: knownPlayers.firstOrNull { it.name.equals(normalizedName, ignoreCase = true) }?.uuid
                        .orEmpty()
                    onlinePlayers.replaceAllMatching(
                        normalizedName = normalizedName,
                        uuid = normalizedUuid
                    ) { player -> player.copy(isOp = true) }
                    knownPlayers.replaceAllMatching(
                        normalizedName = normalizedName,
                        uuid = normalizedUuid
                    ) { player -> player.copy(isOp = true) }
                    sessionPlayers.replaceAllMatching(
                        normalizedName = normalizedName,
                        uuid = normalizedUuid
                    ) { player -> player.copy(isOp = true) }
                }
                refreshAll()
            }
        }
    }

    fun removeOp(name: String) {
        val normalizedName = name.trim()
        if (normalizedName.isBlank()) return

        sendCommand("deop $normalizedName")

        scope.launch(Dispatchers.IO) {
            val error = mutateNamedList("ops.json") { list ->
                list.filterNot { it.name.equals(normalizedName, ignoreCase = true) }
            }.exceptionOrNull()

            withContext(Dispatchers.Main) {
                if (error != null) {
                    appendLog("[PocketCraft] Failed to remove OP from $normalizedName: ${error.message}")
                } else {
                    val normalizedUuid = onlinePlayers.firstOrNull { it.name.equals(normalizedName, ignoreCase = true) }?.uuid
                        ?: knownPlayers.firstOrNull { it.name.equals(normalizedName, ignoreCase = true) }?.uuid
                        .orEmpty()
                    onlinePlayers.replaceAllMatching(
                        normalizedName = normalizedName,
                        uuid = normalizedUuid
                    ) { player -> player.copy(isOp = false) }
                    knownPlayers.replaceAllMatching(
                        normalizedName = normalizedName,
                        uuid = normalizedUuid
                    ) { player -> player.copy(isOp = false) }
                    sessionPlayers.replaceAllMatching(
                        normalizedName = normalizedName,
                        uuid = normalizedUuid
                    ) { player -> player.copy(isOp = false) }
                }
                refreshAll()
            }
        }
    }

    fun unbanPlayer(name: String) {
        val normalizedName = name.trim()
        if (normalizedName.isBlank()) return

        scope.launch(Dispatchers.IO) {
            val error = mutateNamedList("banned-players.json") { list ->
                list.filterNot { it.name.equals(normalizedName, ignoreCase = true) }
            }.exceptionOrNull()

            withContext(Dispatchers.Main) {
                if (error != null) {
                    appendLog("[PocketCraft] Failed to unban $normalizedName: ${error.message}")
                }
                refreshAll()
            }
        }
    }

    fun removeWhitelistPlayer(name: String) {
        val normalizedName = name.trim()
        if (normalizedName.isBlank()) return

        scope.launch(Dispatchers.IO) {
            val error = mutateNamedList("whitelist.json") { list ->
                list.filterNot { it.name.equals(normalizedName, ignoreCase = true) }
            }.exceptionOrNull()

            val isBedrock = normalizedName.startsWith(".") || normalizedName.startsWith("*") || normalizedName.startsWith("!")
            val cleanName = if (isBedrock) normalizedName.trimStart('.', '*', '!') else normalizedName

            if (isRunning) {
                if (isBedrock) {
                    sendCommand("fwhitelist remove $cleanName", showOfflineWarning = false)
                } else {
                    sendCommand("whitelist remove $normalizedName", showOfflineWarning = false)
                }
            }

            withContext(Dispatchers.Main) {
                if (error != null) {
                    appendLog("[PocketCraft] Failed to remove $normalizedName from whitelist: ${error.message}")
                }
                refreshAll()
            }
        }
    }

    fun addWhitelistPlayer(name: String) {
        val normalizedName = name.trim()
        if (normalizedName.isBlank()) return

        // First check if player already exists
        val existingPlayers = whitelistPlayers.map { it.name.lowercase() }
        if (existingPlayers.contains(normalizedName.lowercase())) {
            return // Player already in whitelist
        }

        scope.launch(Dispatchers.IO) {
            // Resolve UUID
            val resolvedUuid = knownPlayers.firstOrNull { it.name.equals(normalizedName, ignoreCase = true) }?.uuid
                ?: sessionPlayers.firstOrNull { it.name.equals(normalizedName, ignoreCase = true) }?.uuid
                ?: run {
                    val userCacheFile = File(serverDir, "usercache.json")
                    var foundUuid = ""
                    if (userCacheFile.exists()) {
                        runCatching {
                            val arr = JSONArray(userCacheFile.readText())
                            for (i in 0 until arr.length()) {
                                val obj = arr.optJSONObject(i) ?: continue
                                val cachedName = obj.optString("name")
                                if (cachedName.equals(normalizedName, ignoreCase = true)) {
                                    foundUuid = obj.optString("uuid")
                                    break
                                }
                            }
                        }
                    }
                    foundUuid
                }
                .ifBlank {
                    // Always fallback to offline UUID for Java players since server runs in online-mode=false
                    com.pocketcraft.server.service.PlayerDataManager.getOfflineUuid(normalizedName)
                }

            val error = mutateNamedList("whitelist.json") { list ->
                list + NamedPlayerRecord(
                    name = normalizedName,
                    uuid = resolvedUuid,
                    extra = JSONObject()
                )
            }.exceptionOrNull()

            val isBedrock = normalizedName.startsWith(".") || normalizedName.startsWith("*") || normalizedName.startsWith("!")
            val cleanName = if (isBedrock) normalizedName.trimStart('.', '*', '!') else normalizedName

            if (isRunning) {
                if (isBedrock) {
                    sendCommand("fwhitelist add $cleanName", showOfflineWarning = false)
                } else {
                    sendCommand("whitelist add $normalizedName", showOfflineWarning = false)
                }
            }

            withContext(Dispatchers.Main) {
                if (error != null) {
                    appendLog("[PocketCraft] Failed to add $normalizedName to whitelist: ${error.message}")
                }
                refreshAll()
            }
        }
    }

    fun copySeedToClipboard() {
        val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("PocketCraft seed", config.worldSeed.ifBlank { "Random" }))
    }

    fun readServerProperty(key: String): String? {
        val file = File(serverDir, "server.properties")
        if (!file.exists()) return null
        return file.readLines()
            .firstOrNull { it.startsWith("$key=") }
            ?.removePrefix("$key=")
            ?.trim()
    }

    fun writeServerProperty(key: String, value: String) {
        val file = File(serverDir, "server.properties")
        if (!file.exists()) return
        val lines = file.readLines().toMutableList()
        val idx = lines.indexOfFirst { it.startsWith("$key=") }
        if (idx >= 0) {
            lines[idx] = "$key=$value"
        } else {
            lines.add("$key=$value")
        }
        file.writeText(lines.joinToString("\n"))
    }

    fun writeBulkServerProperties(entries: Map<String, String>) {
        if (entries.isEmpty()) return
        val file = File(serverDir, "server.properties")
        if (!file.exists()) return
        val lines = file.readLines().toMutableList()
        for ((key, value) in entries) {
            val idx = lines.indexOfFirst { it.startsWith("$key=") }
            if (idx >= 0) {
                lines[idx] = "$key=$value"
            } else {
                lines.add("$key=$value")
            }
        }
        file.writeText(lines.joinToString("\n"))
    }

    fun copyPublicAddressToClipboard(): Boolean {
        val address = publicAddress?.trim().orEmpty()
        if (address.isBlank()) return false
        val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("PocketCraft address", address))
        return true
    }

    fun clearTunnelError() {
        tunnelError = null
    }

    fun enableWhitelist() {
        scope.launch(Dispatchers.IO) {
            val next = config.copy(whiteList = true, enforceWhitelist = true)
            saveConfig(next)
            withContext(Dispatchers.Main) {
                config = next
            }

            val rconResult = applyRuntimeSettingsState(next, onlyWhitelist = true)

            withContext(Dispatchers.Main) {
                if (rconResult == null) {
                    appendLog("[PocketCraft] Whitelist enabled.")
                } else {
                    appendLog("[PocketCraft] Whitelist saved. It will fully apply once the server is ready. RCON error: $rconResult")
                }
            }
        }
    }

    suspend fun saveSettings(next: ServerConfig, targetWorldName: String? = null): String = withContext(Dispatchers.IO) {
        val enforced = next.copy(
            port = singleServerPort,
            maxPlayers = next.maxPlayers.coerceIn(1, 50),
            viewDistance = next.viewDistance.coerceIn(3, 32),
            simulationDistance = next.simulationDistance.coerceIn(3, 32),
            whiteList = next.whiteList || next.enforceWhitelist
        )
        val targetDir = if (targetWorldName != null) {
            ServerFileManager.getServerDir(appContext, targetWorldName)
        } else {
            serverDir
        }
        saveConfig(enforced, targetDir = targetDir)
        runCatching {
            ServerConfigRepository(appContext).apply {
                setWorldNameOverride(targetWorldName ?: activeWorld)
            }.saveConfig(enforced)
        }.onFailure { error ->
            android.util.Log.w("ServerStateHolder", "Failed to sync saved settings: ${error.message}")
        }

        if (isRunning) {
            scope.launch(Dispatchers.IO) {
                runCatching {
                    applyRuntimeSettingsState(enforced, onlyWhitelist = false)
                }
            }
        }

        withContext(Dispatchers.Main) {
            config = enforced
        }
        "Settings saved."
    }

    private fun applyRuntimeSettingsState(config: ServerConfig, onlyWhitelist: Boolean = false): String? {
        val commands = buildList {
            add(if (config.whiteList) "whitelist on" else "whitelist off")
            add("whitelist reload")
            if (!onlyWhitelist) {
                add("difficulty ${config.difficulty.lowercase()}")
                add("defaultgamemode ${config.gameMode.lowercase()}")
                add("gamemode ${config.gameMode.lowercase()} @a")
                add("gamerule pvp ${config.pvp}")
                add("gamerule doMobSpawning ${config.spawnMonsters}")
                add("save-all")
            }
        }
        return runCatching {
            commands.forEach { sendRconCommand(it) }
            if (config.whiteList && config.enforceWhitelist) {
                kickPlayersNotOnWhitelist(::sendRconCommand)
            }
            null
        }.getOrElse { error ->
            commands.forEach { com.pocketcraft.server.server.ServerLauncher.sendCommand(it) }
            if (config.whiteList && config.enforceWhitelist) {
                kickPlayersNotOnWhitelist(com.pocketcraft.server.server.ServerLauncher::sendCommand)
            }
            error.message ?: "RCON unavailable"
        }
    }

    private fun kickPlayersNotOnWhitelist(sendCommandAction: (String) -> Unit) {
        val allowedPlayers = readNamedList("whitelist.json")
            .map { canonicalPlayerName(it.name) }
            .toSet()
        val activePlayers = (onlinePlayers.toList() + sessionPlayers.toList())
            .distinctBy { canonicalPlayerName(it.name) }
        val playersToKick = activePlayers.filter { player ->
            canonicalPlayerName(player.name) !in allowedPlayers
        }

        playersToKick.forEach { player ->
            val escapedName = escapeSelectorName(player.name)
            sendCommandAction("""kick @a[name="$escapedName",limit=1] Whitelist is enabled on this server""")
        }
    }

    suspend fun updateRelayHost(host: String): String = withContext(Dispatchers.IO) {
        val normalizedHost = host.trim()
        if (normalizedHost.isBlank()) {
            return@withContext "Invalid relay location."
        }
        val displayName = com.pocketcraft.server.config.RelayServers.getDisplayName(normalizedHost)
        if (normalizedHost == relayHost) {
            return@withContext "Relay is already set to $displayName."
        }

        val switchingLive = isNavigationLocked
        val previousHost = relayHost

        if (switchingLive) {
            withContext(Dispatchers.Main) {
                publicAddress = null
                relaySwitchInProgress = true
                tunnelConnecting = true
                tunnelError = null
            }
            runCatching {
                kotlinx.coroutines.withTimeout(5_000L) {
                    RelayManager(appContext).unregisterFromHost(previousHost)
                }
            }.onFailure { error ->
                android.util.Log.w("ServerStateHolder", "Failed to unregister old relay $previousHost: ${error.message}")
            }
        }

        com.pocketcraft.server.data.preferences.AppPreferences(appContext).setManualRelayHost(normalizedHost)
        FirebaseAnalyticsManager.logSettingsChanged("relay_host", normalizedHost)
        withContext(Dispatchers.Main) {
            relayHost = normalizedHost
            if (switchingLive) {
                ServerHostService.reconnectRelay(
                    appContext,
                    skipUnregister = true,
                    relayHost = normalizedHost
                )
            }
        }
        if (switchingLive) {
            "Switching relay to $displayName. Your internet address will update shortly — players may need to rejoin."
        } else {
            "Relay location set to $displayName."
        }
    }

    suspend fun updateSeed(seed: String): String = withContext(Dispatchers.IO) {
        val next = config.copy(worldSeed = seed.trim())
        saveConfig(next)
        withContext(Dispatchers.Main) {
            config = next
        }
        "World seed updated."
    }

    suspend fun updateWorldServerDetails(worldName: String, displayName: String, photoUrl: String, description: String = DEFAULT_SERVER_DESCRIPTION): String = withContext(Dispatchers.IO) {
        val normalized = sanitizeWorldName(worldName)
        if (normalized.isBlank()) return@withContext "Invalid world name."

        val trimmedName = displayName.trim().ifBlank { normalized }
        val trimmedPhoto = photoUrl.trim()
        val trimmedDescription = description.trim().ifBlank { DEFAULT_SERVER_DESCRIPTION }
        val targetServerDir = ServerFileManager.getServerDir(appContext, normalized)
        val props = ServerPropertiesHelper.readProperties(targetServerDir)
        props[worldDisplayNameKey(normalized)] = trimmedName
        props[worldPhotoKey(normalized)] = trimmedPhoto
        props[worldDescriptionKey(normalized)] = trimmedDescription
        val activeWorldName = activeWorld.ifBlank { "world" }
        val activeMotd = if (activeWorldName.equals(normalized, ignoreCase = true)) buildServerMotd(trimmedName, trimmedDescription) else props.getProperty("motd", config.motd)
        if (activeWorldName.equals(normalized, ignoreCase = true)) {
            props["motd"] = activeMotd
        }
        ServerPropertiesHelper.saveProperties(targetServerDir, props)
        if (activeWorldName.equals(normalized, ignoreCase = true)) {
            writeServerIcon(trimmedPhoto)
        }

        withContext(Dispatchers.Main) {
            if (activeWorld.equals(normalized, ignoreCase = true)) {
                serverName = trimmedName
                serverPhotoUrl = trimmedPhoto
                serverDescription = trimmedDescription
                config = config.copy(motd = activeMotd)
            }
        }
        "Server details updated for $normalized."
    }

    suspend fun importWorldServerPhoto(
        worldName: String,
        sourceUri: Uri,
        onProgress: (Int) -> Unit = {}
    ): String = withContext(Dispatchers.IO) {
        val normalized = sanitizeWorldName(worldName)
        if (normalized.isBlank()) return@withContext ""

        val extension = guessPhotoExtension(sourceUri)
        serverPhotosDir.mkdirs()
        serverPhotosDir.listFiles()
            ?.filter { it.name.startsWith("${normalized}_") }
            ?.forEach { it.delete() }

        val destination = File(serverPhotosDir, "${normalized}_${UUID.randomUUID().toString().take(8)}.$extension")
        val totalBytes = appContext.contentResolver.openAssetFileDescriptor(sourceUri, "r")?.length ?: -1L
        val inputStream = appContext.contentResolver.openInputStream(sourceUri)
            ?: throw IllegalStateException("Could not read the selected image.")
        inputStream.use { input ->
            destination.outputStream().use { output ->
                val buffer = ByteArray(16 * 1024)
                var copied = 0L
                var bytesRead = input.read(buffer)
                while (bytesRead != -1) {
                    output.write(buffer, 0, bytesRead)
                    copied += bytesRead
                    if (totalBytes > 0L) {
                        withContext(Dispatchers.Main) {
                            onProgress(((copied * 100L) / totalBytes).toInt().coerceIn(0, 100))
                        }
                    }
                    bytesRead = input.read(buffer)
                }
            }
        }

        if (!destination.exists() || destination.length() <= 0L) {
            destination.delete()
            throw IllegalStateException("The selected image could not be saved.")
        }

        withContext(Dispatchers.Main) {
            onProgress(100)
        }

        Uri.fromFile(destination).toString()
    }

    suspend fun setActiveWorld(worldName: String, syncPluginProfiles: Boolean = true): String = withContext(Dispatchers.IO) {
        if (isRunning || isStarting || isStopping || isBackingUp || isRestoringBackup || isDownloadingBackup) {
            return@withContext "Stop active server or backup operations before switching worlds."
        }

        val normalized = sanitizeWorldName(worldName)
        if (normalized.isBlank()) {
            return@withContext "Enter a valid world name."
        }

        val currentWorld = activeWorld.ifBlank { "world" }
        if (normalized.equals(currentWorld, ignoreCase = true)) {
            return@withContext "$worldName is already active."
        }

        ensureWorldDirectories(normalized)
        
        val targetServerDir = ServerFileManager.getServerDir(appContext, normalized)

        // Only enforce the shared server port on the target world.
        // Do NOT copy game-play settings (difficulty, view-distance, etc.) from
        // the current world — each world keeps its own independent configuration.
        val targetProps = ServerPropertiesHelper.readProperties(targetServerDir)
        val hadExistingSettings = targetProps.containsKey("level-name")
        targetProps["level-name"] = normalized
        targetProps["server-port"] = singleServerPort.toString()
        ServerPropertiesHelper.saveProperties(targetServerDir, targetProps)

        if (syncPluginProfiles) {
            runCatching {
                syncWorldPluginProfiles(fromWorld = currentWorld, toWorld = normalized)
            }.onFailure { error ->
                return@withContext "Failed switching world plugins: ${error.message ?: "unknown error"}"
            }
        } else {
            // For freshly created worlds, keep content isolated from the previous world.
            syncProfileIntoActiveWorldContent(normalized)
        }

        // Load the target world's own stored config — never overwrite it with the
        // current world's settings. The target world already persists its own
        // server.properties and we just ensured port and level-name are correct.
        // Only re-read and re-save if there were no existing settings (fresh world).
        if (!hadExistingSettings) {
            // Brand-new world with no prior settings — seed in sensible defaults
            // by inheriting the current world's config as a one-time template,
            // but do NOT carry over world-specific values or runtime selection.
            val templateConfig = config.copy(
                worldName = normalized,
                port = singleServerPort,
                worldSeed = "",
                levelType = "default",
                serverType = ServerType.PAPER,
                gameVersion = "",
                customJarPath = null
            )
            saveConfig(templateConfig, targetDir = targetServerDir)
        }

        // Ensure data is migrated for the new world
        PlayerDataManager.fixOfflineUuids(targetServerDir, normalized)

        // Save to preferences so the app recomposes with the new world
        AppPreferencesStore.setSelectedWorld(appContext, normalized)

        // Sync registries to all world slots!
        syncRegistriesAcrossAllWorlds(extraWorlds = setOf(currentWorld, normalized))

        withContext(Dispatchers.Main) {
            activeWorld = normalized
            refreshAll()
        }
        "Active world switched to $normalized."
    }

    suspend fun createWorld(worldName: String): String = withContext(Dispatchers.IO) {
        if (isRunning || isStarting || isStopping || isBackingUp || isRestoringBackup || isDownloadingBackup) {
            return@withContext "Stop active server or backup operations before creating a world."
        }

        val prefs = com.pocketcraft.server.data.preferences.AppPreferences(appContext)
        val isPremium = prefs.isPremiumUser || prefs.debugPremiumOverride
        val currentWorld = activeWorld.ifBlank { "world" }
        val existingNames = listWorldEntries(currentWorld).map { it.name }

        if (!isPremium && existingNames.isNotEmpty()) {
            return@withContext "Free tier is limited to 1 world. Please delete your current world or buy Premium to create more."
        }

        val requestedName = sanitizeWorldName(worldName)
        if (requestedName.isBlank()) {
            return@withContext "Enter a valid world name."
        }

        val normalized = generateUniqueWorldName(requestedName, existingNames)


        ensureWorldDirectories(normalized)
        
        val targetServerDir = ServerFileManager.getServerDir(appContext, normalized)

        // Seed the new world's server.properties using the current world's settings
        // as a one-time template (so the user doesn't need to re-configure RAM,
        // max-players, etc. for every new world). World-specific values and
        // runtime selection are reset.
        val templateConfig = config.copy(
            worldName = normalized,
            port = singleServerPort,
            worldSeed = "",        // new world gets a random seed
            levelType = "default", // reset level-type; user can change it in setup
            serverType = ServerType.PAPER,
            gameVersion = "",
            customJarPath = null
        )
        saveConfig(templateConfig, targetDir = targetServerDir)
        
        ServerFileManager.acceptEula(appContext, normalized)

        runCatching {
            initializeIsolatedWorldPluginProfile(normalized)
        }.onFailure { error ->
            return@withContext "Failed preparing world plugins: ${error.message ?: "unknown error"}"
        }

        // Sync registries to all world slots and mark setup pending!
        syncRegistriesAcrossAllWorlds(extraWorlds = setOf(currentWorld, normalized), completedToRemove = setOf(normalized))

        withContext(Dispatchers.Main) {
            refreshAll()
        }
        if (normalized.equals(requestedName, ignoreCase = true)) {
            "World added: $normalized."
        } else {
            "World added as $normalized because $requestedName already existed."
        }
    }
    suspend fun deleteWorld(worldName: String): String = withContext(Dispatchers.IO) {
        val target = sanitizeWorldName(worldName)
        if (target.isBlank()) {
            return@withContext "Enter a valid world name."
        }

        val knownWorlds = listWorldEntries(activeWorld).map { it.name }
        val match = knownWorlds.firstOrNull { it.equals(worldName, ignoreCase = true) }
            ?: knownWorlds.firstOrNull { it.equals(target, ignoreCase = true) }
            ?: return@withContext "$worldName was not found."

        val currentActive = activeWorld.ifBlank { "world" }
        if ((isRunning || isStarting || isStopping || isBackingUp || isRestoringBackup || isDownloadingBackup) && currentActive.equals(match, ignoreCase = true)) {
            return@withContext "Stop active server or backup operations before deleting the active world."
        }
        val remainingWorlds = knownWorlds.filterNot { it.equals(match, ignoreCase = true) }
        val nextActive = if (currentActive.equals(match, ignoreCase = true)) {
            remainingWorlds.firstOrNull()
        } else {
            currentActive
        }

        if (currentActive.equals(match, ignoreCase = true)) {
            if (nextActive != null) {
                runCatching {
                    syncWorldPluginProfiles(fromWorld = currentActive, toWorld = nextActive)
                }.onFailure { error ->
                    return@withContext "Failed switching plugins before delete: ${error.message ?: "unknown error"}"
                }
            }
        }

        val serverDirToDelete = ServerFileManager.getServerDirNoCreate(appContext, match)
        val deletedWorldData = serverDirToDelete.deleteRecursively()
        pluginProfileDir(match).deleteRecursively()
        backupsDirForWorld(match).deleteRecursively()

        // Sync registries to all world slots!
        syncRegistriesAcrossAllWorlds(worldsToRemove = setOf(match))

        if (currentActive.equals(match, ignoreCase = true)) {
            val newActive = nextActive ?: "world"
            activeWorld = newActive
            AppPreferencesStore.setSelectedWorld(appContext, newActive)
            withContext(Dispatchers.Main) {
                val next = config.copy(worldName = newActive, port = singleServerPort)
                config = next
                refreshAll()
            }
        } else {
            withContext(Dispatchers.Main) {
                refreshAll()
            }
        }

        if (deletedWorldData) {
            "Deleted world $match."
        } else {
            "Removed world entry $match."
        }
    }

    suspend fun createBackup(): String = withContext(Dispatchers.IO) {
        if (isRunning || isStarting || isStopping) {
            return@withContext "Stop the server before creating a backup."
        }
        try {
            withContext(Dispatchers.Main) {
                isBackingUp = true
                manualBackupState = com.pocketcraft.server.service.BackupProgressTracker.State.RUNNING
                backupProgressPercent = 2
                backupStatusMessage = "Preparing world profile..."
            }
            val currentActiveWorld = sanitizeWorldName(activeWorld.ifBlank { "world" })
            syncActiveWorldContentIntoProfile(currentActiveWorld)
            val worldFolders = worldDirectoryCandidates(currentActiveWorld).filter(File::exists)
            if (worldFolders.isEmpty()) {
                withContext(Dispatchers.Main) {
                    isBackingUp = false
                    manualBackupState = com.pocketcraft.server.service.BackupProgressTracker.State.IDLE
                    backupProgressPercent = 0
                    backupStatusMessage = ""
                }
                return@withContext "No world folders found to back up."
            }

            withContext(Dispatchers.Main) {
                backupProgressPercent = 5
                backupStatusMessage = "Scanning world files..."
            }
            kotlinx.coroutines.delay(100)

            val backupName = buildString {
                append(activeWorld.ifBlank { "world" })
                append("-")
                append(SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()))
                append(".zip")
            }
            val backupFile = File(backupsDirForWorld(activeWorld), backupName)
            val entries = collectBackupEntries()
            val fileEntries = entries.filterNot { it.isDirectory }
            if (fileEntries.isEmpty()) {
                withContext(Dispatchers.Main) {
                    isBackingUp = false
                    manualBackupState = com.pocketcraft.server.service.BackupProgressTracker.State.IDLE
                    backupProgressPercent = 0
                    backupStatusMessage = ""
                }
                return@withContext "No server files found to back up."
            }

            ZipOutputStream(BufferedOutputStream(FileOutputStream(backupFile), 65536)).use { zip ->
                zip.setLevel(java.util.zip.Deflater.BEST_SPEED)
                withContext(Dispatchers.Main) {
                    backupStatusMessage = "Backing up server directory..."
                    backupProgressPercent = 5
                }

                var processedFiles = 0
                var lastUpdateMillis = 0L
                var lastProgressPercent = -1
                entries.forEach { entry ->
                    if (!isActive) throw kotlinx.coroutines.CancellationException("Backup cancelled")
                    val progress = (10 + ((processedFiles * 85) / fileEntries.size.coerceAtLeast(1))).coerceIn(10, 95)
                    val now = System.currentTimeMillis()
                    if (now - lastUpdateMillis >= 150L || progress != lastProgressPercent) {
                        lastUpdateMillis = now
                        lastProgressPercent = progress
                        withContext(Dispatchers.Main) {
                            backupProgressPercent = progress
                            backupStatusMessage = if (entry.isDirectory) "Preparing backup..." else "Backing up: ${entry.relativePath.substringAfterLast('/')}"
                        }
                    }

                    if (entry.isDirectory) {
                        zip.putNextEntry(ZipEntry(entry.relativePath))
                        zip.closeEntry()
                    } else {
                        // backupExcludeDirs already filters the top-level heavy dirs;
                        // this check is kept as a safety net for any subdirs matched by name.
                        addFileToZip(entry.file, entry.relativePath, zip)
                        processedFiles++
                    }
                }

                withContext(Dispatchers.Main) {
                    backupProgressPercent = 95
                    backupStatusMessage = "Finalizing backup..."
                }
            }

            // Backup to persistent location
            withContext(Dispatchers.Main) {
                backupProgressPercent = 98
                backupStatusMessage = "Saving to Downloads..."
            }
            saveToPersistentBackups(backupFile, backupName, activeWorld)

            // Determine and display save location
            val saveLocation = "Downloads/PocketCraftWorldBackups/$activeWorld"

            val includedItems = listOf("Worlds", "Plugins", "Mods", "Resource packs", "Configs", "Server files")
            val includedText = includedItems.joinToString(", ")

            withContext(Dispatchers.Main) {
                backupProgressPercent = 100
                backupStatusMessage = "Backup complete!"
                backupSaveLocation = saveLocation
                manualBackupState = com.pocketcraft.server.service.BackupProgressTracker.State.COMPLETED
                isBackingUp = false
                refreshAll()
            }
            kotlinx.coroutines.delay(500)
            FirebaseAnalyticsManager.logBackupCreated(activeWorld, backupFile.length())

            return@withContext "Backup created: $backupName\nIncluded: $includedText\nSaved to Downloads folder"
        } catch (e: Exception) {
            android.util.Log.e("ServerBackup", "Backup failed", e)
            val isCancelled = e is kotlinx.coroutines.CancellationException
            withContext(Dispatchers.Main) {
                backupProgressPercent = 0
                backupStatusMessage = if (isCancelled) "Backup cancelled." else "Backup failed: ${e.javaClass.simpleName}"
                manualBackupState = com.pocketcraft.server.service.BackupProgressTracker.State.FAILED
                isBackingUp = false
            }
            return@withContext if (isCancelled) "Backup cancelled." else "Backup failed: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    suspend fun importBackup(uri: Uri): String = withContext(Dispatchers.IO) {
        if (isRunning || isStarting || isStopping || isBackingUp || isRestoringBackup || isDownloadingBackup) {
            return@withContext "Stop active backup or server actions before importing a backup."
        }

        val targetWorld = sanitizeWorldName(activeWorld.ifBlank { "world" })
        val importedFileName = resolveImportedFileName(uri)
        val tempFile = File(appContext.cacheDir, "backup_import_${System.currentTimeMillis()}.zip")

        try {
            withContext(Dispatchers.Main) {
                isRestoringBackup = true
                restoreProgressPercent = 0
                restoreStatusMessage = "Importing selected world backup..."
            }
            val opened = appContext.contentResolver.openInputStream(uri)
            if (opened == null) {
                withContext(Dispatchers.Main) {
                    isRestoringBackup = false
                    restoreProgressPercent = 0
                    restoreStatusMessage = ""
                }
                return@withContext "Could not open the selected backup."
            }
            val sourceSize = queryContentLength(uri)
            opened.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var copied = 0L
                    var lastProgressPercent = -1
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        if (sourceSize > 0L) {
                            val progress = ((copied * 35L) / sourceSize).toInt().coerceIn(0, 35)
                            if (progress != lastProgressPercent) {
                                lastProgressPercent = progress
                                withContext(Dispatchers.Main) {
                                    restoreProgressPercent = progress
                                    restoreStatusMessage = "Importing selected world backup..."
                                }
                            }
                        }
                    }
                }
            }

            withContext(Dispatchers.Main) {
                restoreProgressPercent = 35
                restoreStatusMessage = "Preparing to extract world backup..."
            }

            ZipFile(tempFile).use { zip ->
                val totalEntries = zip.size()
                if (totalEntries == 0) {
                    withContext(Dispatchers.Main) {
                        isRestoringBackup = false
                        restoreProgressPercent = 0
                        restoreStatusMessage = ""
                    }
                    return@withContext "Selected ZIP is empty."
                }

                clearServerDirectoryForRestore()
                // Give the OS a moment to fully release file handles after deletion
                delay(300)
                var processedEntries = 0
                var lastUpdateMillis = 0L
                var lastProgressPercent = -1

                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val zEntry = entries.nextElement()
                    try {
                        unzipEntry(serverDir, zip, zEntry)
                        processedEntries++
                        val progress = (35 + (processedEntries * 60 / totalEntries)).coerceIn(35, 95)
                        val now = System.currentTimeMillis()
                        if (now - lastUpdateMillis >= 150L || progress != lastProgressPercent) {
                            lastUpdateMillis = now
                            lastProgressPercent = progress
                            withContext(Dispatchers.Main) {
                                restoreProgressPercent = progress
                                restoreStatusMessage = "Extracting ${zEntry.name}"
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("ServerBackup", "Failed to import ${zEntry.name}", e)
                    }
                }
            }

            withContext(Dispatchers.Main) {
                restoreProgressPercent = 97
                restoreStatusMessage = "Normalizing restored world files..."
            }
            WorldImporter.normalizeRestoredServerBackup(serverDir, config.serverType, targetWorld)
            saveConfig(config.copy(worldName = targetWorld))
            flattenWorldStructure(targetWorld)
            DimensionMigrator.syncDimensionsForServerType(appContext, targetWorld, config.serverType)
            val dimensionRestoreSummary = restoreDimensionsFromLatestPreviousBackup(
                targetWorld = targetWorld,
                importedFileName = importedFileName
            )
            syncProfileIntoActiveWorldContent(targetWorld)
            PlayerDataManager.fixOfflineUuids(serverDir, targetWorld)

            withContext(Dispatchers.Main) {
                restoreProgressPercent = 100
                restoreStatusMessage = "Finalizing restore..."
                delay(500)
                refreshAll()
                isRestoringBackup = false
                restoreProgressPercent = 0
                restoreStatusMessage = ""
            }
            return@withContext buildString {
                append("Backup imported into $targetWorld.")
                if (dimensionRestoreSummary != null) {
                    append("\n")
                    append(dimensionRestoreSummary)
                }
                append("\nStart the server to load it.")
            }
        } catch (e: Exception) {
            android.util.Log.e("ServerBackup", "Backup import failed", e)
            withContext(Dispatchers.Main) {
                isRestoringBackup = false
                restoreProgressPercent = 0
                restoreStatusMessage = ""
            }
            return@withContext "Backup import failed: ${e.message ?: e.javaClass.simpleName}"
        } finally {
            if (tempFile.exists()) tempFile.delete()
        }
    }

    suspend fun restoreBackup(entry: BackupEntry): String = withContext(Dispatchers.IO) {
        if (isRunning || isStarting || isStopping) {
            return@withContext "Stop the server before restoring a backup."
        }

        try {
            val targetWorld = sanitizeWorldName(activeWorld.ifBlank { "world" })
            withContext(Dispatchers.Main) {
                isRestoringBackup = true
                restoreProgressPercent = 0
                restoreStatusMessage = "Preparing restore..."
                restoreProgressPercent = 5
            }

            java.util.zip.ZipFile(entry.file).use { zip ->
                val totalEntries = zip.size()
                if (totalEntries == 0) {
                    withContext(Dispatchers.Main) {
                        isRestoringBackup = false
                        restoreProgressPercent = 0
                        restoreStatusMessage = ""
                    }
                    return@withContext "Backup is empty."
                }
                clearServerDirectoryForRestore()
                // Give the OS a moment to fully release file handles after deletion
                delay(300)
                var processedEntries = 0
                var lastUpdateMillis = 0L
                var lastProgressPercent = -1

                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val zEntry = entries.nextElement()
                    try {
                        unzipEntry(serverDir, zip, zEntry)
                        processedEntries++
                        val progress = (processedEntries * 95 / totalEntries).coerceIn(5, 95)

                        val now = System.currentTimeMillis()
                        if (now - lastUpdateMillis >= 150L || progress != lastProgressPercent) {
                            lastUpdateMillis = now
                            lastProgressPercent = progress
                            withContext(Dispatchers.Main) {
                                restoreProgressPercent = progress
                                restoreStatusMessage = "Restoring ${zEntry.name}"
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("ServerRestore", "Failed to restore ${zEntry.name}", e)
                    }
                }
            }

            withContext(Dispatchers.Main) {
                restoreProgressPercent = 97
                restoreStatusMessage = "Normalizing restored world files..."
            }
            WorldImporter.normalizeRestoredServerBackup(serverDir, config.serverType, targetWorld)
            saveConfig(config.copy(worldName = targetWorld))
            flattenWorldStructure(targetWorld)
            DimensionMigrator.syncDimensionsForServerType(appContext, targetWorld, config.serverType)
            // Restore Nether/End from a previous backup if the current backup is missing them.
            // This handles cases where the backup was created before the user visited those dimensions.
            val dimensionRestoreSummary = restoreDimensionsFromLatestPreviousBackup(
                targetWorld = targetWorld,
                importedFileName = entry.file.name
            )
            syncProfileIntoActiveWorldContent(targetWorld)
            
            // Fix offline UUIDs after restore in case the backup came from an online server
            PlayerDataManager.fixOfflineUuids(serverDir, targetWorld)

            withContext(Dispatchers.Main) {
                restoreProgressPercent = 100
                restoreStatusMessage = "Finalizing restore..."
                delay(500)
                refreshAll()
                isRestoringBackup = false
                restoreProgressPercent = 0
                restoreStatusMessage = ""
            }
            FirebaseAnalyticsManager.logBackupRestored(activeWorld, entry.name)

            return@withContext buildString {
                append("Backup restored successfully.")
                if (dimensionRestoreSummary != null) {
                    append("\n")
                    append(dimensionRestoreSummary)
                }
                append("\nStart the server to load it.")
            }
        } catch (e: Exception) {
            android.util.Log.e("ServerRestore", "Restore failed", e)
            withContext(Dispatchers.Main) {
                isRestoringBackup = false
                restoreProgressPercent = 0
                restoreStatusMessage = ""
            }
            return@withContext "Restore failed: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    suspend fun restoreBackupFile(file: File, displayName: String = file.name): String {
        return restoreBackup(
            BackupEntry(
                name = displayName,
                sizeMb = (file.length() / (1024L * 1024L)).coerceAtLeast(0L),
                date = "",
                file = file
            )
        )
    }

    /**
     * Restores ONLY the Nether and End dimension folders from the given backup entry.
     * The overworld (and all other server files) are left completely untouched.
     *
     * For Paper/Purpur backups the Nether lives at  <world>_nether/  and the End at  <world>_the_end/.
     * For Fabric/Modpack backups the Nether lives at  <world>/DIM-1/  and the End at  <world>/DIM1/.
     */
    suspend fun restoreDimensionsOnly(entry: BackupEntry): String = withContext(Dispatchers.IO) {
        if (isRunning || isStarting || isStopping) {
            return@withContext "Stop the server before restoring dimensions."
        }

        val targetWorld = sanitizeWorldName(activeWorld.ifBlank { "world" })
        val isVanillaStyle = config.serverType == ServerType.FABRIC || config.serverType == ServerType.MODPACK

        val netherZipPrefixes = mutableListOf<String>()
        val endZipPrefixes = mutableListOf<String>()

        if (isVanillaStyle) {
            netherZipPrefixes += listOf("$targetWorld/DIM-1/", "world/DIM-1/")
            endZipPrefixes += listOf("$targetWorld/DIM1/", "world/DIM1/")
        } else {
            netherZipPrefixes += listOf("${targetWorld}_nether/", "world_nether/")
            endZipPrefixes += listOf("${targetWorld}_the_end/", "world_the_end/")
        }

        // Auto-detect any folder ending with _nether or _the_end or _end
        try {
            java.util.zip.ZipFile(entry.file).use { zip ->
                val zipEntries = zip.entries()
                while (zipEntries.hasMoreElements()) {
                    val name = zipEntries.nextElement().name.replace('\\', '/').removePrefix("/").removePrefix("./").trim()
                    val parts = name.split('/')
                    if (parts.isNotEmpty()) {
                        val firstPart = parts[0]
                        val lowerFirst = firstPart.lowercase(Locale.getDefault())
                        if (lowerFirst.endsWith("_nether") || lowerFirst == "nether" || lowerFirst == "dim-1") {
                            val prefix = if (parts.size > 1 && firstPart != "dim-1") "$firstPart/" else firstPart
                            if (prefix !in netherZipPrefixes) netherZipPrefixes.add(prefix)
                        }
                        if (lowerFirst.endsWith("_the_end") || lowerFirst.endsWith("_end") || lowerFirst == "end" || lowerFirst == "dim1") {
                            val prefix = if (parts.size > 1 && firstPart != "dim1") "$firstPart/" else firstPart
                            if (prefix !in endZipPrefixes) endZipPrefixes.add(prefix)
                        }
                        
                        if (parts.size > 1) {
                            val secondPart = parts[1]
                            val lowerSecond = secondPart.lowercase(Locale.getDefault())
                            if (lowerSecond.endsWith("_nether") || lowerSecond == "nether" || lowerSecond == "dim-1") {
                                val prefix = "$firstPart/$secondPart/"
                                if (prefix !in netherZipPrefixes) netherZipPrefixes.add(prefix)
                            }
                            if (lowerSecond.endsWith("_the_end") || lowerSecond.endsWith("_end") || lowerSecond == "end" || lowerSecond == "dim1") {
                                val prefix = "$firstPart/$secondPart/"
                                if (prefix !in endZipPrefixes) endZipPrefixes.add(prefix)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("ServerStateHolder", "Failed to scan ZIP for dimension folders: ${e.message}")
        }

        val tempDir = File(appContext.cacheDir, "dim_restore_${System.currentTimeMillis()}")
        try {
            withContext(Dispatchers.Main) {
                isRestoringBackup = true
                restoreProgressPercent = 0
                restoreStatusMessage = "Opening backup for dimension restore…"
            }

            tempDir.mkdirs()

            java.util.zip.ZipFile(entry.file).use { zip ->
                val totalEntries = zip.size().coerceAtLeast(1)
                var processed = 0
                var lastPercent = -1

                val allEntries = zip.entries()
                while (allEntries.hasMoreElements()) {
                    val zEntry = allEntries.nextElement()
                    processed++
                    val progress = (processed * 90 / totalEntries).coerceIn(0, 90)
                    if (progress != lastPercent) {
                        lastPercent = progress
                        withContext(Dispatchers.Main) {
                            restoreProgressPercent = progress
                            restoreStatusMessage = "Scanning ${zEntry.name}…"
                        }
                    }

                    val rawName = zEntry.name
                        .replace('\\', '/')
                        .removePrefix("/")
                        .removePrefix("./")
                        .trim()
                    if (rawName.isBlank() || rawName.startsWith("__MACOSX/") || rawName.endsWith(".DS_Store")) continue

                    val isNether = netherZipPrefixes.any { rawName == it.trimEnd('/') || rawName.startsWith(it) }
                    val isEnd    = endZipPrefixes.any   { rawName == it.trimEnd('/') || rawName.startsWith(it) }
                    if (!isNether && !isEnd) continue

                    // Extract into tempDir preserving the relative path inside the ZIP
                    val target = File(tempDir, rawName).canonicalFile
                    if (!target.path.startsWith(tempDir.canonicalPath)) continue

                    if (zEntry.isDirectory) {
                        target.mkdirs()
                    } else {
                        target.parentFile?.mkdirs()
                        zip.getInputStream(zEntry).use { inp ->
                            target.outputStream().use { out -> inp.copyTo(out) }
                        }
                    }
                }
            }

            withContext(Dispatchers.Main) {
                restoreProgressPercent = 92
                restoreStatusMessage = "Placing dimension files…"
            }

            // Move extracted dimension folders to their correct live locations.
            val restoredNames = mutableListOf<String>()

            if (isVanillaStyle) {
                var foundNether: File? = null
                var foundEnd: File? = null
                
                tempDir.walkTopDown().maxDepth(4).forEach { file ->
                    if (file.isDirectory) {
                        if (file.name.lowercase(Locale.getDefault()) == "dim-1") foundNether = file
                        if (file.name.lowercase(Locale.getDefault()) == "dim1") foundEnd = file
                    }
                }
                
                val destWorldDir = File(serverDir, targetWorld)
                destWorldDir.mkdirs()
                
                foundNether?.let { src ->
                    val dest = File(destWorldDir, "DIM-1")
                    if (dest.exists()) dest.deleteRecursively()
                    src.renameTo(dest)
                    restoredNames += "the nether"
                }
                foundEnd?.let { src ->
                    val dest = File(destWorldDir, "DIM1")
                    if (dest.exists()) dest.deleteRecursively()
                    src.renameTo(dest)
                    restoredNames += "the end"
                }
            } else {
                var foundNetherSrc: File? = null
                var foundEndSrc: File? = null
                
                tempDir.walkTopDown().maxDepth(4).forEach { file ->
                    if (file.isDirectory) {
                        val nameLower = file.name.lowercase(Locale.getDefault())
                        if (nameLower.endsWith("_nether") || nameLower == "nether") {
                            foundNetherSrc = file
                        } else if (nameLower == "dim-1") {
                            if (foundNetherSrc == null || foundNetherSrc?.name?.lowercase(Locale.getDefault()) == "dim-1") {
                                foundNetherSrc = file
                            }
                        }

                        if (nameLower.endsWith("_the_end") || nameLower.endsWith("_end") || nameLower == "end") {
                            foundEndSrc = file
                        } else if (nameLower == "dim1") {
                            if (foundEndSrc == null || foundEndSrc?.name?.lowercase(Locale.getDefault()) == "dim1") {
                                foundEndSrc = file
                            }
                        }
                    }
                }
                
                foundNetherSrc?.let { src ->
                    val isDIM = src.name.lowercase(Locale.getDefault()) == "dim-1"
                    val sourceFolder = if (isDIM) src else {
                        val nested = File(src, "DIM-1")
                        if (nested.exists() && nested.isDirectory) nested else src
                    }
                    
                    val destDir = File(serverDir, "${targetWorld}_nether")
                    destDir.mkdirs()
                    
                    // Copy the contents of sourceFolder (region, poi, entities, data) directly into destDir
                    sourceFolder.listFiles()?.forEach { child ->
                        val childName = child.name.lowercase(Locale.getDefault())
                        if (childName != "level.dat" && childName != "uid.dat" && childName != "level.dat_old") {
                            val targetDest = File(destDir, child.name)
                            if (targetDest.exists()) targetDest.deleteRecursively()
                            if (!child.renameTo(targetDest)) {
                                child.copyRecursively(targetDest, overwrite = true)
                                child.deleteRecursively()
                            }
                        }
                    }
                    
                    // Clear local level.dat and uid.dat in destDir so Paper regenerates them with correct UUID
                    val netherLevelDat = File(destDir, "level.dat")
                    if (netherLevelDat.exists()) netherLevelDat.delete()
                    val netherUid = File(destDir, "uid.dat")
                    if (netherUid.exists()) netherUid.delete()
                    
                    restoredNames += "the nether"
                }
                
                foundEndSrc?.let { src ->
                    val isDIM = src.name.lowercase(Locale.getDefault()) == "dim1"
                    val sourceFolder = if (isDIM) src else {
                        val nested = File(src, "DIM1")
                        if (nested.exists() && nested.isDirectory) nested else src
                    }
                    
                    val destDir = File(serverDir, "${targetWorld}_the_end")
                    destDir.mkdirs()
                    
                    // Copy the contents of sourceFolder directly into destDir
                    sourceFolder.listFiles()?.forEach { child ->
                        val childName = child.name.lowercase(Locale.getDefault())
                        if (childName != "level.dat" && childName != "uid.dat" && childName != "level.dat_old") {
                            val targetDest = File(destDir, child.name)
                            if (targetDest.exists()) targetDest.deleteRecursively()
                            if (!child.renameTo(targetDest)) {
                                child.copyRecursively(targetDest, overwrite = true)
                                child.deleteRecursively()
                            }
                        }
                    }
                    
                    // Clear local level.dat and uid.dat in destDir so Paper regenerates them with correct UUID
                    val endLevelDat = File(destDir, "level.dat")
                    if (endLevelDat.exists()) endLevelDat.delete()
                    val endUid = File(destDir, "uid.dat")
                    if (endUid.exists()) endUid.delete()
                    
                    restoredNames += "the end"
                }

            }

            withContext(Dispatchers.Main) {
                restoreProgressPercent = 100
                restoreStatusMessage = "Done!"
                delay(400)
                refreshAll()
                isRestoringBackup = false
                restoreProgressPercent = 0
                restoreStatusMessage = ""
            }

            return@withContext if (restoredNames.isEmpty()) {
                "No Nether or End data found in ${entry.name}. " +
                "The backup may have been created before those dimensions were visited."
            } else {
                "Restored ${restoredNames.joinToString(" and ")} from ${entry.name}.\n" +
                "Overworld was not touched. Start the server to load it."
            }
        } catch (e: Exception) {
            android.util.Log.e("ServerStateHolder", "Dimension-only restore failed", e)
            withContext(Dispatchers.Main) {
                isRestoringBackup = false
                restoreProgressPercent = 0
                restoreStatusMessage = ""
            }
            return@withContext "Dimension restore failed: ${e.message ?: e.javaClass.simpleName}"
        } finally {
            tempDir.deleteRecursively()
        }
    }

    suspend fun restoreOverworldOnly(entry: BackupEntry): String = withContext(Dispatchers.IO) {
        if (isRunning || isStarting || isStopping) {
            return@withContext "Stop the server before restoring."
        }

        val targetWorld = sanitizeWorldName(activeWorld.ifBlank { "world" })
        val isVanillaStyle = config.serverType == ServerType.FABRIC || config.serverType == ServerType.MODPACK

        val netherZipPrefixes = mutableListOf<String>()
        val endZipPrefixes = mutableListOf<String>()

        if (isVanillaStyle) {
            netherZipPrefixes += listOf("$targetWorld/DIM-1/", "world/DIM-1/")
            endZipPrefixes += listOf("$targetWorld/DIM1/", "world/DIM1/")
        } else {
            netherZipPrefixes += listOf("${targetWorld}_nether/", "world_nether/")
            endZipPrefixes += listOf("${targetWorld}_the_end/", "world_the_end/")
        }

        try {
            java.util.zip.ZipFile(entry.file).use { zip ->
                val zipEntries = zip.entries()
                while (zipEntries.hasMoreElements()) {
                    val name = zipEntries.nextElement().name.replace('\\', '/').removePrefix("/").removePrefix("./").trim()
                    val parts = name.split('/')
                    if (parts.isNotEmpty()) {
                        val firstPart = parts[0]
                        val lowerFirst = firstPart.lowercase(Locale.getDefault())
                        if (lowerFirst.endsWith("_nether") || lowerFirst == "nether" || lowerFirst == "dim-1") {
                            val prefix = if (parts.size > 1 && firstPart != "dim-1") "$firstPart/" else firstPart
                            if (prefix !in netherZipPrefixes) netherZipPrefixes.add(prefix)
                        }
                        if (lowerFirst.endsWith("_the_end") || lowerFirst.endsWith("_end") || lowerFirst == "end" || lowerFirst == "dim1") {
                            val prefix = if (parts.size > 1 && firstPart != "dim1") "$firstPart/" else firstPart
                            if (prefix !in endZipPrefixes) endZipPrefixes.add(prefix)
                        }
                        
                        if (parts.size > 1) {
                            val secondPart = parts[1]
                            val lowerSecond = secondPart.lowercase(Locale.getDefault())
                            if (lowerSecond.endsWith("_nether") || lowerSecond == "nether" || lowerSecond == "dim-1") {
                                val prefix = "$firstPart/$secondPart/"
                                if (prefix !in netherZipPrefixes) netherZipPrefixes.add(prefix)
                            }
                            if (lowerSecond.endsWith("_the_end") || lowerSecond.endsWith("_end") || lowerSecond == "end" || lowerSecond == "dim1") {
                                val prefix = "$firstPart/$secondPart/"
                                if (prefix !in endZipPrefixes) endZipPrefixes.add(prefix)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("ServerStateHolder", "Failed to scan ZIP for dimension folders: ${e.message}")
        }

        val tempDir = File(appContext.cacheDir, "overworld_restore_${System.currentTimeMillis()}")
        try {
            withContext(Dispatchers.Main) {
                isRestoringBackup = true
                restoreProgressPercent = 0
                restoreStatusMessage = "Opening backup for Overworld restore…"
            }

            tempDir.mkdirs()

            java.util.zip.ZipFile(entry.file).use { zip ->
                val totalEntries = zip.size().coerceAtLeast(1)
                var processed = 0
                var lastPercent = -1

                val allEntries = zip.entries()
                while (allEntries.hasMoreElements()) {
                    val zEntry = allEntries.nextElement()
                    processed++
                    val progress = (processed * 90 / totalEntries).coerceIn(0, 90)
                    if (progress != lastPercent) {
                        lastPercent = progress
                        withContext(Dispatchers.Main) {
                            restoreProgressPercent = progress
                            restoreStatusMessage = "Restoring ${zEntry.name}…"
                        }
                    }

                    val rawName = zEntry.name
                        .replace('\\', '/')
                        .removePrefix("/")
                        .removePrefix("./")
                        .trim()
                    if (rawName.isBlank() || rawName.startsWith("__MACOSX/") || rawName.endsWith(".DS_Store")) continue

                    val isNether = netherZipPrefixes.any { rawName == it.trimEnd('/') || rawName.startsWith(it) }
                    val isEnd    = endZipPrefixes.any   { rawName == it.trimEnd('/') || rawName.startsWith(it) }
                    if (isNether || isEnd) continue

                    val target = File(tempDir, rawName).canonicalFile
                    if (!target.path.startsWith(tempDir.canonicalPath)) continue

                    if (zEntry.isDirectory) {
                        target.mkdirs()
                    } else {
                        target.parentFile?.mkdirs()
                        zip.getInputStream(zEntry).use { inp ->
                            target.outputStream().use { out -> inp.copyTo(out) }
                        }
                    }
                }
            }

            withContext(Dispatchers.Main) {
                restoreProgressPercent = 92
                restoreStatusMessage = "Normalizing Overworld files…"
            }

            WorldImporter.normalizeRestoredServerBackup(tempDir, config.serverType, targetWorld)
            tempDir.copyRecursively(serverDir, overwrite = true)

            withContext(Dispatchers.Main) {
                restoreProgressPercent = 100
                restoreStatusMessage = "Done!"
                delay(400)
                refreshAll()
                isRestoringBackup = false
                restoreProgressPercent = 0
                restoreStatusMessage = ""
            }

            return@withContext "Restored Overworld from ${entry.name}.\nNether and End were not touched."
        } catch (e: Exception) {
            android.util.Log.e("ServerStateHolder", "Overworld-only restore failed", e)
            withContext(Dispatchers.Main) {
                isRestoringBackup = false
                restoreProgressPercent = 0
                restoreStatusMessage = ""
            }
            return@withContext "Overworld restore failed: ${e.message ?: e.javaClass.simpleName}"
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun deleteFileFromDownloads(fileName: String, worldName: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val safeWorldName = sanitizeWorldName(worldName)
        val resolver = appContext.contentResolver
        val projection = arrayOf(android.provider.MediaStore.MediaColumns._ID)
        val selection = "${android.provider.MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${android.provider.MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf(fileName, "Download/PocketCraftWorldBackups/$safeWorldName%")
        val queryUri = android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
        
        return try {
            resolver.query(queryUri, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns._ID))
                    val deleteUri = android.content.ContentUris.withAppendedId(queryUri, id)
                    resolver.delete(deleteUri, null, null) > 0
                } else false
            } ?: false
        } catch (e: Exception) {
            android.util.Log.e("ServerStateHolder", "Failed to delete from MediaStore", e)
            false
        }
    }

    suspend fun deleteBackup(entry: BackupEntry): String = withContext(Dispatchers.IO) {
        val targetName = entry.file.name
        var deletedAnything = false
        val candidateFiles = linkedSetOf(
            entry.file,
            File(backupsDirForWorld(activeWorld), targetName),
            File(exportedBackupsDirForWorld(activeWorld), targetName)
        )

        candidateFiles.forEach { candidate ->
            if (candidate.exists() && candidate.delete()) {
                deletedAnything = true
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                if (deleteFileFromDownloads(targetName, activeWorld)) {
                    deletedAnything = true
                }
            }
        }

        val allCopiesGone = candidateFiles.none(File::exists)
        if (!deletedAnything && !allCopiesGone) {
            return@withContext "Could not delete ${entry.name}."
        }
        withContext(Dispatchers.Main) { refreshAll() }
        "Deleted ${entry.name}."
    }

    suspend fun downloadBackup(entry: BackupEntry): String = withContext(Dispatchers.IO) {
        if (!entry.file.exists()) {
            return@withContext "Could not find ${entry.name}."
        }

        val canStartDownload = withContext(Dispatchers.Main.immediate) {
            if (isDownloadingBackup) {
                false
            } else {
                isDownloadingBackup = true
                downloadBackupProgressPercent = 0
                downloadBackupStatusMessage = "Preparing phone download..."
                true
            }
        }
        if (!canStartDownload) {
            return@withContext "Download already in progress..."
        }

        try {
            withContext(Dispatchers.Main) {
                downloadBackupProgressPercent = 5
                downloadBackupStatusMessage = "Copying ${entry.name} to Downloads..."
            }

            saveToPersistentBackups(
                source = entry.file,
                displayName = entry.file.name,
                worldName = activeWorld,
                onProgress = { percent ->
                    withContext(Dispatchers.Main) {
                        downloadBackupProgressPercent = percent.coerceIn(0, 100)
                        downloadBackupStatusMessage = "Copying ${entry.name} to Downloads..."
                    }
                }
            )

            withContext(Dispatchers.Main) {
                downloadBackupProgressPercent = 100
                downloadBackupStatusMessage = "Saved to Downloads folder"
                delay(350)
                isDownloadingBackup = false
                downloadBackupProgressPercent = 0
                downloadBackupStatusMessage = ""
            }

            "Downloaded ${entry.name} to Downloads folder."
        } catch (e: Exception) {
            android.util.Log.e("ServerBackup", "Backup download failed", e)
            withContext(Dispatchers.Main) {
                isDownloadingBackup = false
                downloadBackupProgressPercent = 0
                downloadBackupStatusMessage = ""
            }
            "Download failed: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    suspend fun resetWorld(
        deletePlayerData: Boolean,
        deleteDatapacks: Boolean,
        deleteLogs: Boolean
    ): String = withContext(Dispatchers.IO) {
        if (isRunning || isStarting || isStopping || isBackingUp || isRestoringBackup || isDownloadingBackup) {
            return@withContext "Stop the server or finish active backup operations before resetting the world."
        }
        val activeWorldCopy = sanitizeWorldName(activeWorld.ifBlank { "world" })
        val targetServerDir = ServerFileManager.getServerDirNoCreate(appContext, activeWorldCopy)
        if (!targetServerDir.isDirectory) {
            return@withContext "$activeWorldCopy was not found."
        }

        val recoveryRoot = resetRecoveryRoot(activeWorldCopy)
        var deletedAnything = false

        resetWorldBaseNames(activeWorldCopy, targetServerDir).forEach { base ->
            resetWorldDirectory(File(targetServerDir, base), targetServerDir, recoveryRoot, deletePlayerData, deleteDatapacks)
                .also { deletedAnything = deletedAnything || it }
            resetWorldDirectory(File(targetServerDir, "${base}_nether"), targetServerDir, recoveryRoot, deletePlayerData, deleteDatapacks)
                .also { deletedAnything = deletedAnything || it }
            resetWorldDirectory(File(targetServerDir, "${base}_the_end"), targetServerDir, recoveryRoot, deletePlayerData, deleteDatapacks)
                .also { deletedAnything = deletedAnything || it }
        }

        // Also clear the legacy default "world" folder if it still exists alongside the slot-named folder.
        // Without this, the migration in ensureWorldDirectories would restore the old world on the next
        // server boot, silently discarding the user's intended fresh seed-based generation.
        if (activeWorldCopy != "world") {
            resetWorldDirectory(File(targetServerDir, "world"), targetServerDir, recoveryRoot, deletePlayerData, deleteDatapacks)
                .also { deletedAnything = deletedAnything || it }
            resetWorldDirectory(File(targetServerDir, "world_nether"), targetServerDir, recoveryRoot, deletePlayerData, deleteDatapacks)
                .also { deletedAnything = deletedAnything || it }
            resetWorldDirectory(File(targetServerDir, "world_the_end"), targetServerDir, recoveryRoot, deletePlayerData, deleteDatapacks)
                .also { deletedAnything = deletedAnything || it }
        }

        resetFlatWorldEntries(targetServerDir, recoveryRoot, deletePlayerData, deleteDatapacks)
            .also { deletedAnything = deletedAnything || it }

        if (deleteLogs) {
            moveToResetRecovery(File(targetServerDir, "logs"), targetServerDir, recoveryRoot)
                .also { deletedAnything = deletedAnything || it }
        }

        if (!deletedAnything) {
            return@withContext "Nothing selected for deletion."
        }
        withContext(Dispatchers.Main) { refreshAll() }
        "Selected world data reset. Recovery copy saved in app storage."
    }


    private suspend fun saveToPersistentBackups(
        source: File,
        displayName: String,
        worldName: String,
        onProgress: suspend (Int) -> Unit = {}
    ) {
        val safeWorldName = sanitizeWorldName(worldName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = appContext.contentResolver
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/zip")
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Download/PocketCraftWorldBackups/$safeWorldName/")
                put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Unable to create MediaStore entry")

            val totalBytes = source.length().coerceAtLeast(1L)
            resolver.openOutputStream(uri)?.use { output ->
                FileInputStream(source).use { input ->
                    val buffer = ByteArray(16 * 1024)
                    var copied = 0L
                    var lastPercent = -1
                    var bytes = input.read(buffer)
                    while (bytes != -1) {
                        output.write(buffer, 0, bytes)
                        copied += bytes
                        val percent = ((copied * 100L) / totalBytes).toInt().coerceIn(0, 100)
                        if (percent != lastPercent) {
                            lastPercent = percent
                            onProgress(percent)
                        }
                        bytes = input.read(buffer)
                    }
                }
            } ?: throw IllegalStateException("Unable to open backup output stream")

            val publish = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
            }
            resolver.update(uri, publish, null, null)
            return
        }

        val downloads = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
        val targetDir = File(downloads, "PocketCraftWorldBackups/$safeWorldName").also { it.mkdirs() }
        val targetFile = File(targetDir, displayName)
        val totalBytes = source.length().coerceAtLeast(1L)
        FileInputStream(source).use { input ->
            FileOutputStream(targetFile).use { output ->
                val buffer = ByteArray(16 * 1024)
                var copied = 0L
                var lastPercent = -1
                var bytes = input.read(buffer)
                while (bytes != -1) {
                    output.write(buffer, 0, bytes)
                    copied += bytes
                    val percent = ((copied * 100L) / totalBytes).toInt().coerceIn(0, 100)
                    if (percent != lastPercent) {
                        lastPercent = percent
                        onProgress(percent)
                    }
                    bytes = input.read(buffer)
                }
            }
        }
    }

    fun dispose() {
        stopPeriodicWorldSave()
        stopPeriodicLocationPolling()
        stopStartupProgressTracking(reset = false)
        startupLaunchJob?.cancel()
        startupLaunchJob = null
        stopWatchdogJob?.cancel()
        stopWatchdogJob = null
        restartFallbackJob?.cancel()
        restartFallbackJob = null
        if (receiverRegistered) {
            appContext.unregisterReceiver(receiver)
            receiverRegistered = false
        }
        scope.cancel()
    }

    private fun isServerProcessAlive(): Boolean {
        val extPid = ServerHostService.getExternalJvmPid(appContext)
        if (extPid > 0) {
            return try {
                android.system.Os.kill(extPid.toInt(), 0)
                true
            } catch (e: android.system.ErrnoException) {
                e.errno != android.system.OsConstants.ESRCH
            } catch (e: Exception) {
                false
            }
        }
        val serverPid = ServerHostService.getServerPid(appContext)
        if (serverPid <= 0) return false

        return try {
            android.system.Os.kill(serverPid, 0)
            true
        } catch (e: android.system.ErrnoException) {
            e.errno != android.system.OsConstants.ESRCH
        } catch (e: Exception) {
            false
        }
    }

    private fun startStopWatchdog() {
        stopWatchdogJob?.cancel()
        stopWatchdogJob = scope.launch {
            repeat((stopWatchdogTimeoutMs / 1000L).toInt()) {
                delay(1000)
                // In addition to runtime state, strictly verify the :server process has actually died.
                // If the process is dead, the JVM has successfully finished System.exit(0)
                if (!isServerProcessAlive()) {
                    // Update state to offline because the process might have died before updating SharedPreferences
                    ServerHostService.persistRuntimeState(appContext, versionId, activeWorld, ServerHostService.RUNTIME_STATE_OFFLINE)
                    
                    val shouldRestart = pendingRestart
                    pendingRestart = false
                    stopStartupProgressTracking(reset = !shouldRestart)
                    isStopping = false
                    isStarting = false
                    isRunning = false
                    stopPeriodicWorldSave()
                    stopPeriodicLocationPolling()
                    tps = 0f
                    publicAddress = null
                    tunnelConnecting = false
                    tunnelError = null
                    startedAtRealtime = null
                    onlinePlayers.clear()
                    appendLog("[INFO] Server stopped.")
                    if (shouldRestart) {
                        appendLog("[PocketCraft] Starting server again...")
                        startServer(isRestart = true)
                    }
                    cancel()
                }
            }
            // If it times out, assume it failed to stop and reset UI.
            if (isStopping) {
                pendingRestart = false
                isRestartingCycle = false
                stopStartupProgressTracking(reset = true)
                isStopping = false
                isStarting = false
                isRunning = false
                stopPeriodicWorldSave()
                stopPeriodicLocationPolling()
                tps = 0f
                publicAddress = null
                tunnelConnecting = false
                tunnelError = null
                startedAtRealtime = null
                onlinePlayers.clear()
                ServerHostService.persistRuntimeState(appContext, versionId, activeWorld, ServerHostService.RUNTIME_STATE_OFFLINE)
                appendLog("[ERROR] Server stop timed out. Process may be hung.")
            }
        }
    }

    private fun startPeriodicWorldSave() {
        if (periodicWorldSaveJob?.isActive == true) return
        periodicWorldSaveJob = scope.launch(Dispatchers.IO) {
            while (periodicWorldSaveJob?.isActive == true) {
                val playersOnline = onlinePlayers.isNotEmpty()
                val saveIntervalMs = when {
                    playersOnline -> 15 * 60_000L
                    totalRamGb <= 3 -> 8 * 60_000L
                    totalRamGb <= 4 -> 6 * 60_000L
                    else -> 4 * 60_000L
                }
                delay(saveIntervalMs)
                if (!isRunning || isStopping) continue
                runCatching {
                    sendRconCommand("save-all")
                }.onSuccess {
                    withContext(Dispatchers.Main) {
                        refreshAll()
                    }
                }.onFailure { error ->
                    withContext(Dispatchers.Main) {
                        appendLog("[PocketCraft] Auto-save failed: ${error.message ?: "unknown error"}")
                    }
                }
            }
        }
    }

    private fun stopPeriodicWorldSave() {
        periodicWorldSaveJob?.cancel()
        periodicWorldSaveJob = null
    }

    private fun requestWorldSave(reason: String) {
        scope.launch(Dispatchers.IO) {
            if (!isRunning) return@launch
            runCatching {
                sendRconCommand("save-all flush")
            }.onSuccess {
                withContext(Dispatchers.Main) {
                    appendLog("[PocketCraft] World save requested ($reason).")
                }
            }
        }
    }

    private fun registerReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter(ServerHostService.ACTION_SERVER_EVENT)
        val bgThread = android.os.HandlerThread("ServerEventReceiverThread").also { it.start() }
        val bgHandler = android.os.Handler(bgThread.looper)
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            filter,
            null,
            bgHandler,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        receiverRegistered = true
    }

    private fun startStartupProgressTracking() {
        startupProgressJob?.cancel()
        startupProgressJob = scope.launch(Dispatchers.IO) {
            var lastPersistedProgress = -1
            while (isStarting && !isRunning) {
                if (!isStopping) {
                    val elapsedMs = (SystemClock.elapsedRealtime() - (startupStartedAtRealtime ?: SystemClock.elapsedRealtime())).coerceAtLeast(0L)
                    if (elapsedMs > 720_000L) {
                        appendLog("[ERROR] Server startup timed out (exceeded 12 minutes).")
                        stopServer()
                        recordServerFailure("Server startup timed out (exceeded 12 minutes). Please verify your JRE settings or check the console log for errors.", duringStartup = true)
                        break
                    }
                    val nextProgress = when {
                        elapsedMs < 30_000L -> ((elapsedMs / 30_000f) * 25f)
                        elapsedMs < 75_000L -> 25f + (((elapsedMs - 30_000L) / 45_000f) * 30f)
                        elapsedMs < 135_000L -> 55f + (((elapsedMs - 75_000L) / 60_000f) * 25f)
                        elapsedMs < 180_000L -> 80f + (((elapsedMs - 135_000L) / 45_000f) * 18f)
                        else -> 98f
                    }.toInt().coerceIn(minOf(startupProgressPercent, 98), 98)

                    if (!isStarting || isRunning) break

                    withContext(Dispatchers.Main) {
                        startupProgressPercent = nextProgress
                        if (startupStatusMessage.isBlank() || startupStatusMessage == "Initializing..." || startupStatusMessage == "Preparing server...") {
                            startupStatusMessage = naturalStartupStatus(elapsedMs)
                        }
                    }

                    if (nextProgress - lastPersistedProgress >= 10 || lastPersistedProgress == -1) {
                        lastPersistedProgress = nextProgress
                        ServerHostService.persistRuntimeState(appContext, versionId, activeWorld, "STARTING ($nextProgress%)")
                        ServerHostService.pushWidgetUpdate(appContext, "STARTING ($nextProgress%)")
                    }
                }
                delay(1000)
            }
        }
    }

    private fun stopStartupProgressTracking(reset: Boolean) {
        startupProgressJob?.cancel()
        startupProgressJob = null
        startupStartedAtRealtime = null
        if (reset) {
            startupProgressPercent = 0
            startupStatusMessage = ""
        }
    }

    private fun naturalStartupStatus(elapsedMs: Long): String {
        return when {
            elapsedMs < 8_000L -> "Preparing server..."
            elapsedMs < 20_000L -> "Loading world..."
            elapsedMs < 35_000L -> "Starting server..."
            elapsedMs < 55_000L -> "Finalizing startup..."
            else -> "Almost ready..."
        }
    }

    private fun resolveServerTypeForDiagnostics(): ServerType {
        lastRequestedServerType?.let { return it }
        val recentLogs = logsQueue.toList().asReversed()
        for (line in recentLogs) {
            val normalized = line.lowercase()
            when {
                normalized.contains("booting fabric") || normalized.contains("fabric loader") -> return ServerType.FABRIC
                normalized.contains("booting purpur") || normalized.contains("purpur") -> return ServerType.PURPUR
                normalized.contains("booting modpack") -> return ServerType.MODPACK
                normalized.contains("booting paper") || normalized.contains("paper") -> return ServerType.PAPER
            }
        }
        return config.serverType
    }

    @Volatile
    private var cachedWorldSizeMb: Long = 0L
    @Volatile
    private var lastWorldSizeCalcTime: Long = 0L
    @Volatile
    private var cachedKnownPlayersList: List<PlayerInfo> = emptyList()
    @Volatile
    private var lastKnownPlayersScanTime: Long = 0L
    @Volatile
    private var scanCount: Int = 0
    @Volatile
    private var lastScanUpdateMillis: Long = 0L

    private fun readSnapshot(): DashboardSnapshot {
        val loadedConfig = loadConfig()
        val properties = ServerPropertiesHelper.readProperties(serverDir)
        val levelName = sanitizeWorldName(loadedConfig.worldName.ifBlank { activeWorld })
        val worldDetails = readWorldServerDetails(properties, levelName)

        val now = System.currentTimeMillis()
        val worldSize = if (cachedWorldSizeMb > 0L && (now - lastWorldSizeCalcTime) < 60_000L) {
            cachedWorldSizeMb
        } else {
            val size = bytesToDisplayMb(worldDirectoryCandidates(activeWorld).sumOf(::directorySize))
            cachedWorldSizeMb = size
            lastWorldSizeCalcTime = now
            size
        }
        
        val knownPlayers = readKnownPlayers(activeWorld)
        
        return DashboardSnapshot(
            config = loadedConfig,
            localIp = resolveLocalIp(),
            serverName = worldDetails.first.ifBlank { levelName },
            serverPhotoUrl = worldDetails.second,
            serverDescription = worldDetails.third,
            worldSizeMb = worldSize,
            worlds = listWorldEntries(activeWorld),
            knownPlayers = knownPlayers,
            whitelist = readNamedList("whitelist.json"),
            ops = readNamedList("ops.json"),
            banned = readNamedList("banned-players.json"),
            backups = listBackupsForWorld(activeWorld),
            relayHost = com.pocketcraft.server.data.preferences.AppPreferences(appContext).relayHost,
            activeWorldNeedsSetup = !readWorldsWithCompletedSetup(properties).contains(levelName),
            bedrockBridgeEnabled = PluginManager.isBedrockBridgeEnabled(appContext, activeWorld.ifBlank { "world" })
        )
    }

    private fun loadConfig(): ServerConfig {
        return kotlinx.coroutines.runBlocking {
            ServerConfigRepository(appContext).apply {
                setWorldNameOverride(activeWorld)
            }.loadConfig()
        }
    }

    private fun saveConfig(config: ServerConfig, targetDir: File = serverDir) {
        val prefs = com.pocketcraft.server.data.preferences.AppPreferences(appContext)
        val isPremium = prefs.isPremiumUser || prefs.debugPremiumOverride
        val maxPlayersLimit = if (isPremium) 50 else 10
        val enforcedConfig = config.copy(
            maxPlayers = config.maxPlayers.coerceIn(1, maxPlayersLimit),
            viewDistance = config.viewDistance.coerceIn(3, 32),
            simulationDistance = config.simulationDistance.coerceIn(3, 32)
        )
        val props = ServerPropertiesHelper.readProperties(targetDir)
        ServerPropertiesWriter.overlayManagedValues(targetDir, props, ServerPropertiesWriter.toSnapshot(enforcedConfig), isPremium)
        ServerPropertiesHelper.saveProperties(targetDir, props)

        // Directly sync difficulty to level.dat on disk to prevent world auto-saves from reverting difficulty to easy
        val levelName = props.getProperty("level-name", enforcedConfig.worldName.ifBlank { "world" })
        val worldDir = File(targetDir, levelName)
        val levelDat = File(worldDir, "level.dat")
        if (levelDat.exists()) {
            runCatching {
                com.pocketcraft.server.service.NBTParser.updateDifficultyInLevelDat(levelDat, enforcedConfig.difficulty)
            }
        }

        // Sync registries to all worlds to keep them updated
        val active = sanitizeWorldName(enforcedConfig.worldName)
        syncRegistriesAcrossAllWorlds(extraWorlds = setOf(active))
    }

    private fun adaptiveViewDistance(): Int = 6

    private fun adaptiveSimulationDistance(): Int = 4

    private fun adaptiveMaxPlayers(): Int = 10

    private fun buildServerMotd(displayName: String, description: String): String {
        val cleanName = displayName.trim()
        val cleanDescription = description.trim()
        return when {
            cleanDescription.isNotBlank() -> cleanDescription
            cleanName.isNotBlank() -> cleanName
            else -> "A PocketCraft Server"
        }.take(120)
    }

    private fun syncActiveWorldServerPresentation() {
        val props = ServerPropertiesHelper.readProperties(serverDir)
        val activeWorld = sanitizeWorldName(props.getProperty("level-name", config.worldName.ifBlank { "world" }))
        val worldDetails = readWorldServerDetails(props, activeWorld)
        props["motd"] = buildServerMotd(worldDetails.first, worldDetails.third)
        ServerPropertiesHelper.saveProperties(serverDir, props)
        writeServerIcon(worldDetails.second)
    }

    private fun writeServerIcon(photoUrl: String) {
        val target = File(serverDir, "server-icon.png")
        val cleaned = photoUrl.trim()
        if (cleaned.isBlank()) {
            // No custom photo — write the app icon as the default.
            writeAppIconAsServerIcon(target)
            return
        }

        val uri = Uri.parse(cleaned)
        val bitmap = openBitmap(uri) ?: run {
            writeAppIconAsServerIcon(target)
            return
        }

        val squareSize = minOf(bitmap.width, bitmap.height)
        val x = ((bitmap.width - squareSize) / 2).coerceAtLeast(0)
        val y = ((bitmap.height - squareSize) / 2).coerceAtLeast(0)
        val squareBitmap = Bitmap.createBitmap(bitmap, x, y, squareSize, squareSize)
        val scaledBitmap = Bitmap.createScaledBitmap(squareBitmap, 64, 64, true)

        target.outputStream().use { output ->
            scaledBitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }

        if (squareBitmap != bitmap) squareBitmap.recycle()
        if (scaledBitmap != squareBitmap) scaledBitmap.recycle()
        bitmap.recycle()
    }

    private fun writeAppIconAsServerIcon(target: File) {
        // Always overwrite so a fresh icon is in place even on first run.
        runCatching {
            val drawable = ContextCompat.getDrawable(appContext, com.pocketcraft.server.R.mipmap.ic_launcher)
                ?: return@runCatching
            val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            drawable.setBounds(0, 0, 64, 64)
            drawable.draw(canvas)
            target.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            bitmap.recycle()
        }
    }

    /** Reads the actual world seed from level.dat (NBT) after the world has been generated. */
    fun readActualWorldSeed(): Long? {
        val worldName = sanitizeWorldName(activeWorld.ifBlank { "world" })
        val levelDat = File(serverDir, "$worldName/level.dat")
        if (!levelDat.exists()) return null
        return runCatching {
            GZIPInputStream(levelDat.inputStream()).use { gzip ->
                val bytes = gzip.readBytes()
                parseRandomSeedFromNbt(bytes)
            }
        }.getOrNull()
    }

    private fun parseRandomSeedFromNbt(bytes: ByteArray): Long? {
        val marker = "RandomSeed".toByteArray(Charsets.UTF_8)
        for (i in 0 until bytes.size - marker.size - 8) {
            if (bytes.sliceArray(i until i + marker.size).contentEquals(marker)) {
                val offset = i + marker.size
                return ByteBuffer.wrap(bytes, offset, 8).long
            }
        }
        return null
    }

    /**
     * Writes optimization settings to bukkit.yml and paper-world-defaults.yml.
     * preset: "none", "lite", "balanced", "performance"
     */
    fun applyOptimizationPreset(preset: String) {
        scope.launch(Dispatchers.IO) {
            writeOptimizationPreset(preset)
        }
    }

    suspend fun applyOptimizationPresetBlocking(preset: String) = withContext(Dispatchers.IO) {
        writeOptimizationPreset(preset)
    }

    private fun writeOptimizationPreset(preset: String) {
        val bukkitFile = File(serverDir, "bukkit.yml")
        val paperWorldFile = File(serverDir, "config/paper-world-defaults.yml")
        paperWorldFile.parentFile?.mkdirs()

        // Read server.properties ONCE, apply all changes, save ONCE
        val props = ServerPropertiesHelper.readProperties(serverDir)
        props["pocketcraft-optimization-preset"] = preset

        when (preset) {
            "lite" -> {
                writeBukkitSpawnLimits(bukkitFile, monsters = 50, animals = 12, waterAnimals = 5, waterAmbient = 15, ambient = 10)
                writePaperWorldOptimization(paperWorldFile, entityActivation = true, eigenRedstone = true, crammingLimit = 16, props = props)
            }
            "balanced" -> {
                writeBukkitSpawnLimits(bukkitFile, monsters = 35, animals = 10, waterAnimals = 5, waterAmbient = 10, ambient = 5)
                writePaperWorldOptimization(paperWorldFile, entityActivation = true, eigenRedstone = true, crammingLimit = 8, props = props)
            }
            "performance" -> {
                writeBukkitSpawnLimits(bukkitFile, monsters = 20, animals = 8, waterAnimals = 3, waterAmbient = 5, ambient = 3)
                writePaperWorldOptimization(paperWorldFile, entityActivation = true, eigenRedstone = true, crammingLimit = 6, props = props)
            }
            else -> {
                writeBukkitSpawnLimits(bukkitFile, monsters = 70, animals = 15, waterAnimals = 5, waterAmbient = 20, ambient = 15)
                writePaperWorldOptimization(paperWorldFile, entityActivation = false, eigenRedstone = false, crammingLimit = 24, props = props)
            }
        }
        // Single write for all server.properties changes
        ServerPropertiesHelper.saveProperties(serverDir, props)
    }

    fun readOptimizationPreset(): String {
        val defaultPreset = when {
            totalRamGb <= 3 -> "performance"
            totalRamGb <= 5 -> "lite"
            else -> "none"
        }
        return ServerPropertiesHelper.readProperties(serverDir)
            .getProperty("pocketcraft-optimization-preset", defaultPreset)
    }

    private fun writeBukkitSpawnLimits(
        bukkitFile: File,
        monsters: Int,
        animals: Int,
        waterAnimals: Int,
        waterAmbient: Int,
        ambient: Int
    ) {
        val original = runCatching { bukkitFile.readText() }.getOrDefault("")
        var updated = original
        updated = ensureBukkitValue(updated, "spawn-limits", "monsters", monsters.toString())
        updated = ensureBukkitValue(updated, "spawn-limits", "animals", animals.toString())
        updated = ensureBukkitValue(updated, "spawn-limits", "water-animals", waterAnimals.toString())
        updated = ensureBukkitValue(updated, "spawn-limits", "water-ambient", waterAmbient.toString())
        updated = ensureBukkitValue(updated, "spawn-limits", "ambient", ambient.toString())
        if (updated != original) bukkitFile.writeText(updated)
    }

    private fun writePaperWorldOptimization(
        paperWorldFile: File,
        entityActivation: Boolean,
        eigenRedstone: Boolean,
        crammingLimit: Int,
        props: java.util.Properties
    ) {
        val original = runCatching { paperWorldFile.readText() }.getOrDefault("")
        var updated = original
        updated = ensurePaperWorldValue(updated, "entity-activation-range", "enabled", entityActivation.toString())
        updated = ensurePaperWorldValue(updated, "misc", "use-faster-eigencraft-redstone", eigenRedstone.toString())
        // Write cramming limit into the already-loaded props object — caller saves once
        props["max-entity-cramming"] = crammingLimit.toString()
        if (updated != original) paperWorldFile.writeText(updated)
    }

    private fun ensureBukkitValue(content: String, section: String, key: String, value: String): String {
        val lines = content.ifBlank { "" }.split('\n').toMutableList()
        if (lines.size == 1 && lines[0].isBlank()) lines.clear()
        // Find or create section (stricter top-level check)
        var sectionIdx = lines.indexOfFirst { it.trimEnd() == "$section:" }
        if (sectionIdx < 0) { lines.add("$section:"); sectionIdx = lines.lastIndex }
        // Find or update key within section
        val keyLine = "  $key:"
        val keyIdx = (sectionIdx + 1 until lines.size).firstOrNull { i ->
            lines[i].trimStart().startsWith("$key:") && lines[i].startsWith("  ")
        }
        if (keyIdx != null) {
            lines[keyIdx] = "  $key: $value"
        } else {
            lines.add(sectionIdx + 1, "  $key: $value")
        }
        return lines.joinToString("\n").trimEnd() + "\n"
    }

    private fun ensurePaperWorldValue(content: String, section: String, key: String, value: String): String {
        val lines = content.ifBlank { "" }.split('\n').toMutableList()
        if (lines.size == 1 && lines[0].isBlank()) lines.clear()
        var sectionIdx = lines.indexOfFirst { it.trimEnd() == "$section:" }
        if (sectionIdx < 0) { lines.add("$section:"); sectionIdx = lines.lastIndex }
        val keyIdx = (sectionIdx + 1 until lines.size).firstOrNull { i ->
            lines[i].trimStart().startsWith("$key:") && lines[i].startsWith("  ")
        }
        if (keyIdx != null) {
            lines[keyIdx] = "  $key: $value"
        } else {
            lines.add(sectionIdx + 1, "  $key: $value")
        }
        return lines.joinToString("\n").trimEnd() + "\n"
    }

    private fun openBitmap(uri: Uri): Bitmap? {
        return runCatching {
            when (uri.scheme?.lowercase(Locale.getDefault())) {
                "file" -> BitmapFactory.decodeFile(uri.path)
                else -> appContext.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
            }
        }.getOrNull()
    }

    private fun worldDisplayNameKey(worldName: String): String = "pocketcraft-world-display.${sanitizeWorldName(worldName)}"

    private fun worldPhotoKey(worldName: String): String = "pocketcraft-world-photo.${sanitizeWorldName(worldName)}"

    private fun worldDescriptionKey(worldName: String): String = "pocketcraft-world-description.${sanitizeWorldName(worldName)}"

    private fun readWorldServerDetails(properties: Properties, worldName: String): Triple<String, String, String> {
        val normalized = sanitizeWorldName(worldName).ifBlank { "world" }
        val displayName = properties.getProperty(worldDisplayNameKey(normalized), normalized).trim().ifBlank { normalized }
        val photoUrl = properties.getProperty(worldPhotoKey(normalized), "").trim()
        val description = properties.getProperty(worldDescriptionKey(normalized), DEFAULT_SERVER_DESCRIPTION)
            .trim()
            .ifBlank { DEFAULT_SERVER_DESCRIPTION }
        return Triple(displayName, photoUrl, description)
    }

    private fun applyNewWorldChunkTuning(worldName: String) {
        val normalized = sanitizeWorldName(worldName)
        val props = ServerPropertiesHelper.readProperties(serverDir)
        val active = sanitizeWorldName(props.getProperty("level-name", "world"))
        if (!active.equals(normalized, ignoreCase = true)) return

        val view = props.getProperty(ServerPropertiesHelper.DESIRED_VIEW_DISTANCE_KEY)
            ?.toIntOrNull()
            ?: props.getProperty("view-distance", adaptiveViewDistance().toString()).toIntOrNull()
            ?: adaptiveViewDistance()
        val simulation = props.getProperty(ServerPropertiesHelper.DESIRED_SIMULATION_DISTANCE_KEY)
            ?.toIntOrNull()
            ?: props.getProperty("simulation-distance", adaptiveSimulationDistance().toString()).toIntOrNull()
            ?: adaptiveSimulationDistance()

        props["view-distance"] = view.coerceIn(3, 32).toString()
        props["simulation-distance"] = simulation.coerceIn(3, 32).toString()
        props[ServerPropertiesHelper.DESIRED_VIEW_DISTANCE_KEY] = view.coerceIn(3, 32).toString()
        props[ServerPropertiesHelper.DESIRED_SIMULATION_DISTANCE_KEY] = simulation.coerceIn(3, 32).toString()
        props["sync-chunk-writes"] = "false"
        props["network-compression-threshold"] = ServerPropertiesHelper.RELAY_READY_COMPRESSION_THRESHOLD.toString()
        props["enforce-secure-profile"] = "false"
        ServerPropertiesHelper.saveProperties(serverDir, props)
    }

    private fun guessPhotoExtension(uri: Uri): String {
        val mime = appContext.contentResolver.getType(uri).orEmpty().lowercase(Locale.getDefault())
        return when {
            mime.contains("png") -> "png"
            mime.contains("webp") -> "webp"
            mime.contains("gif") -> "gif"
            mime.contains("heic") -> "heic"
            mime.contains("heif") -> "heif"
            uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase(Locale.getDefault()) in setOf("png", "webp", "gif", "heic", "heif", "jpg", "jpeg") ->
                uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase(Locale.getDefault()).orEmpty()
            else -> "jpg"
        }
    }

    private fun readKnownPlayers(worldName: String): List<PlayerInfo> {
        val now = System.currentTimeMillis()
        if (cachedKnownPlayersList.isNotEmpty() && (now - lastKnownPlayersScanTime) < 30_000L) {
            return cachedKnownPlayersList
        }
        val knownUuids = linkedSetOf<String>()
        val candidates = worldDirectoryCandidates(worldName)
        android.util.Log.i("ServerStateHolder", "readKnownPlayers: worldName=$worldName, candidates size=${candidates.size}")

        candidates.forEach { worldDir ->
            android.util.Log.i("ServerStateHolder", "readKnownPlayers: Checking candidate dir=${worldDir.absolutePath}")
            // Try stats (case-insensitive)
            val statsDir = worldDir.listFiles()?.find { it.isDirectory && it.name.equals("stats", ignoreCase = true) }
            val statsCount = statsDir?.listFiles()
                ?.filter { it.isFile && it.extension.equals("json", ignoreCase = true) }
                ?.mapTo(knownUuids) { it.nameWithoutExtension }
                ?.size ?: 0
            android.util.Log.i("ServerStateHolder", "readKnownPlayers: statsDir=${statsDir?.absolutePath}, found $statsCount jsons")

            // Try playerdata (case-insensitive)
            val pdDir = worldDir.listFiles()?.find { it.isDirectory && it.name.equals("playerdata", ignoreCase = true) }
            val pdCount = pdDir?.listFiles()
                ?.filter { it.isFile && it.extension.equals("dat", ignoreCase = true) }
                ?.mapTo(knownUuids) { it.nameWithoutExtension }
                ?.size ?: 0
            android.util.Log.i("ServerStateHolder", "readKnownPlayers: pdDir=${pdDir?.absolutePath}, found $pdCount dats")
        }

        // Use usercache.json ONLY for name resolution (includes Bedrock players via Geyser)
        val cachedData = loadUserCache()
        android.util.Log.i("ServerStateHolder", "readKnownPlayers: total unique UUIDs=${knownUuids.size}, cachedData size=${cachedData.size}")

        
        val opLookup = readNamedList("ops.json")
        val opUuids = opLookup.mapNotNull { it.uuid.takeIf(String::isNotBlank) }.toSet()
        val opNames = opLookup.map { it.name.lowercase(Locale.getDefault()) }.toSet()

        val result = knownUuids.map { uuid ->
            val resolvedName = cachedData[uuid] ?: uuid.take(8)
            PlayerInfo(
                name = resolvedName,
                uuid = uuid,
                pingMs = 0,
                isOp = uuid in opUuids || resolvedName.lowercase(Locale.getDefault()) in opNames
            )
        }
            .groupBy { canonicalPlayerName(it.name) }
            .values
            .map { group ->
                group.maxWithOrNull(
                    compareBy<PlayerInfo> { it.isOp }
                        .thenBy { it.uuid.isNotBlank() }
                        .thenByDescending { it.name.startsWith(".").not() }
                ) ?: group.first()
            }
            .sortedBy { it.name.lowercase(Locale.getDefault()) }
        cachedKnownPlayersList = result
        lastKnownPlayersScanTime = now
        return result
    }

    private fun MutableList<PlayerInfo>.replaceAllMatching(
        normalizedName: String,
        uuid: String,
        transform: (PlayerInfo) -> PlayerInfo
    ) {
        for (index in indices) {
            val player = this[index]
            val nameMatches = player.name.equals(normalizedName, ignoreCase = true)
            val uuidMatches = uuid.isNotBlank() && player.uuid == uuid
            if (nameMatches || uuidMatches) {
                this[index] = transform(player)
            }
        }
    }

    private fun escapeSelectorName(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun canonicalPlayerName(name: String): String =
        name.trim().trimStart('.', '!', '*').lowercase(Locale.getDefault())

    private fun sanitizeWifiPingSample(
        player: PlayerInfo,
        sample: ParsedPlayerPing,
        nextIp: String
    ): Int {
        if (sample.pingMs < 0) return -1
        if (sample.pingMs > MAX_REALISTIC_WIFI_PING_MS) {
            return player.pingMs.takeIf { it in 0..MAX_REALISTIC_WIFI_PING_MS } ?: -1
        }
        return sample.pingMs
    }

    private fun isPrivateWifiIp(value: String): Boolean {
        val host = value.trim()
        return host.startsWith("192.168.") ||
            host.startsWith("10.") ||
            Regex("""^172\.(1[6-9]|2\d|3[0-1])\.""").containsMatchIn(host)
    }

    private fun readNamedList(fileName: String): List<PlayerInfo> {
        val file = File(serverDir, fileName)
        if (!file.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(file.readText())
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val name = obj.optString("name").trim()
                    if (name.isBlank()) continue
                    add(
                        PlayerInfo(
                            name = name,
                            uuid = obj.optString("uuid").trim(),
                            pingMs = 0
                        )
                    )
                }
            }.sortedBy { it.name.lowercase(Locale.getDefault()) }
        }.getOrDefault(emptyList())
    }

    private fun mutateNamedList(
        fileName: String,
        transform: (List<NamedPlayerRecord>) -> List<NamedPlayerRecord>
    ): Result<Unit> {
        return runCatching {
            synchronized(namedListLock) {
                val file = File(serverDir, fileName)
                val current = if (file.exists()) {
                    runCatching {
                        val arr = JSONArray(file.readText())
                        buildList {
                            for (i in 0 until arr.length()) {
                                val obj = arr.optJSONObject(i) ?: continue
                                val name = obj.optString("name").trim()
                                if (name.isBlank()) continue
                                add(
                                    NamedPlayerRecord(
                                        name = name,
                                        uuid = obj.optString("uuid").trim(),
                                        extra = JSONObject(obj.toString()).apply {
                                            remove("name")
                                            remove("uuid")
                                        }
                                    )
                                )
                            }
                        }
                    }
                    .getOrDefault(emptyList())
                } else {
                    emptyList()
                }

                val result = transform(current).sortedBy { it.name.lowercase(Locale.getDefault()) }
                val arr = JSONArray()
                result.forEach { player ->
                    val obj = JSONObject(player.extra.toString())
                    obj.put("uuid", player.uuid)
                    obj.put("name", player.name)
                    arr.put(obj)
                }
                file.writeText(arr.toString(2))
            }
        }
    }

    private fun loadUserCache(): Map<String, String> {
        val file = File(serverDir, "usercache.json")
        if (!file.exists()) return emptyMap()
        return runCatching {
            val arr = JSONArray(file.readText())
            buildMap {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val uuid = obj.optString("uuid").trim()
                    val name = obj.optString("name").trim()
                    if (uuid.isNotBlank() && name.isNotBlank()) {
                        put(uuid, name)
                        put(name, uuid)
                    }
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun resolveLocalIp(): String {
        return ServerAddressResolver.getLocalIpAddress()
            ?: runCatching {
                NetworkInterface.getNetworkInterfaces().toList()
                    .asSequence()
                    .filter { it.isUp && !it.isLoopback }
                    .flatMap { it.inetAddresses.toList().asSequence() }
                    .firstOrNull { address ->
                        !address.isLoopbackAddress &&
                            address.hostAddress?.contains(':') == false &&
                            address.hostAddress?.startsWith("169.254.") == false
                    }
                    ?.hostAddress
            }.getOrNull().orEmpty().ifBlank { "127.0.0.1" }
    }

    private fun directorySize(file: File, depth: Int = 0): Long {
        if (!file.exists()) return 0L
        if (file.isFile) return file.length()
        if (depth > 3) return 0L
        val children = file.listFiles() ?: return 0L
        var total = 0L
        for (child in children) {
            total += if (child.isFile) child.length() else directorySize(child, depth + 1)
        }
        return total
    }

    private fun bytesToDisplayMb(bytes: Long): Long {
        if (bytes <= 0L) return 0L
        val mb = 1024L * 1024L
        return (bytes + mb - 1L) / mb
    }

    private fun worldDirectoryCandidates(worldName: String): List<File> {
        val wDir = ServerFileManager.getServerDirNoCreate(appContext, worldName)
        if (!wDir.exists()) return emptyList()

        val props = ServerPropertiesHelper.readProperties(wDir)
        val explicit = props.getProperty("level-name")?.trim().orEmpty().ifBlank { worldName }
        val sanitized = sanitizeWorldName(explicit)

        // Non-world directories inside the server dir that should not count toward world size
        val ignoredDirs = setOf(
            "logs", "plugins", "cache", "config", "libraries",
            "bundler", "versions", "crash-reports"
        )

        val candidates = mutableListOf<File>()

        // Always include the named world subfolders (overworld + dimensions)
        candidates.add(File(wDir, sanitized))
        candidates.add(File(wDir, "${sanitized}_nether"))
        candidates.add(File(wDir, "${sanitized}_the_end"))

        // If world data lives directly at serverDir root (flattened layout)
        if (File(wDir, "level.dat").exists() || File(wDir, "region").isDirectory) {
            val rootWorldFiles = setOf(
                "advancements", "data", "datapacks", "DIM-1", "DIM1", "entities",
                "level.dat", "level.dat_old", "playerdata", "poi", "region",
                "session.lock", "stats", "uid.dat"
            )
            rootWorldFiles.forEach { candidates.add(File(wDir, it)) }
        }

        // Include all top-level directories that look like world/dimension data
        // (e.g. world, world_nether, world_the_end) even if their name differs from sanitized
        wDir.listFiles()?.forEach { f ->
            if (f.isDirectory && f.name !in ignoredDirs) {
                if (directoryLooksLikeWorldDimension(f)) {
                    candidates.add(f)
                }
            }
        }
        
        // Final fallback: Deep search for ANY directory containing a level.dat, playerdata, or stats
        wDir.walkTopDown().maxDepth(3).filter { it.isDirectory }.forEach { dir ->
            if (File(dir, "level.dat").exists() || 
                File(dir, "playerdata").isDirectory || 
                File(dir, "stats").isDirectory) {
                candidates.add(dir)
            }
        }

        return candidates.filter { it.exists() }.distinctBy { it.absolutePath }
    }

    private fun resetWorldBaseNames(worldName: String, targetServerDir: File): Set<String> {
        val props = ServerPropertiesHelper.readProperties(targetServerDir, persistDefaults = false)
        val bases = linkedSetOf(sanitizeWorldName(worldName))

        props.getProperty("level-name")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { bases.add(sanitizeWorldName(it)) }

        val existingBase = bases.firstOrNull { base ->
            directoryLooksLikeWorldDimension(File(targetServerDir, base)) ||
                directoryLooksLikeWorldDimension(File(targetServerDir, "${base}_nether")) ||
                directoryLooksLikeWorldDimension(File(targetServerDir, "${base}_the_end"))
        }
        if (existingBase != null) {
            return bases.filter { it.isNotBlank() }.toSet()
        }

        val discoveredBases = targetServerDir.listFiles()
            .orEmpty()
            .asSequence()
            .filter { it.isDirectory && it.name.lowercase(Locale.getDefault()) !in serverSlotSystemFolderNames }
            .filter(::directoryLooksLikeWorldDimension)
            .mapNotNull { extractWorldBaseName(it.name) }
            .map(::sanitizeWorldName)
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase(Locale.getDefault()) }
            .toList()

        if (discoveredBases.size == 1) {
            bases.add(discoveredBases.single())
        }

        return bases.filter { it.isNotBlank() }.toSet()
    }

    private fun resetWorldDirectory(
        worldDir: File,
        allowedRoot: File,
        recoveryRoot: File,
        deletePlayerData: Boolean,
        deleteDatapacks: Boolean
    ): Boolean {
        if (!worldDir.isDirectory || !isPathInsideRoot(worldDir, allowedRoot)) return false

        val preservedNames = buildSet {
            if (!deletePlayerData) {
                add("playerdata")
                add("stats")
                add("advancements")
            }
            if (!deleteDatapacks) {
                add("datapacks")
            }
        }

        var movedAnything = false
        worldDir.listFiles().orEmpty().forEach { child ->
            val childName = child.name.lowercase(Locale.getDefault())
            if (childName !in preservedNames) {
                moveToResetRecovery(child, allowedRoot, recoveryRoot)
                    .also { movedAnything = movedAnything || it }
            }
        }
        return movedAnything
    }

    private fun resetFlatWorldEntries(
        targetServerDir: File,
        recoveryRoot: File,
        deletePlayerData: Boolean,
        deleteDatapacks: Boolean
    ): Boolean {
        val flatWorldEntryNames = linkedSetOf(
            "DIM-1",
            "DIM1",
            "data",
            "entities",
            "level.dat",
            "level.dat_old",
            "poi",
            "region",
            "session.lock",
            "uid.dat"
        )
        if (deletePlayerData) {
            flatWorldEntryNames.add("playerdata")
            flatWorldEntryNames.add("stats")
            flatWorldEntryNames.add("advancements")
        }
        if (deleteDatapacks) {
            flatWorldEntryNames.add("datapacks")
        }

        var movedAnything = false
        flatWorldEntryNames.forEach { name ->
            moveToResetRecovery(File(targetServerDir, name), targetServerDir, recoveryRoot)
                .also { movedAnything = movedAnything || it }
        }
        return movedAnything
    }

    private fun resetRecoveryRoot(worldName: String): File {
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        return File(
            File(appContext.filesDir, "reset_recovery"),
            "${sanitizeWorldName(worldName)}/$timestamp"
        ).also { it.mkdirs() }
    }

    private fun moveToResetRecovery(target: File, allowedRoot: File, recoveryRoot: File): Boolean {
        if (!target.exists()) return false
        if (!isPathInsideRoot(target, allowedRoot)) {
            android.util.Log.e("ServerStateHolder", "Blocked reset delete outside selected world slot: ${target.absolutePath}")
            return false
        }

        return runCatching {
            val root = allowedRoot.canonicalFile
            val source = target.canonicalFile
            if (source == root) return@runCatching false

            val relativePath = source.relativeTo(root).invariantSeparatorsPath
            val destination = uniqueRecoveryPath(File(recoveryRoot, relativePath))
            destination.parentFile?.mkdirs()

            if (source.renameTo(destination)) {
                return@runCatching true
            }

            val copied = if (source.isDirectory) {
                source.copyRecursively(destination, overwrite = false)
            } else {
                source.copyTo(destination, overwrite = false)
                true
            }
            if (!copied) {
                destination.deleteRecursively()
                return@runCatching false
            }

            val removed = if (source.isDirectory) source.deleteRecursively() else source.delete()
            if (!removed) {
                android.util.Log.w("ServerStateHolder", "Copied reset recovery but could not remove ${source.absolutePath}")
            }
            removed
        }.getOrElse { error ->
            android.util.Log.e("ServerStateHolder", "Failed moving reset target to recovery: ${target.absolutePath}", error)
            false
        }
    }

    private fun uniqueRecoveryPath(preferred: File): File {
        if (!preferred.exists()) return preferred
        val parent = preferred.parentFile ?: return preferred
        val baseName = preferred.nameWithoutExtension.ifBlank { preferred.name }
        val extension = preferred.extension.takeIf { it.isNotBlank() }?.let { ".$it" }.orEmpty()
        var index = 2
        while (true) {
            val candidate = File(parent, "$baseName-$index$extension")
            if (!candidate.exists()) return candidate
            index++
        }
    }

    private fun isPathInsideRoot(path: File, root: File): Boolean {
        val canonicalRoot = root.canonicalFile
        val canonicalPath = path.canonicalFile
        return canonicalPath != canonicalRoot &&
            canonicalPath.path.startsWith(canonicalRoot.path + File.separator)
    }

    private fun listWorldEntries(activeWorld: String): List<WorldEntry> {
        val active = activeWorld.ifBlank { "world" }
        val worldsBaseDir = File(appContext.filesDir, "servers/worlds").also { it.mkdirs() }

        val systemFolderNames = setOf(
            "plugins", "jre", "jre-21", "jre-runtime", "logs", "cache", "config", "libraries", 
            "binaries", "backups", "crash-reports", "bundler", "versions"
        )

        // Matches version strings like "1.21", "1.21.1", "1.8.9" — never valid world names
        val versionPattern = Regex("""^\d+\.\d+(\.\d+)?$""")

        val discovered = worldsBaseDir.listFiles()
            .orEmpty()
            .asSequence()
            .filter { it.isDirectory }
            .map { it.name }
            .filterNot { it.lowercase(Locale.getDefault()) in systemFolderNames }
            .filterNot { versionPattern.matches(it) }
            .toMutableSet()

        // Ensure active world is always recognized if it exists, or if no worlds exist yet
        if (File(worldsBaseDir, "world").exists()) {
            discovered.add("world")
        }
        if (File(worldsBaseDir, active).exists() || discovered.isEmpty()) {
            discovered.add(active)
        }

        return discovered
            .map { worldName ->
                val targetServerDir = ServerFileManager.getServerDirNoCreate(appContext, worldName)
                val targetProps = ServerPropertiesHelper.readProperties(targetServerDir, persistDefaults = false)
                val worldDetails = readWorldServerDetails(targetProps, worldName)
                WorldEntry(
                    name = worldName,
                    sizeMb = 0L,
                    isActive = worldName.equals(active, ignoreCase = true),
                    photoUrl = worldDetails.second
                )
            }
            .sortedWith(compareByDescending<WorldEntry> { it.isActive }.thenBy { it.name.lowercase(Locale.getDefault()) })
            .toList()
    }

    private fun directoryLooksLikeWorldDimension(directory: File): Boolean {
        if (!directory.isDirectory) return false
        val children = directory.listFiles() ?: return false
        return children.any { it.name.equals("level.dat", ignoreCase = true) } ||
               children.any { it.name.equals("region", ignoreCase = true) && it.isDirectory } ||
               children.any { it.name.equals("stats", ignoreCase = true) && it.isDirectory } ||
               children.any { it.name.equals("playerdata", ignoreCase = true) && it.isDirectory }
    }

    private fun extractWorldBaseName(directoryName: String): String? {
        val base = when {
            directoryName.endsWith("_nether") -> directoryName.removeSuffix("_nether")
            directoryName.endsWith("_the_end") -> directoryName.removeSuffix("_the_end")
            else -> directoryName
        }
        return base.takeIf { it.isNotBlank() }
    }

    private fun sanitizeWorldName(input: String): String {
        val cleaned = input.trim().replace(Regex("[^A-Za-z0-9_.-]"), "_")
        return cleaned.replace(Regex("_+"), "_")
            .replace(Regex("\\.+"), ".")
            .trim('_', '.')
            .ifBlank { "world" }
    }

    private fun ensureWorldDirectories(worldName: String) {
        val targetServerDir = ServerFileManager.getServerDir(appContext, worldName)
        val base = File(targetServerDir, sanitizeWorldName(worldName))
        val flatLayoutExists = File(targetServerDir, "level.dat").exists() ||
            File(targetServerDir, "region").isDirectory

        if (flatLayoutExists) {
            base.mkdirs()
            migrateFlatLayoutToNested(targetServerDir, base)
        }

        // Migration marker check: prevents repeating migration on subsequent boots
        val migrationMarker = File(targetServerDir, ".migration_done")
        if (migrationMarker.exists()) {
            return
        }

        // Migrate old nested default 'world' folder to the slot-named folder if it has real data
        val oldNestedDir = File(targetServerDir, "world")
        val oldNestedHasData = File(oldNestedDir, "level.dat").exists() || File(oldNestedDir, "region").isDirectory
        val targetName = sanitizeWorldName(worldName)

        if (oldNestedHasData && targetName != "world") {
            val baseHasData = File(base, "level.dat").exists() || File(base, "region").isDirectory
            var shouldOverwriteBase = !baseHasData
            
            if (baseHasData) {
                // If both exist, prioritize the old folder if the new one looks freshly generated (no playerdata/stats)
                val oldPlayerCount = File(oldNestedDir, "playerdata").listFiles()?.size ?: 0
                val newPlayerCount = File(base, "playerdata").listFiles()?.size ?: 0
                val oldStatsCount = File(oldNestedDir, "stats").listFiles()?.size ?: 0
                val newStatsCount = File(base, "stats").listFiles()?.size ?: 0

                // Also compare region chunks — a freshly-generated world has very few
                val oldRegionCount = File(oldNestedDir, "region").listFiles()?.size ?: 0
                val newRegionCount = File(base, "region").listFiles()?.size ?: 0

                // Prefer the old folder if it has significantly more players, stats, or region chunks.
                // Using > (not == 0) so even 1 fresh dat in the new folder doesn't block migration.
                if (oldPlayerCount > newPlayerCount || oldStatsCount > newStatsCount ||
                    (oldRegionCount > 0 && oldRegionCount > newRegionCount)) {
                    shouldOverwriteBase = true
                }
            }

            if (shouldOverwriteBase) {
                android.util.Log.w("PocketCraft", "Migrating old default nested 'world' folder to '$targetName'")
                if (base.exists()) {
                    base.deleteRecursively()
                }
                oldNestedDir.renameTo(base)

                // Nether dimension folder migration
                val oldNether = File(targetServerDir, "world_nether")
                val newNether = File(targetServerDir, "${targetName}_nether")
                if (oldNether.exists() && oldNether.isDirectory) {
                    if (newNether.exists()) newNether.deleteRecursively()
                    oldNether.renameTo(newNether)
                }

                // End dimension folder migration
                val oldEnd = File(targetServerDir, "world_the_end")
                val newEnd = File(targetServerDir, "${targetName}_the_end")
                if (oldEnd.exists() && oldEnd.isDirectory) {
                    if (newEnd.exists()) newEnd.deleteRecursively()
                    oldEnd.renameTo(newEnd)
                }
            }
        }

        if (!base.exists()) {
            base.mkdirs()
        }
        pluginProfileDir(worldName).mkdirs()
        modsProfileDir(worldName).mkdirs()
        resourcePacksProfileDir(worldName).mkdirs()

        // Mark migration as completed so it never runs again for this worldName
        runCatching {
            val marker = File(targetServerDir, ".migration_done")
            if (!marker.exists()) {
                marker.createNewFile()
                android.util.Log.i("PocketCraft", "Migration marker written for worldName: $worldName")
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

    private fun generateUniqueWorldName(requestedName: String, existingNames: List<String>): String {
        val normalized = sanitizeWorldName(requestedName).ifBlank { "world" }
        val taken = existingNames.map { sanitizeWorldName(it).lowercase(Locale.getDefault()) }.toSet()
        if (normalized.lowercase(Locale.getDefault()) !in taken &&
            worldDirectoryCandidates(normalized).none(File::exists)
        ) {
            return normalized
        }

        var suffix = 2
        while (true) {
            val candidate = "${normalized}_$suffix"
            if (candidate.lowercase(Locale.getDefault()) !in taken &&
                worldDirectoryCandidates(candidate).none(File::exists)
            ) {
                return candidate
            }
            suffix++
        }
    }

    private fun syncRegistriesAcrossAllWorlds(
        extraWorlds: Set<String> = emptySet(),
        worldsToRemove: Set<String> = emptySet(),
        completedToAdd: Set<String> = emptySet(),
        completedToRemove: Set<String> = emptySet()
    ) {
        val worldsBaseDir = File(appContext.filesDir, "servers/worlds").also { it.mkdirs() }
        val systemFolderNames = setOf(
            "plugins", "jre", "jre-21", "jre-runtime", "logs", "cache", "config", "libraries", 
            "binaries", "backups", "crash-reports", "bundler", "versions"
        )
        val allDirs = worldsBaseDir.listFiles()?.filter { 
            it.isDirectory && it.name.lowercase(Locale.getDefault()) !in systemFolderNames 
        }.orEmpty()

        val accumulatedWorlds = mutableSetOf<String>()
        accumulatedWorlds.addAll(extraWorlds.map(::sanitizeWorldName))
        accumulatedWorlds.addAll(allDirs.map { it.name })

        val accumulatedCompleted = mutableSetOf<String>()

        // Read current values from all properties
        allDirs.forEach { dir ->
            val p = ServerPropertiesHelper.readProperties(dir)
            accumulatedWorlds.addAll(readKnownWorldsFromProperties(p, dir.name))
            accumulatedCompleted.addAll(readWorldsWithCompletedSetup(p))
        }

        accumulatedWorlds.removeAll(worldsToRemove.map(::sanitizeWorldName).toSet())
        accumulatedCompleted.removeAll(worldsToRemove.map(::sanitizeWorldName).toSet())

        accumulatedCompleted.addAll(completedToAdd.map(::sanitizeWorldName))
        accumulatedCompleted.removeAll(completedToRemove.map(::sanitizeWorldName).toSet())

        val mergedWorldsStr = accumulatedWorlds
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase(Locale.getDefault()) }
            .sortedBy { it.lowercase(Locale.getDefault()) }
            .joinToString(",")

        val mergedCompletedStr = accumulatedCompleted
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase(Locale.getDefault()) }
            .sortedBy { it.lowercase(Locale.getDefault()) }
            .joinToString(",")

        allDirs.forEach { dir ->
            val p = ServerPropertiesHelper.readProperties(dir)
            p[worldRegistryKey] = mergedWorldsStr
            p[worldSetupRegistryKey] = mergedCompletedStr
            p["server-port"] = singleServerPort.toString()

            // Clean up deleted worlds metadata if any
            worldsToRemove.forEach { r ->
                val targetName = sanitizeWorldName(r)
                p.remove(worldDisplayNameKey(targetName))
                p.remove(worldPhotoKey(targetName))
                p.remove(worldDescriptionKey(targetName))
            }

            ServerPropertiesHelper.saveProperties(dir, p)
        }
    }

    private fun registerWorldNames(worldNames: Set<String>) {
        syncRegistriesAcrossAllWorlds(extraWorlds = worldNames)
    }

    fun importWorldDimension(uri: Uri, targetWorld: String) {
        if (isImportingWorld || isRunning || isStarting) return
        isImportingWorld = true
        importProgressPercent = 0f
        importProgressMessage = "Importing selected world file..."
        
        scope.launch {
            try {
                WorldImporter.importWorld(
                    context = context,
                    zipUri = uri,
                    serverType = config.serverType,
                    serverVersionId = versionId,
                    folderName = targetWorld,
                    onProgress = { progress, message ->
                        launch(Dispatchers.Main) {
                            importProgressPercent = (progress * 100).coerceIn(0f, 100f)
                            importProgressMessage = message
                        }
                    }
                )
                refreshAll()
            } catch (e: Exception) {
                android.util.Log.e("ServerStateHolder", "Failed to import dimension", e)
            } finally {
                isImportingWorld = false
                importProgressPercent = 0f
                importProgressMessage = ""
            }
        }
    }

    fun markActiveWorldSetupCompleted() {
        scope.launch(Dispatchers.IO) {
            val world = sanitizeWorldName(activeWorld.ifBlank { "world" })
            syncRegistriesAcrossAllWorlds(completedToAdd = setOf(world))
            withContext(Dispatchers.Main) {
                activeWorldNeedsSetup = false
            }
        }
    }

    private fun markWorldSetupPending(worldName: String) {
        val world = sanitizeWorldName(worldName)
        syncRegistriesAcrossAllWorlds(completedToRemove = setOf(world))
    }

    private fun readWorldsWithCompletedSetup(props: Properties): Set<String> {
        return props.getProperty(worldSetupRegistryKey, "")
            .split(',')
            .map(::sanitizeWorldName)
            .filter { it.isNotBlank() }
            .toSet()
    }

    private fun backupsDirForWorld(worldName: String): File {
        return File(backupsDir, sanitizeWorldName(worldName)).also { it.mkdirs() }
    }

    private fun exportedBackupsDirForWorld(worldName: String): File {
        val safeWorldName = sanitizeWorldName(worldName)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            File(
                android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
                "PocketCraftWorldBackups/$safeWorldName"
            )
        } else {
            File(
                android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
                "PocketCraftWorldBackups/$safeWorldName"
            )
        }
    }

    private fun listBackupsForWorld(worldName: String): List<BackupEntry> {
        val formatter = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault())
        val downloads = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
        val filesList = mutableListOf<File>()
        
        fun scanDir(dir: File, depth: Int) {
            if (depth > 3 || !dir.exists() || !dir.isDirectory) return
            val children = dir.listFiles() ?: return
            for (child in children) {
                if (child.isDirectory) {
                    scanDir(child, depth + 1)
                } else if (child.isFile && child.extension.equals("zip", ignoreCase = true)) {
                    val nameLower = child.name.lowercase(Locale.getDefault())
                    val parentName = child.parentFile?.name?.lowercase(Locale.getDefault()) ?: ""
                    val parentParentName = child.parentFile?.parentFile?.name?.lowercase(Locale.getDefault()) ?: ""
                    
                    val isPCFolder = parentName.contains("pocketcraft") || parentParentName.contains("pocketcraft") ||
                                     parentName == "world" || parentName == "main_world" || parentName == "modded" ||
                                     parentName == "alrigth" || parentName == "fabric_26_1_2" || parentName == "wwww"
                                     
                    val isPCFile = nameLower.contains("backup") || nameLower.contains("nether") || nameLower.contains("end") ||
                                   nameLower.contains("world") || nameLower.matches(Regex(".*\\d{8}-\\d{6}.*"))
                                   
                    if (isPCFolder || isPCFile) {
                        filesList.add(child)
                    }
                }
            }
        }
        
        val extFiles = appContext.getExternalFilesDir(null)
        val privateFiles = appContext.filesDir

        scanDir(downloads, 0)
        scanDir(backupsDir, 0)
        if (extFiles != null) {
            scanDir(extFiles, 0)
        }
        scanDir(privateFiles, 0)
        
        return filesList
            .distinctBy { runCatching { it.canonicalPath }.getOrDefault(it.absolutePath) }
            .sortedByDescending { it.lastModified() }
            .map { file ->
                BackupEntry(
                    name = file.nameWithoutExtension,
                    sizeMb = (file.length() / (1024L * 1024L)).coerceAtLeast(1L),
                    date = formatter.format(Date(file.lastModified())),
                    file = file
                )
            }
    }

    private fun restoreDimensionsFromLatestPreviousBackup(
        targetWorld: String,
        importedFileName: String
    ): String? {
        val latestPreviousBackup = latestPreviousBackupForWorld(targetWorld, importedFileName) ?: return null
        val tempRestoreDir = File(
            appContext.cacheDir,
            "dimension_restore_${sanitizeWorldName(targetWorld)}_${System.currentTimeMillis()}"
        )

        return try {
            tempRestoreDir.mkdirs()
            ZipFile(latestPreviousBackup.file).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    unzipEntry(tempRestoreDir, zip, entries.nextElement())
                }
            }
            WorldImporter.normalizeRestoredServerBackup(tempRestoreDir, config.serverType, targetWorld)

            val restoredDimensions = restoreDimensionsFromNormalizedBackupDir(tempRestoreDir, targetWorld)
            if (restoredDimensions.isEmpty()) {
                null
            } else {
                "Restored ${restoredDimensions.joinToString(" and ")} from previous backup ${latestPreviousBackup.file.name}."
            }
        } catch (e: Exception) {
            android.util.Log.w(
                "ServerStateHolder",
                "Failed to restore dimensions from previous backup ${latestPreviousBackup.file.name}",
                e
            )
            null
        } finally {
            tempRestoreDir.deleteRecursively()
        }
    }

    private fun latestPreviousBackupForWorld(worldName: String, importedFileName: String): BackupEntry? {
        val excludedNames = linkedSetOf(
            importedFileName.lowercase(Locale.getDefault()),
            importedFileName.substringBeforeLast('.').lowercase(Locale.getDefault())
        )
        val prefix = "${sanitizeWorldName(worldName).lowercase(Locale.getDefault())}-"
        return listBackupsForWorld(worldName).firstOrNull { entry ->
            val fileName = entry.file.name.lowercase(Locale.getDefault())
            val baseName = entry.file.nameWithoutExtension.lowercase(Locale.getDefault())
            val isSameWorld = fileName.startsWith(prefix) || entry.file.parentFile?.name?.lowercase(Locale.getDefault()) == sanitizeWorldName(worldName).lowercase(Locale.getDefault())
            isSameWorld && fileName !in excludedNames && baseName !in excludedNames
        }
    }

    private fun restoreDimensionsFromNormalizedBackupDir(sourceRoot: File, targetWorld: String): List<String> {
        val restored = mutableListOf<String>()
        val isVanillaStyle = config.serverType == ServerType.FABRIC || config.serverType == ServerType.MODPACK

        if (isVanillaStyle) {
            val sourceWorldRoot = File(sourceRoot, targetWorld)
            val targetWorldRoot = File(serverDir, targetWorld)
            // Only restore from previous backup if the dimension is genuinely missing from current restore
            if (!File(targetWorldRoot, "DIM-1").exists()) {
                if (replaceDirectoryIfPresent(File(sourceWorldRoot, "DIM-1"), File(targetWorldRoot, "DIM-1"))) {
                    restored += "the nether"
                }
            }
            if (!File(targetWorldRoot, "DIM1").exists()) {
                if (replaceDirectoryIfPresent(File(sourceWorldRoot, "DIM1"), File(targetWorldRoot, "DIM1"))) {
                    restored += "the end"
                }
            }
            return restored
        }

        // Only restore from previous backup if the dimension is genuinely missing from current restore
        if (!File(serverDir, "${targetWorld}_nether").exists()) {
            if (replaceDirectoryIfPresent(File(sourceRoot, "${targetWorld}_nether"), File(serverDir, "${targetWorld}_nether"))) {
                restored += "the nether"
            }
        }
        if (!File(serverDir, "${targetWorld}_the_end").exists()) {
            if (replaceDirectoryIfPresent(File(sourceRoot, "${targetWorld}_the_end"), File(serverDir, "${targetWorld}_the_end"))) {
                restored += "the end"
            }
        }
        return restored
    }

    private fun replaceDirectoryIfPresent(source: File, target: File): Boolean {
        if (!source.exists()) return false

        if (target.exists() && !target.deleteRecursively()) {
            android.util.Log.w("ServerStateHolder", "Could not clear dimension target ${target.absolutePath}")
        }
        target.parentFile?.mkdirs()

        return if (source.renameTo(target)) {
            true
        } else {
            runCatching {
                source.copyRecursively(target, overwrite = true)
                true
            }.getOrElse { error ->
                android.util.Log.w(
                    "ServerStateHolder",
                    "Failed to copy dimension ${source.absolutePath} -> ${target.absolutePath}",
                    error
                )
                false
            }
        }
    }

    private fun resolveImportedFileName(uri: Uri): String {
        val fallback = uri.lastPathSegment
            ?.substringAfterLast('/')
            ?.ifBlank { null }
            ?: "uploaded_backup.zip"
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
        val nameFromProvider = runCatching {
            appContext.contentResolver.query(uri, projection, null, null, null)?.use { cursor: Cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
            }
        }.getOrNull()
        return (nameFromProvider ?: fallback).replace(File.separatorChar, '_')
    }

    private fun queryContentLength(uri: Uri): Long {
        appContext.contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
            if (descriptor.length > 0L) return descriptor.length
        }
        val projection = arrayOf(OpenableColumns.SIZE)
        return runCatching {
            appContext.contentResolver.query(uri, projection, null, null, null)?.use { cursor: Cursor ->
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex != -1 && cursor.moveToFirst()) cursor.getLong(sizeIndex) else -1L
            } ?: -1L
        }.getOrDefault(-1L)
    }

    private fun uniqueBackupTarget(directory: File, preferredName: String): File {
        val sanitizedBaseName = preferredName.substringBeforeLast('.')
            .ifBlank { "backup" }
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
        val extension = preferredName.substringAfterLast('.', "zip")
        var candidate = File(directory, "$sanitizedBaseName.$extension")
        var index = 2
        while (candidate.exists()) {
            candidate = File(directory, "${sanitizedBaseName}_$index.$extension")
            index++
        }
        return candidate
    }

    private fun readKnownWorldsFromProperties(props: Properties, activeWorld: String): Set<String> {
        val fromProps = props.getProperty(worldRegistryKey, "")
            .split(',')
            .map { sanitizeWorldName(it) }
            .filter { it.isNotBlank() }
            .toMutableSet()
        val active = sanitizeWorldName(activeWorld)
        if (active.isNotBlank()) {
            fromProps.add(active)
        }
        return fromProps
    }

    private fun pluginProfileDir(worldName: String): File {
        val targetServerDir = ServerFileManager.getServerDirNoCreate(appContext, worldName)
        return File(File(targetServerDir, "world_plugin_profiles"), sanitizeWorldName(worldName))
    }

    private fun cloneWorldPluginProfile(fromWorld: String, toWorld: String) {
        if (fromWorld.equals(toWorld, ignoreCase = true)) return
        syncActiveWorldContentIntoProfile(fromWorld)
        copyDirectoryContents(
            pluginProfileDir(fromWorld).also { it.mkdirs() },
            pluginProfileDir(toWorld).also { it.mkdirs() },
            clearTarget = true,
            excludedTopLevelNames = setOf("mods", "resourcepacks")
        )
        copyDirectoryContents(modsProfileDir(fromWorld).also { it.mkdirs() }, modsProfileDir(toWorld).also { it.mkdirs() }, clearTarget = true)
        copyDirectoryContents(resourcePacksProfileDir(fromWorld).also { it.mkdirs() }, resourcePacksProfileDir(toWorld).also { it.mkdirs() }, clearTarget = true)
    }

    private fun initializeIsolatedWorldPluginProfile(worldName: String) {
        val pluginsProfile = pluginProfileDir(worldName).also { it.mkdirs() }
        pluginsProfile.listFiles().orEmpty().forEach { it.deleteRecursively() }
        modsProfileDir(worldName).mkdirs()
        resourcePacksProfileDir(worldName).mkdirs()
    }

    private fun syncWorldPluginProfiles(fromWorld: String, toWorld: String) {
        if (fromWorld.equals(toWorld, ignoreCase = true)) return
        syncActiveWorldContentIntoProfile(fromWorld)
        syncProfileIntoActiveWorldContent(toWorld)
    }

    private fun activePluginsDir(): File = File(serverDir, "plugins").also { it.mkdirs() }

    private fun activeModsDir(): File = File(serverDir, "mods").also { it.mkdirs() }

    private fun activeResourcePacksDir(): File = File(serverDir, "resourcepacks").also { it.mkdirs() }

    private fun modsProfileDir(worldName: String): File {
        val targetServerDir = ServerFileManager.getServerDirNoCreate(appContext, worldName)
        return File(File(targetServerDir, "world_mod_profiles"), sanitizeWorldName(worldName))
    }

    private fun resourcePacksProfileDir(worldName: String): File {
        val targetServerDir = ServerFileManager.getServerDirNoCreate(appContext, worldName)
        return File(File(targetServerDir, "world_resourcepack_profiles"), sanitizeWorldName(worldName))
    }

    private fun syncActiveWorldContentIntoProfile(worldName: String) {
        copyDirectoryContents(activePluginsDir(), pluginProfileDir(worldName).also { it.mkdirs() }, clearTarget = true)
        copyDirectoryContents(activeModsDir(), modsProfileDir(worldName).also { it.mkdirs() }, clearTarget = true)
        copyDirectoryContents(activeResourcePacksDir(), resourcePacksProfileDir(worldName).also { it.mkdirs() }, clearTarget = true)
    }

    private fun syncProfileIntoActiveWorldContent(worldName: String) {
        copyDirectoryContents(
            pluginProfileDir(worldName).also { it.mkdirs() },
            activePluginsDir(),
            clearTarget = true,
            excludedTopLevelNames = setOf("mods", "resourcepacks")
        )
        copyDirectoryContents(modsProfileDir(worldName).also { it.mkdirs() }, activeModsDir(), clearTarget = true)
        copyDirectoryContents(resourcePacksProfileDir(worldName).also { it.mkdirs() }, activeResourcePacksDir(), clearTarget = true)
    }

    private fun copyDirectoryContents(
        source: File,
        target: File,
        clearTarget: Boolean,
        excludedTopLevelNames: Set<String> = emptySet()
    ) {
        if (!target.exists()) target.mkdirs()
        if (clearTarget) {
            target.listFiles().orEmpty().forEach { it.deleteRecursively() }
        }
        if (!source.exists() || !source.isDirectory) return

        source.walkTopDown()
            .filter { it != source }
            .filter { src ->
                val relative = src.relativeTo(source)
                relative.path.substringBefore(File.separator, "").lowercase(Locale.getDefault()) !in excludedTopLevelNames
            }
            .forEach { src ->
                val relative = src.relativeTo(source)
                val dest = File(target, relative.path)
                if (src.isDirectory) {
                    dest.mkdirs()
                } else {
                    dest.parentFile?.mkdirs()
                    src.copyTo(dest, overwrite = true)
                }
            }
    }

    private fun collectBackupEntries(): List<BackupPathEntry> {
        scanCount = 0
        lastScanUpdateMillis = 0L
        val entries = mutableListOf<BackupPathEntry>()
        serverDir.listFiles()
            .orEmpty()
            .filter { it.name !in backupExcludeDirs }
            .forEach { child ->
                collectBackupEntries(child, entries)
            }
        return entries
    }

    private fun collectBackupEntries(file: File, entries: MutableList<BackupPathEntry>) {
        if (file.name in backupExcludeDirs || file.name.startsWith(".cache") || file.name.startsWith(".mixin")) return
        scanCount++
        val now = System.currentTimeMillis()
        if (now - lastScanUpdateMillis >= 150L) {
            lastScanUpdateMillis = now
            val count = scanCount
            scope.launch(Dispatchers.Main) {
                backupStatusMessage = "Scanning world files... ($count found)"
            }
        }

        val relativePath = file.relativeTo(serverDir).invariantSeparatorsPath
        if (file.isDirectory) {
            entries += BackupPathEntry(file = file, relativePath = "$relativePath/", isDirectory = true)
            file.listFiles()
                .orEmpty()
                .filter { it.name !in backupExcludeDirs && !it.name.startsWith(".cache") }
                .forEach { child ->
                    collectBackupEntries(child, entries)
                }
        } else if (file.isFile) {
            if (file.name.endsWith(".zip") || file.name.endsWith(".tmp") || file.name == "session.lock") return
            entries += BackupPathEntry(file = file, relativePath = relativePath, isDirectory = false)
        }
    }

    private fun clearServerDirectoryForRestore() {
        serverDir.listFiles()
            .orEmpty()
            .forEach { child ->
                child.deleteRecursively()
            }
    }

    private fun zipDirectory(source: File, prefix: String, zip: ZipOutputStream) {
        source.listFiles().orEmpty().forEach { child ->
            val childName = prefix + child.name
            if (child.isDirectory) {
                zip.putNextEntry(ZipEntry("$childName/"))
                zip.closeEntry()
                zipDirectory(child, "$childName/", zip)
            } else {
                addFileToZip(child, childName, zip)
            }
        }
    }

    private fun addFileToZip(file: File, entryName: String, zip: ZipOutputStream) {
        if (!file.exists() || !file.isFile) return
        if (file.name.endsWith(".zip") || file.name.endsWith(".tmp") || file.name == "session.lock") return
        runCatching {
            FileInputStream(file).use { input ->
                zip.putNextEntry(ZipEntry(entryName))
                input.copyTo(zip, bufferSize = 65536)
                zip.closeEntry()
            }
        }.onFailure { error ->
            android.util.Log.w("ServerStateHolder", "addFileToZip skipped ${file.name}: ${error.message}")
        }
    }

    private fun unzipEntry(targetRoot: File, zip: ZipFile, entry: ZipEntry) {
        val rawName = entry.name
        val normalizedName = rawName
            .replace('\\', '/')
            .removePrefix("/")
            .removePrefix("./")
            .trim()
        if (normalizedName.isBlank()) return
        if (normalizedName.startsWith("__MACOSX/")) return
        if (normalizedName.endsWith(".DS_Store")) return

        val target = File(targetRoot, normalizedName).canonicalFile
        if (!target.path.startsWith(targetRoot.canonicalPath)) return

        if (entry.isDirectory) {
            target.mkdirs()
            return
        }

        target.parentFile?.mkdirs()
        zip.getInputStream(entry).use { input ->
            FileOutputStream(target).use { output -> input.copyTo(output) }
        }
    }

    private fun isoNow(): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(Date())
    }

    private fun <T> replaceAll(target: MutableList<T>, incoming: List<T>) {
        target.clear()
        target.addAll(incoming)
    }

    private data class DashboardSnapshot(
        val config: ServerConfig,
        val localIp: String,
        val serverName: String,
        val serverPhotoUrl: String,
        val serverDescription: String,
        val worldSizeMb: Long,
        val worlds: List<WorldEntry>,
        val knownPlayers: List<PlayerInfo>,
        val whitelist: List<PlayerInfo>,
        val ops: List<PlayerInfo>,
        val banned: List<PlayerInfo>,
        val backups: List<BackupEntry>,
        val relayHost: String,
        val activeWorldNeedsSetup: Boolean,
        val bedrockBridgeEnabled: Boolean
    )

    private data class PersistedRuntimeState(
        val isStarting: Boolean = false,
        val isRunning: Boolean = false,
        val publicAddress: String? = null
    )

    private data class BackupPathEntry(
        val file: File,
        val relativePath: String,
        val isDirectory: Boolean
    )

    private data class NamedPlayerRecord(
        val name: String,
        val uuid: String = UUID.randomUUID().toString(),
        val extra: JSONObject = JSONObject()
    )

    private fun isAnyPlayerDeathLog(line: String): Boolean {
        val lower = line.lowercase()
        val deathHints = listOf(
            " was slain", " was shot", " was pummeled", " was squashed", " was killed",
            " fell ", " drowned", " burned", " blew up", " hit the ground too hard",
            " starved to death", " suffocated", " froze to death", " walked into danger", " died"
        )
        return deathHints.any { it in lower }
    }

    private fun extractDeathVictim(line: String): String? {
        val lower = line.lowercase()
        // Most death messages start with the player name.
        // We can check against onlinePlayers names.
        return onlinePlayers.firstOrNull { 
            lower.startsWith(it.name.lowercase()) 
        }?.name
    }

    fun changePlayerGamemode(player: PlayerInfo, mode: String) {
        val isOnline = onlinePlayers.any { it.name.equals(player.name, ignoreCase = true) }
        if (isOnline) {
            sendCommand("gamemode $mode ${player.name}")
        } else {
            // Offline - try to edit .dat file
            scope.launch(Dispatchers.IO) {
                val uuid = player.uuid
                if (uuid.isBlank()) {
                    withContext(Dispatchers.Main) {
                        appendLog("[PocketCraft] Cannot change offline gamemode: UUID unknown for ${player.name}")
                    }
                    return@launch
                }
                val modeInt = when(mode.lowercase()) {
                    "survival" -> 0
                    "creative" -> 1
                    "adventure" -> 2
                    "spectator" -> 3
                    else -> 0
                }
                val success = PlayerDataManager.updateOfflineGamemode(appContext, versionId, uuid, modeInt)
                withContext(Dispatchers.Main) {
                    if (success) {
                        appendLog("[PocketCraft] Changed offline gamemode for ${player.name} to $mode")
                        refreshAll()
                    } else {
                        appendLog("[PocketCraft] Failed to change offline gamemode for ${player.name}")
                    }
                }
            }
        }
    }

    private fun startPeriodicLocationPolling() {
        periodicLocationJob?.cancel()
        // Disabled: PocketCraftCompanion plugin streams live player position and ping telemetry
        // directly in console logs without needing periodic RCON polling.
    }



    private fun startPeriodicPingPolling() {
        periodicPingJob?.cancel()
        // PocketCraftCompanion plugin broadcasts live player ping and telemetry directly to the console.
    }

    private suspend fun applyPingUpdatesFromRcon() {
        if (!isRunning || isStopping) return
        val currentOnline = onlinePlayers.toList()
        if (currentOnline.isEmpty()) return

        for (player in currentOnline) {
            val target = """@a[name="${escapeSelectorName(player.name)}",limit=1]"""
            val pingResp = sendRconCommand("ping ${escapeSelectorName(player.name)}")
            var parsedPing = -1
            if (pingResp.isNotBlank() && !pingResp.startsWith("[RCON]")) {
                val pings = ConsoleParser.parsePing(pingResp)
                if (pings.isNotEmpty()) {
                    parsedPing = pings.values.firstOrNull()?.pingMs ?: -1
                } else {
                    parsedPing = ConsoleParser.parseFabricEntityLatency(pingResp)
                }
            }
            if (parsedPing < 0) {
                val latResp = sendRconCommand("data get entity $target latency")
                parsedPing = ConsoleParser.parseFabricEntityLatency(latResp)
            }

            if (parsedPing >= 0) {
                val finalPing = parsedPing
                withContext(Dispatchers.Main) {
                    onlinePlayers.replaceAllMatching(player.name, player.uuid) { p ->
                        p.copy(pingMs = finalPing)
                    }
                }
            }
        }
    }


    private fun stopPeriodicLocationPolling() {
        periodicLocationJob?.cancel()
        periodicLocationJob = null
        stopPeriodicPingPolling()
    }

    private fun stopPeriodicPingPolling() {
        periodicPingJob?.cancel()
        periodicPingJob = null
    }

    private fun flattenWorldStructure(specificWorld: String? = null) {
        val currentWorlds = runCatching { worlds.toList() }.getOrDefault(emptyList())
        val worldsToFix = if (specificWorld != null) listOf(specificWorld) else {
            (currentWorlds.map { it.name } + activeWorld).filter { it.isNotBlank() }.distinct()
        }

        worldsToFix.forEach { worldName ->
            val worldDir = File(serverDir, sanitizeWorldName(worldName))
            if (!worldDir.exists() || !worldDir.isDirectory) return@forEach

            // Find level.dat up to 3 levels deep (e.g. world/world/level.dat)
            val levelDat = worldDir.walkTopDown().maxDepth(4).find { it.name == "level.dat" } ?: return@forEach
            val realRoot = levelDat.parentFile ?: return@forEach

            if (realRoot.absolutePath != worldDir.absolutePath) {
                // Guard: if the worldDir root already has its own level.dat the structure is
                // already correct — skip flattening to avoid overwriting good restored content.
                if (File(worldDir, "level.dat").exists()) {
                    android.util.Log.i("PocketCraft", "Skipping flatten for $worldName — root level.dat already present")
                    return@forEach
                }

                android.util.Log.i("PocketCraft", "Auto-flattening nested world: ${realRoot.absolutePath} -> ${worldDir.absolutePath}")
                
                // 1. Move all contents up
                realRoot.listFiles()?.forEach { file ->
                    val target = File(worldDir, file.name)
                    if (target.exists()) target.deleteRecursively()
                    if (!file.renameTo(target)) {
                        file.copyTo(target, overwrite = true)
                        file.deleteRecursively()
                    }
                }

                // 2. Check for dimension siblings (e.g. world/world_nether)
                val parent = realRoot.parentFile
                if (parent != null && parent.absolutePath != worldDir.absolutePath && parent.absolutePath != serverDir.absolutePath) {
                    parent.listFiles()?.forEach { sibling ->
                        if (sibling.isDirectory && sibling != realRoot) {
                            // Check both flat (region/) and Paper-nested (DIM-1/, DIM1/) structures
                            if (File(sibling, "level.dat").exists() ||
                                File(sibling, "region").isDirectory ||
                                File(sibling, "DIM-1").isDirectory ||
                                File(sibling, "DIM1").isDirectory) {
                                val target = File(serverDir, sibling.name)
                                if (!target.exists()) {
                                    android.util.Log.i("PocketCraft", "Auto-migrating nested dimension: ${sibling.name}")
                                    sibling.renameTo(target)
                                }
                            }
                        }
                    }
                }

                // 3. Cleanup
                realRoot.delete()
                var current = realRoot.parentFile
                while (current != null && current.absolutePath != worldDir.absolutePath && current.listFiles()?.isEmpty() == true) {
                    val toDelete = current
                    current = current.parentFile
                    toDelete.delete()
                }
            }
        }
    }
}

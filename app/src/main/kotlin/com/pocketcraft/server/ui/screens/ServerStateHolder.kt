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
import com.pocketcraft.server.WorldImporter
import com.pocketcraft.server.data.model.PlayerInfo
import com.pocketcraft.server.data.model.ServerConfig
import com.pocketcraft.server.data.model.ServerType
import com.pocketcraft.server.data.preferences.AppPreferences
import com.pocketcraft.server.data.preferences.AppPreferencesStore
import com.pocketcraft.server.data.repository.ServerConfigRepository
import com.pocketcraft.server.notification.NotificationHelper
import com.pocketcraft.server.service.PluginManager
import com.pocketcraft.server.service.ConsoleParser
import com.pocketcraft.server.service.DimensionMigrator
import com.pocketcraft.server.service.ServerFileManager
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
    private val initialServerType: ServerType = ServerType.PAPER,
    val activeWorld: String = "world"
) {
    companion object {
        const val DEFAULT_SERVER_DESCRIPTION = "hosted on Pocketcraft"
        private const val POCKETCRAFT_JOIN_MESSAGE_TEXT =
            "hosted on Pocketcraft"
        private const val POCKETCRAFT_JOIN_MESSAGE_URL = "https://discord.gg/NGPzXFYp"
    }
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val serverDir = ServerFileManager.getServerDir(appContext, activeWorld)
    private val serverPhotosDir = File(serverDir, "server_photos").also { it.mkdirs() }
    private val backupsDir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "PocketCraft Server Backups").also { it.mkdirs() }
    private val logsQueue = ArrayDeque<String>(2000)
    private var receiverRegistered = false
    private var startedAtMillis: Long? = null
    private var startupStartedAtMillis: Long? = null
    private var startupProgressJob: Job? = null
    private var stopWatchdogJob: Job? = null
    private var restartFallbackJob: Job? = null
    private var periodicWorldSaveJob: Job? = null
    private var periodicLocationJob: Job? = null
    private var pendingRestart by mutableStateOf(false)
    private var hasAnnouncedServerOnline = false
    private val namedListLock = Any()
    private val worldRegistryKey = "pocketcraft-world-list"
    private val worldSetupRegistryKey = "pocketcraft-world-setup-list"
    private val singleServerPort = 25565
    private val stopWatchdogTimeoutMs = 15_000L
    private val restartFallbackDelayMs = 10_000L
    private val worldPluginProfilesDir = File(serverDir, "world_plugin_profiles").also { it.mkdirs() }
    private val totalRamGb by lazy {
        val manager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(info)
        (info.totalMem / (1024L * 1024L * 1024L)).toInt().coerceAtLeast(1)
    }

    var isRunning by mutableStateOf(false)
        private set
    var isStarting by mutableStateOf(false)
        private set
    var isRestartingCycle by mutableStateOf(false)
        private set
    private var lastStartRequestedMillis: Long = 0L
    var config by mutableStateOf(ServerConfig())
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
    var publicAddress by mutableStateOf<String?>(null)
        private set
    var tunnelConnecting by mutableStateOf(false)
        private set
    var tunnelError by mutableStateOf<String?>(null)
        private set
    var relayHost by mutableStateOf("play.pocketcraft.online")
        private set
    var relayFallbackActive by mutableStateOf(false)
        private set
    var isStopping by mutableStateOf(false)
        private set
    var isBackingUp by mutableStateOf(false)
        private set
    var backupProgressPercent by mutableStateOf(0)
        private set
    var backupStatusMessage by mutableStateOf("")
        private set
    var backupSaveLocation by mutableStateOf("")
        private set
    var activeWorldNeedsSetup by mutableStateOf(false)
        private set
    var isRestoringBackup by mutableStateOf(false)
        private set
    var restoreProgressPercent by mutableStateOf(0)
        private set
    var restoreStatusMessage by mutableStateOf("")
        private set
    var isDownloadingBackup by mutableStateOf(false)
        private set
    var downloadBackupProgressPercent by mutableStateOf(0)
        private set
    var downloadBackupStatusMessage by mutableStateOf("")
        private set
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
    var serverJoinable by mutableStateOf(false)
        private set
    val isServerFullyReady: Boolean get() = serverJoinable
    var movedTooQuicklyCount by mutableStateOf(0)
        private set
    private var lastMovedTooQuicklyMillis = 0L
    var isImportingWorld by mutableStateOf(false)
        private set
    var importProgressPercent by mutableStateOf(0f)
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

    private fun attemptTransitionToOnline() {
        if (!isStarting) return

        val bridgeEnabled = try { PluginManager.isBedrockBridgeEnabled(appContext, versionId) } catch (e: Exception) { false }
        val canTransition = if (bridgeEnabled) {
            isJavaServerDone && isGeyserDone
        } else {
            isJavaServerDone
        }

        if (canTransition) {
            markServerReady()
            markJoinable()
            bedrockBridgeEnabled = bridgeEnabled
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

            if (type == ServerHostService.EVENT_STOPPED || type == ServerHostService.EVENT_SERVER_CRASHED) {
                scope.launch {
                    val shouldRestart = type == ServerHostService.EVENT_STOPPED && pendingRestart
                    pendingRestart = false
                    restartFallbackJob?.cancel()
                    restartFallbackJob = null
                    stopStartupProgressTracking(reset = !shouldRestart)
                    isStopping = false
                    isStarting = false
                    isRunning = false
                    if (!shouldRestart) {
                        isRestartingCycle = false
                    }
                    stopPeriodicLocationPolling()
                    tps = 0f
                    if (!shouldRestart) {
                        publicAddress = null
                        tunnelConnecting = false
                        tunnelError = null
                        startedAtMillis = null
                        resetJoinable()
                    }
                    onlinePlayers.clear()
                    appendLog("[INFO] Server stopped.")
                    stopWatchdogJob?.cancel()
                    stopWatchdogJob = null
                    if (shouldRestart) {
                        isStarting = true
                        appendLog("[PocketCraft] Starting server again...")
                        delay(1500)
                        startServer(isRestart = true)
                    }
                }
                return
            }

            scope.launch {
                when (type) {
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
                        relayFallbackActive = intent.getBooleanExtra(ServerHostService.EXTRA_IS_FALLBACK, false)
                        if (!publicAddress.isNullOrBlank()) {
                            appendLog("[PocketCraft] Internet address: $publicAddress")
                            if (relayFallbackActive) {
                                appendLog("[PocketCraft] NOTE: India relay is offline. Falling back to Singapore for connection.")
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
                        appendLog("[WARN] ${tunnelError.orEmpty()}")
                        if (isStarting) {
                            attemptTransitionToOnline()
                        }
                    }
                    ServerHostService.EVENT_SERVER_CRASHED -> {
                        appendLog("[ERROR] Server process crashed (${line.ifBlank { "unknown" }}).")
                        FirebaseAnalyticsManager.logServerCrashed(versionId, line)
                        stopStartupProgressTracking(reset = true)
                        isStopping = false
                        pendingRestart = false
                        isStarting = false
                        isRunning = false
                        tps = 0f
                        resetJoinable()
                    }
                    ServerHostService.EVENT_ERROR -> {
                        appendLog("[ERROR] $line")
                        stopStartupProgressTracking(reset = true)
                        isStopping = false
                        pendingRestart = false
                        isStarting = false
                        isRunning = false
                        tps = 0f
                        resetJoinable()
                    }
                    ServerHostService.EVENT_STOPPED -> {
                        val shouldRestart = pendingRestart
                        pendingRestart = false
                        stopStartupProgressTracking(reset = !shouldRestart)
                        isStopping = false
                        isStarting = false
                        isRunning = false
                        tps = 0f
                        if (!shouldRestart) {
                            publicAddress = null
                            tunnelConnecting = false
                            tunnelError = null
                            startedAtMillis = null
                            resetJoinable()
                        }
                        onlinePlayers.clear()
                        appendLog(line)
                        stopWatchdogJob?.cancel()
                        stopWatchdogJob = null
                        if (shouldRestart) {
                            isStarting = true
                            appendLog("[PocketCraft] Starting server again...")
                            delay(1500)
                            startServer(isRestart = true)
                        }
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
        refreshAll()
        ensureBedrockBridgeProvisioned()
        
        scope.launch {
            AppPreferencesStore.isFirstBootCompleteFlow(appContext).collect { complete ->
                firstBootComplete = complete
            }
        }
    }

    private fun ensureBedrockBridgeProvisioned() {
        scope.launch(Dispatchers.IO) {
            try {
                PluginManager.ensureBedrockBridgePlugins(appContext, versionId)
                    .onSuccess {
                        PluginManager.enforceBedrockBridgeLocalConfig(appContext, versionId)
                    }
                    .onFailure { error ->
                        android.util.Log.w("ServerStateHolder", "Failed to provision Bedrock bridge: ${error.message}")
                    }
            } catch (e: Exception) {
                android.util.Log.e("ServerStateHolder", "Bedrock bridge provisioning crashed: ${e.message}", e)
            }
            withContext(Dispatchers.Main) {
                try {
                    bedrockBridgeEnabled = PluginManager.isBedrockBridgeEnabled(appContext, versionId)
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
            // Derive server status from runtime lifecycle only.
            // Player presence and address availability are independent UI concerns.
            isStarting -> ServerStatus.STARTING
            isRunning -> ServerStatus.ONLINE
            else -> ServerStatus.OFFLINE
        }

    val versionLabel: String
        get() = versionId

    val runtimeVersionLabel: String
        get() = config.gameVersion

    val isNavigationLocked: Boolean
        get() = isStarting || isRunning || isStopping

    val isRestarting: Boolean by derivedStateOf {
        isStopping && pendingRestart
    }

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
        }
    }

    var showEulaDialog by mutableStateOf(false)
        private set

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
                appendLog("[PocketCraft] EULA accepted. Press Start Server to continue.")
            }
        }
    }

    fun startServer(isRestart: Boolean = false) {
        if (versionId.isBlank()) {
            appendLog("[ERROR] No version selected. Please select a Minecraft version first.")
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
        // Ensure eula.txt is present in the active world dir (for new worlds or switched worlds)
        ServerFileManager.prepareEula(appContext, activeWorld)
        if (isRunning || (!isRestart && isStarting) || (isStopping && !isRestart)) return
        lastStartRequestedMillis = System.currentTimeMillis()
        stopWatchdogJob?.cancel()
        stopWatchdogJob = null
        pendingRestart = false
        isStopping = false
        isStarting = true
        isRunning = false
        areSpawnChunksLoaded = false
        isJavaServerDone = false
        isGeyserDone = false
        resetJoinable()
        tps = 4f
        startedAtMillis = System.currentTimeMillis()
        startupStartedAtMillis = System.currentTimeMillis()
        publicAddress = null
        tunnelConnecting = false
        tunnelError = null
        relayFallbackActive = false
        startupProgressPercent = 0
        startupStatusMessage = "Initializing..."
        hasAnnouncedServerOnline = false
        chunkyProgressPercent = null
        startStartupProgressTracking()
        clearLogs()
        onlinePlayers.clear()
        sessionPlayers.clear()
        appendLog("[PocketCraft] Booting Paper $versionId...")
        appendLog("[PocketCraft] Checking Bedrock bridge plugins...")

        scope.launch {
            val bridgeProvisionResult = withContext(Dispatchers.IO) {
                PluginManager.ensureBedrockBridgePlugins(appContext, versionId)
            }

            bridgeProvisionResult.onFailure { error ->
                appendLog("[PocketCraft] Bedrock bridge setup warning: ${error.message ?: "unknown error"}")
            }

            appendLog("[PocketCraft] Checking optimization plugins...")

            bedrockBridgeEnabled = withContext(Dispatchers.IO) {
                runCatching { PluginManager.isBedrockBridgeEnabled(appContext, versionId) }
                    .getOrDefault(false)
            }

            appendLog("[PocketCraft] Starting server - this may take 30-60 seconds...")
            
            withContext(Dispatchers.IO) {
                val activeWorld = sanitizeWorldName(config.worldName.ifBlank { "world" })
                PlayerDataManager.fixOfflineUuids(serverDir, activeWorld)
            }

            markActiveWorldSetupCompleted()
            FirebaseAnalyticsManager.logServerStarted(versionId, config.maxPlayers)
            ServerHostService.start(appContext, versionId, activeWorld)
            
            // Re-sync UI state once service starts
            delay(1000)
            refreshAll()
        }
    }

    fun stopServer() {
        if (isStopping || (!isRunning && !isStarting)) return
        pendingRestart = false
        restartFallbackJob?.cancel()
        restartFallbackJob = null
        isStopping = true
        appendLog("[PocketCraft] Stopping server...")
        val durationSeconds = startedAtMillis
            ?.let { ((System.currentTimeMillis() - it) / 1000L).coerceAtLeast(0L) }
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
        tunnelConnecting = true
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
        isStopping = true
        appendLog("[PocketCraft] Restart requested...")
        requestWorldSave(reason = "before restart")
        stopPeriodicWorldSave()
        stopPeriodicLocationPolling()
        startStopWatchdog()
        runCatching {
            ServerHostService.stop(appContext)
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
            delay(restartFallbackDelayMs)
            if (!pendingRestart) return@launch
            if (isRunning || isStarting) return@launch
            if (isServerProcessAlive()) return@launch

            pendingRestart = false
            isStopping = false
            isRestartingCycle = false
            appendLog("[PocketCraft] Starting server again...")
            startServer(isRestart = true)
        }
    }

    fun appendLog(line: String) {
        val cleanLine = ConsoleParser.stripAnsi(line).trimEnd()
        if (cleanLine.isBlank()) return
        
        // Debug: log important lines
        if (cleanLine.contains("Done", ignoreCase = true) || cleanLine.contains("Server port", ignoreCase = true)) {
            android.util.Log.d("ServerStateHolder", "appendLog received: $cleanLine")
        }

        // Track startup progress
        if (isStarting) {
            when {
                cleanLine.contains("Loading properties", ignoreCase = true) -> {
                    startupStatusMessage = "Loading properties..."
                }
                cleanLine.contains("Loading chunks", ignoreCase = true) -> {
                    startupStatusMessage = "Loading world..."
                }
                cleanLine.contains("Preparing spawn", ignoreCase = true) || cleanLine.contains("Preparing level", ignoreCase = true) -> {
                    startupStatusMessage = "Preparing spawn area..."
                }
                cleanLine.contains("Starting server", ignoreCase = true) -> {
                    startupStatusMessage = "Starting server..."
                }
                cleanLine.contains("Connecting tunnel", ignoreCase = true) ||
                    cleanLine.contains("Opening internet relay", ignoreCase = true) -> {
                    startupStatusMessage = "Opening internet relay..."
                }
            }
        }

        if (logsQueue.size >= 2000) {
            logsQueue.removeFirst()
            if (logs.isNotEmpty()) {
                logs.removeAt(0)
            }
        }
        logsQueue.addLast(cleanLine)
        logs.add(cleanLine)

        if (isLegacyRelayAuthError(cleanLine)) {
            val hint = "[PocketCraft] Legacy relay plugin auth failed (401). Disable/remove old Minekube/relay plugin from the server plugins folder."
            if (logsQueue.lastOrNull() != hint) {
                if (logsQueue.size >= 2000) {
                    logsQueue.removeFirst()
                    if (logs.isNotEmpty()) {
                        logs.removeAt(0)
                    }
                }
                logsQueue.addLast(hint)
                logs.add(hint)
            }
        }

        ConsoleParser.parsePing(cleanLine).takeIf { it.isNotEmpty() }?.let { pings ->
            onlinePlayers.replaceAll { player ->
                val newPing = pings[player.name] ?: pings[player.name.lowercase()]
                if (newPing != null) {
                    player.copy(pingMs = newPing)
                } else {
                    player
                }
            }
        }

        ConsoleParser.parseTps(cleanLine)?.let { parsedTps ->
            tps = parsedTps
        }

        ConsoleParser.parseJoin(cleanLine)?.let { (name, uuid) ->
            upsertOnlinePlayer(name = name, uuid = uuid)
            val sessionIdx = sessionPlayers.indexOfFirst { it.name.equals(name, ignoreCase = true) }
            val existingPlayer = onlinePlayers.find { it.name.equals(name, ignoreCase = true) }
            if (existingPlayer != null && sessionIdx < 0) {
                sessionPlayers.add(existingPlayer)
            }
            
            // Send branded welcome message
            scope.launch {
                delay(1500) // Ensure player is fully connected before sending message

                val shortName = serverName.take(12)
                val displayUrl = POCKETCRAFT_JOIN_MESSAGE_URL
                    .removePrefix("https://")
                    .removePrefix("http://")
                val firstLine = """{"text":"\n[","color":"gray"},{"text":"$shortName","color":"green","bold":true},{"text":"] ","color":"gray"},{"text":"$POCKETCRAFT_JOIN_MESSAGE_TEXT","color":"white"}"""
                val urlSection = """,{"text":"\n[","color":"gray"},{"text":"$shortName","color":"green","bold":true},{"text":"] ","color":"gray"},{"text":"Join our Discord using ","color":"white"},{"text":"$displayUrl","color":"aqua","underlined":true,"clickEvent":{"action":"open_url","value":"$POCKETCRAFT_JOIN_MESSAGE_URL"}},"""
                val spacerLine = """{"text":"\n \n","color":"white"}"""
                val tellrawArg = """[$firstLine$urlSection$spacerLine]"""
                val escapedName = escapeSelectorName(name)
                sendCommand("tellraw @a[name=\"$escapedName\"] $tellrawArg")
            }
        }
        ConsoleParser.parseLeave(cleanLine)?.let { name ->
            onlinePlayers.removeAll { it.name.equals(name, ignoreCase = true) }
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

        if (ConsoleParser.isDone(cleanLine) || cleanLine.contains("Done (", ignoreCase = true)) {
            areSpawnChunksLoaded = true
            isJavaServerDone = true
            if (isStarting) {
                attemptTransitionToOnline()
            }
        }

        if (cleanLine.contains("[Geyser-Spigot] Done (", ignoreCase = true) || 
            cleanLine.contains("Started Geyser on UDP port", ignoreCase = true)) {
            isGeyserDone = true
            if (isStarting) {
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

        if (cleanLine.contains("moved too quickly", ignoreCase = true)) {
            val now = System.currentTimeMillis()

            if (now - lastMovedTooQuicklyMillis > 10000) {
                movedTooQuicklyCount = 1
            } else {
                movedTooQuicklyCount++
            }
            lastMovedTooQuicklyMillis = now
            
            if (movedTooQuicklyCount >= 5) {
                appendLog("[PocketCraft] Detected severe movement lag (5+ events). Please consider reducing render distance.")
                movedTooQuicklyCount = 0
            }
        }
    }

    private fun markServerReady() {
        stopStartupProgressTracking(reset = false)
        isStarting = false
        isRestartingCycle = false
        isRunning = true
        startPeriodicWorldSave()
        startPeriodicLocationPolling()
        startupProgressPercent = 100
        startupStatusMessage = "Server ready!"
        if (tps <= 0f) tps = 20f
        if (startedAtMillis == null) startedAtMillis = System.currentTimeMillis()
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
        logsQueue.clear()
        logs.clear()
    }

    fun sendCommand(cmd: String) {
        val clean = cmd.trim()
        if (clean.isBlank()) return
        if (!isRunning) {
            appendLog("[RCON] Server is offline. Start the server before sending commands.")
            return
        }
        appendLog("> $clean")
        scope.launch(Dispatchers.IO) {
            try {
                val response = sendRconCommand(clean)
                if (response.isNotBlank()) {
                    withContext(Dispatchers.Main) { appendLog(response) }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    appendLog("[RCON] Failed to send command: ${e.message}")
                }
            }
        }
    }

    // Lightweight Source RCON client (RFC-compliant packet framing)
    fun sendRconCommand(command: String): String {
        val password = "pocketcraft-internal-rcon"
        val port = 25575
        return runCatching {
            Socket().use { socket ->
                socket.connect(java.net.InetSocketAddress("127.0.0.1", port), 3000)
                socket.soTimeout = 5000
                val out = java.io.DataOutputStream(socket.getOutputStream().buffered())
                val inp = java.io.DataInputStream(socket.getInputStream().buffered())

                fun sendPacket(id: Int, type: Int, payload: String) {
                    val payloadBytes = payload.toByteArray(Charsets.UTF_8)
                    val length = 4 + 4 + payloadBytes.size + 2  // id + type + payload + 2 null terminators
                    out.writeIntLE(length)
                    out.writeIntLE(id)
                    out.writeIntLE(type)
                    out.write(payloadBytes)
                    out.write(0)  // null terminator
                    out.write(0)  // padding
                    out.flush()
                }

                fun readPacket(): Triple<Int, Int, String> {
                    val length = inp.readIntLE()
                    val id = inp.readIntLE()
                    val type = inp.readIntLE()
                    val payloadLen = (length - 10).coerceAtLeast(0)
                    val payload = if (payloadLen > 0) ByteArray(payloadLen).also { inp.readFully(it) } else ByteArray(0)
                    inp.read()  // null terminator
                    inp.read()  // padding
                    return Triple(id, type, payload.toString(Charsets.UTF_8))
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
                is IOException -> "[RCON] Connection failed: ${error.message ?: error.javaClass.simpleName}"
                else -> "[RCON] Failed: ${error.message ?: error.javaClass.simpleName}"
            }
        }
    }

    fun sendRconCommands(commands: List<String>): List<String> {
        val password = "pocketcraft-internal-rcon"
        val port = 25575
        val results = mutableListOf<String>()
        return try {
            Socket().use { socket ->
                socket.connect(java.net.InetSocketAddress("127.0.0.1", port), 3000)
                socket.soTimeout = 5000
                val out = java.io.DataOutputStream(socket.getOutputStream().buffered())
                val inp = java.io.DataInputStream(socket.getInputStream().buffered())

                fun sendPacket(id: Int, type: Int, payload: String) {
                    val payloadBytes = payload.toByteArray(Charsets.UTF_8)
                    val length = 4 + 4 + payloadBytes.size + 2
                    out.writeIntLE(length)
                    out.writeIntLE(id)
                    out.writeIntLE(type)
                    out.write(payloadBytes)
                    out.write(0)
                    out.write(0)
                    out.flush()
                }

                fun readPacket(): Triple<Int, Int, String> {
                    val length = inp.readIntLE()
                    val id = inp.readIntLE()
                    val type = inp.readIntLE()
                    val payloadLen = (length - 10).coerceAtLeast(0)
                    val payload = if (payloadLen > 0) ByteArray(payloadLen).also { inp.readFully(it) } else ByteArray(0)
                    inp.read()
                    inp.read()
                    return Triple(id, type, payload.toString(Charsets.UTF_8))
                }

                // Auth
                sendPacket(1, 3, password)
                val (authId, _, _) = readPacket()
                if (authId == -1) {
                    return List(commands.size) { "[RCON] Authentication failed." }
                }

                for ((i, command) in commands.withIndex()) {
                    sendPacket(2 + i, 2, command)
                    val (_, _, response) = readPacket()
                    results.add(response.ifBlank { "[OK]" })
                }
                results
            }
        } catch (e: java.net.SocketTimeoutException) {
            List(commands.size) { "[RCON] Timed out waiting for response." }
        } catch (e: java.net.ConnectException) {
            List(commands.size) { "[RCON] Connection refused." }
        } catch (e: IOException) {
            List(commands.size) { "[RCON] Connection failed: ${e.message ?: e.javaClass.simpleName}" }
        }
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
        val mergedPlayer = PlayerInfo(
            name = normalizedName,
            uuid = normalizedUuid.ifBlank { existingPlayer?.uuid.orEmpty() },
            pingMs = existingPlayer?.pingMs ?: 0,
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
    }

    private fun applyPersistedRuntimeState(state: PersistedRuntimeState) {
        if (isStopping) {
            // Fallback: if service/runtime are already offline, unblock UI even if a stop event was missed.
            if (!state.isRunning && !state.isStarting) {
                isStopping = false
                pendingRestart = false
                isStarting = false
                isRunning = false
                stopPeriodicWorldSave()
                startedAtMillis = null
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

        if (!state.isStarting && isStarting && (System.currentTimeMillis() - lastStartRequestedMillis < 120000)) {
            // Keep isStarting = true during the 120s grace period to allow service to spawn and Paper to boot
        } else {
            isStarting = state.isStarting
        }
        isRunning = state.isRunning
        if (state.isRunning) {
            startPeriodicWorldSave()
            startPeriodicLocationPolling()
            markJoinable()
        } else if (!isStarting) {
            stopPeriodicWorldSave()
            stopPeriodicLocationPolling()
            resetJoinable()
        }
        if (isStarting && startupStartedAtMillis == null) {
            startupStartedAtMillis = System.currentTimeMillis()
            startStartupProgressTracking()
        } else if (!isStarting) {
            stopStartupProgressTracking(reset = false)
        }

        if (state.isRunning && tps <= 0f) {
            tps = 20f
        }

        if (state.isRunning && publicAddress.isNullOrBlank() && !state.publicAddress.isNullOrBlank()) {
            publicAddress = state.publicAddress
            tunnelError = null
        }

        if (!state.isRunning && !state.isStarting) {
            startedAtMillis = null
        } else if (startedAtMillis == null) {
            startedAtMillis = System.currentTimeMillis()
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
        
        if (rawState == ServerHostService.RUNTIME_STATE_RUNNING) {
            // Verify if the server is actually running by checking its port
            if (isServerPortOpen(config.port)) {
                return PersistedRuntimeState(isRunning = true)
            }
            // If the port is closed, double check the process just in case
            if (isServiceActive()) {
                return PersistedRuntimeState(isRunning = true)
            }
            // If both fail, it's a dead state
            ServerHostService.persistRuntimeState(appContext, versionId, config.worldName, ServerHostService.RUNTIME_STATE_OFFLINE)
            return PersistedRuntimeState()
        }
        
        if (rawState == ServerHostService.RUNTIME_STATE_STARTING) {
            // If starting, just check if the service process is alive
            if (isServiceActive()) {
                return PersistedRuntimeState(isStarting = true)
            }
            // Dead state
            ServerHostService.persistRuntimeState(appContext, versionId, config.worldName, ServerHostService.RUNTIME_STATE_OFFLINE)
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
            sendCommand("list")
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
            val error = mutateNamedList("whitelist.json") { list ->
                list + NamedPlayerRecord(
                    name = normalizedName,
                    uuid = "",
                    extra = JSONObject()
                )
            }.exceptionOrNull()

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

            val rconResult = runCatching {
                sendRconCommand("whitelist on")
                sendRconCommand("whitelist reload")
                null
            }.getOrElse { error ->
                error.message ?: "RCON unavailable"
            }

            withContext(Dispatchers.Main) {
                if (rconResult == null) {
                    appendLog("[PocketCraft] Whitelist enabled.")
                } else {
                    appendLog("[PocketCraft] Whitelist saved. It will fully apply once the server is ready. RCON error: $rconResult")
                }
            }
        }
    }

    suspend fun saveSettings(next: ServerConfig): String = withContext(Dispatchers.IO) {
        val enforced = next.copy(
            port = singleServerPort,
            maxPlayers = next.maxPlayers.coerceIn(1, 20),
            viewDistance = next.viewDistance.coerceIn(3, 32),
            simulationDistance = next.simulationDistance.coerceIn(3, 32)
        )
        saveConfig(enforced)
        runCatching {
            ServerConfigRepository(appContext).saveConfig(enforced)
        }.onFailure { error ->
            android.util.Log.w("ServerStateHolder", "Failed to sync saved settings: ${error.message}")
        }
        withContext(Dispatchers.Main) {
            config = enforced
        }
        "Settings saved."
    }

    suspend fun updateRelayHost(host: String): String = withContext(Dispatchers.IO) {
        if (isNavigationLocked) {
            return@withContext "Stop the server before changing relay location."
        }
        com.pocketcraft.server.data.preferences.AppPreferences(appContext).relayHost = host
        FirebaseAnalyticsManager.logSettingsChanged("relay_host", host)
        withContext(Dispatchers.Main) {
            relayHost = host
        }
        "Relay server location updated to ${if (host.contains("mine")) "Asia" else "Global"}."
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
        val props = ServerPropertiesHelper.readProperties(serverDir)
        props[worldDisplayNameKey(normalized)] = trimmedName
        props[worldPhotoKey(normalized)] = trimmedPhoto
        props[worldDescriptionKey(normalized)] = trimmedDescription
        val activeWorld = sanitizeWorldName(config.worldName.ifBlank { "world" })
        val activeMotd = if (activeWorld == normalized) buildServerMotd(trimmedName, trimmedDescription) else props.getProperty("motd", config.motd)
        if (activeWorld == normalized) {
            props["motd"] = activeMotd
        }
        ServerPropertiesHelper.saveProperties(serverDir, props)
        if (activeWorld == normalized) {
            writeServerIcon(trimmedPhoto)
        }

        withContext(Dispatchers.Main) {
            if (sanitizeWorldName(config.worldName) == normalized) {
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
        appContext.contentResolver.openInputStream(sourceUri)?.use { input ->
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
        } ?: return@withContext ""

        withContext(Dispatchers.Main) {
            onProgress(100)
        }

        Uri.fromFile(destination).toString()
    }

    suspend fun setActiveWorld(worldName: String, syncPluginProfiles: Boolean = true): String = withContext(Dispatchers.IO) {
        if (isRunning || isStarting || isStopping) {
            return@withContext "Stop the server before switching worlds."
        }

        val normalized = sanitizeWorldName(worldName)
        if (normalized.isBlank()) {
            return@withContext "Enter a valid world name."
        }

        val currentWorld = config.worldName.ifBlank { "world" }
        if (normalized == currentWorld) {
            return@withContext "${config.worldName} is already active."
        }

        ensureWorldDirectories(normalized)
        registerWorldNames(setOf(currentWorld, normalized))
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

        val next = config.copy(worldName = normalized, port = singleServerPort)
        saveConfig(next)

        // Ensure data is migrated for the new world
        PlayerDataManager.fixOfflineUuids(serverDir, normalized)

        withContext(Dispatchers.Main) {
            refreshAll()
        }
        "Active world switched to $normalized."
        }

    suspend fun createWorld(worldName: String): String = withContext(Dispatchers.IO) {
        if (isRunning || isStarting || isStopping) {
            return@withContext "Stop the server before creating a world."
        }

        val requestedName = sanitizeWorldName(worldName)
        if (requestedName.isBlank()) {
            return@withContext "Enter a valid world name."
        }

        val currentWorld = config.worldName.ifBlank { "world" }
        val existingNames = listWorldEntries(currentWorld).map { it.name }
        val normalized = generateUniqueWorldName(requestedName, existingNames)

        ensureWorldDirectories(normalized)
        registerWorldNames(setOf(currentWorld, normalized))
        markWorldSetupPending(normalized)

        runCatching {
            initializeIsolatedWorldPluginProfile(normalized)
        }.onFailure { error ->
            return@withContext "Failed preparing world plugins: ${error.message ?: "unknown error"}"
        }

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

        val knownWorlds = listWorldEntries(config.worldName).map { it.name }
        val match = knownWorlds.firstOrNull { it.equals(target, ignoreCase = true) }
            ?: return@withContext "$target was not found."

        val activeWorld = sanitizeWorldName(config.worldName.ifBlank { "world" })
        if ((isRunning || isStarting || isStopping) && activeWorld.equals(match, ignoreCase = true)) {
            return@withContext "Stop the server before deleting the active world."
        }
        val remainingWorlds = knownWorlds.filterNot { it.equals(match, ignoreCase = true) }
        val nextActive = if (activeWorld.equals(match, ignoreCase = true)) {
            remainingWorlds.firstOrNull()
        } else {
            activeWorld
        }

        if (activeWorld.equals(match, ignoreCase = true)) {
            if (nextActive != null) {
                runCatching {
                    syncWorldPluginProfiles(fromWorld = activeWorld, toWorld = nextActive)
                }.onFailure { error ->
                    return@withContext "Failed switching plugins before delete: ${error.message ?: "unknown error"}"
                }
            } else {
                val props = ServerPropertiesHelper.readProperties(serverDir)
                props["level-name"] = "world"
                ServerPropertiesHelper.saveProperties(serverDir, props)
            }
        }

        val deletedWorldData = worldDirectoryCandidates(match)
            .filter(File::exists)
            .onEach { it.deleteRecursively() }
            .isNotEmpty()
        pluginProfileDir(match).deleteRecursively()
        backupsDirForWorld(match).deleteRecursively()

        val props = ServerPropertiesHelper.readProperties(serverDir)
        val updatedWorlds = readKnownWorldsFromProperties(props, activeWorld)
            .filterNot { it.equals(match, ignoreCase = true) }
            .sortedBy { it.lowercase(Locale.getDefault()) }
        props[worldRegistryKey] = updatedWorlds.joinToString(",")
        props.remove(worldDisplayNameKey(match))
        props.remove(worldPhotoKey(match))
        props.remove(worldDescriptionKey(match))
        if (activeWorld.equals(match, ignoreCase = true)) {
            props["level-name"] = nextActive ?: "world"
        }
        ServerPropertiesHelper.saveProperties(serverDir, props)

        if (activeWorld.equals(match, ignoreCase = true)) {
            withContext(Dispatchers.Main) {
                val next = config.copy(worldName = nextActive ?: "world", port = singleServerPort)
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
            val activeWorld = sanitizeWorldName(config.worldName.ifBlank { "world" })
            syncActiveWorldContentIntoProfile(activeWorld)
            val worldFolders = worldDirectoryCandidates(config.worldName).filter(File::exists)
            if (worldFolders.isEmpty()) {
                return@withContext "No world folders found to back up."
            }

            withContext(Dispatchers.Main) {
                isBackingUp = true
                backupProgressPercent = 0
                backupStatusMessage = "Preparing backup..."
            }
            kotlinx.coroutines.delay(100)

            val backupName = buildString {
                append(config.worldName.ifBlank { "world" })
                append("-")
                append(SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()))
                append(".zip")
            }
            val backupFile = File(backupsDirForWorld(activeWorld), backupName)
            val entries = collectBackupEntries()
            val fileEntries = entries.filterNot { it.isDirectory }
            if (fileEntries.isEmpty()) {
                return@withContext "No server files found to back up."
            }

            ZipOutputStream(BufferedOutputStream(FileOutputStream(backupFile))).use { zip ->
                withContext(Dispatchers.Main) {
                    backupStatusMessage = "Backing up server directory..."
                    backupProgressPercent = 5
                }

                var processedFiles = 0
                entries.forEach { entry ->
                    withContext(Dispatchers.Main) {
                        val progress = 10 + ((processedFiles * 85) / fileEntries.size.coerceAtLeast(1))
                        backupProgressPercent = progress.coerceIn(10, 95)
                        // Don't show individual file names, just generic progress
                        if (entry.isDirectory) {
                            backupStatusMessage = "Preparing backup..."
                        } else {
                            backupStatusMessage = "Backing up..."
                        }
                    }

                    if (entry.isDirectory) {
                        zip.putNextEntry(ZipEntry(entry.relativePath))
                        zip.closeEntry()
                    } else {
                        // Exclude the profile cache directory to avoid doubling the backup size,
                        // as we already included the active plugins/mods/resourcepacks.
                        if (!entry.relativePath.startsWith("world_plugin_profiles/")) {
                            addFileToZip(entry.file, entry.relativePath, zip)
                            processedFiles++
                        }
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
                isBackingUp = false
                refreshAll()
            }
            kotlinx.coroutines.delay(500)
            FirebaseAnalyticsManager.logBackupCreated(config.worldName, backupFile.length())

            return@withContext "Backup created: $backupName\nIncluded: $includedText\nSaved to Downloads folder"
        } catch (e: Exception) {
            android.util.Log.e("ServerBackup", "Backup failed", e)
            withContext(Dispatchers.Main) {
                backupProgressPercent = 0
                backupStatusMessage = "Backup failed: ${e.javaClass.simpleName}"
                isBackingUp = false
            }
            return@withContext "Backup failed: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    suspend fun importBackup(uri: Uri): String = withContext(Dispatchers.IO) {
        if (isRunning || isStarting || isStopping || isBackingUp || isRestoringBackup || isDownloadingBackup) {
            return@withContext "Stop active backup or server actions before importing a backup."
        }

        val targetWorld = sanitizeWorldName(config.worldName.ifBlank { "world" })
        val tempFile = File(appContext.cacheDir, "backup_import_${System.currentTimeMillis()}.zip")

        try {
            val opened = appContext.contentResolver.openInputStream(uri)
                ?: return@withContext "Could not open the selected backup."
            opened.use { input ->
                FileOutputStream(tempFile).use { output -> input.copyTo(output) }
            }

            withContext(Dispatchers.Main) {
                isRestoringBackup = true
                restoreProgressPercent = 0
                restoreStatusMessage = "Preparing restore..."
                restoreProgressPercent = 5
            }

            ZipFile(tempFile).use { zip ->
                val allEntries = zip.entries().toList()
                if (allEntries.isEmpty()) {
                    return@withContext "Selected ZIP is empty."
                }

                clearServerDirectoryForRestore()
                // Give the OS a moment to fully release file handles after deletion
                delay(300)
                val totalEntries = allEntries.size
                var processedEntries = 0

                allEntries.forEach { zEntry ->
                    try {
                        unzipEntry(serverDir, zip, zEntry)
                        processedEntries++
                        val progress = (processedEntries * 95 / totalEntries).coerceIn(5, 95)
                        withContext(Dispatchers.Main) {
                            restoreProgressPercent = progress
                            restoreStatusMessage = "Restoring ${zEntry.name}"
                            if (progress % 10 == 0) delay(50)
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
            WorldImporter.normalizeRestoredServerBackup(serverDir, config.serverType)
            saveConfig(config.copy(worldName = targetWorld))
            flattenWorldStructure(targetWorld)
            DimensionMigrator.syncDimensionsForServerType(appContext, versionId, config.serverType)
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
            return@withContext "Backup imported into $targetWorld. Start the server to load it."
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
            val targetWorld = sanitizeWorldName(config.worldName.ifBlank { "world" })
            withContext(Dispatchers.Main) {
                isRestoringBackup = true
                restoreProgressPercent = 0
                restoreStatusMessage = "Preparing restore..."
                restoreProgressPercent = 5
            }

            java.util.zip.ZipFile(entry.file).use { zip ->
                val allEntries = zip.entries().toList()
                if (allEntries.isEmpty()) {
                    return@withContext "Backup is empty."
                }
                clearServerDirectoryForRestore()
                // Give the OS a moment to fully release file handles after deletion
                delay(300)
                val totalEntries = allEntries.size
                var processedEntries = 0

                allEntries.forEach { zEntry ->
                    try {
                        unzipEntry(serverDir, zip, zEntry)
                        processedEntries++
                        val progress = (processedEntries * 95 / totalEntries).coerceIn(5, 95)

                        withContext(Dispatchers.Main) {
                            restoreProgressPercent = progress
                            restoreStatusMessage = "Restoring ${zEntry.name}"
                            if (progress % 10 == 0) delay(50) // Small delay to allow UI updates
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
            WorldImporter.normalizeRestoredServerBackup(serverDir, config.serverType)
            saveConfig(config.copy(worldName = targetWorld))
            flattenWorldStructure(targetWorld)
            DimensionMigrator.syncDimensionsForServerType(appContext, versionId, config.serverType)
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
            FirebaseAnalyticsManager.logBackupRestored(config.worldName, entry.name)

            return@withContext "Backup restored successfully. Start the server to load it."
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

    suspend fun deleteBackup(entry: BackupEntry): String = withContext(Dispatchers.IO) {
        if (!entry.file.exists() || !entry.file.delete()) {
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
                worldName = config.worldName,
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
        if (isRunning || isStarting) {
            return@withContext "Stop the server before resetting the world."
        }
        val activeWorld = sanitizeWorldName(config.worldName.ifBlank { "world" })
        val worldTargets = linkedSetOf(
            "world",
            activeWorld
        ).filter { it.isNotBlank() }.distinct()

        var deletedAnything = false
        worldTargets.flatMap { base ->
            worldDirectoryCandidates(base)
        }.distinctBy { it.absolutePath }.forEach { target ->
            if (target.exists()) {
                target.deleteRecursively()
                deletedAnything = true
            }
        }

        if (deletePlayerData) {
            listOf(
                File(serverDir, "$activeWorld/playerdata"),
                File(serverDir, "$activeWorld/stats"),
                File(serverDir, "$activeWorld/advancements")
            ).forEach {
                if (it.exists()) {
                    it.deleteRecursively()
                    deletedAnything = true
                }
            }
        }

        if (deleteDatapacks) {
            val datapacks = File(serverDir, "$activeWorld/datapacks")
            if (datapacks.exists()) {
                datapacks.deleteRecursively()
                deletedAnything = true
            }
        }

        if (deleteLogs) {
            val logs = File(serverDir, "logs")
            if (logs.exists()) {
                logs.deleteRecursively()
                deletedAnything = true
            }
        }

        if (!deletedAnything) {
            return@withContext "Nothing selected for deletion."
        }
        withContext(Dispatchers.Main) { refreshAll() }
        "Selected world data deleted."
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
                    var bytes = input.read(buffer)
                    while (bytes != -1) {
                        output.write(buffer, 0, bytes)
                        copied += bytes
                        onProgress(((copied * 100L) / totalBytes).toInt().coerceIn(0, 100))
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
                var bytes = input.read(buffer)
                while (bytes != -1) {
                    output.write(buffer, 0, bytes)
                    copied += bytes
                    onProgress(((copied * 100L) / totalBytes).toInt().coerceIn(0, 100))
                    bytes = input.read(buffer)
                }
            }
        }
    }

    fun dispose() {
        stopPeriodicWorldSave()
        stopPeriodicLocationPolling()
        stopStartupProgressTracking(reset = false)
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
        val pid = ServerHostService.getServerPid(appContext)
        if (pid <= 0) return false
        return java.io.File("/proc/$pid").exists()
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
                    startedAtMillis = null
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
                startedAtMillis = null
                onlinePlayers.clear()
                ServerHostService.persistRuntimeState(appContext, versionId, activeWorld, ServerHostService.RUNTIME_STATE_OFFLINE)
                appendLog("[ERROR] Server stop timed out. Process may be hung.")
            }
        }
    }

    private fun startPeriodicWorldSave() {
        if (periodicWorldSaveJob?.isActive == true) return
        periodicWorldSaveJob = scope.launch(Dispatchers.IO) {
            val saveIntervalMs = when {
                totalRamGb <= 3 -> 8 * 60_000L
                totalRamGb <= 4 -> 6 * 60_000L
                else -> 4 * 60_000L
            }
            while (periodicWorldSaveJob?.isActive == true) {
                delay(saveIntervalMs)
                if (!isRunning || isStopping) continue
                runCatching {
                    sendRconCommand("save-all")
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
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        receiverRegistered = true
    }

    private fun startStartupProgressTracking() {
        startupProgressJob?.cancel()
        startupProgressJob = scope.launch {
            while (isStarting) {
                val elapsedMs = (System.currentTimeMillis() - (startupStartedAtMillis ?: System.currentTimeMillis())).coerceAtLeast(0L)
                val nextProgress = when {
                    elapsedMs < 8_000L -> ((elapsedMs / 8_000f) * 18f)
                    elapsedMs < 20_000L -> 18f + (((elapsedMs - 8_000L) / 12_000f) * 30f)
                    elapsedMs < 35_000L -> 48f + (((elapsedMs - 20_000L) / 15_000f) * 24f)
                    elapsedMs < 55_000L -> 72f + (((elapsedMs - 35_000L) / 20_000f) * 20f)
                    else -> 92f
                }.toInt().coerceIn(minOf(startupProgressPercent, 92), 92)

                startupProgressPercent = nextProgress
                if (startupStatusMessage.isBlank() || startupStatusMessage == "Initializing..." || startupStatusMessage == "Preparing server...") {
                    startupStatusMessage = naturalStartupStatus(elapsedMs)
                }
                delay(700)
            }
        }
    }

    private fun stopStartupProgressTracking(reset: Boolean) {
        startupProgressJob?.cancel()
        startupProgressJob = null
        startupStartedAtMillis = null
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

    private fun readSnapshot(): DashboardSnapshot {
        val loadedConfig = loadConfig()
        val properties = ServerPropertiesHelper.readProperties(serverDir)
        // activeWorldName in properties is the level-name, but we must use activeWorld (slot name) for file lookups
        val levelName = sanitizeWorldName(loadedConfig.worldName.ifBlank { "world" })
        val worldDetails = readWorldServerDetails(properties, levelName)
        
        val knownPlayers = readKnownPlayers(activeWorld)
        android.util.Log.i("ServerStateHolder", "readSnapshot: activeWorld=$activeWorld, knownPlayers size=${knownPlayers.size}")
        
        return DashboardSnapshot(
            config = loadedConfig,
            localIp = resolveLocalIp(),
            serverName = worldDetails.first.ifBlank { levelName },
            serverPhotoUrl = worldDetails.second,
            serverDescription = worldDetails.third,
            worldSizeMb = bytesToDisplayMb(worldDirectoryCandidates(activeWorld).sumOf(::directorySize)),
            worlds = listWorldEntries(activeWorld),
            knownPlayers = knownPlayers,
            whitelist = readNamedList("whitelist.json"),
            ops = readNamedList("ops.json"),
            banned = readNamedList("banned-players.json"),
            backups = listBackupsForWorld(activeWorld),
            relayHost = com.pocketcraft.server.data.preferences.AppPreferences(appContext).relayHost,
            activeWorldNeedsSetup = !readWorldsWithCompletedSetup(properties).contains(levelName),
            bedrockBridgeEnabled = PluginManager.isBedrockBridgeEnabled(appContext, versionId)
        )
    }

    private fun loadConfig(): ServerConfig {
        val props = ServerPropertiesHelper.readProperties(serverDir)
        var loaded = ServerConfig(
            worldName = props.getProperty("level-name", "world"),
            worldSeed = props.getProperty("level-seed", ""),
            maxPlayers = (props.getProperty("max-players", adaptiveMaxPlayers().toString()).toIntOrNull() ?: adaptiveMaxPlayers()).coerceIn(1, 20),
            port = singleServerPort,
            difficulty = props.getProperty("difficulty", "normal"),
            gameMode = props.getProperty("gamemode", "survival"),
            onlineMode = props.getProperty("online-mode", "false").toBoolean(),
            motd = props.getProperty("motd", "A PocketCraft Server"),
            pvp = props.getProperty("pvp", "true").toBoolean(),
            viewDistance = props.getProperty("view-distance", adaptiveViewDistance().toString()).toIntOrNull() ?: adaptiveViewDistance(),
            simulationDistance = props.getProperty("simulation-distance", adaptiveSimulationDistance().toString()).toIntOrNull() ?: adaptiveSimulationDistance(),
            spawnProtection = props.getProperty("spawn-protection", "16").toIntOrNull() ?: 16,
            allowFlight = props.getProperty("allow-flight", "false").toBoolean(),
            whiteList = props.getProperty("white-list", "false").toBoolean(),
            enforceWhitelist = props.getProperty("enforce-whitelist", "false").toBoolean(),
            commandBlocks = props.getProperty("enable-command-block", "true").toBoolean(),
            netherEnabled = props.getProperty("allow-nether", "true").toBoolean(),
            spawnMonsters = props.getProperty("spawn-monsters", "true").toBoolean(),
            spawnAnimals = props.getProperty("spawn-animals", "true").toBoolean(),
            spawnNpcs = props.getProperty("spawn-npcs", "true").toBoolean(),
            hardcore = props.getProperty("hardcore", "false").toBoolean(),
            maxRamMb = props.getProperty("pocketcraft-max-ram-mb", "1024").toIntOrNull() ?: 1024,
            generateStructures = props.getProperty("generate-structures", "true").toBoolean(),
            levelType = props.getProperty("level-type", "default"),
            serverType = ServerType.fromString(props.getProperty("pocketcraft-server-type") ?: initialServerType.name),
            gameVersion = props.getProperty("pocketcraft-game-version", versionId),
            customJarPath = props.getProperty("pocketcraft-custom-jar-path")?.takeIf { it.isNotBlank() }
        )
        return loaded.copy(
            joinMessageEnabled = true,
            joinMessageText = POCKETCRAFT_JOIN_MESSAGE_TEXT,
            joinMessageUrl = POCKETCRAFT_JOIN_MESSAGE_URL
        )
    }

    private fun saveConfig(config: ServerConfig) {
        val enforcedConfig = config.copy(
            maxPlayers = config.maxPlayers.coerceIn(1, 20),
            viewDistance = config.viewDistance.coerceIn(3, 32),
            simulationDistance = config.simulationDistance.coerceIn(3, 32)
        )
        val props = ServerPropertiesHelper.readProperties(serverDir)
        ServerPropertiesWriter.overlayManagedValues(props, ServerPropertiesWriter.toSnapshot(enforcedConfig))
        val knownWorlds = readKnownWorldsFromProperties(props, enforcedConfig.worldName) + sanitizeWorldName(enforcedConfig.worldName)
        props[worldRegistryKey] = knownWorlds.joinToString(",")
        ServerPropertiesHelper.saveProperties(serverDir, props)
    }

    private fun adaptiveViewDistance(): Int = 6

    private fun adaptiveSimulationDistance(): Int = 4

    private fun adaptiveMaxPlayers(): Int = if (totalRamGb >= 6) 20 else 10

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
        val worldName = sanitizeWorldName(config.worldName.ifBlank { "world" })
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
            val bukkitFile = File(serverDir, "bukkit.yml")
            val paperWorldFile = File(serverDir, "config/paper-world-defaults.yml")
            paperWorldFile.parentFile?.mkdirs()

            when (preset) {
                "lite" -> {
                    writeBukkitSpawnLimits(bukkitFile, monsters = 50, animals = 12, waterAnimals = 5, waterAmbient = 15, ambient = 10)
                    writePaperWorldOptimization(paperWorldFile, entityActivation = true, eigenRedstone = true, crammingLimit = 16)
                }
                "balanced" -> {
                    writeBukkitSpawnLimits(bukkitFile, monsters = 35, animals = 10, waterAnimals = 5, waterAmbient = 10, ambient = 5)
                    writePaperWorldOptimization(paperWorldFile, entityActivation = true, eigenRedstone = true, crammingLimit = 8)
                }
                "performance" -> {
                    writeBukkitSpawnLimits(bukkitFile, monsters = 20, animals = 8, waterAnimals = 3, waterAmbient = 5, ambient = 3)
                    writePaperWorldOptimization(paperWorldFile, entityActivation = true, eigenRedstone = true, crammingLimit = 6)
                }
                else -> {
                    // "none" — restore vanilla defaults
                    writeBukkitSpawnLimits(bukkitFile, monsters = 70, animals = 15, waterAnimals = 5, waterAmbient = 20, ambient = 15)
                    writePaperWorldOptimization(paperWorldFile, entityActivation = false, eigenRedstone = false, crammingLimit = 24)
                }
            }
            // Persist preset choice
            val props = ServerPropertiesHelper.readProperties(serverDir)
            props["pocketcraft-optimization-preset"] = preset
            ServerPropertiesHelper.saveProperties(serverDir, props)
        }
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
        crammingLimit: Int
    ) {
        val original = runCatching { paperWorldFile.readText() }.getOrDefault("")
        var updated = original
        updated = ensurePaperWorldValue(updated, "entity-activation-range", "enabled", entityActivation.toString())
        updated = ensurePaperWorldValue(updated, "misc", "use-faster-eigencraft-redstone", eigenRedstone.toString())
        // max-entity-cramming lives in server.properties
        val props = ServerPropertiesHelper.readProperties(serverDir)
        props["max-entity-cramming"] = crammingLimit.toString()
        ServerPropertiesHelper.saveProperties(serverDir, props)
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

        val view = props.getProperty("view-distance", adaptiveViewDistance().toString()).toIntOrNull()
            ?: adaptiveViewDistance()
        val simulation = props.getProperty("simulation-distance", adaptiveSimulationDistance().toString()).toIntOrNull()
            ?: adaptiveSimulationDistance()

        props["view-distance"] = view.coerceIn(3, 32).toString()
        props["simulation-distance"] = simulation.coerceIn(3, 32).toString()
        props["sync-chunk-writes"] = "false"
        props["network-compression-threshold"] = ServerPropertiesHelper.RELAY_READY_COMPRESSION_THRESHOLD.toString()
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

        return knownUuids.map { uuid ->
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

    private fun directorySize(file: File): Long {
        if (!file.exists()) return 0L
        if (file.isFile) return file.length()
        return file.listFiles().orEmpty().sumOf(::directorySize)
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
        val explicit = props.getProperty("level-name")?.trim().orEmpty().ifBlank { "world" }
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

    private fun listWorldEntries(activeWorld: String): List<WorldEntry> {
        val active = activeWorld.ifBlank { "world" }
        val ignoredDirectories = setOf(
            "logs",
            "plugins",
            "cache",
            "config",
            "libraries"
        )

        val discovered = serverDir.listFiles()
            .orEmpty()
            .asSequence()
            .filter { it.isDirectory }
            .filterNot { it.name in ignoredDirectories }
            .filter { directoryLooksLikeWorldDimension(it) }
            .mapNotNull { extractWorldBaseName(it.name) }
            .toMutableSet()

        val props = ServerPropertiesHelper.readProperties(serverDir)
        discovered.addAll(readKnownWorldsFromProperties(props, active))

        return discovered
            .asSequence()
            .map { worldName ->
                val worldDetails = readWorldServerDetails(props, worldName)
                WorldEntry(
                    name = worldName,
                    sizeMb = bytesToDisplayMb(worldDirectoryCandidates(worldName).sumOf(::directorySize)),
                    isActive = worldName == active,
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
        val cleaned = input.trim().replace(Regex("[^A-Za-z0-9_-]"), "_")
        return cleaned.replace(Regex("_+"), "_").trim('_').ifBlank { "world" }
    }

    private fun ensureWorldDirectories(worldName: String) {
        val base = File(serverDir, sanitizeWorldName(worldName))
        if (!base.exists()) {
            base.mkdirs()
        }
        pluginProfileDir(worldName).mkdirs()
        modsProfileDir(worldName).mkdirs()
        resourcePacksProfileDir(worldName).mkdirs()
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

    private fun registerWorldNames(worldNames: Set<String>) {
        val props = ServerPropertiesHelper.readProperties(serverDir)
        val merged = (readKnownWorldsFromProperties(props, config.worldName) + worldNames.map(::sanitizeWorldName))
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase(Locale.getDefault()) }
            .sortedBy { it.lowercase(Locale.getDefault()) }
        props[worldRegistryKey] = merged.joinToString(",")
        props["server-port"] = singleServerPort.toString()
        ServerPropertiesHelper.saveProperties(serverDir, props)
    }

    fun importWorldDimension(uri: Uri, targetWorld: String) {
        if (isImportingWorld || isRunning || isStarting) return
        isImportingWorld = true
        importProgressPercent = 0f
        
        scope.launch {
            try {
                WorldImporter.importWorld(
                    context = context,
                    zipUri = uri,
                    serverType = config.serverType,
                    serverVersionId = versionId,
                    folderName = targetWorld,
                    onProgress = { progress ->
                        importProgressPercent = (progress * 100).coerceIn(0f, 100f)
                    }
                )
                refreshAll()
            } catch (e: Exception) {
                android.util.Log.e("ServerStateHolder", "Failed to import dimension", e)
            } finally {
                isImportingWorld = false
                importProgressPercent = 0f
            }
        }
    }

    fun markActiveWorldSetupCompleted() {
        scope.launch(Dispatchers.IO) {
            val props = ServerPropertiesHelper.readProperties(serverDir)
            val world = sanitizeWorldName(config.worldName.ifBlank { "world" })
            val completed = (readWorldsWithCompletedSetup(props) + world)
                .distinctBy { it.lowercase(Locale.getDefault()) }
                .sortedBy { it.lowercase(Locale.getDefault()) }
            props[worldSetupRegistryKey] = completed.joinToString(",")
            ServerPropertiesHelper.saveProperties(serverDir, props)
            withContext(Dispatchers.Main) {
                activeWorldNeedsSetup = false
            }
        }
    }

    private fun markWorldSetupPending(worldName: String) {
        val props = ServerPropertiesHelper.readProperties(serverDir)
        val world = sanitizeWorldName(worldName)
        val next = readWorldsWithCompletedSetup(props)
            .filterNot { it.equals(world, ignoreCase = true) }
            .sortedBy { it.lowercase(Locale.getDefault()) }
        props[worldSetupRegistryKey] = next.joinToString(",")
        ServerPropertiesHelper.saveProperties(serverDir, props)
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
        val merged = linkedMapOf<String, File>()
        listOf(backupsDirForWorld(worldName), exportedBackupsDirForWorld(worldName)).forEach { dir ->
            dir.listFiles()
                .orEmpty()
                .filter { it.isFile && it.extension.equals("zip", ignoreCase = true) }
                .sortedByDescending { it.lastModified() }
                .forEach { file ->
                    val key = file.name.lowercase(Locale.getDefault())
                    val existing = merged[key]
                    if (existing == null || existing.parentFile == exportedBackupsDirForWorld(worldName)) {
                        merged[key] = file
                    }
                }
        }
        return merged.values
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
        return fromProps
    }

    private fun pluginProfileDir(worldName: String): File {
        return File(worldPluginProfilesDir, sanitizeWorldName(worldName))
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
        return File(pluginProfileDir(worldName), "mods")
    }

    private fun resourcePacksProfileDir(worldName: String): File {
        return File(pluginProfileDir(worldName), "resourcepacks")
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
        val entries = mutableListOf<BackupPathEntry>()
        serverDir.listFiles()
            .orEmpty()
            .sortedBy { it.name.lowercase(Locale.getDefault()) }
            .forEach { child ->
                collectBackupEntries(child, entries)
            }
        return entries
    }

    private fun collectBackupEntries(file: File, entries: MutableList<BackupPathEntry>) {
        val relativePath = file.relativeTo(serverDir).invariantSeparatorsPath
        if (file.isDirectory) {
            entries += BackupPathEntry(file = file, relativePath = "$relativePath/", isDirectory = true)
            file.listFiles()
                .orEmpty()
                .sortedBy { it.name.lowercase(Locale.getDefault()) }
                .forEach { child ->
                    collectBackupEntries(child, entries)
                }
        } else if (file.isFile) {
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
        FileInputStream(file).use { input ->
            zip.putNextEntry(ZipEntry(entryName))
            input.copyTo(zip)
            zip.closeEntry()
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
        periodicLocationJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(60_000)
                if (!isRunning || isStopping) continue
                onlinePlayers.toList().forEach { player ->
                    val target = """@a[name="${escapeSelectorName(player.name)}",limit=1]"""
                    runCatching { sendRconCommand("data get entity $target Pos") }
                        .onFailure { error ->
                            android.util.Log.w("ServerStateHolder", "RCON position poll failed: ${error.message}")
                        }
                    delay(200)
                    runCatching { sendRconCommand("data get entity $target Dimension") }
                        .onFailure { error ->
                            android.util.Log.w("ServerStateHolder", "RCON dimension poll failed: ${error.message}")
                        }
                    delay(200)
                }
            }
        }
    }

    private fun stopPeriodicLocationPolling() {
        periodicLocationJob?.cancel()
        periodicLocationJob = null
    }

    private fun flattenWorldStructure(specificWorld: String? = null) {
        val worldsToFix = if (specificWorld != null) listOf(specificWorld) else {
            (worlds.map { it.name } + config.worldName).filter { it.isNotBlank() }.distinct()
        }

        worldsToFix.forEach { worldName ->
            val worldDir = File(serverDir, sanitizeWorldName(worldName))
            if (!worldDir.exists() || !worldDir.isDirectory) return@forEach

            // Find level.dat up to 3 levels deep (e.g. world/world/level.dat)
            val levelDat = worldDir.walkTopDown().maxDepth(4).find { it.name == "level.dat" } ?: return@forEach
            val realRoot = levelDat.parentFile ?: return@forEach

            if (realRoot.absolutePath != worldDir.absolutePath) {
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
                            if (File(sibling, "level.dat").exists() || File(sibling, "region").isDirectory) {
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

package com.pocketcraft.server.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
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
import com.pocketcraft.server.service.ConsoleParser
import com.pocketcraft.server.service.ServerFileManager
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ServerHostService : Service() {

    private var currentVersionId: String? = null
    private var serverProcess: java.lang.Process? = null
    private var isLaunching = false
    private var logcatThread: Thread? = null
    private var logTailThread: Thread? = null
    private var portProbeThread: Thread? = null
    private val logcatRunning = AtomicBoolean(false)
    private val logTailRunning = AtomicBoolean(false)
    private val portProbeRunning = AtomicBoolean(false)
    private val tunnelStarted = AtomicBoolean(false)
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
    private var lastNotificationText: String = ""
    private var lastNotificationUpdateMs: Long = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val versionId = intent?.getStringExtra(EXTRA_VERSION_ID)
        if (intent?.action == ACTION_STOP) {
            stopReason = "user"
            autoRecoverAttempts = 0
            autoRecoverWindowStartMs = 0L
            currentVersionId?.let {
                sendEvent(it, EVENT_OUTPUT, "[PocketCraft] Stop requested.")
            }
            stopServer()
            return START_NOT_STICKY
        }


        if (intent?.action != ACTION_START || versionId.isNullOrBlank()) {
            return START_NOT_STICKY
        }

        if (isLaunching) {
            sendEvent(versionId, EVENT_OUTPUT, "[PocketCraft] Server is already starting.")
            return START_STICKY
        }

        isLaunching = true
        currentVersionId = versionId
        stopReason = "unknown"
        persistRuntimeState(applicationContext, versionId, RUNTIME_STATE_STARTING)
        resetNotificationState("Starting $versionId")
        startForeground(NOTIFICATION_ID, createForegroundNotification("Starting $versionId"))
        startLogcatBridge(versionId)
        startServerLogTail(versionId)
        val serverPort = resolveServerPort(versionId)
        currentServerPort = serverPort
        startPortProbe(versionId, serverPort)
        acquireWakeLock()

        ServerLauncher(applicationContext).startServer(
            versionId = versionId,
            onOutput = { line ->
                sendEvent(versionId, EVENT_OUTPUT, line)
                updateNotification(line)
                if (looksLikeServerReady(line)) {
                    onServerReady()
                }
            },
            onError = { line ->
                sendEvent(versionId, EVENT_ERROR, line)
                updateNotification("Server error", force = true)
                // During an explicit user stop, shutdown noise should not be treated as a crash.
                if (!stopInProgress.get() && stopReason != "user") {
                    stopReason = if (line.contains("outofmemory", ignoreCase = true) || line.contains("oom", ignoreCase = true)) {
                        "oom"
                    } else {
                        "crash"
                    }
                }
            },
            onStopped = { exitCode ->
                isLaunching = false
                stopInProgress.set(false)
                relayJob?.cancel()
                relayJob = null
                serviceScope.launch(Dispatchers.IO) {
                    runCatching { relayManager.unregister() }
                }
                tunnelStarted.set(false)
                stopLogcatBridge()
                stopServerLogTail()
                stopPortProbe()

                if (exitCode != 0 && stopReason != "user") {
                    sendEvent(versionId, EVENT_SERVER_CRASHED, "exit_code=$exitCode")
                    sendEvent(versionId, EVENT_ERROR, "[PocketCraft] Server exited unexpectedly (code $exitCode).")
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
                            start(applicationContext, versionId)
                        }
                    }
                }

                persistPublicAddress(applicationContext, "")
                persistRuntimeState(applicationContext, versionId, RUNTIME_STATE_OFFLINE)
                releaseWakeLock()
                if (shouldAutoRecover) {
                    updateNotification("Recovering server...", force = true)
                } else {
                    updateNotification("Server stopped", force = true)
                }
            }
        )

        return START_STICKY
    }

    override fun onDestroy() {
        persistPublicAddress(applicationContext, "")
        currentVersionId?.let { persistRuntimeState(applicationContext, it, RUNTIME_STATE_OFFLINE) }
        if (!stopInProgress.get() && (isLaunching || relayJob != null || serverProcess != null)) {
            stopReason = "user"
            stopServer()
        }
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
        CoroutineScope(Dispatchers.IO).launch {
            try {
                serverProcess?.outputStream?.let {
                    it.write("stop\n".toByteArray())
                    it.flush()
                }

                val proc = serverProcess
                if (proc != null) {
                    launch { proc.inputStream.copyTo(OutputStream.nullOutputStream()) }
                    launch { proc.errorStream.copyTo(OutputStream.nullOutputStream()) }
                    val exited = proc.waitFor(10, TimeUnit.SECONDS)
                    if (!exited) {
                        android.util.Log.w("PocketCraft", "Server did not stop in 10s, forcing kill")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("PocketCraft", "Error during stop: ${e.message}")
            } finally {
                serverProcess?.destroyForcibly()
                serverProcess = null
                runCatching { relayManager.unregister() }
                relayJob?.cancel()
                relayJob = null
                tunnelStarted.set(false)
                stopLogcatBridge()
                stopServerLogTail()
                stopPortProbe()
                releaseWakeLock()
                currentVersionId?.let { versionId ->
                    persistPublicAddress(applicationContext, "")
                    persistRuntimeState(applicationContext, versionId, RUNTIME_STATE_OFFLINE)
                    sendEvent(versionId, EVENT_STOPPED, "[INFO] Server stopped.")
                }
                // Fallback stop signal when version-scoped event cannot be emitted.
                sendBroadcast(Intent(EVENT_STOPPED).setPackage(packageName))
                isLaunching = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                stopInProgress.set(false)
            }
        }
    }

    private fun updateNotification(text: String, force: Boolean = false) {
        val cleanText = ConsoleParser.stripAnsi(text).trim()
        if (cleanText.isBlank()) return

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

    private fun resetNotificationState(initialText: String = "") {
        lastNotificationText = initialText
        lastNotificationUpdateMs = SystemClock.elapsedRealtime()
    }

    private fun createForegroundNotification(text: String): Notification {
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

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("PocketCraft Server Running")
            .setContentText(text.take(100).ifBlank { "Tap to manage your server" })
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = getSystemService(NotificationManager::class.java)
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Server Running",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows while your Minecraft server is running"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
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

        val latestLog = File(ServerFileManager.getServerDir(applicationContext, versionId), "logs/latest.log")
        val initialOffset = latestLog.takeIf { it.exists() }?.length() ?: 0L

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
                            addLogLine(line)
                            sendEvent(versionId, EVENT_OUTPUT, line)
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
            while (portProbeRunning.get() && !Thread.currentThread().isInterrupted) {
                try {
                    Socket().use { socket ->
                        socket.connect(InetSocketAddress("127.0.0.1", port), 750)
                    }
                    val line = "[PocketCraft] Server port $port is accepting connections."
                    sendEvent(versionId, EVENT_OUTPUT, line)
                    resolveLanEndpoint(port)?.let { endpoint ->
                        sendEvent(versionId, EVENT_OUTPUT, "[PocketCraft] LAN address: $endpoint")
                    }
                    updateNotification(line, force = true)
                    onServerReady()
                    break
                } catch (_: Exception) {
                    Thread.sleep(1500)
                }
            }
            portProbeRunning.set(false)
        }.apply {
            name = "server-port-probe"
            isDaemon = true
            start()
        }
    }

    private fun resolveServerPort(versionId: String): Int {
        val serverDir = ServerFileManager.getServerDir(applicationContext, versionId)
        val propsFile = File(serverDir, "server.properties")
        if (!propsFile.exists()) return 25565

        return runCatching {
            propsFile.inputStream().use { input ->
                Properties().apply { load(input) }
            }.getProperty("server-port", "25565").toIntOrNull() ?: 25565
        }.getOrDefault(25565)
    }

    private fun looksLikeServerReady(line: String): Boolean {
        val normalized = line.lowercase()
        return normalized.contains("done (") && normalized.contains("for help")
    }

    private fun stopPortProbe() {
        portProbeRunning.set(false)
        portProbeThread?.interrupt()
        portProbeThread = null
    }

    private fun onServerReady() {
        if (tunnelStarted.getAndSet(true)) {
            android.util.Log.d("ServerHostService", "onServerReady: Tunnel already started, skipping.")
            return
        }

        autoRecoverAttempts = 0
        autoRecoverWindowStartMs = 0L
        currentVersionId?.let { persistRuntimeState(applicationContext, it, RUNTIME_STATE_RUNNING) }
        currentVersionId?.let { sendEvent(it, EVENT_TUNNEL_CONNECTING, "[PocketCraft] Opening internet relay...") }

        relayJob = serviceScope.launch(Dispatchers.IO) {
            var registrationAttempts = 0
            val maxRegistrationAttempts = 5
            relayManager.disconnect()

            while (isActive) {
                try {
                    val alreadyKnownPort = relayManager.assignedPort

                    registrationAttempts++
                    android.util.Log.i("ServerHostService", "Relay registration attempt $registrationAttempts/$maxRegistrationAttempts")

                    val address = relayManager.register()
                    persistPublicAddress(applicationContext, address.toString())

                    registrationAttempts = 0

                    val intent = Intent(ACTION_SERVER_EVENT).apply {
                        setPackage(packageName)
                        putExtra(EXTRA_VERSION_ID, currentVersionId ?: "unknown")
                        putExtra(EXTRA_EVENT_TYPE, EVENT_TUNNEL_CONNECTED)
                        putExtra(EXTRA_LINE, address.toString())
                        putExtra(EXTRA_IS_FALLBACK, address.isFallback)
                    }
                    sendBroadcast(intent)

                    if (alreadyKnownPort == null) {
                        android.util.Log.i("ServerHostService", "Opening internet relay...")
                    }
                    relayManager.connectTunnelPool(currentServerPort)

                    while (isActive) {
                        delay(60_000)
                    }
                } catch (e: Exception) {
                    if (!isActive) break

                    android.util.Log.e("ServerHostService", "Relay Error (attempt $registrationAttempts): ${e.message}")

                    // Ensure failed attempts do not keep stale sockets around.
                    relayManager.disconnect()

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
            android.util.Log.i("ServerHostService", "Relay job ended (isActive=$isActive)")
        }
    }

    private fun shouldScheduleAutoRecover(versionId: String): Boolean {
        if (stopReason == "user") return false
        if (versionId.isBlank()) return false

        val now = System.currentTimeMillis()
        if (autoRecoverWindowStartMs == 0L || (now - autoRecoverWindowStartMs) > AUTO_RECOVER_WINDOW_MS) {
            autoRecoverWindowStartMs = now
            autoRecoverAttempts = 0
        }

        if (autoRecoverAttempts >= AUTO_RECOVER_MAX_ATTEMPTS) {
            sendEvent(
                versionId,
                EVENT_ERROR,
                "[PocketCraft] Auto-recovery limit reached. Please reduce plugins/load before restarting again."
            )
            return false
        }

        autoRecoverAttempts += 1
        return true
    }

    private fun acquireWakeLock() {
        if (wakeLock != null) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PocketCraft:ServerWakeLock").apply {
            setReferenceCounted(false)
            acquire(4 * 60 * 60 * 1000L) // 4 hours max
        }
        android.util.Log.i("ServerHostService", "WakeLock acquired.")
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
            android.util.Log.i("ServerHostService", "WakeLock released.")
        }
        wakeLock = null
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
        }.getOrNull()
        return if (ip.isNullOrBlank()) null else "$ip:$port"
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
    }

    companion object {
        private const val AUTO_RECOVER_WINDOW_MS = 20 * 60 * 1000L
        private const val AUTO_RECOVER_MAX_ATTEMPTS = 3
        private const val CHANNEL_ID = "pocketcraft_server"
        private const val NOTIFICATION_ID = 2001
        private const val NOTIFICATION_UPDATE_INTERVAL_MS = 2500L
        private const val IMPORTANT_NOTIFICATION_UPDATE_INTERVAL_MS = 750L
        private const val STOP_GRACE_PERIOD_MS = 12_000L

        const val ACTION_START = "com.pocketcraft.server.action.START_SERVER"
        const val ACTION_STOP = "com.pocketcraft.server.action.STOP_SERVER"
        const val ACTION_SERVER_EVENT = "com.pocketcraft.server.action.SERVER_EVENT"

        const val EXTRA_VERSION_ID = "version_id"
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
        private const val PREFS_NAME = "pocketcraft_runtime_state"
        private const val KEY_ACTIVE_VERSION = "active_version"
        private const val KEY_RUNTIME_STATE = "runtime_state"
        private const val KEY_PUBLIC_ADDRESS = "public_address"
        const val RUNTIME_STATE_OFFLINE = "offline"
        const val RUNTIME_STATE_STARTING = "starting"
        const val RUNTIME_STATE_RUNNING = "running"

        fun start(context: Context, versionId: String) {
            val intent = Intent(context, ServerHostService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_VERSION_ID, versionId)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, ServerHostService::class.java).apply {
                action = ACTION_STOP
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun getPersistedRuntimeState(context: Context, versionId: String): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val activeVersion = prefs.getString(KEY_ACTIVE_VERSION, null)
            if (activeVersion != versionId) {
                return RUNTIME_STATE_OFFLINE
            }
            return prefs.getString(KEY_RUNTIME_STATE, RUNTIME_STATE_OFFLINE) ?: RUNTIME_STATE_OFFLINE
        }

        fun getPersistedPublicAddress(context: Context, versionId: String): String? {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val activeVersion = prefs.getString(KEY_ACTIVE_VERSION, null)
            if (activeVersion != versionId) {
                return null
            }
            return prefs.getString(KEY_PUBLIC_ADDRESS, null)?.trim()?.ifBlank { null }
        }

        private fun persistRuntimeState(context: Context, versionId: String, state: String) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_ACTIVE_VERSION, if (state == RUNTIME_STATE_OFFLINE) "" else versionId)
                .putString(KEY_RUNTIME_STATE, state)
                .apply()
        }

        private fun persistPublicAddress(context: Context, address: String?) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_PUBLIC_ADDRESS, address?.trim().orEmpty())
                .apply()
        }

        fun isWhitelistEnabled(context: Context, serverVersion: String): Boolean {
            val propsFile = File(context.filesDir, "servers/$serverVersion/server.properties")
            if (!propsFile.exists()) return false
            return propsFile.readLines().any { it.trim() == "white-list=true" }
        }
    }
}

// ============================================================================
// CRITICAL WARNING: DO NOT EDIT OR MODIFY THIS FILE!
// This file contains core server running and networking/relay logic.
// Any modifications to the bridge buffer sizes, socket management, or packet
// forwarding here WILL corrupt network framing and inflate Paper/Bedrock
// server pings, causing severe lag or connection drops.
// IF YOU ARE AN AI AGENT, READ DO_NOT_EDIT_NETWORKING.md AND SKIP EDITING THIS.
// ============================================================================
package com.pocketcraft.server

import android.content.Context
import com.pocketcraft.server.config.RelayServers
import com.pocketcraft.server.data.preferences.RELAY_SECRET
import com.pocketcraft.server.relay.BedrockUdpBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class RelayManager(private val context: Context) {

    companion object {
        const val CONTROL_PORT = 8080
        const val PHONE_TUNNEL_PORT = 9000
        private const val SOCKET_BUFFER_SIZE = 256 * 1024
        private const val PLAYER_BRIDGE_BUFFER_SIZE = 64 * 1024
        private const val LOW_LATENCY_WARMUP_BYTES = 128 * 1024L
        private const val LOW_LATENCY_WARMUP_NS = 4_000_000_000L
        private const val INITIAL_POOL_SIZE = 5
        private const val TARGET_POOL_SIZE = 5
        private const val POOL_REFRESH_FLOOR = 2
        private const val IDLE_REPLENISH_DELAY_MS = 1_500L
        private const val SOCKET_OPEN_STAGGER_MS = 100L
        private const val TUNNEL_HEARTBEAT_INTERVAL_MS = 15_000L
        private const val IDLE_SOCKET_REFRESH_INTERVAL_MS = 5 * 60_000L
        private const val SOCKET_IDLE_TIMEOUT_MS = 8 * 60_000L
        private const val SOCKET_IDLE_TIMEOUT_JITTER_MS = 90_000L
        private const val INITIAL_POOL_READY_TIMEOUT_MS = 8_000L
        private const val READY_POOL_SIZE = 2
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private var activeRelaySessionId: String? = null
    var assignedPort: Int? = null
        private set
    private val _isPoolReady = MutableStateFlow(false)
    val isPoolReady: StateFlow<Boolean> = _isPoolReady.asStateFlow()
    @Volatile
    private var activeRelayHost: String? = null
    @Volatile
    private var lastRegisteredHost: String? = null
    @Volatile
    private var activeRelayIsFallback = false
    @Volatile
    private var preferFallbackRelay = false

    private var bedrockUdpBridge: BedrockUdpBridge? = null
    @Volatile
    private var activeBedrockSocket: Socket? = null
    private val bedrockTxChannel = Channel<ByteArray>(capacity = 256)
    private var bedrockTxJob: kotlinx.coroutines.Job? = null
    private val droppedFrameCount = AtomicInteger(0)


    private val poolTargetSize = AtomicInteger(INITIAL_POOL_SIZE)
    private val socketPool = mutableListOf<PooledSocket>()
    private val connectingSockets = AtomicInteger(0)
    private val poolTopUpScheduled = AtomicBoolean(false)
    private val consecutiveFailures = AtomicInteger(0)
    private var poolJob = SupervisorJob()
    private var poolScope = CoroutineScope(Dispatchers.IO + poolJob)
    private var tunnelHeartbeatJob: kotlinx.coroutines.Job? = null
    private val statusHttpClient = OkHttpClient()
    @Volatile
    private var lastIdleSocketRefreshAtMs = 0L
    @Volatile
    private var activeTunnelLocalPort: Int? = null
    @Volatile
    private var resolvedRelayIp: String? = null
    @Volatile
    private var activeGeyserUdpHost: String = "127.0.0.1"
    private val inboundBedrockFrameCount = AtomicInteger(0)

    private data class PooledSocket(
        val socket: Socket,
        var isBridging: Boolean = false,
        val createdAt: Long = System.currentTimeMillis()
    )

    data class RelayAddress(val host: String, val port: Int, val isFallback: Boolean = false) {
        override fun toString() = "$host:$port"
    }

    data class TunnelPoolResult(
        val readyAck: Boolean,
        val poolReady: Boolean
    )

    data class TunnelHealth(
        val healthy: Boolean,
        val poolSize: Int,
        val poolReady: Boolean,
        val heartbeatActive: Boolean,
        val hasLocalPort: Boolean
    )

    /**
     * Registers this user with the relay control API.
     * Returns the public address players should use to connect.
     * Safe to call multiple times — relay returns existing port if already registered.
     */
    suspend fun register(): RelayAddress = withContext(Dispatchers.IO) {
        val prefs = com.pocketcraft.server.data.preferences.AppPreferences(context)
        val userId = currentRelaySessionId()
        val preferredRelayHost = prefs.relayHost
        val fallbackRelayHost = if (preferredRelayHost == RelayServers.MUMBAI.host) {
            RelayServers.EUROPE.host
        } else {
            RelayServers.MUMBAI.host
        }
        val hostCandidates = buildList {
            if (preferFallbackRelay && preferredRelayHost != fallbackRelayHost) {
                add(fallbackRelayHost)
                add(preferredRelayHost)
            } else {
                add(preferredRelayHost)
                if (preferredRelayHost != fallbackRelayHost) add(fallbackRelayHost)
            }
        }.distinct()

        var lastError: Exception? = null
        for (relayHost in hostCandidates) {
            val isFallback = relayHost != preferredRelayHost
            android.util.Log.d("RelayManager", "Registering relay session: $userId on $relayHost")

            val url = URL("http://$relayHost:$CONTROL_PORT/register")
            val conn = url.openConnection() as HttpURLConnection

            try {
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000
                conn.doOutput = true

                val body = """{"userId":"$userId"}"""
                android.util.Log.d("RelayManager", "Register request: $body")
                conn.outputStream.write(body.toByteArray(Charsets.UTF_8))
                conn.outputStream.flush()

                val responseCode = conn.responseCode
                android.util.Log.d("RelayManager", "Register response code: $responseCode")

                if (responseCode != 200) {
                    throw IOException("Relay register failed: HTTP $responseCode")
                }

                val response = conn.inputStream.bufferedReader().readText()
                android.util.Log.d("RelayManager", "Register response: $response")
                val json = JSONObject(response)

                assignedPort = json.getInt("port")
                com.pocketcraft.server.data.preferences.AppPreferences(context).relayPort = assignedPort
                activeRelayHost = relayHost
                lastRegisteredHost = relayHost
                activeRelayIsFallback = isFallback
                preferFallbackRelay = isFallback
                resolvedRelayIp = null
                val address = RelayAddress(relayHost, assignedPort!!, isFallback = isFallback)
                android.util.Log.d("RelayManager", "Register success: $address")
                return@withContext address
            } catch (e: Exception) {
                lastError = e
                android.util.Log.w("RelayManager", "Register failed on $relayHost: ${e.message}")
            } finally {
                conn.disconnect()
            }
        }

        throw lastError ?: IOException("Relay register failed")
    }

    fun startBedrockBridge() {
        if (bedrockUdpBridge != null) return

        bedrockTxJob?.cancel()
        bedrockTxJob = poolScope.launch(Dispatchers.IO) {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY)
            var currentSocket: java.net.Socket? = null
            var outStream: java.io.BufferedOutputStream? = null

            while (poolScope.isActive) {
                val firstFrame = try {
                    bedrockTxChannel.receive()
                } catch (e: Exception) {
                    break // Channel closed or job cancelled
                }

                var socket = activeBedrockSocket
                var waitedMs = 0L
                while ((socket == null || socket.isClosed) && waitedMs < 2_000L && poolScope.isActive) {
                    delay(25)
                    waitedMs += 25
                    socket = activeBedrockSocket
                }

                if (socket == null || socket.isClosed) {
                    android.util.Log.w(
                        "RelayManager",
                        "Dropping Bedrock response frame (${firstFrame.size} bytes): no active Bedrock relay socket."
                    )
                    continue
                }

                if (socket != currentSocket) {
                    outStream = java.io.BufferedOutputStream(socket.getOutputStream(), 128 * 1024)
                    currentSocket = socket
                }

                try {
                    outStream?.write(firstFrame)
                    
                    // Batch drain any other immediately available frames to reduce syscalls
                    while (true) {
                        val nextResult = bedrockTxChannel.tryReceive()
                        if (nextResult.isSuccess) {
                            val nextFrame = nextResult.getOrThrow()
                            outStream?.write(nextFrame)
                        } else {
                            break
                        }
                    }
                    
                    // Flush the batched frames to the network
                    outStream?.flush()
                } catch (e: Exception) {
                    android.util.Log.e("RelayManager", "Failed to send Bedrock response: ${e.message}")
                    currentSocket = null
                    outStream = null
                }
            }
        }

        // Always use 127.0.0.1 (loopback) for local Geyser UDP IPC.
        // Using the WiFi IP (activeGeyserUdpHost) breaks the connected DatagramSocket:
        // on Android, packets to your own IP route via loopback, so Geyser replies
        // arrive *from* 127.0.0.1 — a connected socket silently drops replies from
        // any address other than the one it connected to, causing socket.receive()
        // to block forever and no outbound frames to flow back to the relay.
        bedrockUdpBridge = BedrockUdpBridge(
            geyserHostProvider = { "127.0.0.1" }
        ) { frame ->
            val result = bedrockTxChannel.trySend(frame)
            if (result.isFailure) {
                val dropped = droppedFrameCount.incrementAndGet()
                if (dropped % 100 == 0) {
                    android.util.Log.w("RelayManager", "Dropped $dropped Bedrock UDP frames due to channel capacity")
                }
            }
        }
        bedrockUdpBridge?.start()
    }

    fun stopBedrockBridge() {
        bedrockTxJob?.cancel()
        bedrockTxJob = null
        bedrockUdpBridge?.stop()
        bedrockUdpBridge = null
        activeBedrockSocket = null
        droppedFrameCount.set(0)
    }

    /**
     * Attempts to initialize the tunnel pool with exponential backoff.
     */
    suspend fun initPool(localPort: Int) {
        var attempts = 0
        while (attempts < 5) {
            try {
                android.util.Log.i("RelayManager", "Initializing relay pool (attempt ${attempts + 1}/5)")
                val result = connectTunnelPool(localPort)
                if (result.poolReady) {
                    _isPoolReady.value = true
                    android.util.Log.i("RelayManager", "Relay pool is ready.")
                    return
                }
            } catch (e: Exception) {
                android.util.Log.e("RelayManager", "Failed to init pool: ${e.message}")
            }
            attempts++
            _isPoolReady.value = false
            delay(2000L * attempts)
        }
        android.util.Log.e("RelayManager", "Relay pool failed to initialize after 5 attempts.")
    }

    /**
     * Opens a pool of persistent TCP sockets from the phone to the relay.
     * The relay uses these sockets to forward incoming player traffic.
     * Must be called AFTER register() and AFTER the Minecraft server is ready on 25565.
     */
    suspend fun connectTunnelPool(localPort: Int): TunnelPoolResult = withContext(Dispatchers.IO) {
        resetPoolSizing()
        android.util.Log.i("RelayManager", "Starting pool of ${poolTargetSize.get()} sockets...")
        activeTunnelLocalPort = localPort
        updateActiveGeyserUdpHost()
        tunnelHeartbeatJob?.cancel()
        tunnelHeartbeatJob = poolScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(TUNNEL_HEARTBEAT_INTERVAL_MS)
                val currentLocalPort = activeTunnelLocalPort ?: break
                val readyAck = notifyPhoneReady(currentLocalPort)
                if (!readyAck) {
                    android.util.Log.w(
                        "RelayManager",
                        "Relay heartbeat did not get phone-ready acknowledgment; refreshing socket pool."
                    )
                }
                topUpPool(currentLocalPort)
                // Closing an "idle" relay socket has been triggering native process
                // death on-device (signal 34) immediately after the refresh log line.
                // Keep the pool topped up, but avoid proactive socket churn here.
            }
        }
        topUpPool(localPort)
        val poolReady = waitForInitialPoolReady()
        val readyAck = if (poolReady) {
            notifyPhoneReady(localPort)
        } else {
            false
        }
        if (!readyAck) {
            android.util.Log.w(
                "RelayManager",
                "Relay control did not acknowledge phone-ready on known endpoints; player status ping may fail until relay API is updated."
            )
        }
        if (!poolReady) {
            val currentFallback = if ((activeRelayHost ?: "") == RelayServers.MUMBAI.host) {
                RelayServers.EUROPE.host
            } else {
                RelayServers.MUMBAI.host
            }
            if (!activeRelayIsFallback && (activeRelayHost ?: "") != currentFallback) {
                preferFallbackRelay = true
                android.util.Log.w(
                    "RelayManager",
                    "Selected relay tunnel pool did not become ready. Next registration attempt will use fallback relay $currentFallback."
                )
            }
            android.util.Log.w(
                "RelayManager",
                "Relay tunnel pool did not become ready within ${INITIAL_POOL_READY_TIMEOUT_MS}ms."
            )
        }
        TunnelPoolResult(readyAck = readyAck, poolReady = poolReady)
    }

    private suspend fun waitForInitialPoolReady(): Boolean {
        val deadline = System.currentTimeMillis() + INITIAL_POOL_READY_TIMEOUT_MS
        while (poolScope.isActive && System.currentTimeMillis() < deadline) {
            if (currentPoolSize() >= READY_POOL_SIZE) {
                return true
            }
            delay(100)
        }
        return currentPoolSize() >= READY_POOL_SIZE
    }

    fun snapshotTunnelHealth(): TunnelHealth {
        val poolSize = currentPoolSize()
        val poolReady = _isPoolReady.value
        val heartbeatActive = tunnelHeartbeatJob?.isActive == true
        val hasLocalPort = activeTunnelLocalPort != null
        val healthy = poolReady && heartbeatActive && hasLocalPort && poolSize >= POOL_REFRESH_FLOOR
        return TunnelHealth(
            healthy = healthy,
            poolSize = poolSize,
            poolReady = poolReady,
            heartbeatActive = heartbeatActive,
            hasLocalPort = hasLocalPort
        )
    }

    suspend fun notifyPhoneReady(localPort: Int): Boolean = withContext(Dispatchers.IO) {
        val prefs = com.pocketcraft.server.data.preferences.AppPreferences(context)
        val userId = currentRelaySessionId()
        val relayHost = activeRelayHost ?: prefs.relayHost
        val localIpCandidates = buildList {
            add(com.pocketcraft.server.server.ServerAddressResolver.getLocalIpAddress())
            add(resolveRouteLocalIp(relayHost))
        }
            .filterNotNull()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("127.") && it != "0.0.0.0" }
            .distinct()

        val localIp = localIpCandidates.firstOrNull()
        if (localIp != null && localIp != activeGeyserUdpHost) {
            activeGeyserUdpHost = localIp
            android.util.Log.i("RelayManager", "Using local Geyser UDP host $activeGeyserUdpHost for Bedrock relay bridge.")
        }

        android.util.Log.d("RelayManager", "Notifying relay phone-ready (userId=$userId, relay=$relayHost, local=$localIp:$localPort)")

        val endpointCandidates = listOf("/phone-ready", "/phone_ready", "/ready", "/phoneReady")
        val payloadCandidates = buildList {
            for (candidateHost in localIpCandidates) {
                // Try common key variants for relay compatibility.
                add("""{"userId":"$userId","host":"$candidateHost","port":$localPort,"bedrockPort":19132}""")
                add("""{"userId":"$userId","host":"$candidateHost","port":$localPort}""")
                add("""{"userId":"$userId","host":"$candidateHost"}""")
                add("""{"userId":"$userId","ip":"$candidateHost","port":$localPort,"bedrockPort":19132}""")
                add("""{"userId":"$userId","ip":"$candidateHost","port":$localPort}""")
                add("""{"userId":"$userId","ip":"$candidateHost"}""")
                add("""{"userId":"$userId","localIp":"$candidateHost","port":$localPort,"bedrockPort":19132}""")
                add("""{"userId":"$userId","localIp":"$candidateHost","port":$localPort}""")
                add("""{"userId":"$userId","localHost":"$candidateHost","port":$localPort,"bedrockPort":19132}""")
                add("""{"userId":"$userId","localHost":"$candidateHost","port":$localPort}""")
            }
            add("""{"userId":"$userId","port":$localPort,"bedrockPort":19132}""")
            add("""{"userId":"$userId","port":$localPort}""")
            add("""{"userId":"$userId"}""")
        }.distinct()

        for (endpoint in endpointCandidates) {
            for (body in payloadCandidates) {
                val url = URL("http://$relayHost:$CONTROL_PORT$endpoint")
                val conn = url.openConnection() as HttpURLConnection
                try {
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.connectTimeout = 10_000
                    conn.readTimeout = 10_000
                    conn.doOutput = true

                    conn.outputStream.write(body.toByteArray(Charsets.UTF_8))
                    conn.outputStream.flush()

                    val code = conn.responseCode
                    android.util.Log.d("RelayManager", "phone-ready endpoint $endpoint result: HTTP $code")
                    if (code in 200..299) {
                        return@withContext true
                    }
                } catch (e: Exception) {
                    android.util.Log.w("RelayManager", "Failed $endpoint notify attempt: ${e.message}")
                    if (e is java.net.ConnectException || e is java.net.SocketTimeoutException || e is java.net.UnknownHostException || e is java.net.NoRouteToHostException) {
                        android.util.Log.w("RelayManager", "Host $relayHost is unreachable, aborting phone-ready loop.")
                        return@withContext false
                    }
                } finally {
                    conn.disconnect()
                }
            }
        }

        false
    }

    suspend fun postServerStatus(
        motd: String,
        players: Int,
        maxPlayers: Int,
        version: String
    ): Boolean = withContext(Dispatchers.IO) {
        val relayPort = assignedPort ?: return@withContext false
        val secret = RELAY_SECRET.trim()
        if (secret.isBlank()) {
            android.util.Log.w("RelayManager", "Skipping relay status publish because RELAY_SECRET is empty.")
            return@withContext false
        }

        val body = JSONObject().apply {
            put("host", activeRelayHost ?: "")
            put("port", relayPort)
            put("motd", motd.trim())
            put("players", players.coerceAtLeast(0))
            put("maxPlayers", maxPlayers.coerceAtLeast(1))
            put("version", version.trim())
        }

        val endpoint = "/bedrock-status"
        val relayHosts = listOfNotNull(activeRelayHost)
        var anySuccess = false

        relayHosts.forEach { relayHost ->
            val request = Request.Builder()
                .url("http://$relayHost:$CONTROL_PORT$endpoint")
                .addHeader("X-PocketCraft-Secret", secret)
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            runCatching {
                statusHttpClient.newCall(request).execute().use { response ->
                    val responseBody = runCatching {
                        response.body?.string().orEmpty()
                    }.getOrDefault("")
                    android.util.Log.i(
                        "RelayManager",
                        "Relay status POST $relayHost$endpoint -> HTTP ${response.code}, body=${responseBody.ifBlank { "<empty>" }}"
                    )
                    if (response.isSuccessful) {
                        anySuccess = true
                    }
                }
            }.onFailure { error ->
                android.util.Log.w(
                    "RelayManager",
                    "Failed to publish relay status to $relayHost$endpoint: ${error.message}"
                )
            }
        }

        anySuccess
    }

    private fun resolveRouteLocalIp(relayHost: String): String? {
        return runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(relayHost, CONTROL_PORT), 1500)
                socket.localAddress?.hostAddress
            }
        }
            .getOrNull()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun updateActiveGeyserUdpHost() {
        val relayHost = activeRelayHost ?: com.pocketcraft.server.data.preferences.AppPreferences(context).relayHost
        val candidate = listOfNotNull(
            com.pocketcraft.server.server.ServerAddressResolver.getLocalIpAddress(),
            resolveRouteLocalIp(relayHost)
        )
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() && !it.startsWith("127.") && it != "0.0.0.0" }

        val nextHost = candidate ?: "127.0.0.1"
        if (nextHost != activeGeyserUdpHost) {
            activeGeyserUdpHost = nextHost
            android.util.Log.i("RelayManager", "Using local Geyser UDP host $activeGeyserUdpHost for Bedrock relay bridge.")
        }
    }

    private fun resolvePreferIPv4(host: String): InetAddress {
        return InetAddress.getAllByName(host)
            .firstOrNull { it is Inet4Address }
            ?: InetAddress.getByName(host)
    }

    private fun resolveRelayIp(relayHost: String): String? {
        val configuredFallback = RelayServers.getByHost(relayHost).fallbackIp
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        if (configuredFallback != null) {
            return configuredFallback
        }

        return runBlocking(Dispatchers.IO) {
            runCatching {
                withTimeoutOrNull(2_000L) {
                    resolvePreferIPv4(relayHost).hostAddress
                }
            }.getOrNull()
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        }
    }

    private fun topUpPool(localPort: Int) {
        val targetSize = poolTargetSize.get()
        while (poolScope.isActive && reservePoolSlotIfAvailable(targetSize)) {
            openPhoneSocket(localPort)
        }
    }

    private fun reservePoolSlotIfAvailable(targetSize: Int): Boolean {
        synchronized(socketPool) {
            val currentConnecting = connectingSockets.get()
            if (socketPool.size + currentConnecting >= targetSize) {
                return false
            }
            connectingSockets.incrementAndGet()
            return true
        }
    }

    private fun currentPoolSize(): Int = synchronized(socketPool) { socketPool.size }

    private fun nextIdleSocketTimeoutMs(): Long {
        return SOCKET_IDLE_TIMEOUT_MS + Random.nextLong(SOCKET_IDLE_TIMEOUT_JITTER_MS)
    }

    private fun refreshOneIdlePoolSocketIfNeeded(localPort: Int) {
        val now = System.currentTimeMillis()
        if ((now - lastIdleSocketRefreshAtMs) < IDLE_SOCKET_REFRESH_INTERVAL_MS) return

        val socketToRefresh = synchronized(socketPool) {
            if (socketPool.size <= POOL_REFRESH_FLOOR) return@synchronized null
            socketPool.firstOrNull { !it.isBridging }
                ?.also { socketPool.remove(it) }
        } ?: return

        lastIdleSocketRefreshAtMs = now
        android.util.Log.v("RelayManager", "Refreshing one idle relay socket to avoid stale pool entries.")
        runCatching { socketToRefresh.socket.close() }
        schedulePoolTopUp(localPort, delayMs = SOCKET_OPEN_STAGGER_MS)
    }

    private fun schedulePoolTopUp(localPort: Int, delayMs: Long = 0L) {
        if (!poolTopUpScheduled.compareAndSet(false, true)) {
            return
        }
        poolScope.launch(Dispatchers.IO) {
            try {
                if (delayMs > 0L) delay(delayMs)
                if (poolScope.isActive) {
                    topUpPool(localPort)
                }
            } finally {
                poolTopUpScheduled.set(false)
            }
        }
    }

    private fun openPhoneSocket(localPort: Int) {
        poolScope.launch(Dispatchers.IO) {
            var socket: Socket? = null
            var pooledSocket: PooledSocket? = null
            var addedToPool = false
            var reservedSlot = true

            fun releaseReservedSlot() {
                if (reservedSlot) {
                    connectingSockets.decrementAndGet()
                    reservedSlot = false
                }
            }

            try {
                val staggerDelayMs = ((connectingSockets.get() - 1).coerceAtLeast(0) * SOCKET_OPEN_STAGGER_MS)
                    .coerceAtMost(4_000L)
                if (staggerDelayMs > 0L) {
                    delay(staggerDelayMs)
                }

                val userId = currentRelaySessionId()
                val relayHost = activeRelayHost ?: com.pocketcraft.server.data.preferences.AppPreferences(context).relayHost

                var targetIp = resolvedRelayIp
                if (targetIp == null) {
                    targetIp = resolveRelayIp(relayHost).also {
                        resolvedRelayIp = it
                    }
                }
                if (targetIp.isNullOrBlank()) {
                    throw IOException("Unable to resolve relay IP for $relayHost")
                }

                socket = Socket()
                configureSocket(socket)
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_MORE_FAVORABLE)
                socket.connect(java.net.InetSocketAddress(targetIp, PHONE_TUNNEL_PORT), 10_000)

                socket.outputStream.write("$userId\n".toByteArray(Charsets.UTF_8))
                socket.outputStream.flush()

                pooledSocket = PooledSocket(socket!!)
                synchronized(socketPool) {
                    socketPool.add(pooledSocket!!)
                    addedToPool = true
                }
                releaseReservedSlot()
                android.util.Log.v("RelayManager", "Socket added to pool. Size: ${socketPool.size}/${poolTargetSize.get()}")

                // Keep idle pool sockets alive long enough to serve a join burst without churn.
                socket!!.soTimeout = nextIdleSocketTimeoutMs().toInt()

                val connectedAt = System.currentTimeMillis()

                val firstByte = try {
                    socket!!.inputStream.read()
                } catch (e: java.net.SocketTimeoutException) {
                    -2 // Special flag for idle rotation
                }

                val timeOpenMs = System.currentTimeMillis() - connectedAt
                if (timeOpenMs > 5000L) {
                    consecutiveFailures.set(0)
                }

                pooledSocket?.isBridging = true
                synchronized(socketPool) { pooledSocket?.let { socketPool.remove(it) } }
                addedToPool = false

                if (firstByte == -2) {
                    android.util.Log.v("RelayManager", "Socket hit local idle timeout, rotating gracefully...")
                    runCatching { socket!!.close() }
                    val replenishDelayMs = if (currentPoolSize() <= POOL_REFRESH_FLOOR) {
                        SOCKET_OPEN_STAGGER_MS
                    } else {
                        IDLE_REPLENISH_DELAY_MS
                    }
                    schedulePoolTopUp(localPort, delayMs = replenishDelayMs)
                } else if (firstByte != -1) {
                    android.util.Log.d("RelayManager", "Socket consumed (firstByte=$firstByte), replenishing pool...")
                    schedulePoolTopUp(localPort)
                    // Reset timeout to 0 so the active player connection doesn't drop
                    socket!!.soTimeout = 0
                    if (firstByte == 0x02) {
                        bridgeBedrockConnection(socket!!, firstByte)
                    } else {
                        bridgePlayerConnection(socket!!, firstByte, localPort)
                    }
                } else {
                    android.util.Log.v("RelayManager", "Socket closed by relay or network, replenishing pool...")
                    runCatching { socket!!.close() }
                    
                    val isRapidFailure = timeOpenMs < 5000L
                    
                    val replenishDelayMs = if (isRapidFailure) {
                        val fails = consecutiveFailures.incrementAndGet()
                        (2000L * (1 shl fails.coerceAtMost(5))).coerceAtMost(60_000L)
                    } else if (currentPoolSize() <= POOL_REFRESH_FLOOR) {
                        SOCKET_OPEN_STAGGER_MS
                    } else {
                        IDLE_REPLENISH_DELAY_MS
                    }
                    schedulePoolTopUp(localPort, delayMs = replenishDelayMs)
                }

            } catch (e: Exception) {
                android.util.Log.e("RelayManager", "Pool socket error: ${e.message}")
                resolvedRelayIp = null
                synchronized(socketPool) {
                    pooledSocket?.let { socketPool.remove(it) }
                }
                addedToPool = false
                releaseReservedSlot()
                socket?.close()
                if (poolScope.isActive) {
                    val fails = consecutiveFailures.incrementAndGet()
                    val backoffMs = (2000L * (1 shl fails.coerceAtMost(5))).coerceAtMost(60_000L)
                    schedulePoolTopUp(localPort, delayMs = backoffMs)
                }
            } finally {
                releaseReservedSlot()
            }
        }
    }

    private suspend fun bridgeBedrockConnection(relaySocket: Socket, firstByte: Int) {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY)
        android.util.Log.i("RelayManager", "Bedrock UDP bridge ACTIVE via TCP tunnel.")
        android.util.Log.d(
            "RelayManager",
            "Active Bedrock relay socket assigned: remote=${relaySocket.inetAddress?.hostAddress}:${relaySocket.port}, local=${relaySocket.localAddress?.hostAddress}:${relaySocket.localPort}"
        )
        activeBedrockSocket = relaySocket
        var handedOffToJavaBridge = false
        
        try {
            val relayInput = java.io.BufferedInputStream(relaySocket.getInputStream(), 128 * 1024)
            
            // The first byte was already consumed (it was 0x02).
            // Header is 9 bytes total: type (1), len (2), ip (4), port (2).
            val headerBuffer = ByteArray(9)
            headerBuffer[0] = firstByte.toByte()
            
            // Read next 8 bytes of the first frame header
            var hOffset = 1
            while (hOffset < 9) {
                val read = relayInput.read(headerBuffer, hOffset, 9 - hOffset)
                if (read == -1) return
                hOffset += read
            }
            
            val payloadLen = ((headerBuffer[1].toInt() and 0xFF) shl 8) or (headerBuffer[2].toInt() and 0xFF)
            val firstFrame = ByteArray(9 + payloadLen)
            System.arraycopy(headerBuffer, 0, firstFrame, 0, 9)
            
            var pOffset = 0
            while (pOffset < payloadLen) {
                val read = relayInput.read(firstFrame, 9 + pOffset, payloadLen - pOffset)
                if (read == -1) break
                pOffset += read
            }
            if (pOffset == payloadLen) {
                val frameNo = inboundBedrockFrameCount.incrementAndGet()
                android.util.Log.d(
                    "RelayManager",
                    "Received first Bedrock relay frame #$frameNo (${firstFrame.size} bytes, payload=$payloadLen); forwarding to local Geyser."
                )
                bedrockUdpBridge?.onIncomingFrame(firstFrame)
            }
            
            // Now loop for subsequent frames on this same TCP socket
            while (true) {
                val type = relayInput.read()
                if (type == -1) break
                if (type != 0x02) {
                    android.util.Log.i(
                        "RelayManager",
                        "Switching TCP socket from Bedrock to Java bridge (firstByte=$type, remote=${relaySocket.inetAddress?.hostAddress}:${relaySocket.port})"
                    )
                    val localPort = activeTunnelLocalPort ?: 25565
                    if (activeBedrockSocket == relaySocket) {
                        android.util.Log.d("RelayManager", "Clearing active Bedrock socket before Java handoff.")
                        activeBedrockSocket = null
                    }
                    handedOffToJavaBridge = true
                    bridgePlayerConnection(relaySocket, type, localPort)
                    return
                }
                
                val nextHeader = ByteArray(8)
                var nhOff = 0
                while (nhOff < 8) {
                    val r = relayInput.read(nextHeader, nhOff, 8 - nhOff)
                    if (r == -1) break
                    nhOff += r
                }
                if (nhOff < 8) break
                
                val len = ((nextHeader[0].toInt() and 0xFF) shl 8) or (nextHeader[1].toInt() and 0xFF)
                val frame = ByteArray(9 + len)
                frame[0] = 0x02.toByte()
                System.arraycopy(nextHeader, 0, frame, 1, 8)
                
                var npOff = 0
                while (npOff < len) {
                    val r = relayInput.read(frame, 9 + npOff, len - npOff)
                    if (r == -1) break
                    npOff += r
                }
                if (npOff < len) break

                val frameNo = inboundBedrockFrameCount.incrementAndGet()
                if (frameNo <= 20 || frameNo % 25 == 0) {
                    android.util.Log.d(
                        "RelayManager",
                        "Inbound Bedrock relay frame #$frameNo type=0x02 bytes=${frame.size} payload=$len client=${frame[3].toInt() and 0xFF}.${frame[4].toInt() and 0xFF}.${frame[5].toInt() and 0xFF}.${frame[6].toInt() and 0xFF}:${((frame[7].toInt() and 0xFF) shl 8) or (frame[8].toInt() and 0xFF)}"
                    )
                }
                bedrockUdpBridge?.onIncomingFrame(frame)
            }
        } catch (e: Exception) {
            android.util.Log.e("RelayManager", "Bedrock bridge error: ${e.message}")
        } finally {
            if (activeBedrockSocket == relaySocket) {
                android.util.Log.i(
                    "RelayManager",
                    "Bedrock relay socket closed: remote=${relaySocket.inetAddress?.hostAddress}:${relaySocket.port}, local=${relaySocket.localAddress?.hostAddress}:${relaySocket.localPort}"
                )
                activeBedrockSocket = null
            }
            if (!handedOffToJavaBridge) {
                android.util.Log.d("RelayManager", "Closing Bedrock relay socket after bridge loop exit.")
                runCatching { relaySocket.close() }
            }
        }
    }


    private suspend fun bridgePlayerConnection(relaySocket: Socket, firstByte: Int, localPort: Int) {
        android.util.Log.i("RelayManager", "Player incoming! Bridging to localhost:$localPort...")
        val bridgeStartedAt = System.nanoTime()

        val localSocket = try {
            withContext(Dispatchers.IO) {
                Socket().apply {
                    configureSocket(this)
                    connect(java.net.InetSocketAddress("127.0.0.1", localPort), 5000)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("RelayManager", "Failed to connect to local Minecraft server: ${e.message}")
            relaySocket.close()
            return
        }

        android.util.Log.i("RelayManager", "Bridge ACTIVE: Relay <-> Local:$localPort")

        val relayToLocalThread = Thread {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY)
            var totalBytes = 0L
            try {
                val output = localSocket.getOutputStream()
                val relayInput = relaySocket.getInputStream()

                // Manually push the first byte to the server
                output.write(firstByte)
                totalBytes += 1

                // Continue streaming normally
                val buffer = ByteArray(PLAYER_BRIDGE_BUFFER_SIZE)
                var bytesRead: Int

                while (true) {
                    bytesRead = try { relayInput.read(buffer) } catch (e: Exception) { -1 }
                    if (bytesRead <= 0) break

                    val startTime = System.nanoTime()
                    // tcpNoDelay=true on the socket means every write() is sent immediately
                    // by the kernel — an explicit flush() on an unbuffered OutputStream is
                    // a no-op and only wastes a syscall round-trip.
                    output.write(buffer, 0, bytesRead)
                    totalBytes += bytesRead

                    val durationMicros = (System.nanoTime() - startTime) / 1000
                    if (durationMicros > 50_000) {
                         android.util.Log.w("RelayManager", "RelayToLocal stall! Wrote $bytesRead bytes in ${durationMicros}μs")
                    }
                }
                android.util.Log.d("RelayManager", "RelayToLocal: End of stream. Total downstream: $totalBytes bytes")
            } catch (e: Exception) {
                android.util.Log.e("RelayManager", "RelayToLocal error: ${e.message}")
            } finally {
                runCatching { localSocket.close() }
                runCatching { relaySocket.close() }
            }
        }
        relayToLocalThread.name = "JavaRelayToLocal"
        relayToLocalThread.priority = Thread.MAX_PRIORITY

        val localToRelayThread = Thread {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY)
            var totalBytes = 0L
            try {
                val input = localSocket.getInputStream()
                val output = relaySocket.getOutputStream()
                val buffer = ByteArray(PLAYER_BRIDGE_BUFFER_SIZE)
                var bytesRead: Int

                while (true) {
                    bytesRead = try { input.read(buffer) } catch (e: Exception) { -1 }
                    if (bytesRead <= 0) break

                    val startTime = System.nanoTime()
                    // Socket OutputStream is unbuffered; flush() is a no-op here.
                    // tcpNoDelay=true ensures the kernel sends each write immediately.
                    output.write(buffer, 0, bytesRead)
                    totalBytes += bytesRead

                    val durationMicros = (System.nanoTime() - startTime) / 1000
                    if (durationMicros > 100_000) {
                        android.util.Log.d("RelayManager", "LocalToRelay write delay: ${durationMicros}μs for $bytesRead bytes")
                    }
                }
                android.util.Log.d("RelayManager", "LocalToRelay: End of stream. Total upstream: $totalBytes bytes")
            } catch (e: Exception) {
                android.util.Log.e("RelayManager", "LocalToRelay error: ${e.message}")
            } finally {
                runCatching { localSocket.close() }
                runCatching { relaySocket.close() }
            }
        }
        localToRelayThread.name = "JavaLocalToRelay"
        localToRelayThread.priority = Thread.MAX_PRIORITY

        relayToLocalThread.start()
        localToRelayThread.start()

        poolScope.launch(Dispatchers.IO) {
            try {
                relayToLocalThread.join()
            } catch (_: Exception) {}
            try {
                localToRelayThread.join()
            } catch (_: Exception) {}
            runCatching { localSocket.close() }
            runCatching { relaySocket.close() }
        }
    }

    private fun configureSocket(socket: Socket) {
        runCatching {
            socket.tcpNoDelay = true
            socket.keepAlive = true
            socket.reuseAddress = true
            socket.sendBufferSize = SOCKET_BUFFER_SIZE
            socket.receiveBufferSize = SOCKET_BUFFER_SIZE
            socket.trafficClass = 0x10 // IPTOS_LOWDELAY
            socket.setPerformancePreferences(0, 1, 0)
        }
    }

    /**
     * Closes the tunnel sockets.
     * Call this when the server stops.
     */
    fun disconnect() {
        stopBedrockBridge()
        tunnelHeartbeatJob?.cancel()
        tunnelHeartbeatJob = null
        activeTunnelLocalPort = null
        activeGeyserUdpHost = "127.0.0.1"
        resetPoolSizing()
        poolJob.cancel()
        poolJob = SupervisorJob()
        poolScope = CoroutineScope(Dispatchers.IO + poolJob)
        poolTopUpScheduled.set(false)
        lastIdleSocketRefreshAtMs = 0L
        resolvedRelayIp = null
        activeRelayHost = null
        activeRelayIsFallback = false
        preferFallbackRelay = false
        _isPoolReady.value = false

        synchronized(socketPool) {
            socketPool.forEach { try { it.socket.close() } catch (_: Exception) {} }
            socketPool.clear()
        }
        connectingSockets.set(0)
        // assignedPort = null // Keep port persistent across restarts
        activeRelaySessionId = null
        android.util.Log.i("RelayManager", "Tunnel disconnected and scope reset.")
    }

    private fun resetPoolSizing() {
        poolTargetSize.set(INITIAL_POOL_SIZE)
    }

    /**
     * Unregisters userId from relay server (frees the port).
     * Call this when the user explicitly stops hosting, not just on pause.
     */
    suspend fun unregister() = withContext(Dispatchers.IO) {
        val prefs = com.pocketcraft.server.data.preferences.AppPreferences(context)
        val relayHost = lastRegisteredHost ?: activeRelayHost ?: prefs.relayHost
        val sessionId = activeRelaySessionId.takeIf { !it.isNullOrBlank() } ?: prefs.userId

        if (!sessionId.isNullOrBlank()) {
            try {
                val url = URL("http://$relayHost:$CONTROL_PORT/unregister")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.outputStream.write("""{"userId":"$sessionId"}""".toByteArray())
                conn.outputStream.flush()
                conn.disconnect()
            } catch (_: IOException) {}
        }

        lastRegisteredHost = null
        disconnect()
    }

    @Synchronized
    private fun currentRelaySessionId(): String {
        val existing = activeRelaySessionId
        if (!existing.isNullOrBlank()) return existing
        val deviceId = com.pocketcraft.server.data.preferences.AppPreferences(context).userId
        activeRelaySessionId = deviceId
        android.util.Log.i("RelayManager", "Using persistent relay user id: $deviceId")
        return deviceId
    }
}

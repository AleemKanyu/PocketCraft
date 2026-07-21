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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.selects.select
import kotlin.OptIn
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
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
        // Bound kernel queues so chunks backpressure Paper before keepalives sit
        // behind seconds of unsent data on constrained mobile relay routes.
        private const val SOCKET_BUFFER_SIZE = 64 * 1024
        private const val PLAYER_BRIDGE_BUFFER_SIZE = 8 * 1024
        private const val PLAYER_BRIDGE_UPSTREAM_BUFFER_SIZE = 8 * 1024
        private const val BEDROCK_TX_BUFFER_SIZE = 8 * 1024
        private const val BEDROCK_SMALL_FRAME_MAX_BYTES = 3072
        private const val BEDROCK_LARGE_FRAME_BATCH_MAX = 4
        private const val BEDROCK_MAX_BYTES_PER_CYCLE = 16 * 1024
        private const val BEDROCK_PING_CHANNEL_CAPACITY = 512
        private const val BEDROCK_CHUNK_CHANNEL_CAPACITY = 1024
        private const val UPSTREAM_YIELD_EVERY_FULL_READS = 2
        private const val LOW_LATENCY_WARMUP_BYTES = 128 * 1024L
        private const val LOW_LATENCY_WARMUP_NS = 4_000_000_000L
        private const val INITIAL_POOL_SIZE = 3
        private const val TARGET_POOL_SIZE = 3
        private const val POOL_REFRESH_FLOOR = 1
        private const val IDLE_REPLENISH_DELAY_MS = 500L
        private const val SOCKET_OPEN_STAGGER_MS = 50L
        // Heartbeat every 20s instead of 10s — reduces network request traffic while keeping session alive.
        private const val TUNNEL_HEARTBEAT_INTERVAL_MS = 20_000L
        private const val IDLE_SOCKET_REFRESH_INTERVAL_MS = 5 * 60_000L
        private const val SOCKET_IDLE_TIMEOUT_MS = 8 * 60_000L
        private const val SOCKET_IDLE_TIMEOUT_JITTER_MS = 90_000L
        private const val INITIAL_POOL_READY_TIMEOUT_MS = 8_000L
        private const val READY_POOL_SIZE = 2
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun extractIPv4FromNAT64(addr: InetAddress): InetAddress {
            val bytes = addr.address
            if (bytes.size == 16) {
                val ip4Bytes = byteArrayOf(bytes[12], bytes[13], bytes[14], bytes[15])
                try {
                    return InetAddress.getByAddress(addr.hostName, ip4Bytes)
                } catch (e: Exception) {
                    // Ignore
                }
            }
            return addr
        }
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
    private val bedrockPingChannel = Channel<ByteArray>(capacity = BEDROCK_PING_CHANNEL_CAPACITY)
    private val bedrockChunkChannel = Channel<ByteArray>(capacity = BEDROCK_CHUNK_CHANNEL_CAPACITY)
    private var bedrockTxJob: kotlinx.coroutines.Job? = null
    private val bedrockSocketWriteLock = Any()
    private val droppedFrameCount = AtomicInteger(0)


    private val poolTargetSize = AtomicInteger(INITIAL_POOL_SIZE)
    private val socketPool = mutableListOf<PooledSocket>()
    private val connectingSockets = AtomicInteger(0)
    private val poolTopUpScheduled = AtomicBoolean(false)
    /** Blocks phone-ready heartbeats after stop so the relay cannot re-open a dead tunnel. */
    private val relayHostingAllowed = AtomicBoolean(false)
    private val consecutiveFailures = AtomicInteger(0)
    private var poolJob = SupervisorJob()
    private var poolScope = CoroutineScope(Dispatchers.IO + poolJob)
    private var tunnelHeartbeatJob: kotlinx.coroutines.Job? = null
    private val statusHttpClient = OkHttpClient.Builder()
        .dns(object : okhttp3.Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                val resolved = try {
                    InetAddress.getAllByName(hostname).map { addr ->
                        if (addr is java.net.Inet6Address) {
                            extractIPv4FromNAT64(addr)
                        } else {
                            addr
                        }
                    }
                } catch (e: Exception) {
                    okhttp3.Dns.SYSTEM.lookup(hostname)
                }
                return resolved
            }
        })
        .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    @Volatile
    private var lastIdleSocketRefreshAtMs = 0L
    @Volatile
    private var activeTunnelLocalPort: Int? = null
    @Volatile
    private var resolvedRelayIp: String? = null
    @Volatile
    private var activeGeyserUdpHost: String = "127.0.0.1"
    private val inboundBedrockFrameCount = AtomicInteger(0)
    private val logThrottleMap = ConcurrentHashMap<String, Long>()

    private data class PooledSocket(
        val socket: Socket,
        var isBridging: Boolean = false,
        val createdAt: Long = System.currentTimeMillis()
    )

    private inline fun logThrottled(
        key: String,
        intervalMs: Long,
        crossinline logAction: () -> Unit
    ) {
        val now = System.currentTimeMillis()
        val previous = logThrottleMap.putIfAbsent(key, now)
        if (previous == null) {
            logAction()
            return
        }
        if (now - previous >= intervalMs && logThrottleMap.replace(key, previous, now)) {
            logAction()
        }
    }

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
        val isUserOverridden = prefs.relayHostUserOverridden
        val currentPreferredHost = prefs.relayHost
        val preferredRelayHost = if (!isUserOverridden) {
            val measured = com.pocketcraft.server.config.RelayLatencySelector.measureRelayLatency(currentPreferredHost)
            if (measured > 180L) {
                val fastest = com.pocketcraft.server.config.RelayLatencySelector.pickFastestRelay(
                    com.pocketcraft.server.config.RelayServers.defaultRegions()
                )
                android.util.Log.i("RelayManager", "Preferred host $currentPreferredHost latency high (${measured}ms > 180ms). Auto-selected low-latency relay ${fastest.host}")
                fastest.host
            } else {
                currentPreferredHost
            }
        } else {
            currentPreferredHost
        }
        val fallbackRelayHost = when (preferredRelayHost) {
            RelayServers.MUMBAI.host -> RelayServers.EUROPE.host
            RelayServers.AMERICA.host -> RelayServers.EUROPE.host
            else -> RelayServers.MUMBAI.host
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
            // Use a region-scoped ID so each relay server assigns a different port
            // from its 10 000-slot pool instead of all three sharing the same one.
            val userId = relaySessionIdForHost(relayHost)
            val isFallback = relayHost != preferredRelayHost
            android.util.Log.d("RelayManager", "Registering relay session: $userId on $relayHost")

            val resolvedHost = resolveRelayIp(relayHost) ?: relayHost
            val url = URL("http://$resolvedHost:$CONTROL_PORT/register")
            val conn = url.openConnection() as HttpURLConnection

            try {
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Host", relayHost)
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
                relayHostingAllowed.set(true)
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

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun startBedrockBridge() {
        if (bedrockUdpBridge != null) return

        bedrockTxJob?.cancel()
        bedrockTxJob = poolScope.launch(Dispatchers.IO) {
            var currentSocket: java.net.Socket? = null
            var outStream: java.io.BufferedOutputStream? = null

            while (poolScope.isActive) {
                var socket = activeBedrockSocket
                var waitedMs = 0L
                while ((socket == null || socket.isClosed) && waitedMs < 2_000L && poolScope.isActive) {
                    delay(25)
                    waitedMs += 25
                    socket = activeBedrockSocket
                }

                if (socket == null || socket.isClosed) {
                    delay(10)
                    continue
                }

                if (socket != currentSocket) {
                    outStream = java.io.BufferedOutputStream(socket.getOutputStream(), BEDROCK_TX_BUFFER_SIZE)
                    currentSocket = socket
                }

                val stream = outStream ?: continue

                try {
                    drainBedrockPingFrames(stream, socket)
                    if (!bedrockPingChannel.isEmpty) {
                        kotlinx.coroutines.yield()
                        continue
                    }

                    val readyChunk = bedrockChunkChannel.tryReceive()
                    if (readyChunk.isSuccess) {
                        sendBedrockChunkBurst(stream, socket, readyChunk.getOrThrow())
                        kotlinx.coroutines.yield()
                        continue
                    }

                    // Suspend-wait instantly on either channel for event-driven wakeups
                    select<Unit> {
                        bedrockPingChannel.onReceive { frame ->
                            writeBedrockFrame(stream, socket, frame)
                        }
                        bedrockChunkChannel.onReceive { frame ->
                            sendBedrockChunkBurst(stream, socket, frame)
                        }
                    }
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
            enqueueBedrockTxFrame(frame)
        }
        bedrockUdpBridge?.start()
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun enqueueBedrockTxFrame(frame: ByteArray) {
        // Route by RakNet packet ID (byte 9 in our framing: 0x03 | 2-byte len | 4-byte IP | 2-byte port | payload)
        // ACK(0xC0), NACK(0xA0), ConnectedPing(0x00), ConnectedPong(0x03), Disconnect(0x15),
        // handshake replies (0x06,0x08,0x10,0x13,0x14) must bypass the chunk queue.
        val pingFrame = isHighPriorityBedrockFrame(frame)
        val channel = if (pingFrame) bedrockPingChannel else bedrockChunkChannel
        if (channel.trySend(frame).isSuccess) return
        if (pingFrame) {
            bedrockPingChannel.tryReceive()
            if (channel.trySend(frame).isFailure) {
                logDroppedBedrockFrame()
            }
        } else if (!bedrockPingChannel.isEmpty) {
            // Drop stale chunk data while keepalives are still queued.
            logDroppedBedrockFrame()
        } else {
            logDroppedBedrockFrame()
        }
    }

    private fun isHighPriorityBedrockFrame(frame: ByteArray): Boolean {
        // Frame layout: [0x03][len_hi][len_lo][ip0][ip1][ip2][ip3][port_hi][port_lo][payload...]
        // Byte 9 is the first byte of the RakNet UDP payload (the RakNet packet ID).
        if (frame.size < 10) return true // tiny/unknown frames: treat as high-priority
        val packetId = frame[9].toInt() and 0xFF
        return when (packetId) {
            0xC0,           // ACK — acknowledgement, critical for RakNet reliability
            0xA0,           // NACK — triggers retransmit
            0x00,           // ConnectedPing
            0x03,           // ConnectedPong
            0x06,           // OpenConnectionReply1
            0x08,           // OpenConnectionReply2
            0x10,           // NewIncomingConnection
            0x13,           // DisconnectNotification
            0x14,           // InvalidVersion
            0x15,           // NoFreeIncomingConnections
            0x1C            // UnconnectedPong
            -> true
            else -> frame.size <= BEDROCK_SMALL_FRAME_MAX_BYTES // fallback: small = ping
        }
    }

    private fun logDroppedBedrockFrame() {
        val dropped = droppedFrameCount.incrementAndGet()
        if (dropped % 100 == 0) {
            android.util.Log.w("RelayManager", "Dropped $dropped Bedrock UDP frames due to channel capacity")
        }
    }

    private fun writeBedrockFrame(
        outStream: java.io.BufferedOutputStream,
        socket: Socket,
        frame: ByteArray,
        flush: Boolean = true
    ): Boolean = synchronized(bedrockSocketWriteLock) {
        if (activeBedrockSocket !== socket || socket.isClosed) return@synchronized false
        outStream.write(frame)
        if (flush) outStream.flush()
        true
    }

    private fun drainBedrockPingFrames(
        outStream: java.io.BufferedOutputStream,
        socket: Socket
    ) {
        var drainedAny = false
        while (true) {
            val next = bedrockPingChannel.tryReceive()
            if (!next.isSuccess) break
            if (!writeBedrockFrame(outStream, socket, next.getOrThrow(), flush = false)) break
            drainedAny = true
        }
        if (drainedAny) {
            synchronized(bedrockSocketWriteLock) {
                if (activeBedrockSocket === socket && !socket.isClosed) {
                    try { outStream.flush() } catch (_: Exception) {}
                }
            }
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun sendBedrockChunkBurst(
        outStream: java.io.BufferedOutputStream,
        socket: Socket,
        firstChunk: ByteArray
    ) {
        var bytesThisCycle = 0
        var largeCount = 0

        fun writeChunk(frame: ByteArray): Boolean {
            if (!writeBedrockFrame(outStream, socket, frame, flush = false)) return false
            bytesThisCycle += frame.size
            largeCount++
            return true
        }

        if (!writeChunk(firstChunk)) return

        while (
            bytesThisCycle < BEDROCK_MAX_BYTES_PER_CYCLE &&
            largeCount < BEDROCK_LARGE_FRAME_BATCH_MAX &&
            bedrockPingChannel.isEmpty
        ) {
            val next = bedrockChunkChannel.tryReceive()
            if (!next.isSuccess) break
            if (!writeChunk(next.getOrThrow())) return
        }
        drainBedrockPingFrames(outStream, socket)
        synchronized(bedrockSocketWriteLock) {
            if (activeBedrockSocket === socket && !socket.isClosed) outStream.flush()
        }
    }

    fun stopBedrockBridge() {
        android.util.Log.d("RelayManager", "Stopping Bedrock bridge...")
        bedrockTxJob?.cancel()
        bedrockTxJob = null
        bedrockUdpBridge?.stop()
        bedrockUdpBridge = null

        // Explicitly close the active Bedrock relay socket to prevent socket leaks
        runCatching { activeBedrockSocket?.close() }
        activeBedrockSocket = null
        droppedFrameCount.set(0)

        // Drain the channels so stale packets from old connections don't flood the new connection
        while (bedrockPingChannel.tryReceive().isSuccess) {}
        while (bedrockChunkChannel.tryReceive().isSuccess) {}
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
        if (!relayHostingAllowed.get()) {
            android.util.Log.d("RelayManager", "phone-ready skipped: relay hosting is disabled")
            return@withContext false
        }
        val prefs = com.pocketcraft.server.data.preferences.AppPreferences(context)
        val relayHost = activeRelayHost ?: prefs.relayHost
        // Use the same region-scoped ID used in register() / tunnel sockets.
        val userId = relaySessionIdForHost(relayHost)
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
                val resolvedHost = resolveRelayIp(relayHost) ?: relayHost
                val url = URL("http://$resolvedHost:$CONTROL_PORT$endpoint")
                val conn = url.openConnection() as HttpURLConnection
                try {
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.setRequestProperty("Host", relayHost)
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
            val resolvedHost = resolveRelayIp(relayHost) ?: relayHost
            val request = Request.Builder()
                .url("http://$resolvedHost:$CONTROL_PORT$endpoint")
                .header("Host", relayHost)
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
                val resolvedHost = resolveRelayIp(relayHost) ?: relayHost
                socket.connect(InetSocketAddress(resolvedHost, CONTROL_PORT), 1500)
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
        val resolved = try {
            InetAddress.getAllByName(host)
        } catch (e: Exception) {
            emptyArray<InetAddress>()
        }
        val ipv4 = resolved.firstOrNull { it is Inet4Address }
        if (ipv4 != null) return ipv4
        val ipv6 = resolved.firstOrNull { it is java.net.Inet6Address }
        if (ipv6 != null) {
            return extractIPv4FromNAT64(ipv6)
        }
        return InetAddress.getByName(host)
    }

    private fun resolveRelayIp(relayHost: String): String {
        val staticIp = RelayServers.getByHost(relayHost).fallbackIp?.trim()
        if (!staticIp.isNullOrBlank()) {
            val asyncDns = runCatching {
                val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
                try {
                    val future = executor.submit<String> {
                        resolvePreferIPv4(relayHost).hostAddress
                    }
                    future.get(1200, java.util.concurrent.TimeUnit.MILLISECONDS)
                } finally {
                    executor.shutdownNow()
                }
            }.getOrNull()?.trim()?.takeIf { it.isNotBlank() }

            if (asyncDns != null) {
                return asyncDns
            }
            return staticIp
        }

        return runCatching { resolvePreferIPv4(relayHost).hostAddress }.getOrNull()?.trim()?.takeIf { it.isNotBlank() } ?: relayHost
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

            val relayHost = activeRelayHost ?: com.pocketcraft.server.data.preferences.AppPreferences(context).relayHost
            try {
                val staggerDelayMs = ((connectingSockets.get() - 1).coerceAtLeast(0) * SOCKET_OPEN_STAGGER_MS)
                    .coerceAtMost(4_000L)
                if (staggerDelayMs > 0L) {
                    delay(staggerDelayMs)
                }

                // Use the same region-scoped ID that was used during register() so the relay
                // can match this tunnel socket to the correct port assignment.
                val userId = relaySessionIdForHost(relayHost)

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
                configureRelaySocket(socket)
                socket.connect(java.net.InetSocketAddress(targetIp, PHONE_TUNNEL_PORT), 3000)

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
                        if (fails >= 2 && !preferFallbackRelay) {
                            preferFallbackRelay = true
                            android.util.Log.w("RelayManager", "Relay socket rapidly closed. Triggering fallback relay.")
                        }
                        (1500L * (1 shl fails.coerceAtMost(4))).coerceAtMost(30_000L)
                    } else if (currentPoolSize() <= POOL_REFRESH_FLOOR) {
                        SOCKET_OPEN_STAGGER_MS
                    } else {
                        IDLE_REPLENISH_DELAY_MS
                    }
                    schedulePoolTopUp(localPort, delayMs = replenishDelayMs)
                }

            } catch (e: Exception) {
                android.util.Log.e("RelayManager", "Pool socket error for $relayHost: ${e.message}")
                resolvedRelayIp = null
                synchronized(socketPool) {
                    pooledSocket?.let { socketPool.remove(it) }
                }
                addedToPool = false
                releaseReservedSlot()
                socket?.close()
                if (poolScope.isActive) {
                    val fails = consecutiveFailures.incrementAndGet()
                    if (fails >= 2 && !preferFallbackRelay) {
                        preferFallbackRelay = true
                        android.util.Log.w("RelayManager", "Pool socket error count $fails on $relayHost. Activating fallback relay.")
                    }
                    val backoffMs = (1500L * (1 shl fails.coerceAtMost(4))).coerceAtMost(30_000L)
                    schedulePoolTopUp(localPort, delayMs = backoffMs)
                }
            } finally {
                releaseReservedSlot()
            }
        }
    }

    private suspend fun bridgeBedrockConnection(relaySocket: Socket, firstByte: Int) {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_MORE_FAVORABLE)
        android.util.Log.i("RelayManager", "Bedrock UDP bridge ACTIVE via TCP tunnel.")
        android.util.Log.d(
            "RelayManager",
            "Active Bedrock relay socket assigned: remote=${relaySocket.inetAddress?.hostAddress}:${relaySocket.port}, local=${relaySocket.localAddress?.hostAddress}:${relaySocket.localPort}"
        )
        activeBedrockSocket = relaySocket
        var handedOffToJavaBridge = false
        
        try {
            val relayInput = java.io.BufferedInputStream(relaySocket.getInputStream(), 16 * 1024)
            
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
                    // Wait for any in-flight Bedrock frame write to finish. The writer
                    // rechecks activeBedrockSocket while holding this same lock.
                    synchronized(bedrockSocketWriteLock) {}
                    handedOffToJavaBridge = true
                    bridgePlayerConnection(relaySocket, type, localPort, relayInput)
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
                if (frameNo <= 20 || frameNo % 250 == 0) {
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


    private suspend fun bridgePlayerConnection(
        relaySocket: Socket,
        firstByte: Int,
        localPort: Int,
        relayInputOverride: InputStream? = null
    ) {
        android.util.Log.i("RelayManager", "Player incoming! Bridging to localhost:$localPort...")
        val bridgeStartedAt = System.nanoTime()

        val localSocket = try {
            withContext(Dispatchers.IO) {
                Socket().apply {
                    configureLocalSocket(this)
                    connect(java.net.InetSocketAddress("127.0.0.1", localPort), 5000)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("RelayManager", "Failed to connect to local Minecraft server: ${e.message}")
            relaySocket.close()
            return
        }

        android.util.Log.i("RelayManager", "Bridge ACTIVE: Relay <-> Local:$localPort")
        logThrottled("bridge-queues", intervalMs = 30_000L) {
            android.util.Log.i(
                "RelayManager",
                "Bridge queues: relaySend=${relaySocket.sendBufferSize}, relayReceive=${relaySocket.receiveBufferSize}, " +
                    "localSend=${localSocket.sendBufferSize}, localReceive=${localSocket.receiveBufferSize}"
            )
        }

        val relayToLocalThread = Thread {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_MORE_FAVORABLE)
            var totalBytes = 0L
            try {
                val output = localSocket.getOutputStream()
                // A Bedrock-to-Java handoff must keep using the BufferedInputStream.
                val relayInput = relayInputOverride ?: relaySocket.getInputStream()

                // Manually push the first byte to the server
                output.write(firstByte)
                totalBytes += 1

                // Continue streaming normally
                val buffer = ByteArray(PLAYER_BRIDGE_BUFFER_SIZE)
                var bytesRead: Int
                var consecutiveFullReads = 0

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

        val localToRelayThread = Thread {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_MORE_FAVORABLE)
            var totalBytes = 0L
            try {
                configureRelaySocket(relaySocket)
                configureLocalSocket(localSocket)
                val input = localSocket.getInputStream()
                val output = relaySocket.getOutputStream()
                val buffer = ByteArray(PLAYER_BRIDGE_UPSTREAM_BUFFER_SIZE)
                var bytesRead: Int

                while (true) {
                    bytesRead = try { input.read(buffer) } catch (e: Exception) { -1 }
                    if (bytesRead <= 0) break

                    output.write(buffer, 0, bytesRead)
                    totalBytes += bytesRead
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

    private fun configureLocalSocket(socket: Socket) {
        runCatching {
            socket.tcpNoDelay = true
            socket.keepAlive = true
            socket.reuseAddress = true
            socket.trafficClass = 0x10 // IPTOS_LOWDELAY
            socket.setPerformancePreferences(0, 2, 0) // latency > bandwidth > connection time
            // Do NOT set sendBufferSize/receiveBufferSize — let Linux TCP auto-tune.
            // Artificial limits throttle chunk bursts and inflate ping under load.
        }
    }

    private fun configureRelaySocket(socket: Socket) {
        runCatching {
            socket.tcpNoDelay = true
            socket.keepAlive = true
            socket.reuseAddress = true
            socket.trafficClass = 0x10 // IPTOS_LOWDELAY
            socket.setPerformancePreferences(0, 2, 0) // latency > bandwidth > connection time
            // Do NOT set sendBufferSize/receiveBufferSize — let Linux TCP auto-tune.
            // Artificial limits throttle chunk bursts and inflate ping under load.
        }
    }

    /**
     * Closes the tunnel sockets.
     * Call this when the server stops.
     */
    fun disconnect() {
        relayHostingAllowed.set(false)
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
        activeRelayIsFallback = false
        preferFallbackRelay = false
        _isPoolReady.value = false

        synchronized(socketPool) {
            socketPool.forEach { try { it.socket.close() } catch (_: Exception) {} }
            socketPool.clear()
        }
        connectingSockets.set(0)
        android.util.Log.i("RelayManager", "Tunnel disconnected and scope reset.")
    }

    private fun resetPoolSizing() {
        poolTargetSize.set(INITIAL_POOL_SIZE)
    }

    /**
     * Unregisters userId from relay server (frees the port).
     * Call this when the user explicitly stops hosting, not just on pause.
     */
    /**
     * Unregisters from a specific relay host during a live region switch.
     */
    suspend fun unregisterFromHost(relayHost: String) = withContext(Dispatchers.IO) {
        relayHostingAllowed.set(false)
        val prefs = com.pocketcraft.server.data.preferences.AppPreferences(context)
        val sessionId = activeRelaySessionId.takeIf { !it.isNullOrBlank() } ?: prefs.userId
        val normalizedHost = relayHost.trim()

        if (!sessionId.isNullOrBlank() && normalizedHost.isNotBlank()) {
            for (attempt in 0 until 3) {
                try {
                    val resolvedHost = resolveRelayIp(normalizedHost) ?: normalizedHost
                    val url = URL("http://$resolvedHost:$CONTROL_PORT/unregister")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.setRequestProperty("Host", normalizedHost)
                    conn.connectTimeout = 5_000
                    conn.readTimeout = 5_000
                    conn.doOutput = true
                    conn.outputStream.write("""{"userId":"$sessionId"}""".toByteArray(Charsets.UTF_8))
                    conn.outputStream.flush()
                    val responseCode = conn.responseCode
                    conn.disconnect()
                    if (responseCode in 200..299) {
                        android.util.Log.i("RelayManager", "Relay unregistered from $normalizedHost on attempt ${attempt + 1}")
                        break
                    }
                    android.util.Log.w("RelayManager", "Unregister from $normalizedHost HTTP $responseCode on attempt ${attempt + 1}")
                } catch (e: IOException) {
                    android.util.Log.w("RelayManager", "Unregister from $normalizedHost attempt ${attempt + 1} failed: ${e.message}")
                }
                if (attempt < 2) delay(400L * (attempt + 1))
            }
        }

        lastRegisteredHost = null
        activeRelayHost = null
        preferFallbackRelay = false
        disconnect()
    }

    suspend fun unregister() = withContext(Dispatchers.IO) {
        relayHostingAllowed.set(false)
        val prefs = com.pocketcraft.server.data.preferences.AppPreferences(context)
        val preferredRelayHost = prefs.relayHost
        val fallbackRelayHost = if (preferredRelayHost == RelayServers.MUMBAI.host) {
            RelayServers.EUROPE.host
        } else {
            RelayServers.MUMBAI.host
        }
        val candidateHosts = buildList {
            activeRelayHost?.let(::add)
            lastRegisteredHost?.let(::add)
            if (preferredRelayHost.isNotBlank()) add(preferredRelayHost)
            add(fallbackRelayHost)
        }.map { it.trim() }.filter { it.isNotBlank() }.distinct()

        candidateHosts.forEach { relayHost ->
            runCatching { unregisterFromHost(relayHost) }
                .onFailure { error ->
                    android.util.Log.w("RelayManager", "Unregister from $relayHost failed: ${error.message}")
                }
        }

        assignedPort = null
        prefs.relayPort = null
        lastRegisteredHost = null
        activeRelayHost = null
        activeRelaySessionId = null
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

    /**
     * Returns a region-scoped session ID so that each relay server assigns a
     * DIFFERENT port from its pool. Without this, all regions hash the same
     * userId to the same port, wasting the 10 000-slot capacity per server.
     *
     * Format: "<deviceId>-<region>" where <region> is a short slug derived from
     * the relay hostname (e.g. "mine.pocketcraft.online" → "mumbai").
     * This makes the ID deterministic — the same phone always gets the same
     * port on the same region, which keeps DNS-based routing stable.
     */
    private fun relaySessionIdForHost(relayHost: String): String {
        val base = currentRelaySessionId()
        val regionSlug = when {
            relayHost.contains("mine.") || relayHost.contains("mumbai") || relayHost.contains("india") -> "mumbai"
            relayHost.contains("eu.") || relayHost.contains("europe") || relayHost.contains("frankfurt") -> "eu"
            relayHost.contains("us.") || relayHost.contains("america") || relayHost.contains("ohio") -> "us"
            else -> relayHost.substringBefore(".").take(8).lowercase().ifBlank { "custom" }
        }
        return "$base-$regionSlug"
    }
}

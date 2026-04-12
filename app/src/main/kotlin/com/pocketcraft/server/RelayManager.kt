package com.pocketcraft.server

import android.content.Context
import com.pocketcraft.server.relay.BedrockUdpBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class RelayManager(private val context: Context) {

    companion object {
        const val CONTROL_PORT = 8080
        const val PHONE_TUNNEL_PORT = 9000
        private const val SOCKET_BUFFER_SIZE = 64 * 1024
        private const val PLAYER_BRIDGE_BUFFER_SIZE = 16 * 1024
        private const val LOW_LATENCY_WARMUP_BYTES = 128 * 1024L
        private const val LOW_LATENCY_WARMUP_NS = 4_000_000_000L
        private const val TARGET_POOL_SIZE = 5
        private const val POOL_REFRESH_FLOOR = 2
        private const val IDLE_REPLENISH_DELAY_MS = 1_500L
        private const val SOCKET_OPEN_STAGGER_MS = 250L
        private const val TUNNEL_HEARTBEAT_INTERVAL_MS = 15_000L
        private const val IDLE_SOCKET_REFRESH_INTERVAL_MS = 45_000L
        private const val INITIAL_POOL_READY_TIMEOUT_MS = 12_000L
    }

    private var activeRelaySessionId: String? = null
    var assignedPort: Int? = null
        private set

    private var bedrockUdpBridge: BedrockUdpBridge? = null
    @Volatile
    private var activeBedrockSocket: Socket? = null

    private val POOL_SIZE = TARGET_POOL_SIZE
    private val socketPool = mutableListOf<Socket>()
    private val connectingSockets = AtomicInteger(0)
    private val poolTopUpScheduled = AtomicBoolean(false)
    private val consecutiveFailures = AtomicInteger(0)
    private var poolJob = SupervisorJob()
    private var poolScope = CoroutineScope(Dispatchers.IO + poolJob)
    private var tunnelHeartbeatJob: kotlinx.coroutines.Job? = null
    @Volatile
    private var lastIdleSocketRefreshAtMs = 0L
    @Volatile
    private var activeTunnelLocalPort: Int? = null
    @Volatile
    private var resolvedRelayIp: String? = null

    data class RelayAddress(val host: String, val port: Int, val isFallback: Boolean = false) {
        override fun toString() = "$host:$port"
    }

    data class TunnelPoolResult(
        val readyAck: Boolean,
        val poolReady: Boolean
    )

    /**
     * Registers this user with the relay control API.
     * Returns the public address players should use to connect.
     * Safe to call multiple times — relay returns existing port if already registered.
     */
    suspend fun register(): RelayAddress = withContext(Dispatchers.IO) {
        val prefs = com.pocketcraft.server.data.preferences.AppPreferences(context)
        val userId = currentRelaySessionId()
        val relayHost = prefs.relayHost

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
            val address = RelayAddress(relayHost, assignedPort!!, isFallback = false)
            android.util.Log.d("RelayManager", "Register success: $address")
            address

        } finally {
            conn.disconnect()
        }
    }

    fun startBedrockBridge() {
        if (bedrockUdpBridge != null) return
        bedrockUdpBridge = BedrockUdpBridge { frame ->
            val socket = activeBedrockSocket
            if (socket != null && !socket.isClosed) {
                poolScope.launch(Dispatchers.IO) {
                    try {
                        val output = socket.getOutputStream()
                        synchronized(output) {
                            output.write(frame)
                            output.flush()
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("RelayManager", "Failed to send Bedrock response: ${e.message}")
                    }
                }
            }
        }
        bedrockUdpBridge?.start()
    }

    fun stopBedrockBridge() {
        bedrockUdpBridge?.stop()
        bedrockUdpBridge = null
        activeBedrockSocket = null
    }

    /**
     * Opens a pool of persistent TCP sockets from the phone to the relay.
     * The relay uses these sockets to forward incoming player traffic.
     * Must be called AFTER register() and AFTER the Minecraft server is ready on 25565.
     */
    suspend fun connectTunnelPool(localPort: Int): TunnelPoolResult = withContext(Dispatchers.IO) {
        android.util.Log.i("RelayManager", "Starting pool of $POOL_SIZE sockets...")
        activeTunnelLocalPort = localPort
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
        val readyAck = notifyPhoneReady(localPort)
        if (!readyAck) {
            android.util.Log.w(
                "RelayManager",
                "Relay control did not acknowledge phone-ready on known endpoints; player status ping may fail until relay API is updated."
            )
        }
        topUpPool(localPort)
        val poolReady = waitForInitialPoolReady()
        if (!poolReady) {
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
            if (currentPoolSize() > 0) {
                return true
            }
            delay(100)
        }
        return currentPoolSize() > 0
    }

    suspend fun notifyPhoneReady(localPort: Int): Boolean = withContext(Dispatchers.IO) {
        val prefs = com.pocketcraft.server.data.preferences.AppPreferences(context)
        val userId = currentRelaySessionId()
        val relayHost = prefs.relayHost
        val localIpCandidates = buildList {
            add(com.pocketcraft.server.server.ServerAddressResolver.getLocalIpAddress())
            add(resolveRouteLocalIp(relayHost))
        }
            .filterNotNull()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("127.") && it != "0.0.0.0" }
            .distinct()

        val localIp = localIpCandidates.firstOrNull()

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
                } finally {
                    conn.disconnect()
                }
            }
        }

        false
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

    private fun topUpPool(localPort: Int) {
        while (poolScope.isActive && reservePoolSlotIfAvailable(POOL_SIZE)) {
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

    private fun refreshOneIdlePoolSocketIfNeeded(localPort: Int) {
        val now = System.currentTimeMillis()
        if ((now - lastIdleSocketRefreshAtMs) < IDLE_SOCKET_REFRESH_INTERVAL_MS) return

        val socketToRefresh = synchronized(socketPool) {
            if (socketPool.size <= POOL_REFRESH_FLOOR) return@synchronized null
            socketPool.firstOrNull()
                ?.also { socketPool.remove(it) }
        } ?: return

        lastIdleSocketRefreshAtMs = now
        android.util.Log.v("RelayManager", "Refreshing one idle relay socket to avoid stale pool entries.")
        runCatching { socketToRefresh.close() }
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

                val prefs = com.pocketcraft.server.data.preferences.AppPreferences(context)
                val userId = currentRelaySessionId()
                val relayHost = prefs.relayHost

                var targetIp = resolvedRelayIp
                if (targetIp == null) {
                    targetIp = java.net.InetAddress.getByName(relayHost).hostAddress.also {
                        resolvedRelayIp = it
                    }
                }

                socket = Socket()
                configureSocket(socket)
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_MORE_FAVORABLE)
                socket.connect(java.net.InetSocketAddress(targetIp, PHONE_TUNNEL_PORT), 10_000)

                socket.outputStream.write("$userId\n".toByteArray(Charsets.UTF_8))
                socket.outputStream.flush()

                synchronized(socketPool) {
                    socketPool.add(socket!!)
                    addedToPool = true
                }
                releaseReservedSlot()
                android.util.Log.v("RelayManager", "Socket added to pool. Size: ${socketPool.size}/$POOL_SIZE")

                // Add a staggered timeout so sockets naturally rotate before the remote LB drops them
                val baseTimeoutMs = 40_000
                socket!!.soTimeout = baseTimeoutMs + kotlin.random.Random.nextInt(15_000)

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

                synchronized(socketPool) { socketPool.remove(socket!!) }
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
                    socket?.let { socketPool.remove(it) }
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
        android.util.Log.i("RelayManager", "Bedrock UDP bridge ACTIVE via TCP tunnel.")
        activeBedrockSocket = relaySocket
        var handedOffToJavaBridge = false
        
        try {
            val relayInput = relaySocket.getInputStream()
            
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
                bedrockUdpBridge?.onIncomingFrame(firstFrame)
            }
            
            // Now loop for subsequent frames on this same TCP socket
            while (true) {
                val type = relayInput.read()
                if (type == -1) break
                if (type != 0x02) {
                    android.util.Log.i("RelayManager", "Switching TCP socket from Bedrock to Java bridge (firstByte=$type)")
                    val localPort = activeTunnelLocalPort ?: 25565
                    if (activeBedrockSocket == relaySocket) {
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
                
                bedrockUdpBridge?.onIncomingFrame(frame)
            }
        } catch (e: Exception) {
            android.util.Log.e("RelayManager", "Bedrock bridge error: ${e.message}")
        } finally {
            if (activeBedrockSocket == relaySocket) {
                activeBedrockSocket = null
            }
            if (!handedOffToJavaBridge) {
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

        val relayToLocal = poolScope.launch(Dispatchers.IO) {
            var totalBytes = 0L
            try {
                val output = localSocket.getOutputStream()
                val relayInput = relaySocket.getInputStream()

                // Manually push the first byte to the server
                output.write(firstByte)
                output.flush()
                totalBytes += 1

                // Continue streaming normally
                val buffer = ByteArray(PLAYER_BRIDGE_BUFFER_SIZE)
                var bytesRead: Int
                var unflushedBytes = 0
                var lastFlushTime = System.nanoTime()
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY)

                while (true) {
                    bytesRead = try { relayInput.read(buffer) } catch (e: Exception) { -1 }
                    if (bytesRead <= 0) break

                    val startTime = System.nanoTime()
                    output.write(buffer, 0, bytesRead)
                    unflushedBytes += bytesRead
                    totalBytes += bytesRead

                    // Keep the first moments of a connection ultra-low-latency so
                    // Minecraft status pings and login handshakes answer immediately.
                    val lowLatencyWarmup = totalBytes <= LOW_LATENCY_WARMUP_BYTES ||
                        (System.nanoTime() - bridgeStartedAt) <= LOW_LATENCY_WARMUP_NS
                    val shouldFlush = lowLatencyWarmup ||
                        unflushedBytes >= 4_096 ||
                        (unflushedBytes > 0 && (System.nanoTime() - lastFlushTime) > 8_000_000) // 8ms

                    if (shouldFlush) {
                        output.flush()
                        unflushedBytes = 0
                        lastFlushTime = System.nanoTime()
                    }

                    val durationMicros = (System.nanoTime() - startTime) / 1000
                    if (durationMicros > 50_000) {
                         android.util.Log.w("RelayManager", "RelayToLocal stall! Wrote $bytesRead bytes in ${durationMicros}μs")
                    }
                }
                // Final flush
                if (unflushedBytes > 0) output.flush()
                android.util.Log.d("RelayManager", "RelayToLocal: End of stream. Total downstream: $totalBytes bytes")
            } catch (e: Exception) {
                android.util.Log.e("RelayManager", "RelayToLocal error: ${e.message}")
            } finally {
                // Graceful half-close to allow pending data to finish
                runCatching { localSocket.shutdownOutput() }
            }
        }

        val localToRelay = poolScope.launch(Dispatchers.IO) {
            var totalBytes = 0L
            try {
                val input = localSocket.getInputStream()
                val output = relaySocket.getOutputStream()
                val buffer = ByteArray(PLAYER_BRIDGE_BUFFER_SIZE)
                var bytesRead: Int
                var unflushedBytes = 0
                var lastFlushTime = System.nanoTime()
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY)

                while (true) {
                    bytesRead = try { input.read(buffer) } catch (e: Exception) { -1 }
                    if (bytesRead <= 0) break

                    val startTime = System.nanoTime()
                    output.write(buffer, 0, bytesRead)
                    unflushedBytes += bytesRead
                    totalBytes += bytesRead

                    val lowLatencyWarmup = totalBytes <= LOW_LATENCY_WARMUP_BYTES ||
                        (System.nanoTime() - bridgeStartedAt) <= LOW_LATENCY_WARMUP_NS
                    val shouldFlush = lowLatencyWarmup ||
                        unflushedBytes >= 4_096 ||
                        (unflushedBytes > 0 && (System.nanoTime() - lastFlushTime) > 8_000_000) // 8ms

                    if (shouldFlush) {
                        output.flush()
                        unflushedBytes = 0
                        lastFlushTime = System.nanoTime()
                    }

                    val durationMicros = (System.nanoTime() - startTime) / 1000
                    if (durationMicros > 100_000) {
                        android.util.Log.d("RelayManager", "LocalToRelay write delay: ${durationMicros}μs for $bytesRead bytes")
                    }
                }
                // Final flush
                if (unflushedBytes > 0) output.flush()
                android.util.Log.d("RelayManager", "LocalToRelay: End of stream. Total upstream: $totalBytes bytes")
            } catch (e: Exception) {
                android.util.Log.e("RelayManager", "LocalToRelay error: ${e.message}")
            } finally {
                // Graceful half-close so the relay proxy flushes completely to client
                runCatching { relaySocket.shutdownOutput() }
            }
        }

        poolScope.launch(Dispatchers.IO) {
            relayToLocal.join()
            localToRelay.join()
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
            // Latency = 1, Bandwidth = 0, ConnectionTime = 0
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
        poolJob.cancel()
        poolJob = SupervisorJob()
        poolScope = CoroutineScope(Dispatchers.IO + poolJob)
        poolTopUpScheduled.set(false)
        lastIdleSocketRefreshAtMs = 0L
        resolvedRelayIp = null

        synchronized(socketPool) {
            socketPool.forEach { try { it.close() } catch (_: Exception) {} }
            socketPool.clear()
        }
        connectingSockets.set(0)
        // assignedPort = null // Keep port persistent across restarts
        activeRelaySessionId = null
        android.util.Log.i("RelayManager", "Tunnel disconnected and scope reset.")
    }

    /**
     * Unregisters userId from relay server (frees the port).
     * Call this when the user explicitly stops hosting, not just on pause.
     */
    suspend fun unregister() = withContext(Dispatchers.IO) {
        val prefs = com.pocketcraft.server.data.preferences.AppPreferences(context)
        val relayHost = prefs.relayHost
        val sessionId = activeRelaySessionId

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

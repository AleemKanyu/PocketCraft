package com.pocketcraft.server

import android.content.Context
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
import java.util.concurrent.atomic.AtomicInteger

class RelayManager(private val context: Context) {

    companion object {
        const val CONTROL_PORT = 8080
        const val PHONE_TUNNEL_PORT = 9000
        private const val SOCKET_BUFFER_SIZE = 256 * 1024 // 256KB - optimized for network distance
        private const val LOW_LATENCY_WARMUP_BYTES = 32 * 1024L
        private const val LOW_LATENCY_WARMUP_NS = 2_000_000_000L
    }

    private var activeRelaySessionId: String? = null
    var assignedPort: Int? = null
        private set

    private val POOL_SIZE = 25  // Increased pool size for better concurrency on distant relays
    private val socketPool = mutableListOf<Socket>()
    private val connectingSockets = AtomicInteger(0)
    private var poolJob = SupervisorJob()
    private var poolScope = CoroutineScope(Dispatchers.IO + poolJob)
    private var tunnelHeartbeatJob: kotlinx.coroutines.Job? = null
    @Volatile
    private var activeTunnelLocalPort: Int? = null

    data class RelayAddress(val host: String, val port: Int, val isFallback: Boolean = false) {
        override fun toString() = "$host:$port"
    }

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

    /**
     * Opens a pool of persistent TCP sockets from the phone to the relay.
     * The relay uses these sockets to forward incoming player traffic.
     * Must be called AFTER register() and AFTER the Minecraft server is ready on 25565.
     */
    suspend fun connectTunnelPool(localPort: Int) = withContext(Dispatchers.IO) {
        android.util.Log.i("RelayManager", "Starting pool of $POOL_SIZE sockets...")
        activeTunnelLocalPort = localPort
        tunnelHeartbeatJob?.cancel()
        tunnelHeartbeatJob = poolScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(30_000)
                val currentLocalPort = activeTunnelLocalPort ?: break
                val readyAck = notifyPhoneReady(currentLocalPort)
                if (!readyAck) {
                    android.util.Log.w(
                        "RelayManager",
                        "Relay heartbeat did not get phone-ready acknowledgment; refreshing socket pool."
                    )
                }
                topUpPool(currentLocalPort)
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
        readyAck
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
                add("""{"userId":"$userId","host":"$candidateHost","port":$localPort}""")
                add("""{"userId":"$userId","host":"$candidateHost"}""")
                add("""{"userId":"$userId","ip":"$candidateHost","port":$localPort}""")
                add("""{"userId":"$userId","ip":"$candidateHost"}""")
                add("""{"userId":"$userId","localIp":"$candidateHost","port":$localPort}""")
                add("""{"userId":"$userId","localHost":"$candidateHost","port":$localPort}""")
            }
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
        while (poolScope.isActive && reservePoolSlotIfAvailable()) {
            openPhoneSocket(localPort)
        }
    }

    private fun reservePoolSlotIfAvailable(): Boolean {
        synchronized(socketPool) {
            val currentConnecting = connectingSockets.get()
            if (socketPool.size + currentConnecting >= POOL_SIZE) {
                return false
            }
            connectingSockets.incrementAndGet()
            return true
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
                val prefs = com.pocketcraft.server.data.preferences.AppPreferences(context)
                val userId = currentRelaySessionId()
                val relayHost = prefs.relayHost

                socket = Socket()
                configureSocket(socket)
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_MORE_FAVORABLE)
                socket.connect(java.net.InetSocketAddress(relayHost, PHONE_TUNNEL_PORT), 10_000)

                socket.outputStream.write("$userId\n".toByteArray(Charsets.UTF_8))
                socket.outputStream.flush()

                synchronized(socketPool) {
                    socketPool.add(socket!!)
                    addedToPool = true
                }
                releaseReservedSlot()
                android.util.Log.v("RelayManager", "Socket added to pool. Size: ${socketPool.size}/$POOL_SIZE")

                val firstByte = socket.inputStream.read()

                synchronized(socketPool) { socketPool.remove(socket!!) }
                addedToPool = false

                if (firstByte != -1) {
                    android.util.Log.d("RelayManager", "Socket consumed (firstByte=$firstByte), replenishing pool...")
                    topUpPool(localPort)
                    bridgePlayerConnection(socket!!, firstByte, localPort)
                } else {
                    android.util.Log.v("RelayManager", "Socket timed out or closed by relay, replenishing pool...")
                    socket!!.close()
                    topUpPool(localPort)
                }

            } catch (e: Exception) {
                android.util.Log.e("RelayManager", "Pool socket error: ${e.message}")
                synchronized(socketPool) {
                    socket?.let { socketPool.remove(it) }
                }
                addedToPool = false
                releaseReservedSlot()
                socket?.close()
                if (poolScope.isActive) {
                    delay(3000)
                    topUpPool(localPort)
                }
            } finally {
                releaseReservedSlot()
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
                val buffer = ByteArray(SOCKET_BUFFER_SIZE)
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
                        unflushedBytes >= 16_384 ||
                        (unflushedBytes > 0 && (System.nanoTime() - lastFlushTime) > 30_000_000) // 30ms

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
                val buffer = ByteArray(SOCKET_BUFFER_SIZE)
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
                        unflushedBytes >= 16_384 ||
                        (unflushedBytes > 0 && (System.nanoTime() - lastFlushTime) > 30_000_000) // 30ms

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
        tunnelHeartbeatJob?.cancel()
        tunnelHeartbeatJob = null
        activeTunnelLocalPort = null
        poolJob.cancel()
        poolJob = SupervisorJob()
        poolScope = CoroutineScope(Dispatchers.IO + poolJob)

        synchronized(socketPool) {
            socketPool.forEach { try { it.close() } catch (_: Exception) {} }
            socketPool.clear()
        }
        connectingSockets.set(0)
        assignedPort = null
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

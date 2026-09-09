/**
 * High-performance UDP datagram bridge forwarding Bedrock RakNet packets
 * between remote relays and local Bedrock/Geyser listeners.
 */
package com.pockethost.app.relay

import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.PortUnreachableException
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap

class BedrockUdpBridge(
    private val geyserHostProvider: () -> String = { GEYSER_LOCAL_HOST },
    private val onResponse: (ByteArray) -> Unit
) {

    companion object {
        private const val TAG = "BedrockUdpBridge"
        private const val GEYSER_LOCAL_PORT = 19132
        private const val MAX_UDP_SIZE = 65536
        private const val GEYSER_LOCAL_HOST = "127.0.0.1"
    }

    @Volatile
    private var running = false
    private val clientSockets = ConcurrentHashMap<String, DatagramSocket>()

    fun start() {
        if (running) return
        running = true
        Log.i(TAG, "Bedrock UDP bridge started (Ultra-Low Latency Direct Pipeline)")
    }

    fun onIncomingFrame(data: ByteArray) {
        if (!running) return
        val frame = parseIncomingFrame(data) ?: return
        try {
            forwardFrameToGeyser(frame)
        } catch (e: Exception) {
            Log.e(TAG, "Error forwarding to Geyser: ${e.message}")
        }
    }

    private fun forwardFrameToGeyser(frame: BedrockFrame) {
        val clientKey = "${frame.clientIp}:${frame.clientPort}"
        val socket = clientSockets.computeIfAbsent(clientKey) {
            val geyserHost = geyserHostProvider()
                .trim()
                .takeIf { it.isNotBlank() }
                ?: GEYSER_LOCAL_HOST
            try {
                DatagramSocket().also { datagramSocket ->
                    datagramSocket.receiveBufferSize = 256 * 1024  // 256 KB (low-latency queue)
                    datagramSocket.sendBufferSize   = 256 * 1024  // 256 KB

                    datagramSocket.soTimeout = 30000 // 30s timeout
                    runCatching { datagramSocket.trafficClass = 0x10 } // IPTOS_LOWDELAY
                    datagramSocket.reuseAddress = true
                    datagramSocket.connect(InetAddress.getByName(geyserHost), GEYSER_LOCAL_PORT)
                    startListening(datagramSocket, frame.clientIp, frame.clientPort)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create DatagramSocket for $clientKey: ${e.message}")
                throw e
            }
        }

        try {
            val packet = DatagramPacket(frame.payload, frame.payload.size)
            socket.send(packet)
        } catch (e: PortUnreachableException) {
            Log.w(TAG, "Geyser port unreachable, frame dropped for ${frame.clientIp}")
        }
    }

    private fun startListening(socket: DatagramSocket, clientIp: String, clientPort: Int) {
        val ipBytes = runCatching {
            val addr = InetAddress.getByName(clientIp)
            if (addr is java.net.Inet4Address) addr.address else null
        }.getOrNull() ?: runCatching {
            val parts = clientIp.split(".").map { it.toIntOrNull() ?: 0 }
            if (parts.size == 4) byteArrayOf(parts[0].toByte(), parts[1].toByte(), parts[2].toByte(), parts[3].toByte()) else null
        }.getOrNull() ?: byteArrayOf(127, 0, 0, 1)

        val thread = Thread {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY)
            try {
                val buffer = ByteArray(MAX_UDP_SIZE)
                val packet = DatagramPacket(buffer, buffer.size)

                while (running && !socket.isClosed) {
                    try {
                        packet.length = buffer.size
                        socket.receive(packet)

                        val payloadLen = packet.length

                        val frameLen = 1 + 2 + 4 + 2 + payloadLen
                        val frame = ByteArray(frameLen)
                        frame[0] = 0x03
                        frame[1] = (payloadLen shr 8).toByte()
                        frame[2] = payloadLen.toByte()
                        frame[3] = ipBytes[0]
                        frame[4] = ipBytes[1]
                        frame[5] = ipBytes[2]
                        frame[6] = ipBytes[3]
                        frame[7] = (clientPort shr 8).toByte()
                        frame[8] = clientPort.toByte()
                        System.arraycopy(buffer, 0, frame, 9, payloadLen)

                        onResponse(frame)
                    } catch (e: SocketTimeoutException) {
                        break
                    } catch (e: PortUnreachableException) {
                        try { Thread.sleep(1000) } catch (_: InterruptedException) { break }
                    } catch (e: Exception) {
                        break
                    }
                }
            } catch (e: Exception) {
                // Ignore
            } finally {
                clientSockets.remove("$clientIp:$clientPort")
                runCatching { socket.close() }
            }
        }
        thread.name = "BedrockUDP-$clientIp:$clientPort"
        thread.priority = Thread.MAX_PRIORITY
        thread.start()
    }

    fun stop() {
        if (!running) return
        running = false
        clientSockets.values.forEach { runCatching { it.close() } }
        clientSockets.clear()
        Log.i(TAG, "Bedrock UDP bridge stopped")
    }

    data class BedrockFrame(val clientIp: String, val clientPort: Int, val payload: ByteArray)

    fun parseIncomingFrame(data: ByteArray): BedrockFrame? {
        if (data.size < 9) return null
        if (data[0] != 0x02.toByte()) return null
        val payloadLen = ((data[1].toInt() and 0xFF) shl 8) or (data[2].toInt() and 0xFF)
        val ip = "${data[3].toInt() and 0xFF}.${data[4].toInt() and 0xFF}.${data[5].toInt() and 0xFF}.${data[6].toInt() and 0xFF}"
        val port = ((data[7].toInt() and 0xFF) shl 8) or (data[8].toInt() and 0xFF)
        if (port !in 1..65_535) {
            Log.w(TAG, "Dropping incoming Bedrock frame with invalid clientPort=$port from $ip")
            return null
        }
        if (ip == "0.0.0.0") {
            Log.w(TAG, "Dropping incoming Bedrock frame with invalid clientIp=$ip")
            return null
        }
        if (data.size < 9 + payloadLen) return null
        val payload = data.copyOfRange(9, 9 + payloadLen)
        return BedrockFrame(ip, port, payload)
    }
}

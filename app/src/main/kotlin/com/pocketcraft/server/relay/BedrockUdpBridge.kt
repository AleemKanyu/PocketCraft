package com.pocketcraft.server.relay

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

class BedrockUdpBridge(private val onResponse: (ByteArray) -> Unit) {

    companion object {
        private const val TAG = "BedrockUdpBridge"
        private const val GEYSER_LOCAL_PORT = 19132
        private const val MAX_UDP_SIZE = 2048
        private const val GEYSER_LOCAL_HOST = "127.0.0.1"
    }

    private var running = false
    private var bridgeJob: Job? = null
    private var scope: CoroutineScope? = null
    private val clientSockets = ConcurrentHashMap<String, DatagramSocket>()

    fun start() {
        if (running) return
        running = true
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        bridgeJob = scope?.launch { }
        Log.i(TAG, "Bedrock UDP bridge started")
    }

    fun onIncomingFrame(data: ByteArray) {
        if (!running) return
        val frame = parseIncomingFrame(data) ?: return
        val clientKey = "${frame.clientIp}:${frame.clientPort}"

        scope?.launch {
            try {
                val socket = clientSockets.computeIfAbsent(clientKey) {
                    DatagramSocket().also { startListening(it, frame.clientIp, frame.clientPort) }
                }
                val address = InetAddress.getByName(GEYSER_LOCAL_HOST)
                val packet = DatagramPacket(frame.payload, frame.payload.size, address, GEYSER_LOCAL_PORT)
                socket.send(packet)
            } catch (e: Exception) {
                Log.e(TAG, "Error forwarding to Geyser: ${e.message}")
            }
        }
    }

    private fun startListening(socket: DatagramSocket, clientIp: String, clientPort: Int) {
        scope?.launch {
            val buffer = ByteArray(MAX_UDP_SIZE)
            try {
                while (isActive) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)

                    val payload = packet.data.copyOfRange(0, packet.length)
                    val responseFrame = buildResponseFrame(clientIp, clientPort, payload)
                    onResponse(responseFrame)
                }
            } catch (e: Exception) {
                if (isActive) Log.v(TAG, "Socket closed for $clientIp:$clientPort")
            } finally {
                clientSockets.remove("$clientIp:$clientPort")
                socket.close()
            }
        }
    }

    fun stop() {
        if (!running) return
        running = false
        bridgeJob?.cancel()
        bridgeJob = null
        clientSockets.values.forEach { it.close() }
        clientSockets.clear()
        scope?.cancel()
        scope = null
        Log.i(TAG, "Bedrock UDP bridge stopped")
    }

    data class BedrockFrame(val clientIp: String, val clientPort: Int, val payload: ByteArray)

    fun parseIncomingFrame(data: ByteArray): BedrockFrame? {
        if (data.size < 9) return null
        if (data[0] != 0x02.toByte()) return null
        val payloadLen = ((data[1].toInt() and 0xFF) shl 8) or (data[2].toInt() and 0xFF)
        val ip = "${data[3].toInt() and 0xFF}.${data[4].toInt() and 0xFF}.${data[5].toInt() and 0xFF}.${data[6].toInt() and 0xFF}"
        val port = ((data[7].toInt() and 0xFF) shl 8) or (data[8].toInt() and 0xFF)
        if (data.size < 9 + payloadLen) return null
        val payload = data.copyOfRange(9, 9 + payloadLen)
        return BedrockFrame(ip, port, payload)
    }

    fun buildResponseFrame(clientIp: String, clientPort: Int, payload: ByteArray): ByteArray {
        val ipParts = clientIp.split(".").map { it.toInt() }
        val frame = ByteArray(1 + 2 + 4 + 2 + payload.size)
        var offset = 0
        frame[offset++] = 0x03.toByte()
        frame[offset++] = (payload.size shr 8).toByte()
        frame[offset++] = (payload.size and 0xFF).toByte()
        frame[offset++] = ipParts[0].toByte()
        frame[offset++] = ipParts[1].toByte()
        frame[offset++] = ipParts[2].toByte()
        frame[offset++] = ipParts[3].toByte()
        frame[offset++] = (clientPort shr 8).toByte()
        frame[offset++] = (clientPort and 0xFF).toByte()
        payload.copyInto(frame, offset)
        return frame
    }
}

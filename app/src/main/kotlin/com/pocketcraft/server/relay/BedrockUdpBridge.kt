package com.pocketcraft.server.relay

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

class BedrockUdpBridge(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val geyserHostProvider: () -> String = { GEYSER_LOCAL_HOST },
    private val onResponse: (ByteArray) -> Unit
) {

    companion object {
        private const val TAG = "BedrockUdpBridge"
        private const val GEYSER_LOCAL_PORT = 19132
        private const val MAX_UDP_SIZE = 65536
        private const val GEYSER_LOCAL_HOST = "127.0.0.1"
        private const val INBOUND_CHANNEL_CAPACITY = Channel.UNLIMITED
    }

    private var running = false
    private var bridgeJob: Job? = null
    private var outboundJob: Job? = null
    private var scope: CoroutineScope? = null
    private val clientSockets = ConcurrentHashMap<String, DatagramSocket>()
    private val inboundFrames = Channel<BedrockFrame>(capacity = INBOUND_CHANNEL_CAPACITY)
    private val outboundFrames = Channel<ByteArray>(capacity = INBOUND_CHANNEL_CAPACITY)

    fun start() {
        if (running) return
        running = true
        scope = CoroutineScope(SupervisorJob() + dispatcher)
        bridgeJob = scope?.launch {
            for (frame in inboundFrames) {
                try {
                    forwardFrameToGeyser(frame)
                } catch (e: Exception) {
                    Log.e(TAG, "Error forwarding to Geyser: ${e.message}")
                }
            }
        }
        
        outboundJob = scope?.launch {
            for (frame in outboundFrames) {
                try {
                    onResponse(frame)
                } catch (e: Exception) {
                    Log.e(TAG, "Error writing outbound frame: ${e.message}")
                }
            }
        }
        Log.i(TAG, "Bedrock UDP bridge started")
    }

    fun onIncomingFrame(data: ByteArray) {
        if (!running) return
        val frame = parseIncomingFrame(data) ?: return
        val result = inboundFrames.trySend(frame)
        if (result.isFailure) {
            Log.w(TAG, "Dropping Bedrock frame for ${frame.clientIp}:${frame.clientPort} due to bridge backpressure")
        }
    }

    private fun forwardFrameToGeyser(frame: BedrockFrame) {
        val clientKey = "${frame.clientIp}:${frame.clientPort}"
        val socket = clientSockets.computeIfAbsent(clientKey) {
            val geyserHost = geyserHostProvider()
                .trim()
                .takeIf { it.isNotBlank() }
                ?: GEYSER_LOCAL_HOST
            DatagramSocket().also { datagramSocket ->
                // Large receive buffer so the OS can absorb Geyser's chunk burst
                // without silently dropping datagrams before our receive() loop picks them up.
                datagramSocket.receiveBufferSize = 4 * 1024 * 1024  // 4 MB
                datagramSocket.sendBufferSize   = 1024 * 1024        // 1 MB
                datagramSocket.connect(InetAddress.getByName(geyserHost), GEYSER_LOCAL_PORT)
                startListening(datagramSocket, frame.clientIp, frame.clientPort)
            }
        }

        val packet = DatagramPacket(frame.payload, frame.payload.size)
        socket.send(packet)
    }

    private fun startListening(socket: DatagramSocket, clientIp: String, clientPort: Int) {
        val ipParts = clientIp.split(".").map { it.toIntOrNull() ?: 0 }
        val ipBytes = byteArrayOf(ipParts[0].toByte(), ipParts[1].toByte(), ipParts[2].toByte(), ipParts[3].toByte())

        scope?.launch {
            try {
                val buffer = ByteArray(MAX_UDP_SIZE)
                val packet = DatagramPacket(buffer, buffer.size)

                while (isActive) {
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

                    val result = outboundFrames.trySend(frame)
                    if (result.isFailure) {
                        Log.w(TAG, "Dropping outbound Bedrock frame due to TCP write backpressure")
                    }
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
        outboundJob?.cancel()
        outboundJob = null
        clientSockets.values.forEach { it.close() }
        clientSockets.clear()
        scope?.cancel()
        scope = null
        while (inboundFrames.tryReceive().isSuccess) {}
        while (outboundFrames.tryReceive().isSuccess) {}
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

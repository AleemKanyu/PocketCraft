package com.pockethost.app.server

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer

/**
 * Liveness probe for a local Bedrock server.
 *
 * A Bedrock server listens on UDP, so the TCP `connect()` used to detect a running Java server
 * never succeeds against one — a connect to a UDP port either fails outright or, worse, appears
 * to succeed against whatever else holds the TCP port. Instead this sends RakNet's
 * UNCONNECTED_PING and waits for the server's UNCONNECTED_PONG, which is the same handshake a
 * Bedrock client performs when it lists a server, so a pong proves the server is genuinely
 * accepting players rather than merely holding a socket.
 */
object BedrockPortProbe {

    private const val ID_UNCONNECTED_PING: Byte = 0x01
    private const val ID_UNCONNECTED_PONG: Byte = 0x1c

    private val RAKNET_MAGIC = byteArrayOf(
        0x00, 0xff.toByte(), 0xff.toByte(), 0x00,
        0xfe.toByte(), 0xfe.toByte(), 0xfe.toByte(), 0xfe.toByte(),
        0xfd.toByte(), 0xfd.toByte(), 0xfd.toByte(), 0xfd.toByte(),
        0x12, 0x34, 0x56, 0x78
    )

    /** Client GUID; any stable non-zero value is fine for an unconnected ping. */
    private const val CLIENT_GUID = 0x504F434B4554484FL // "POCKETHO"

    /**
     * Returns true when a Bedrock server answers on [port] of the loopback interface.
     *
     * @param timeoutMs how long to wait for the pong before giving up.
     */
    fun isOpen(port: Int, timeoutMs: Int = 400): Boolean {
        if (port !in 1..65_535) return false
        return runCatching {
            DatagramSocket().use { socket ->
                socket.soTimeout = timeoutMs
                val ping = buildPing()
                socket.send(
                    DatagramPacket(ping, ping.size, InetAddress.getByName("127.0.0.1"), port)
                )
                val buffer = ByteArray(2048)
                val response = DatagramPacket(buffer, buffer.size)
                socket.receive(response)
                response.length > 0 && buffer[0] == ID_UNCONNECTED_PONG
            }
        }.getOrDefault(false)
    }

    private fun buildPing(): ByteArray {
        val buffer = ByteBuffer.allocate(1 + 8 + RAKNET_MAGIC.size + 8)
        buffer.put(ID_UNCONNECTED_PING)
        buffer.putLong(System.currentTimeMillis())
        buffer.put(RAKNET_MAGIC)
        buffer.putLong(CLIENT_GUID)
        return buffer.array()
    }
}

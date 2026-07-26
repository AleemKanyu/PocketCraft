package com.pocketcraft.server.network

import android.util.Log
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets

object RconClient {
    private const val TAG = "RconClient"
    private const val DEFAULT_PORT = 25575
    private const val DEFAULT_PASSWORD = "pocketcraft-internal-rcon"
    private const val DEFAULT_TIMEOUT_MS = 2500

    private const val SERVERDATA_AUTH = 3
    private const val SERVERDATA_EXECCOMMAND = 2
    private const val SERVERDATA_RESPONSE_VALUE = 0
    private const val SERVERDATA_AUTH_RESPONSE = 2

    fun sendCommand(
        command: String,
        port: Int = DEFAULT_PORT,
        password: String = DEFAULT_PASSWORD,
        timeoutMs: Int = DEFAULT_TIMEOUT_MS
    ): String {
        val trimmedCommand = command.trim()
        if (trimmedCommand.isBlank()) return ""

        return runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", port), timeoutMs)
                socket.soTimeout = timeoutMs
                val out = DataOutputStream(socket.getOutputStream())
                val inp = DataInputStream(socket.getInputStream())

                fun writeIntLE(value: Int) {
                    out.write(value and 0xFF)
                    out.write((value shr 8) and 0xFF)
                    out.write((value shr 16) and 0xFF)
                    out.write((value shr 24) and 0xFF)
                }

                fun readIntLE(): Int {
                    val b0 = inp.read()
                    val b1 = inp.read()
                    val b2 = inp.read()
                    val b3 = inp.read()
                    if (b0 < 0 || b1 < 0 || b2 < 0 || b3 < 0) return -1
                    return (b0 and 0xFF) or ((b1 and 0xFF) shl 8) or ((b2 and 0xFF) shl 16) or ((b3 and 0xFF) shl 24)
                }

                fun sendPacket(id: Int, type: Int, payload: String) {
                    val payloadBytes = payload.toByteArray(StandardCharsets.UTF_8)
                    val packetLength = 4 + 4 + payloadBytes.size + 2
                    writeIntLE(packetLength)
                    writeIntLE(id)
                    writeIntLE(type)
                    out.write(payloadBytes)
                    out.write(0)
                    out.write(0)
                    out.flush()
                }

                // 1. Send Authentication packet (type 3)
                sendPacket(1, SERVERDATA_AUTH, password)
                val authLength = readIntLE()
                if (authLength < 10) return ""

                val authId = readIntLE()
                val authType = readIntLE()
                val authPayloadSize = (authLength - 10).coerceAtLeast(0)
                if (authPayloadSize > 0) {
                    inp.skipBytes(authPayloadSize)
                }
                inp.read() // null byte
                inp.read() // null byte

                if (authId == -1) {
                    Log.w(TAG, "RCON authentication failed (invalid password).")
                    return ""
                }

                // 2. Send Command packet (type 2)
                sendPacket(2, SERVERDATA_EXECCOMMAND, trimmedCommand)
                val cmdLength = readIntLE()
                if (cmdLength < 10) return "[OK]"

                val cmdId = readIntLE()
                val cmdType = readIntLE()
                val cmdPayloadSize = (cmdLength - 10).coerceAtLeast(0)
                val responseBytes = ByteArray(cmdPayloadSize)
                if (cmdPayloadSize > 0) {
                    inp.readFully(responseBytes)
                }
                inp.read() // null byte
                inp.read() // null byte

                val responseText = String(responseBytes, StandardCharsets.UTF_8).trim()
                if (responseText.isBlank()) "[OK]" else responseText
            }
        }.getOrElse { error ->
            Log.d(TAG, "RCON command connection failed for '$trimmedCommand': ${error.message}")
            ""
        }
    }

    fun sendCommands(
        commands: List<String>,
        port: Int = DEFAULT_PORT,
        password: String = DEFAULT_PASSWORD,
        timeoutMs: Int = DEFAULT_TIMEOUT_MS
    ): List<String> {
        if (commands.isEmpty()) return emptyList()
        return commands.map { cmd -> sendCommand(cmd, port, password, timeoutMs) }
    }
}

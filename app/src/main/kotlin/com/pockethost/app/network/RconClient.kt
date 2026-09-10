package com.pockethost.app.network

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

    /**
     * How many packets to tolerate before the auth response arrives. Valve-style RCON servers
     * send an empty SERVERDATA_RESPONSE_VALUE first; vanilla/Paper send only the auth response.
     */
    private const val MAX_AUTH_PACKETS = 5

    /** Guards against a bogus length header making us allocate an absurd buffer. */
    private const val MAX_PAYLOAD_BYTES = 4 * 1024 * 1024

    fun sendCommand(
        command: String,
        port: Int = DEFAULT_PORT,
        password: String = DEFAULT_PASSWORD,
        timeoutMs: Int = DEFAULT_TIMEOUT_MS
    ): String {
        val trimmedCommand = command.trim().removePrefix("/")
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

                fun readPacket(): Triple<Int, Int, String> {
                    val len = readIntLE()
                    if (len < 10 || len > MAX_PAYLOAD_BYTES) return Triple(-1, -1, "")
                    val id = readIntLE()
                    val type = readIntLE()
                    val payloadSize = (len - 10).coerceAtLeast(0)
                    val bytes = if (payloadSize > 0) ByteArray(payloadSize).also { inp.readFully(it) } else ByteArray(0)
                    inp.read(); inp.read() // null terminators
                    return Triple(id, type, String(bytes, StandardCharsets.UTF_8).trim())
                }

                // 1. Send Authentication packet (type 3)
                sendPacket(1, SERVERDATA_AUTH, password)
                
                // Read until we get SERVERDATA_AUTH_RESPONSE (type 2) or failure (id == -1).
                // Some server implementations emit an empty SERVERDATA_RESPONSE_VALUE before the
                // auth response, so tolerate a few leading packets -- but stop reading the moment
                // auth succeeds. Reading past it blocks until soTimeout and fails the whole call.
                var authenticated = false
                var authAttempts = 0
                while (!authenticated && authAttempts < MAX_AUTH_PACKETS) {
                    authAttempts++
                    val (authId, authType, _) = readPacket()
                    if (authId == -1) {
                        Log.w(TAG, "RCON authentication failed (invalid password).")
                        return ""
                    }
                    if (authType == SERVERDATA_AUTH_RESPONSE && authId == 1) {
                        authenticated = true
                    }
                }

                if (!authenticated) {
                    Log.w(TAG, "RCON authentication timeout or invalid response.")
                    return ""
                }

                // 2. Send Command packet (type 2)
                sendPacket(2, SERVERDATA_EXECCOMMAND, trimmedCommand)
                val (_, _, responseText) = readPacket()
                if (responseText.isBlank()) "[OK]" else responseText
            }
        }.getOrElse { error ->
            Log.d(TAG, "RCON command connection failed for '$trimmedCommand': ${error.message}")
            ""
        }
    }

    /**
     * Sends multiple commands over a single RCON TCP connection.
     *
     * Previously this called sendCommand() per command, which opened a new TCP socket + RCON auth
     * handshake for each call. Every auth handshake runs on the server's tick thread (Paper
     * processes RCON on the main thread), causing a ~20-80ms processing spike per connection.
     * With 2 commands every 20 seconds that was 4 tick interruptions per minute, showing up as
     * 236-305ms ping spikes in the client.
     *
     * This implementation authenticates once and sends all commands over the same socket.
     */
    fun sendCommands(
        commands: List<String>,
        port: Int = DEFAULT_PORT,
        password: String = DEFAULT_PASSWORD,
        timeoutMs: Int = DEFAULT_TIMEOUT_MS
    ): List<String> {
        if (commands.isEmpty()) return emptyList()
        val nonBlank = commands.map { it.trim().removePrefix("/") }.filter { it.isNotBlank() }
        if (nonBlank.isEmpty()) return emptyList()

        return runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", port), timeoutMs)
                socket.soTimeout = timeoutMs
                val out = DataOutputStream(socket.getOutputStream())
                val inp = DataInputStream(socket.getInputStream())

                fun writeIntLE(v: Int) {
                    out.write(v and 0xFF); out.write((v shr 8) and 0xFF)
                    out.write((v shr 16) and 0xFF); out.write((v shr 24) and 0xFF)
                }
                fun readIntLE(): Int {
                    val b0 = inp.read(); val b1 = inp.read()
                    val b2 = inp.read(); val b3 = inp.read()
                    if (b0 < 0 || b1 < 0 || b2 < 0 || b3 < 0) return -1
                    return (b0 and 0xFF) or ((b1 and 0xFF) shl 8) or ((b2 and 0xFF) shl 16) or ((b3 and 0xFF) shl 24)
                }
                fun sendPacket(id: Int, type: Int, payload: String) {
                    val bytes = payload.toByteArray(StandardCharsets.UTF_8)
                    writeIntLE(4 + 4 + bytes.size + 2); writeIntLE(id); writeIntLE(type)
                    out.write(bytes); out.write(0); out.write(0); out.flush()
                }
                fun readPacket(): Triple<Int, Int, String> {
                    val len = readIntLE()
                    if (len < 10 || len > MAX_PAYLOAD_BYTES) return Triple(-1, -1, "")
                    val id = readIntLE(); val type = readIntLE()
                    val payloadSize = (len - 10).coerceAtLeast(0)
                    val bytes = if (payloadSize > 0) ByteArray(payloadSize).also { inp.readFully(it) } else ByteArray(0)
                    inp.read(); inp.read() // null terminators
                    return Triple(id, type, String(bytes, StandardCharsets.UTF_8).trim())
                }

                // Authenticate once
                sendPacket(1, SERVERDATA_AUTH, password)
                var authenticated = false
                var authAttempts = 0
                while (!authenticated && authAttempts < MAX_AUTH_PACKETS) {
                    authAttempts++
                    val (authId, authType, _) = readPacket()
                    if (authId == -1) {
                        Log.w(TAG, "RCON batch auth failed")
                        return@use commands.map { "" }
                    }
                    if (authType == SERVERDATA_AUTH_RESPONSE && authId == 1) {
                        authenticated = true
                    }
                }
                if (!authenticated) {
                    Log.w(TAG, "RCON batch auth timeout or invalid response")
                    return@use commands.map { "" }
                }

                // Send all commands over the same authenticated connection.
                // The result is positionally aligned with `commands` (not `nonBlank`) because
                // callers read responses back by the index of the command they passed in.
                var requestId = 2
                commands.map { original ->
                    val cmd = original.trim().removePrefix("/")
                    if (cmd.isBlank()) return@map ""
                    sendPacket(requestId++, SERVERDATA_EXECCOMMAND, cmd)
                    val (_, _, response) = readPacket()
                    if (response.isBlank()) "[OK]" else response
                }
            }
        }.getOrElse { error ->
            Log.d(TAG, "RCON batch connection failed: ${error.message}")
            commands.map { "" }
        }
    }
}


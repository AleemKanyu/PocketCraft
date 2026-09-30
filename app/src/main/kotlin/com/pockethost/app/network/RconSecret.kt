package com.pockethost.app.network

import android.content.Context
import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import java.security.SecureRandom

/**
 * Per-install RCON password.
 *
 * The server binds RCON (port 25575) to every interface, so a password shared by every
 * install let anyone on the same Wi-Fi run any command on someone else's server. The
 * secret lives in the app's private files so the UI process and the :server process read
 * the same value; a file lock keeps the two from generating different ones at first run.
 */
object RconSecret {
    private const val TAG = "RconSecret"
    private const val FILE_NAME = "rcon_secret"

    /** What older builds wrote into server.properties; only used if [init] never ran. */
    private const val LEGACY_PASSWORD = "pocketcraft-internal-rcon"

    @Volatile
    private var cached: String? = null

    /** Call once per process, before anything talks to RCON or writes server.properties. */
    fun init(context: Context) {
        if (cached != null) return
        cached = runCatching { loadOrCreate(context.applicationContext) }
            .onFailure { Log.e(TAG, "Could not load RCON secret, falling back to legacy password", it) }
            .getOrNull()
    }

    fun current(): String = cached ?: LEGACY_PASSWORD

    private fun loadOrCreate(context: Context): String {
        val secretFile = File(context.filesDir, FILE_NAME)
        RandomAccessFile(File(context.filesDir, "$FILE_NAME.lock"), "rw").use { lockFile ->
            lockFile.channel.lock().use {
                val existing = runCatching { secretFile.readText().trim() }.getOrNull()
                if (!existing.isNullOrBlank() && existing.length >= 32) return existing

                val bytes = ByteArray(24).also { SecureRandom().nextBytes(it) }
                val generated = bytes.joinToString("") { "%02x".format(it) }
                val tmp = File(context.filesDir, "$FILE_NAME.tmp")
                tmp.writeText(generated)
                if (!tmp.renameTo(secretFile)) {
                    secretFile.writeText(generated)
                    tmp.delete()
                }
                return generated
            }
        }
    }
}

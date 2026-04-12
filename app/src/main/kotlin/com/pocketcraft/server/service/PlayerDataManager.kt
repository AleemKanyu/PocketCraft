package com.pocketcraft.server.service

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object PlayerDataManager {
    private val _inventoryJson = MutableStateFlow<String?>(null)
    val inventoryJson: StateFlow<String?> = _inventoryJson.asStateFlow()

    fun updateInventoryJson(json: String?) {
        _inventoryJson.value = json
    }

    fun getStatsFile(context: Context, serverVersion: String, playerUuid: String): File {
        return File(resolveWorldDir(context, serverVersion), "stats/$playerUuid.json")
    }

    fun getPlayerDataFile(context: Context, serverVersion: String, playerUuid: String): File {
        return File(resolveWorldDir(context, serverVersion), "playerdata/$playerUuid.dat")
    }

    fun getAdvancementsFile(context: Context, serverVersion: String, playerUuid: String): File {
        return File(resolveWorldDir(context, serverVersion), "advancements/$playerUuid.json")
    }

    fun getOpsFile(context: Context, serverVersion: String): File {
        return File(context.filesDir, "servers/$serverVersion/ops.json")
    }

    fun getLevelDataFile(context: Context, serverVersion: String): File {
        return File(resolveWorldDir(context, serverVersion), "level.dat")
    }

    fun getWhitelistFile(context: Context, serverVersion: String): File {
        return File(context.filesDir, "servers/$serverVersion/whitelist.json")
    }

    fun getBannedPlayersFile(context: Context, serverVersion: String): File {
        return File(context.filesDir, "servers/$serverVersion/banned-players.json")
    }

    suspend fun parseStats(statsFile: File): Map<String, Long> = withContext(Dispatchers.IO) {
        val result = mutableMapOf<String, Long>()
        if (!statsFile.exists()) return@withContext result

        runCatching {
            val json = JSONObject(statsFile.readText())
            val stats = json.optJSONObject("stats") ?: return@runCatching
            stats.keys().forEach { category ->
                val categoryObj = stats.optJSONObject(category) ?: return@forEach
                categoryObj.keys().forEach { key ->
                    result["$category:$key"] = categoryObj.optLong(key, 0)
                }
            }
        }

        result
    }

    fun isOp(context: Context, serverVersion: String, playerName: String): Boolean {
        return runCatching {
            val file = getOpsFile(context, serverVersion)
            file.exists() && file.readText().contains("\"name\": \"$playerName\"", ignoreCase = true)
        }.getOrDefault(false)
    }

    fun isWhitelisted(context: Context, serverVersion: String, playerName: String): Boolean {
        return runCatching {
            val file = getWhitelistFile(context, serverVersion)
            file.exists() && file.readText().contains("\"name\": \"$playerName\"", ignoreCase = true)
        }.getOrDefault(false)
    }

    fun isBanned(context: Context, serverVersion: String, playerName: String): Boolean {
        return runCatching {
            val file = getBannedPlayersFile(context, serverVersion)
            file.exists() && file.readText().contains("\"name\": \"$playerName\"", ignoreCase = true)
        }.getOrDefault(false)
    }

    suspend fun deletePlayerData(
        context: Context,
        serverVersion: String,
        playerUuid: String,
        deleteExperience: Boolean,
        deleteInventory: Boolean,
        deleteEnderChest: Boolean,
        deletePlayerData: Boolean,
        deleteStats: Boolean,
        deleteAdvancements: Boolean
    ) = withContext(Dispatchers.IO) {
        if (deletePlayerData || deleteExperience || deleteInventory || deleteEnderChest) {
            getPlayerDataFile(context, serverVersion, playerUuid).delete()
        }
        if (deleteStats) {
            getStatsFile(context, serverVersion, playerUuid).delete()
        }
        if (deleteAdvancements) {
            getAdvancementsFile(context, serverVersion, playerUuid).delete()
        }
    }

    /**
     * Attempts to update the playerGameType in a player's .dat file while they are offline.
     * mode: 0=survival, 1=creative, 2=adventure, 3=spectator
     */
    suspend fun updateOfflineGamemode(
        context: Context,
        serverVersion: String,
        playerUuid: String,
        mode: Int
    ): Boolean = withContext(Dispatchers.IO) {
        val file = getPlayerDataFile(context, serverVersion, playerUuid)
        if (!file.exists()) return@withContext false

        runCatching {
            val bytes = java.util.zip.GZIPInputStream(file.inputStream()).use { it.readBytes() }
            
            // Search for playerGameType (Tag ID 3, Name length 14, Name "playerGameType")
            // Pattern: 03 (Int) 00 0E (Length 14) 70 6C 61 79 65 72 47 61 6D 65 54 79 70 65
            val marker = byteArrayOf(
                0x03, 0x00, 0x0E, 
                0x70, 0x6C, 0x61, 0x79, 0x65, 0x72, 0x47, 0x61, 0x6D, 0x65, 0x54, 0x79, 0x70, 0x65
            )
            
            var found = false
            val buffer = bytes.copyOf()
            for (i in 0 until buffer.size - marker.size - 4) {
                var match = true
                for (j in marker.indices) {
                    if (buffer[i + j] != marker[j]) {
                        match = false
                        break
                    }
                }
                if (match) {
                    val offset = i + marker.size
                    // Write the 4-byte big-endian integer
                    buffer[offset] = (mode shr 24).toByte()
                    buffer[offset + 1] = (mode shr 16).toByte()
                    buffer[offset + 2] = (mode shr 8).toByte()
                    buffer[offset + 3] = mode.toByte()
                    found = true
                    // We don't break because there might be previousPlayerGameType or similar (though pattern is different)
                    // But usually there's only one exact match for "playerGameType".
                }
            }
            
            if (found) {
                java.util.zip.GZIPOutputStream(file.outputStream()).use { it.write(buffer) }
                true
            } else {
                false
            }
        }.getOrDefault(false)
    }

    private fun resolveWorldDir(context: Context, serverVersion: String): File {
        val serverDir = File(context.filesDir, "servers/$serverVersion")
        val propsFile = File(serverDir, "server.properties")
        val configuredWorldName = propsFile.takeIf(File::exists)
            ?.readLines()
            ?.firstOrNull { it.startsWith("level-name=") }
            ?.substringAfter('=')
            ?.trim()
            .orEmpty()
            .ifBlank { "world" }

        val candidates = listOf(
            File(serverDir, configuredWorldName),
            File(serverDir, "world")
        )
        return candidates.firstOrNull(File::exists) ?: candidates.first()
    }
}

package com.pocketcraft.server.service

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

object PlayerDataManager {

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

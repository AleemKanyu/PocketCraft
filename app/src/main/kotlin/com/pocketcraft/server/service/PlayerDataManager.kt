package com.pocketcraft.server.service

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import java.nio.charset.StandardCharsets
import org.json.JSONArray

object PlayerDataManager {
    private val _inventoryJson = MutableStateFlow<String?>(null)
    val inventoryJson: StateFlow<String?> = _inventoryJson.asStateFlow()

    fun getOfflineUuid(username: String): String {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).toByteArray(StandardCharsets.UTF_8)).toString()
    }

    /**
     * Scans usercache.json AND the directory itself to identify online UUIDs,
     * then renames playerdata, stats, and advancements files to offline UUIDs.
     */
    suspend fun fixOfflineUuids(serverDir: File, worldName: String) = withContext(Dispatchers.IO) {
        val worldDir = File(serverDir, worldName)
        if (!worldDir.exists()) return@withContext

        val dataDirs = listOf(
            File(worldDir, "playerdata"),
            File(worldDir, "stats"),
            File(worldDir, "advancements")
        )

        // 1. Gather (name, uuid) from usercache.json
        val userCacheFile = File(serverDir, "usercache.json")
        val cache = mutableListOf<Pair<String, String>>()
        if (userCacheFile.exists()) {
            runCatching {
                val arr = JSONArray(userCacheFile.readText())
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val name = obj.optString("name")
                    val uuid = obj.optString("uuid")
                    if (name.isNotBlank() && uuid.isNotBlank()) {
                        cache.add(Pair(name, uuid))
                    }
                }
            }
        }

        // 2. Fallback: Scan .dat files for player names if usercache is missing
        if (cache.isEmpty()) {
            dataDirs.firstOrNull { it.name == "playerdata" && it.exists() }?.listFiles()?.forEach { datFile ->
                if (datFile.extension == "dat" && datFile.nameWithoutExtension.contains("-")) {
                    runCatching {
                        val bytes = java.util.zip.GZIPInputStream(datFile.inputStream()).use { it.readBytes() }
                        // Search for "lastKnownName" (Type 8 - String)
                        val nameIdx = indexOfTagInRange(bytes, "lastKnownName", 8, 0, bytes.size.coerceAtMost(2000))
                        if (nameIdx != -1) {
                            val start = nameIdx + 1 + 2 + "lastKnownName".length
                            val len = ((bytes[start].toInt() and 0xFF) shl 8) or (bytes[start+1].toInt() and 0xFF)
                            val name = String(bytes, start + 2, len)
                            if (name.isNotBlank()) {
                                cache.add(Pair(name, datFile.nameWithoutExtension))
                            }
                        }
                    }
                }
            }
        }

        // 3. Fallback: If still nothing, migrate ALL files that look like online UUIDs (containing dashes)
        // by matching them across directories if we can't find a name.
        dataDirs.forEach { dir ->
            if (dir.exists()) {
                migrateKnownPlayersFallback(dir, cache, dataDirs)
            }
        }

        cache.forEach { (name, onlineUuid) ->
            val offlineUuid = getOfflineUuid(name)
            if (onlineUuid.equals(offlineUuid, ignoreCase = true)) return@forEach

            dataDirs.forEach { dir ->
                if (!dir.exists()) return@forEach
                val extension = if (dir.name == "playerdata") ".dat" else ".json"
                val oldFile = File(dir, "$onlineUuid$extension")
                val newFile = File(dir, "$offlineUuid$extension")

                if (oldFile.exists() && oldFile.absolutePath != newFile.absolutePath) {
                    // Only migrate if target doesn't exist OR target is a "fresh" file (very small)
                    val targetNeedsUpdate = !newFile.exists() || (newFile.length() < 500 && oldFile.length() > newFile.length())
                    
                    if (targetNeedsUpdate) {
                        android.util.Log.i("PlayerDataManager", "Migrating $name data: $onlineUuid -> $offlineUuid ($extension)")
                        if (newFile.exists()) newFile.delete()
                        oldFile.renameTo(newFile)
                    } else {
                        // Safe to delete old online file if we already have a better offline version
                        oldFile.delete()
                    }
                }
            }
        }
    }

    /**
     * Helper to migrate players found in files but not in cache.
     */
    private fun migrateKnownPlayersFallback(dir: File, cache: List<Pair<String, String>>, dataDirs: List<File>) {
        dir.listFiles()?.filter { it.isFile && it.name.contains("-") }?.forEach { oldFile ->
            val uuid = oldFile.nameWithoutExtension
            val matchingCachedName = cache.find { it.second.equals(uuid, ignoreCase = true) }?.first
            if (matchingCachedName != null) {
                val offline = getOfflineUuid(matchingCachedName)
                val ext = if (dir.name == "playerdata") ".dat" else ".json"
                val newFile = File(dir, "$offline$ext")
                
                if (oldFile.absolutePath == newFile.absolutePath) return@forEach

                val targetNeedsUpdate = !newFile.exists() || (newFile.length() < 500 && oldFile.length() > newFile.length())
                
                if (targetNeedsUpdate) {
                    android.util.Log.i("PlayerDataManager", "Migrating known player $matchingCachedName: $uuid -> $offline")
                    if (newFile.exists()) newFile.delete()
                    oldFile.renameTo(newFile)
                } else {
                    oldFile.delete()
                }
            }
        }
    }

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
                    val cleanCategory = if (category.startsWith("minecraft:")) category.substringAfter(":") else category
                    val cleanKey = if (key.startsWith("minecraft:")) key.substringAfter(":") else key
                    val value = categoryObj.optLong(key, 0)
                    result["minecraft:$cleanCategory:minecraft:$cleanKey"] = value
                    
                    // Cross-map legacy keys
                    if (cleanKey == "play_one_minute" && cleanCategory == "custom") {
                        result["minecraft:custom:minecraft:play_time"] = value
                    } else if (cleanKey == "play_time" && cleanCategory == "custom") {
                        result["minecraft:custom:minecraft:play_one_minute"] = value
                    }
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

    private fun indexOfTagInRange(data: ByteArray, name: String, type: Byte, start: Int, end: Int): Int {
        val nameBytes = name.toByteArray()
        for (i in start until end - nameBytes.size - 3) {
            if (data[i] == type) {
                val len = ((data[i + 1].toInt() and 0xFF) shl 8) or (data[i + 2].toInt() and 0xFF)
                if (len == nameBytes.size) {
                    var match = true
                    for (j in nameBytes.indices) {
                        if (data[i + 3 + j] != nameBytes[j]) {
                            match = false
                            break
                        }
                    }
                    if (match) return i
                }
            }
        }
        return -1
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

        val candidates = mutableListOf<File>()
        candidates.add(File(serverDir, configuredWorldName))
        candidates.add(File(serverDir, "world"))
        
        // Add any other directory in serverDir that has a level.dat (fallback for messy imports)
        serverDir.listFiles()?.filter { 
            it.isDirectory && 
            it.name !in setOf("logs", "plugins", "cache", "config", "libraries", "versions", "server_photos", "world_plugin_profiles") &&
            File(it, "level.dat").exists()
        }?.forEach { candidates.add(it) }

        // Find the candidate with the LARGEST stats folder (indicates the most active/real world)
        return candidates.filter { it.exists() }.maxByOrNull { worldDir ->
            val statsDir = File(worldDir, "stats")
            if (statsDir.exists() && statsDir.isDirectory) {
                statsDir.listFiles()?.sumOf { it.length() } ?: 0L
            } else {
                0L
            }
        } ?: File(serverDir, configuredWorldName)
    }
}

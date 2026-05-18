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

    private val _activePlayers = MutableStateFlow<Set<String>>(emptySet())
    val activePlayers: StateFlow<Set<String>> = _activePlayers.asStateFlow()

    private val _sessionPlayers = MutableStateFlow<Map<String, Long>>(emptyMap())
    val sessionPlayers: StateFlow<Map<String, Long>> = _sessionPlayers.asStateFlow()

    fun updateActivePlayers(players: Set<String>) {
        _activePlayers.value = players
    }

    fun updateSessionPlayers(sessions: Map<String, Long>) {
        _sessionPlayers.value = sessions
    }

    fun getOfflineUuid(username: String): String {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).toByteArray(StandardCharsets.UTF_8)).toString()
    }

    private fun canonicalPlayerName(name: String): String {
        return name.trim().trimStart('.', '!', '*').lowercase()
    }

    /**
     * Resolves the best UUID for offline data lookups (stats/playerdata/advancements),
     * especially for Floodgate/Geyser players where the live UUID may not match stored files.
     */
    suspend fun resolveOfflineDataUuid(
        context: Context,
        worldName: String,
        playerName: String,
        playerUuid: String
    ): String = withContext(Dispatchers.IO) {
        fun hasData(uuid: String): Boolean {
            if (uuid.isBlank()) return false
            return getStatsFile(context, worldName, uuid).exists() ||
                getPlayerDataFile(context, worldName, uuid).exists() ||
                getAdvancementsFile(context, worldName, uuid).exists()
        }

        // 1. Try the provided UUID directly
        if (hasData(playerUuid)) return@withContext playerUuid

        // 2. Try raw offline UUID (preserves prefixes like Geyser's ".")
        val rawOfflineUuid = getOfflineUuid(playerName)
        if (hasData(rawOfflineUuid)) return@withContext rawOfflineUuid

        // 3. Try canonical offline UUID (lowercase, no prefixes)
        val canonicalName = canonicalPlayerName(playerName)
        val canonicalOfflineUuid = if (canonicalName != playerName.lowercase()) getOfflineUuid(canonicalName) else ""
        if (canonicalOfflineUuid.isNotBlank() && hasData(canonicalOfflineUuid)) return@withContext canonicalOfflineUuid

        // 4. Search usercache.json for matches, prioritizing exact and then canonical
        val serverDir = ServerFileManager.getServerDirNoCreate(context, worldName)
        val userCacheFile = File(serverDir, "usercache.json")
        val exactMatches = mutableListOf<String>()
        val canonicalMatches = mutableListOf<String>()
        val baseMatches = mutableListOf<String>()

        val baseName = canonicalName.replace(Regex("\\d+$"), "")

        if (userCacheFile.exists()) {
            runCatching {
                val arr = JSONArray(userCacheFile.readText())
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val name = obj.optString("name").trim()
                    val uuid = obj.optString("uuid").trim()
                    if (uuid.isBlank() || name.isBlank()) continue

                    val entryCanonical = canonicalPlayerName(name)
                    val entryBase = entryCanonical.replace(Regex("\\d+$"), "")

                    if (name.equals(playerName, ignoreCase = true)) {
                        exactMatches.add(uuid)
                    } else if (entryCanonical == canonicalName) {
                        canonicalMatches.add(uuid)
                    } else if (baseName.isNotBlank() && (entryCanonical == baseName || entryBase == baseName || entryBase == canonicalName)) {
                        baseMatches.add(uuid)
                    }
                }
            }
        }

        exactMatches.firstOrNull { hasData(it) }?.let { return@withContext it }
        canonicalMatches.firstOrNull { hasData(it) }?.let { return@withContext it }
        
        // 5. Final fallback: only use base name (Player1 -> Player) if nothing else matched
        baseMatches.firstOrNull { hasData(it) }?.let { resolved ->
            android.util.Log.d("PlayerDataManager", "Resolved offline UUID via base match for $playerName: $resolved")
            return@withContext resolved
        }

        // 6. Veteran Heuristic: If we still have NO data, but there are unassigned "fat" files, check them.
        // (This is handled by fixOfflineUuids running periodically or on restore)

        return@withContext playerUuid.ifBlank {
            rawOfflineUuid.ifBlank {
                canonicalOfflineUuid.ifBlank { (exactMatches + canonicalMatches + baseMatches).firstOrNull().orEmpty() }
            }
        }
    }

    fun warnIfFloodgateUsernamePrefixChanged(serverDir: File) {
        val floodgateConfig = listOf(
            File(serverDir, "plugins/Floodgate/config.yml"),
            File(serverDir, "plugins/Floodgate/floodgate.yml"),
            File(serverDir, "plugins/floodgate/config.yml"),
            File(serverDir, "plugins/floodgate/floodgate.yml")
        ).firstOrNull { it.exists() } ?: return

        val currentPrefix = runCatching {
            floodgateConfig.readLines()
                .firstOrNull { it.startsWith("username-prefix:") }
                ?.substringAfter(':')
                ?.trim()
                ?.trim('"', '\'')
                .orEmpty()
        }.getOrDefault(".")

        if (currentPrefix != ".") {
            android.util.Log.w("PlayerData", "Floodgate username prefix changed — existing Bedrock player data may need to be manually cleared from world/playerdata/")
        }
    }

    fun checkForOrphanedData(playerName: String, currentUuid: String, worldDir: File) {
        val playerdataDir = File(worldDir, "playerdata")
        if (!playerdataDir.exists()) return

        val datFiles = playerdataDir.listFiles { file ->
            file.extension == "dat" && file.nameWithoutExtension.contains("-")
        }.orEmpty()

        if (datFiles.size <= 1) return

        val matchingUuids = mutableListOf<String>()
        for (datFile in datFiles) {
            runCatching {
                val bytes = java.util.zip.GZIPInputStream(datFile.inputStream()).use { it.readBytes() }
                val name = findLastKnownName(bytes, limit = bytes.size)
                if (name != null && name.equals(playerName, ignoreCase = true)) {
                    matchingUuids.add(datFile.nameWithoutExtension)
                }
            }
        }

        if (matchingUuids.size > 1) {
            android.util.Log.w(
                "PlayerData",
                "UUID instability detected for $playerName: found ${matchingUuids.size} playerdata files " +
                    "(${matchingUuids.joinToString(", ")}). Current UUID: $currentUuid."
            )
        }
    }

    /**
     * Scans everything to fix offline UUIDs. Extremely aggressive for veteran recovery.
     */
    suspend fun fixOfflineUuids(serverDir: File, worldName: String) = withContext(Dispatchers.IO) {
        val resolvedWorldDir = resolveWorldDirFromServerDir(serverDir, worldName)
        android.util.Log.i("PlayerDataManager", "fixOfflineUuids: serverDir=${serverDir.absolutePath}, resolvedWorldDir=${resolvedWorldDir.absolutePath}")

        if (!resolvedWorldDir.exists()) return@withContext

        // 0. Deep Search & Merge "lost" folders
        val coreFolderNames = setOf("playerdata", "stats", "advancements")
        serverDir.walkTopDown().maxDepth(5).filter { it.isDirectory && it.name.lowercase() in coreFolderNames }.forEach { foundDir ->
            val targetDir = File(resolvedWorldDir, foundDir.name.lowercase())
            if (foundDir.absolutePath != targetDir.absolutePath && !foundDir.absolutePath.startsWith(resolvedWorldDir.absolutePath)) {
                android.util.Log.i("PlayerDataManager", "Merging lost data folder: ${foundDir.absolutePath} -> ${targetDir.absolutePath}")
                mergeDirectoryContents(foundDir, targetDir)
                foundDir.deleteRecursively()
            }
        }

        val playerdataDir = findSubFolder(resolvedWorldDir, "playerdata")
        val statsDir = findSubFolder(resolvedWorldDir, "stats")
        val advancementsDir = findSubFolder(resolvedWorldDir, "advancements")
        val dataDirs = listOfNotNull(playerdataDir, statsDir, advancementsDir)

        if (dataDirs.isEmpty()) return@withContext

        // 1. Map every single UUID file we have to a name if possible
        val uuidToName = mutableMapOf<String, String>()
        val allFoundUuids = mutableSetOf<String>()
        dataDirs.forEach { dir ->
            dir.listFiles()?.filter { it.isFile }?.forEach { allFoundUuids.add(it.nameWithoutExtension) }
        }

        // 1a. Load names from usercache.json
        val userCacheFile = File(serverDir, "usercache.json")
        if (userCacheFile.exists()) {
            runCatching {
                val arr = JSONArray(userCacheFile.readText())
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val name = obj.optString("name")
                    val uuid = obj.optString("uuid")
                    if (name.isNotBlank() && uuid.isNotBlank()) {
                        uuidToName[uuid.lowercase()] = name
                    }
                }
            }.onFailure {
                android.util.Log.e("PlayerDataManager", "Failed to parse usercache.json: ${it.message}", it)
            }
        }

        // 1b. FULL SCAN of all .dat files to find player names
        allFoundUuids.forEach { uuid ->
            if (uuidToName.containsKey(uuid.lowercase())) return@forEach
            val datFile = File(playerdataDir, "$uuid.dat")
            if (datFile.exists()) {
                runCatching {
                    val bytes = java.util.zip.GZIPInputStream(datFile.inputStream()).use { it.readBytes() }
                    val name = findLastKnownName(bytes, limit = bytes.size)
                    if (!name.isNullOrBlank()) {
                        android.util.Log.i("PlayerDataManager", "Discovered player name from .dat: $name for UUID $uuid (${bytes.size} bytes)")
                        uuidToName[uuid.lowercase()] = name
                    }
                }
            }
        }

        // 2. Perform Migration
        val updatedUserCache = mutableListOf<JSONObject>()
        if (userCacheFile.exists()) {
            runCatching {
                val arr = JSONArray(userCacheFile.readText())
                for (i in 0 until arr.length()) updatedUserCache.add(arr.getJSONObject(i))
            }
        }

        // 2a. Auto-recover Bedrock data if it was orphaned by a previous bug
        val namesToUuids = uuidToName.entries.groupBy({ it.value.lowercase() }, { it.key })
        namesToUuids.forEach { (nameLower, uuids) ->
            val bedrockUuid = uuids.find { it.startsWith("00000000-") }
            if (bedrockUuid != null && uuids.size > 1) {
                val otherUuids = uuids.filter { it != bedrockUuid }
                otherUuids.forEach { oldUuid ->
                    dataDirs.forEach { dir ->
                        val extension = if (dir.name.lowercase() == "playerdata") ".dat" else ".json"
                        val oldFile = File(dir, "$oldUuid$extension")
                        val newFile = File(dir, "$bedrockUuid$extension")
                        if (oldFile.exists() && oldFile.absolutePath != newFile.absolutePath) {
                            val isOldVeteran = oldFile.length() > (newFile.takeIf { it.exists() }?.length() ?: 0L) * 1.2 || oldFile.length() > 5000
                            val isNewFresh = !newFile.exists() || newFile.length() < 1000
                            if (isNewFresh || isOldVeteran) {
                                android.util.Log.i("PlayerDataManager", "Recovering BEDROCK data: $oldUuid -> $bedrockUuid ($extension) [${oldFile.length()} bytes]")
                                if (newFile.exists()) newFile.delete()
                                if (!oldFile.renameTo(newFile)) {
                                    oldFile.copyTo(newFile, overwrite = true)
                                    oldFile.delete()
                                }
                            } else {
                                oldFile.delete() // Clean up duplicate
                            }
                        }
                    }
                }
                // Ensure usercache.json points to the bedrockUuid
                val existing = updatedUserCache.find { it.optString("name").equals(nameLower, ignoreCase = true) }
                if (existing != null) {
                    existing.put("uuid", bedrockUuid)
                }
            }
        }

        uuidToName.forEach { (oldUuid, name) ->
            val offlineUuid = getOfflineUuid(name)
            if (oldUuid.equals(offlineUuid, ignoreCase = true)) return@forEach

            // Bedrock/Floodgate UUIDs (e.g. 00000000-0000-0000-0009-...) must never be overwritten
            // They are deterministically generated by Floodgate and bypassing them prevents inventory resets.
            if (oldUuid.startsWith("00000000-")) return@forEach

            dataDirs.forEach { dir ->
                val extension = if (dir.name.lowercase() == "playerdata") ".dat" else ".json"
                val oldFile = File(dir, "$oldUuid$extension")
                val newFile = File(dir, "$offlineUuid$extension")

                if (oldFile.exists() && oldFile.absolutePath != newFile.absolutePath) {
                    // Aggressive overwrite: Restore if old is "Veteran" (>5KB or 1.2x bigger than new)
                    val isOldVeteran = oldFile.length() > (newFile.takeIf { it.exists() }?.length() ?: 0L) * 1.2 || oldFile.length() > 5000
                    val isNewFresh = !newFile.exists() || newFile.length() < 1000

                    if (isNewFresh || isOldVeteran) {
                        android.util.Log.i("PlayerDataManager", "Overwriting with VETERAN data for $name: $oldUuid -> $offlineUuid ($extension) [${oldFile.length()} bytes]")
                        if (newFile.exists()) newFile.delete()
                        if (!oldFile.renameTo(newFile)) {
                            oldFile.copyTo(newFile, overwrite = true)
                            oldFile.delete()
                        }
                    } else {
                        oldFile.delete() // Clean up duplicate
                    }
                }
            }

            // Update usercache.json
            val existing = updatedUserCache.find { it.optString("name").equals(name, ignoreCase = true) }
            if (existing != null) {
                existing.put("uuid", offlineUuid)
            } else {
                updatedUserCache.add(JSONObject().apply {
                    put("name", name)
                    put("uuid", offlineUuid)
                    put("expiresOn", "2030-01-01 00:00:00 +0000")
                })
            }
        }

        // 3. Save updated usercache
        runCatching {
            val arr = JSONArray()
            updatedUserCache.forEach { arr.put(it) }
            userCacheFile.writeText(arr.toString(2))
        }
    }

    private fun findLastKnownName(bytes: ByteArray, limit: Int = 150000): String? {
        val tags = listOf("lastKnownName", "last_known_name", "Name", "playerName", "author")
        for (tag in tags) {
            val idx = indexOfTagInRange(bytes, tag, 8, 0, bytes.size.coerceAtMost(limit))
            if (idx != -1) {
                val start = idx + 1 + 2 + tag.length
                if (start + 2 <= bytes.size) {
                    val len = ((bytes[start].toInt() and 0xFF) shl 8) or (bytes[start + 1].toInt() and 0xFF)
                    if (start + 2 + len <= bytes.size) {
                        val name = String(bytes, start + 2, len)
                        if (name.isNotBlank()) return name
                    }
                }
            }
        }
        return null
    }

    private fun resolveWorldDirFromServerDir(serverDir: File, slotName: String): File {
        val propsFile = File(serverDir, "server.properties")
        val configuredLevelName = runCatching {
            propsFile.takeIf(File::exists)?.readLines()
                ?.firstOrNull { it.startsWith("level-name=") }?.substringAfter('=')?.trim()
        }.getOrNull()

        if (!configuredLevelName.isNullOrBlank()) {
            val configured = File(serverDir, configuredLevelName)
            if (findFile(configured, "level.dat").exists() || findSubFolder(configured, "region").exists()) return configured
        }

        return serverDir.listFiles()?.filter { it.isDirectory }?.firstOrNull { dir ->
            findFile(dir, "level.dat").exists() || findSubFolder(dir, "region").exists()
        } ?: File(serverDir, configuredLevelName ?: "world")
    }

    private fun findSubFolder(parent: File, name: String): File {
        val exact = File(parent, name)
        if (exact.exists() && exact.isDirectory) return exact
        return parent.listFiles()?.find { it.isDirectory && it.name.equals(name, ignoreCase = true) } ?: exact
    }

    private fun findFile(parent: File, name: String): File {
        val exact = File(parent, name)
        if (exact.exists() && exact.isFile) return exact
        return parent.listFiles()?.find { it.isFile && it.name.equals(name, ignoreCase = true) } ?: exact
    }

    private fun mergeDirectoryContents(source: File, target: File) {
        if (!target.exists()) target.mkdirs()
        source.listFiles()?.forEach { child ->
            val destination = File(target, child.name)
            if (child.isDirectory) {
                mergeDirectoryContents(child, destination)
                child.delete()
            } else {
                if (!destination.exists() || child.length() > destination.length()) {
                    if (destination.exists()) destination.delete()
                    child.renameTo(destination)
                } else {
                    child.delete()
                }
            }
        }
    }

    fun updateInventoryJson(json: String?) {
        _inventoryJson.value = json
    }

    fun getStatsFile(context: Context, worldName: String, playerUuid: String): File {
        return File(resolveWorldDir(context, worldName), "stats/$playerUuid.json")
    }

    fun getPlayerDataFile(context: Context, worldName: String, playerUuid: String): File {
        return File(resolveWorldDir(context, worldName), "playerdata/$playerUuid.dat")
    }

    fun getAdvancementsFile(context: Context, worldName: String, playerUuid: String): File {
        return File(resolveWorldDir(context, worldName), "advancements/$playerUuid.json")
    }

    fun getOpsFile(context: Context, worldName: String): File {
        return File(ServerFileManager.getServerDirNoCreate(context, worldName), "ops.json")
    }

    fun getLevelDataFile(context: Context, worldName: String): File {
        return File(resolveWorldDir(context, worldName), "level.dat")
    }

    fun getWhitelistFile(context: Context, worldName: String): File {
        return File(ServerFileManager.getServerDirNoCreate(context, worldName), "whitelist.json")
    }

    fun getBannedPlayersFile(context: Context, worldName: String): File {
        return File(ServerFileManager.getServerDirNoCreate(context, worldName), "banned-players.json")
    }

    suspend fun parseStats(statsFile: File): Map<String, Long> = withContext(Dispatchers.IO) {
        val result = mutableMapOf<String, Long>()
        
        android.util.Log.d("PlayerDataManager", "parseStats called for file: ${statsFile.absolutePath}, exists=${statsFile.exists()}, length=${statsFile.length()}")
        
        if (!statsFile.exists()) return@withContext result

        runCatching {
            val jsonText = statsFile.readText()
            android.util.Log.d("PlayerDataManager", "Read stats JSON, length: ${jsonText.length}")
            val json = JSONObject(jsonText)
            val stats = json.optJSONObject("stats")
            
            if (stats != null) {
                var count = 0
                stats.keys().forEach { category ->
                    val categoryObj = stats.optJSONObject(category) ?: return@forEach
                    categoryObj.keys().forEach { key ->
                        val cleanCategory = if (category.startsWith("minecraft:")) category.substringAfter(":") else category
                        val cleanKey = if (key.startsWith("minecraft:")) key.substringAfter(":") else key
                        val value = categoryObj.optLong(key, 0)
                        
                        val fullKey = "minecraft:$cleanCategory:minecraft:$cleanKey"
                        result[fullKey] = value
                        count++
                        
                        when (cleanKey) {
                            "play_time", "play_one_minute", "time_played", "total_world_time" -> {
                                result["minecraft:custom:minecraft:play_time"] = value
                                result["minecraft:custom:minecraft:play_one_minute"] = value
                            }
                            "time_since_death", "since_death" -> result["minecraft:custom:minecraft:time_since_death"] = value
                            "walk_one_cm", "walk_cm" -> result["minecraft:custom:minecraft:walk_one_cm"] = value
                            "sprint_one_cm", "sprint_cm" -> result["minecraft:custom:minecraft:sprint_one_cm"] = value
                            "crouch_one_cm", "crouch_cm" -> result["minecraft:custom:minecraft:crouch_one_cm"] = value
                            "swim_one_cm", "swim_cm" -> result["minecraft:custom:minecraft:swim_one_cm"] = value
                            "player_kills", "pkills" -> result["minecraft:custom:minecraft:player_kills"] = value
                            "mob_kills", "mkills" -> result["minecraft:custom:minecraft:mob_kills"] = value
                            "deaths" -> result["minecraft:custom:minecraft:deaths"] = value
                            "jump", "jumps" -> result["minecraft:custom:minecraft:jump"] = value
                            "damage_dealt" -> result["minecraft:custom:minecraft:damage_dealt"] = value
                        }
                    }
                }
                android.util.Log.d("PlayerDataManager", "Parsed $count stats from modern format. playtime=${result["minecraft:custom:minecraft:play_time"]}")
            } else {
                var count = 0
                json.keys().forEach { key ->
                    val value = json.optLong(key, 0)
                    result[key] = value
                    count++
                    
                    val cleanKey = if (key.startsWith("stat.")) key.substringAfter("stat.") else key
                    when (cleanKey) {
                        "playOneMinute", "playTime", "timePlayed" -> {
                            result["minecraft:custom:minecraft:play_time"] = value
                            result["minecraft:custom:minecraft:play_one_minute"] = value
                        }
                        "timeSinceDeath" -> result["minecraft:custom:minecraft:time_since_death"] = value
                        "deaths" -> result["minecraft:custom:minecraft:deaths"] = value
                        "jump" -> result["minecraft:custom:minecraft:jump"] = value
                        "damageDealt" -> result["minecraft:custom:minecraft:damage_dealt"] = value
                        "playerKills" -> result["minecraft:custom:minecraft:player_kills"] = value
                        "mobKills" -> result["minecraft:custom:minecraft:mob_kills"] = value
                        "walkOneCm" -> result["minecraft:custom:minecraft:walk_one_cm"] = value
                        "sprintOneCm" -> result["minecraft:custom:minecraft:sprint_one_cm"] = value
                    }
                }
                android.util.Log.d("PlayerDataManager", "Parsed $count stats from legacy format.")
            }
        }.onFailure {
            android.util.Log.e("PlayerDataManager", "Failed to parse stats JSON: ${it.message}", it)
        }
        
        android.util.Log.d("PlayerDataManager", "Returning ${result.size} parsed stats elements.")
        result
    }

    fun isOp(context: Context, worldName: String, playerName: String): Boolean {
        return runCatching {
            getOpsFile(context, worldName).takeIf(File::exists)?.readText()?.contains("\"name\": \"$playerName\"", ignoreCase = true)
        }.getOrDefault(false) == true
    }

    fun isWhitelisted(context: Context, worldName: String, playerName: String): Boolean {
        return runCatching {
            getWhitelistFile(context, worldName).takeIf(File::exists)?.readText()?.contains("\"name\": \"$playerName\"", ignoreCase = true)
        }.getOrDefault(false) == true
    }

    fun isBanned(context: Context, worldName: String, playerName: String): Boolean {
        return runCatching {
            getBannedPlayersFile(context, worldName).takeIf(File::exists)?.readText()?.contains("\"name\": \"$playerName\"", ignoreCase = true)
        }.getOrDefault(false) == true
    }

    suspend fun deletePlayerData(
        context: Context,
        worldName: String,
        playerUuid: String,
        deleteExperience: Boolean,
        deleteInventory: Boolean,
        deleteEnderChest: Boolean,
        deletePlayerData: Boolean,
        deleteStats: Boolean,
        deleteAdvancements: Boolean
    ) = withContext(Dispatchers.IO) {
        if (deletePlayerData || deleteExperience || deleteInventory || deleteEnderChest) {
            getPlayerDataFile(context, worldName, playerUuid).delete()
        }
        if (deleteStats) {
            getStatsFile(context, worldName, playerUuid).delete()
        }
        if (deleteAdvancements) {
            getAdvancementsFile(context, worldName, playerUuid).delete()
        }
    }

    suspend fun updateOfflineGamemode(context: Context, worldName: String, playerUuid: String, mode: Int): Boolean = withContext(Dispatchers.IO) {
        fixPlayerGameMode(getPlayerDataFile(context, worldName, playerUuid), mode)
    }

    fun fixPlayerGameMode(file: File, mode: Int): Boolean {
        if (!file.exists()) return false
        return runCatching {
            val bytes = java.util.zip.GZIPInputStream(file.inputStream()).use { it.readBytes() }
            val marker = byteArrayOf(0x03, 0x00, 0x0E, 0x70, 0x6C, 0x61, 0x79, 0x65, 0x72, 0x47, 0x61, 0x6D, 0x65, 0x54, 0x79, 0x70, 0x65)
            val buffer = bytes.copyOf()
            var found = false
            for (i in 0 until buffer.size - marker.size - 4) {
                if (marker.indices.all { buffer[i + it] == marker[it] }) {
                    val offset = i + marker.size
                    buffer[offset] = (mode shr 24).toByte(); buffer[offset + 1] = (mode shr 16).toByte()
                    buffer[offset + 2] = (mode shr 8).toByte(); buffer[offset + 3] = mode.toByte()
                    found = true
                }
            }
            if (found) java.util.zip.GZIPOutputStream(file.outputStream()).use { it.write(buffer) }
            found
        }.getOrDefault(false)
    }

    private fun indexOfTagInRange(data: ByteArray, name: String, type: Byte, start: Int, end: Int): Int {
        val nameBytes = name.toByteArray()
        for (i in start until end - nameBytes.size - 3) {
            if (data[i] == type) {
                val len = ((data[i + 1].toInt() and 0xFF) shl 8) or (data[i + 2].toInt() and 0xFF)
                if (len == nameBytes.size && nameBytes.indices.all { data[i + 3 + it] == nameBytes[it] }) return i
            }
        }
        return -1
    }

    private fun resolveWorldDir(context: Context, worldName: String): File {
        val serverDir = ServerFileManager.getServerDirNoCreate(context, worldName)
        val propsFile = File(serverDir, "server.properties")
        val configured = runCatching { propsFile.takeIf(File::exists)?.readLines()?.firstOrNull { it.startsWith("level-name=") }?.substringAfter('=')?.trim() }.getOrNull() ?: "world"
        val candidates = mutableListOf(File(serverDir, configured), File(serverDir, "world"), File(serverDir, worldName))
        serverDir.listFiles()?.filter { it.isDirectory && it.name.lowercase() !in setOf("logs", "plugins", "cache", "config", "libraries", "versions") && findFile(it, "level.dat").exists() }?.forEach { candidates.add(it) }
        return candidates.filter { it.exists() }.maxByOrNull { worldDir ->
            val statsDir = findSubFolder(worldDir, "stats")
            if (statsDir.exists() && statsDir.isDirectory) statsDir.listFiles()?.sumOf { it.length() } ?: 0L else 0L
        } ?: File(serverDir, configured)
    }
}

package com.pocketcraft.server.afk

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pocketcraft.server.data.model.PlayerInfo
import com.pocketcraft.server.server.BundledPluginInstaller
import com.pocketcraft.server.service.NBTParser
import com.pocketcraft.server.service.ServerFileManager
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AfkFarmLocation(
    val id: String,
    val worldName: String,
    val name: String,
    val x: Int,
    val y: Int,
    val z: Int,
    val isActive: Boolean,
    val isLive: Boolean,
    val requiresRestart: Boolean,
    val dummyEntityName: String,
    val ownerPlayerName: String,
    val ownerPlayerUuid: String
)

class AfkHelperManager(
    context: Context,
    private val scope: CoroutineScope,
    private val currentWorldProvider: () -> String,
    private val isServerRunningProvider: () -> Boolean,
    private val onlinePlayersProvider: () -> List<PlayerInfo>,
    private val knownPlayersProvider: () -> List<PlayerInfo>,
    private val sendRconCommand: suspend (String) -> String,
    private val appendLog: (String) -> Unit,
    private val notifyStateChanged: () -> Unit
) {
    companion object {
        private const val DUMMY_PREFIX = "AFK_"
        private const val DUMMY_PLUGIN_NAME = "DummyPlayers.jar"
        private const val POLL_INTERVAL_MS = 15_000L
        private const val HOT_RELOAD_WAIT_MS = 4_000L
    }

    private val appContext = context.applicationContext
    private val dao = AfkHelperDatabase.getInstance(appContext).afkFarmLocationDao()
    private val liveDummyIds = linkedSetOf<String>()
    private val restartPendingIds = linkedSetOf<String>()
    private var cachedEntities: List<AfkFarmLocationEntity> = emptyList()
    private var pollJob: Job? = null

    val farms = mutableStateListOf<AfkFarmLocation>()
    var isBusy by mutableStateOf(false)
        private set

    init {
        scope.launch(Dispatchers.IO) {
            dao.observeAll().collectLatest { entities ->
                cachedEntities = entities
                renderCurrentWorld()
                syncCurrentWorldPluginFiles()
            }
        }
    }

    fun onWorldChanged() {
        scope.launch(Dispatchers.IO) {
            renderCurrentWorld()
            syncCurrentWorldPluginFiles()
            refreshNow()
        }
    }

    fun onServerStateChanged(isRunning: Boolean) {
        if (isRunning) {
            startPolling()
            scope.launch(Dispatchers.IO) {
                delay(2_500)
                refreshNow()
            }
        } else {
            pollJob?.cancel()
            pollJob = null
            liveDummyIds.clear()
            restartPendingIds.clear()
            scope.launch(Dispatchers.IO) {
                renderCurrentWorld()
            }
        }
    }

    suspend fun addFarm(
        name: String,
        x: Int,
        y: Int,
        z: Int
    ): String = withContext(Dispatchers.IO) {
        val worldName = currentWorldName()
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            return@withContext "Enter a farm name first."
        }

        val defaultOwner = resolveDefaultOwner()
        val entity = AfkFarmLocationEntity(
            id = UUID.randomUUID().toString(),
            worldName = worldName,
            name = trimmedName,
            x = x,
            y = y,
            z = z,
            isActive = false,
            dummyEntityName = nextDummyEntityName(trimmedName),
            ownerPlayerName = defaultOwner?.name.orEmpty(),
            ownerPlayerUuid = defaultOwner?.uuid.orEmpty(),
            createdAt = System.currentTimeMillis()
        )
        dao.upsert(entity)
        "Saved $trimmedName to AFK Helpers."
    }

    suspend fun deleteFarm(id: String): String = withContext(Dispatchers.IO) {
        val entity = cachedEntities.firstOrNull { it.id == id }
            ?: return@withContext "That AFK helper no longer exists."

        if (entity.isActive) {
            disableFarmInternal(entity)
        }
        dao.delete(entity)
        refreshNow()
        "Deleted ${entity.name}."
    }

    suspend fun toggleFarm(id: String): String = withContext(Dispatchers.IO) {
        val entity = cachedEntities.firstOrNull { it.id == id }
            ?: return@withContext "That AFK helper no longer exists."
        if (entity.isActive) disableFarmInternal(entity) else enableFarmInternal(entity)
    }

    suspend fun refreshNow() = withContext(Dispatchers.IO) {
        if (!isServerRunningProvider()) {
            liveDummyIds.clear()
            restartPendingIds.clear()
            renderCurrentWorld()
            return@withContext
        }

        val worldName = currentWorldName()
        val activeWorldFarms = dao.getAll().filter {
            it.worldName.equals(worldName, ignoreCase = true) && it.isActive
        }

        val liveIds = linkedSetOf<String>()
        activeWorldFarms.forEach { farm ->
            if (isDummyLive(farm)) {
                liveIds += farm.id
            }
        }
        liveDummyIds.clear()
        liveDummyIds += liveIds
        renderCurrentWorld()
    }

    suspend fun captureSuggestedLocation(playerName: String? = null): Triple<Int, Int, Int>? = withContext(Dispatchers.IO) {
        val targetName = playerName ?: onlinePlayersProvider().firstOrNull { it.uuid.isNotBlank() }?.name ?: return@withContext null
        val selector = playerSelector(targetName)
        val posLine = runCatching { sendRconCommand("data get entity $selector Pos") }.getOrNull() ?: return@withContext null
        NBTParser.parsePosition(posLine)?.let { (x, y, z) ->
            Triple(x.toInt(), y.toInt(), z.toInt())
        }
    }

    private suspend fun enableFarmInternal(entity: AfkFarmLocationEntity): String {
        isBusy = true
        return try {
            val owner = entity.resolveOwnerOrDefault() ?: return "Let at least one player join once before activating AFK Helpers."
            val prepared = entity.copy(
                isActive = true,
                ownerPlayerName = owner.name,
                ownerPlayerUuid = owner.uuid,
                dummyUuid = entity.dummyUuid.ifBlank { UUID.randomUUID().toString() },
                createdAt = entity.createdAt.takeIf { it > 0L } ?: System.currentTimeMillis()
            )
            dao.upsert(prepared)
            syncWorldPluginFiles(prepared.worldName)

            if (!isServerRunningProvider() || !prepared.worldName.equals(currentWorldName(), ignoreCase = true)) {
                return "${prepared.name} will spawn automatically the next time this world starts."
            }

            ensureDummyPluginSupport(worldServerDir(prepared.worldName))
            val spawnExecutor = resolveSpawnExecutor(prepared)
            if (spawnExecutor != null) {
                val selector = playerSelector(spawnExecutor.name)
                runCatching {
                    sendRconCommand("execute as $selector at @s run dummy create ${prepared.dummyEntityName}")
                }.onFailure { error ->
                    appendLog("[PocketCraft] AFK helper create failed for ${prepared.name}: ${error.message}")
                }
            } else {
                appendLog("[PocketCraft] No players online for ${prepared.name}; hot-reloading DummyPlayers from saved data.")
            }

            delay(750)
            val synced = readDummyRecordByName(prepared.worldName, prepared.dummyEntityName)?.let { record ->
                prepared.copy(
                    dummyUuid = record.uuid.ifBlank { prepared.dummyUuid },
                    createdAt = record.createdAt.takeIf { it > 0L } ?: prepared.createdAt,
                    ownerPlayerUuid = record.ownerUuid.ifBlank { prepared.ownerPlayerUuid }
                )
            } ?: prepared
            dao.upsert(synced)
            syncWorldPluginFiles(synced.worldName)

            runCatching {
                sendRconCommand(
                    "tp ${dummySelector(synced)} ${synced.x} ${synced.y} ${synced.z}"
                )
            }

            refreshNow()
            if (!liveDummyIds.contains(synced.id)) {
                hotReloadDummyPlugin(synced)
                refreshNow()
            }
            val liveNow = liveDummyIds.contains(synced.id)
            if (liveNow) {
                restartPendingIds.remove(synced.id)
                renderCurrentWorld()
                "${synced.name} is live now."
            } else {
                restartPendingIds += synced.id
                renderCurrentWorld()
                "${synced.name} was saved, but the live dummy did not appear. Restart the server to force-spawn it from the saved plugin data."
            }
        } finally {
            isBusy = false
        }
    }

    private suspend fun disableFarmInternal(entity: AfkFarmLocationEntity): String {
        isBusy = true
        return try {
            val disabled = entity.copy(isActive = false)
            dao.upsert(disabled)
            syncWorldPluginFiles(disabled.worldName)

            if (isServerRunningProvider() && disabled.worldName.equals(currentWorldName(), ignoreCase = true)) {
                resolveOnlineOwner(entity)?.let { onlineOwner ->
                    val selector = playerSelector(onlineOwner.name)
                    runCatching {
                        sendRconCommand("execute as $selector run dummy remove ${entity.dummyEntityName}")
                    }
                }
                runCatching { sendRconCommand("kick ${dummyDisplayName(entity)}") }
                runCatching { sendRconCommand("kill ${dummySelector(entity)}") }
                delay(500)
                refreshNow()
            } else {
                liveDummyIds.remove(entity.id)
                renderCurrentWorld()
            }

            if (liveDummyIds.contains(entity.id)) {
                restartPendingIds += entity.id
                renderCurrentWorld()
                "${entity.name} was disabled. Restart the server to clear the remaining live dummy."
            } else {
                restartPendingIds.remove(entity.id)
                renderCurrentWorld()
                "${entity.name} is no longer active."
            }
        } finally {
            isBusy = false
        }
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = scope.launch(Dispatchers.IO) {
            while (true) {
                refreshNow()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun renderCurrentWorld() {
        val worldName = currentWorldName()
        val visible = cachedEntities
            .filter { it.worldName.equals(worldName, ignoreCase = true) }
            .sortedBy { it.name.lowercase(Locale.getDefault()) }
            .map { entity ->
                AfkFarmLocation(
                    id = entity.id,
                    worldName = entity.worldName,
                    name = entity.name,
                    x = entity.x,
                    y = entity.y,
                    z = entity.z,
                    isActive = entity.isActive,
                    isLive = liveDummyIds.contains(entity.id),
                    requiresRestart = restartPendingIds.contains(entity.id),
                    dummyEntityName = entity.dummyEntityName,
                    ownerPlayerName = entity.ownerPlayerName,
                    ownerPlayerUuid = entity.ownerPlayerUuid
                )
            }

        withContext(Dispatchers.Main) {
            farms.clear()
            farms.addAll(visible)
            notifyStateChanged()
        }
    }

    private fun currentWorldName(): String = currentWorldProvider().ifBlank { "world" }

    private fun worldServerDir(worldName: String): File =
        ServerFileManager.getServerDir(appContext, worldName.ifBlank { "world" })

    private suspend fun syncCurrentWorldPluginFiles() {
        syncWorldPluginFiles(currentWorldName())
    }

    private suspend fun syncWorldPluginFiles(worldName: String) {
        val serverDir = worldServerDir(worldName)
        BundledPluginInstaller.installBundledPlugins(appContext, serverDir)
        ensureDummyPluginSupport(serverDir)

        val activeFarms = dao.getAll().filter {
            it.worldName.equals(worldName, ignoreCase = true) &&
                it.isActive &&
                it.ownerPlayerUuid.isNotBlank()
        }

        val dummiesFile = File(serverDir, "plugins/DummyPlayers/dummies.yml")
        val managedNames = activeFarms.map { it.dummyEntityName.lowercase(Locale.getDefault()) }.toSet()
        val mergedEntries = mutableListOf<DummyYamlEntry>()
        parseDummiesYaml(dummiesFile).forEach { entry ->
            if (entry.name.lowercase(Locale.getDefault()) !in managedNames) {
                mergedEntries += entry
            }
        }

        activeFarms.forEach { farm ->
            val uuid = farm.dummyUuid.ifBlank { UUID.randomUUID().toString() }
            val createdAt = farm.createdAt.takeIf { it > 0L } ?: System.currentTimeMillis()
            if (uuid != farm.dummyUuid || createdAt != farm.createdAt) {
                scope.launch(Dispatchers.IO) {
                    dao.upsert(farm.copy(dummyUuid = uuid, createdAt = createdAt))
                }
            }
            mergedEntries += DummyYamlEntry(
                keyUuid = uuid,
                uuid = uuid,
                name = farm.dummyEntityName,
                ownerUuid = farm.ownerPlayerUuid,
                world = worldName,
                x = farm.x.toDouble(),
                y = farm.y.toDouble(),
                z = farm.z.toDouble(),
                yaw = farm.yaw.toDouble(),
                pitch = farm.pitch.toDouble(),
                createdAt = createdAt
            )
        }

        writeDummiesYaml(dummiesFile, mergedEntries)
    }

    private fun ensureDummyPluginSupport(serverDir: File) {
        val dataDir = File(serverDir, "plugins/DummyPlayers").also { it.mkdirs() }
        val configFile = File(dataDir, "config.yml")
        configFile.writeText(
            """
            max-dummies-per-player: 8
            name-prefix: ${yamlScalar(DUMMY_PREFIX)}
            chunk-loading:
              enabled: true
              radius: 2
            authentication:
              enabled: false
            """.trimIndent() + "\n"
        )
    }

    private fun nextDummyEntityName(name: String): String {
        val base = name
            .lowercase(Locale.getDefault())
            .replace(Regex("[^a-z0-9_]+"), "_")
            .trim('_')
            .ifBlank { "farm" }
            .take(12)
        val used = cachedEntities.map { it.dummyEntityName.lowercase(Locale.getDefault()) }.toSet()
        var candidate = base
        var index = 2
        while (candidate in used) {
            candidate = "${base.take(9)}_$index"
            index += 1
        }
        return candidate
    }

    private fun resolveDefaultOwner(): PlayerInfo? =
        onlinePlayersProvider().firstOrNull { it.uuid.isNotBlank() }
            ?: knownPlayersProvider().firstOrNull { it.uuid.isNotBlank() }

    private fun resolveOnlineOwner(entity: AfkFarmLocationEntity): PlayerInfo? =
        onlinePlayersProvider().firstOrNull {
            entity.ownerPlayerUuid.isNotBlank() && it.uuid == entity.ownerPlayerUuid
        } ?: onlinePlayersProvider().firstOrNull {
            entity.ownerPlayerName.isNotBlank() && it.name.equals(entity.ownerPlayerName, ignoreCase = true)
        }

    private fun resolveSpawnExecutor(entity: AfkFarmLocationEntity): PlayerInfo? =
        resolveOnlineOwner(entity)
            ?: onlinePlayersProvider().firstOrNull { it.uuid.isNotBlank() }
            ?: onlinePlayersProvider().firstOrNull()

    private fun AfkFarmLocationEntity.resolveOwnerOrDefault(): PlayerInfo? {
        val existingOwner = resolveOnlineOwner(this)
            ?: knownPlayersProvider().firstOrNull {
                ownerPlayerUuid.isNotBlank() && it.uuid == ownerPlayerUuid
            }
            ?: knownPlayersProvider().firstOrNull {
                ownerPlayerName.isNotBlank() && it.name.equals(ownerPlayerName, ignoreCase = true)
            }
        return existingOwner ?: resolveDefaultOwner()
    }

    private suspend fun isDummyLive(entity: AfkFarmLocationEntity): Boolean {
        val response = runCatching {
            sendRconCommand("data get entity ${dummySelector(entity)} Pos")
        }.getOrDefault("")
        return NBTParser.parsePosition(response) != null
    }

    private suspend fun hotReloadDummyPlugin(entity: AfkFarmLocationEntity) {
        appendLog("[PocketCraft] Reloading DummyPlayers to live-spawn ${entity.name} without a full server restart.")
        runCatching {
            sendRconCommand("reload confirm")
        }.onFailure { error ->
            appendLog("[PocketCraft] DummyPlayers reload command failed for ${entity.name}: ${error.message}")
        }
        delay(HOT_RELOAD_WAIT_MS)
    }

    private fun playerSelector(playerName: String): String =
        """@a[name="${escapeSelectorName(playerName)}",limit=1]"""

    private fun dummySelector(entity: AfkFarmLocationEntity): String =
        """@a[name="${escapeSelectorName(dummyDisplayName(entity))}",limit=1]"""

    private fun dummyDisplayName(entity: AfkFarmLocationEntity): String =
        "$DUMMY_PREFIX${entity.dummyEntityName}"

    private fun escapeSelectorName(name: String): String =
        name.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun readDummyRecordByName(worldName: String, dummyName: String): DummyYamlRecord? {
        val dummiesFile = File(worldServerDir(worldName), "plugins/DummyPlayers/dummies.yml")
        parseDummiesYaml(dummiesFile).forEach { entry ->
            if (entry.name.equals(dummyName, ignoreCase = true)) {
                return DummyYamlRecord(
                    uuid = entry.uuid.ifBlank { entry.keyUuid },
                    ownerUuid = entry.ownerUuid,
                    createdAt = entry.createdAt
                )
            }
        }
        return null
    }

    private fun parseDummiesYaml(file: File): List<DummyYamlEntry> {
        if (!file.exists()) return emptyList()
        val result = mutableListOf<DummyYamlEntry>()
        var currentKey: String? = null
        var fields = mutableMapOf<String, String>()

        fun flush() {
            val key = currentKey ?: return
            result += DummyYamlEntry(
                keyUuid = key,
                uuid = fields["uuid"].orEmpty().ifBlank { key },
                name = fields["name"].orEmpty(),
                ownerUuid = fields["owner"].orEmpty(),
                world = fields["world"].orEmpty(),
                x = fields["x"]?.toDoubleOrNull(),
                y = fields["y"]?.toDoubleOrNull(),
                z = fields["z"]?.toDoubleOrNull(),
                yaw = fields["yaw"]?.toDoubleOrNull(),
                pitch = fields["pitch"]?.toDoubleOrNull(),
                createdAt = fields["created-at"]?.toLongOrNull() ?: 0L
            )
            currentKey = null
            fields = mutableMapOf()
        }

        file.readLines().forEach { rawLine ->
            val line = rawLine.trimEnd()
            if (line.isBlank() || line.trimStart().startsWith("#") || line.trim() == "dummies:") {
                return@forEach
            }

            val keyMatch = Regex("""^\s{2}([^:#]+):\s*$""").find(line)
            if (keyMatch != null) {
                flush()
                currentKey = keyMatch.groupValues[1].trim()
                return@forEach
            }

            val fieldMatch = Regex("""^\s{4}([^:#]+):\s*(.*)$""").find(line)
            if (fieldMatch != null && currentKey != null) {
                val key = fieldMatch.groupValues[1].trim()
                val value = parseYamlScalar(fieldMatch.groupValues[2].trim())
                fields[key] = value
            }
        }
        flush()
        return result
    }

    private fun writeDummiesYaml(file: File, entries: List<DummyYamlEntry>) {
        file.parentFile?.mkdirs()
        if (entries.isEmpty()) {
            file.writeText("dummies: {}\n")
            return
        }

        val sortedEntries = entries.sortedBy { it.name.lowercase(Locale.getDefault()) }
        val content = buildString {
            appendLine("dummies:")
            sortedEntries.forEach { entry ->
                appendLine("  ${entry.keyUuid}:")
                appendLine("    name: ${yamlScalar(entry.name)}")
                appendLine("    uuid: ${yamlScalar(entry.uuid.ifBlank { entry.keyUuid })}")
                appendLine("    owner: ${yamlScalar(entry.ownerUuid)}")
                appendLine("    world: ${yamlScalar(entry.world)}")
                appendLine("    x: ${entry.x ?: 0.0}")
                appendLine("    y: ${entry.y ?: 0.0}")
                appendLine("    z: ${entry.z ?: 0.0}")
                appendLine("    yaw: ${entry.yaw ?: 0.0}")
                appendLine("    pitch: ${entry.pitch ?: 0.0}")
                appendLine("    created-at: ${entry.createdAt}")
            }
        }
        file.writeText(content)
    }

    private fun parseYamlScalar(raw: String): String {
        if (raw.length >= 2 && raw.startsWith("'") && raw.endsWith("'")) {
            return raw.substring(1, raw.length - 1).replace("''", "'")
        }
        if (raw.length >= 2 && raw.startsWith("\"") && raw.endsWith("\"")) {
            return raw.substring(1, raw.length - 1).replace("\\\"", "\"").replace("\\\\", "\\")
        }
        return raw
    }

    private fun yamlScalar(value: String?): String {
        val safeValue = value.orEmpty()
        return "'${safeValue.replace("'", "''")}'"
    }

    private data class DummyYamlRecord(
        val uuid: String,
        val ownerUuid: String,
        val createdAt: Long
    )

    private data class DummyYamlEntry(
        val keyUuid: String,
        val uuid: String,
        val name: String,
        val ownerUuid: String,
        val world: String,
        val x: Double?,
        val y: Double?,
        val z: Double?,
        val yaw: Double?,
        val pitch: Double?,
        val createdAt: Long
    )

    fun bundledPluginName(): String = DUMMY_PLUGIN_NAME
}

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
        private const val POLL_INTERVAL_MS = 60_000L

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
            runCatching {
                dao.observeAll().collectLatest { entities ->
                    cachedEntities = entities
                    renderCurrentWorld()
                    syncCurrentWorldPluginFiles()
                }
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

        val isPremium = com.pocketcraft.server.billing.BillingManager.getInstance(appContext).isPremium.value
        if (!isPremium) {
            val existingCount = runCatching { dao.getAll() }.getOrDefault(emptyList()).filter { it.worldName.equals(worldName, ignoreCase = true) }.size
            if (existingCount >= 1) {
                return@withContext "Free plan is limited to 1 AFK bot. Upgrade to Pro to unlock unlimited AFK bots!"
            }
        }

        val defaultOwner = resolveDefaultOwner()
        val entity = AfkFarmLocationEntity(
            id = UUID.randomUUID().toString(),
            worldName = worldName,
            name = trimmedName,
            x = x,
            y = y,
            z = z,
            isActive = true,
            dummyEntityName = nextDummyEntityName(trimmedName),
            ownerPlayerName = defaultOwner?.name.orEmpty(),
            ownerPlayerUuid = defaultOwner?.uuid.orEmpty(),
            createdAt = System.currentTimeMillis()
        )
        runCatching { dao.upsert(entity) }
        enableFarmInternal(entity)
    }

    suspend fun deleteFarm(id: String): String = withContext(Dispatchers.IO) {
        val entity = cachedEntities.firstOrNull { it.id == id }
            ?: return@withContext "That AFK helper no longer exists."

        // Always attempt server-side cleanup on deletion
        if (isServerRunningProvider() && entity.worldName.equals(currentWorldName(), ignoreCase = true)) {
            val command = "dummy remove ${entity.dummyEntityName} ${entity.ownerPlayerUuid}"
            runCatching { sendRconCommand(command) }
            runCatching { sendRconCommand("kick ${dummyDisplayName(entity)}") }
            runCatching { sendRconCommand(command) } // run remove again to ensure removal from list
            runCatching { sendRconCommand("kill ${dummySelector(entity)}") }
        }

        if (entity.isActive) {
            disableFarmInternal(entity)
        }
        runCatching { dao.delete(entity) }
        liveDummyIds.remove(entity.id)
        restartPendingIds.remove(entity.id)
        refreshNow()
        "Deleted ${entity.name}."
    }

    suspend fun updateFarm(
        id: String,
        name: String,
        x: Int,
        y: Int,
        z: Int
    ): String = withContext(Dispatchers.IO) {
        val entity = cachedEntities.firstOrNull { it.id == id }
            ?: return@withContext "That AFK helper no longer exists."

        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            return@withContext "Bot name cannot be empty."
        }

        val wasActive = entity.isActive
        if (wasActive) {
            disableFarmInternal(entity)
        }

        val updated = entity.copy(
            name = trimmedName,
            x = x,
            y = y,
            z = z
        )
        runCatching { dao.upsert(updated) }
        syncWorldPluginFiles(updated.worldName)

        if (wasActive) {
            enableFarmInternal(updated)
        } else {
            refreshNow()
        }

        "Updated ${updated.name} settings."
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
        val activeWorldFarms = runCatching { dao.getAll() }.getOrDefault(emptyList()).filter {
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
        val player = if (!playerName.isNullOrBlank()) {
            onlinePlayersProvider().firstOrNull { it.name.equals(playerName, ignoreCase = true) }
        } else {
            onlinePlayersProvider().firstOrNull { it.uuid.isNotBlank() }
        }
        
        if (player != null && player.x != null && player.y != null && player.z != null) {
            return@withContext Triple(player.x, player.y, player.z)
        }

        val targetUuid = player?.uuid.orEmpty()
        val targetName = player?.name ?: playerName.orEmpty()

        val selectorsToTry = listOfNotNull(
            targetUuid.takeIf { it.isNotBlank() },
            targetName.takeIf { it.isNotBlank() },
            targetName.takeIf { it.isNotBlank() }?.let { "@a[name=\"${escapeSelectorName(it)}\",limit=1]" },
            "@p"
        )

        for (selector in selectorsToTry) {
            val rconOutput = runCatching { sendRconCommand("data get entity $selector Pos") }.getOrDefault("")
            var pos = NBTParser.parsePosition(rconOutput) ?: NBTParser.parseBlockPosition(rconOutput)
            if (pos == null) {
                val stdinOutput = runCatching {
                    com.pocketcraft.server.server.ServerLauncher.sendCommand("data get entity $selector Pos")
                    ""
                }.getOrDefault("")
                pos = NBTParser.parsePosition(stdinOutput) ?: NBTParser.parseBlockPosition(stdinOutput)
            }
            if (pos != null) {
                return@withContext Triple(pos.first.toInt(), pos.second.toInt(), pos.third.toInt())
            }
        }
        null
    }

    private suspend fun enableFarmInternal(entity: AfkFarmLocationEntity): String {
        isBusy = true
        return try {
            val owner = entity.resolveOwnerOrDefault()
            val safeOwnerUuid = owner.uuid.ifBlank {
                entity.ownerPlayerUuid.ifBlank { UUID.randomUUID().toString() }
            }
            val safeDummyUuid = entity.dummyUuid.ifBlank { UUID.randomUUID().toString() }
            val activeWorld = currentWorldName()
            val prepared = entity.copy(
                isActive = true,
                worldName = activeWorld,
                ownerPlayerName = owner.name.ifBlank { "Server" },
                ownerPlayerUuid = safeOwnerUuid,
                dummyUuid = safeDummyUuid,
                createdAt = entity.createdAt.takeIf { it > 0L } ?: System.currentTimeMillis()
            )
            dao.upsert(prepared)
            syncWorldPluginFiles(prepared.worldName)

            if (!isServerRunningProvider()) {
                return "${prepared.name} will spawn automatically the next time this world starts."
            }

            val serverDir = worldServerDir(prepared.worldName)
            ensureDummyPluginSupport(serverDir)

            val forceloadKey = "${prepared.x} ${prepared.z}"
            runCatching { sendRconCommand("forceload add $forceloadKey") }
            runCatching { com.pocketcraft.server.server.ServerLauncher.sendCommand("forceload add $forceloadKey") }

            val configuredWorldName = resolveConfiguredLevelName(serverDir)
            val worldCandidates = listOf(activeWorld, configuredWorldName, "world").distinct()

            // Remove any stale registration in DummyManager so dummyNameExists returns false
            val removeCmd = "dummy remove ${prepared.dummyEntityName} $safeOwnerUuid"
            runCatching { sendRconCommand(removeCmd) }
            runCatching { com.pocketcraft.server.server.ServerLauncher.sendCommand(removeCmd) }
            delay(150)

            var spawned = false
            for (wName in worldCandidates) {
                if (spawned) break
                val command = "dummy create ${prepared.dummyEntityName} $safeOwnerUuid $wName ${prepared.x} ${prepared.y} ${prepared.z}"
                runCatching { sendRconCommand(command) }
                runCatching { com.pocketcraft.server.server.ServerLauncher.sendCommand(command) }
                delay(400)
                if (isDummyLive(prepared)) {
                    spawned = true
                    break
                }
            }

            if (!spawned) {
                val botDisplayName = dummyDisplayName(prepared)
                val safeName = botDisplayName.replace("'", "").replace("\"", "")

                // Try Purpur native bot commands (/bot <name> spawn at x y z or /bot spawn <name>)
                val purpurCmd1 = "bot $safeName spawn at ${prepared.x} ${prepared.y} ${prepared.z}"
                runCatching { sendRconCommand(purpurCmd1) }
                runCatching { com.pocketcraft.server.server.ServerLauncher.sendCommand(purpurCmd1) }
                delay(300)

                if (!isDummyLive(prepared)) {
                    val purpurCmd2 = "bot spawn $safeName"
                    runCatching { sendRconCommand(purpurCmd2) }
                    runCatching { com.pocketcraft.server.server.ServerLauncher.sendCommand(purpurCmd2) }
                    delay(300)
                }

                if (!isDummyLive(prepared)) {
                    // Try Carpet mod fake-player bot spawn command (/player <name> spawn at x y z)
                    val carpetSpawnCmd = com.pocketcraft.server.server.CarpetModManager.buildSpawnCommand(safeName, prepared.x, prepared.y, prepared.z)
                    runCatching { sendRconCommand(carpetSpawnCmd) }
                    runCatching { com.pocketcraft.server.server.ServerLauncher.sendCommand(carpetSpawnCmd) }
                    delay(300)
                }

                if (!isDummyLive(prepared)) {
                    // Fallback if Carpet mod / Purpur bot / Dummy plugin are not loaded: summon Zombie placeholder
                    val summonCommand = "summon minecraft:zombie ${prepared.x} ${prepared.y} ${prepared.z} {CustomName:'\"$safeName\"',CustomNameVisible:1b,Invulnerable:1b,NoAI:1b,Silent:1b,PersistenceRequired:1b,IsBaby:0b,Tags:[\"pocketcraft_afk_bot\"]}"
                    runCatching { sendRconCommand(summonCommand) }
                    runCatching { com.pocketcraft.server.server.ServerLauncher.sendCommand(summonCommand) }
                    delay(300)
                }
            }
            runCatching { sendRconCommand("forceload remove $forceloadKey") }
            runCatching { com.pocketcraft.server.server.ServerLauncher.sendCommand("forceload remove $forceloadKey") }

            delay(500)
            val synced = readDummyRecordByName(prepared.worldName, prepared.dummyEntityName)?.let { record ->
                prepared.copy(
                    dummyUuid = record.uuid.ifBlank { prepared.dummyUuid },
                    createdAt = record.createdAt.takeIf { it > 0L } ?: prepared.createdAt,
                    ownerPlayerUuid = record.ownerUuid.ifBlank { prepared.ownerPlayerUuid }
                )
            } ?: prepared

            dao.upsert(synced)
            syncWorldPluginFiles(synced.worldName)

            refreshNow()
            val liveNow = liveDummyIds.contains(synced.id) || isDummyLive(synced)
            if (liveNow) {
                liveDummyIds += synced.id
                restartPendingIds.remove(synced.id)
                renderCurrentWorld()
                "${synced.name} is live now."
            } else {
                restartPendingIds += synced.id
                renderCurrentWorld()
                "${synced.name} was saved, but the live dummy did not appear. Check coordinates or reload the server."
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
                val botDisplayName = dummyDisplayName(disabled)
                val safeName = botDisplayName.replace("'", "").replace("\"", "")
                
                // 1. Try Bukkit /dummy remove
                val command = "dummy remove ${disabled.dummyEntityName} ${disabled.ownerPlayerUuid}"
                runCatching { sendRconCommand(command) }
                
                // 2. Try Purpur native bot kill
                runCatching { sendRconCommand("bot $safeName kill") }
                runCatching { sendRconCommand("bot $safeName remove") }

                // 3. Try Carpet mod /player <name> kill
                val carpetKillCmd = com.pocketcraft.server.server.CarpetModManager.buildKillCommand(safeName)
                runCatching { sendRconCommand(carpetKillCmd) }

                // 4. Clean up kicks/kills for other formats
                runCatching { sendRconCommand("kick $safeName") }
                runCatching { sendRconCommand("kill ${dummySelector(disabled)}") }
                
                // Fallback cleanup for Fabric / Purpur / Vanilla tagged bots.
                val botTagSelector = "@e[tag=pocketcraft_afk_bot,limit=1]"
                runCatching { sendRconCommand("kill $botTagSelector") }
                runCatching { sendRconCommand("kill @e[tag=pocketcraft_afk_bot]") }
                runCatching { sendRconCommand("forceload remove ${disabled.x} ${disabled.z}") }
                
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

    private fun resolveConfiguredLevelName(serverDir: File): String {
        val propsFile = File(serverDir, "server.properties")
        if (!propsFile.exists()) return "world"
        val props = java.util.Properties()
        runCatching {
            propsFile.inputStream().use { props.load(it) }
        }
        return props.getProperty("level-name", "world").trim().ifBlank { "world" }
    }

    private suspend fun syncCurrentWorldPluginFiles() {
        syncWorldPluginFiles(currentWorldName())
    }

    private suspend fun syncWorldPluginFiles(worldName: String) {
        val serverDir = worldServerDir(worldName)
        BundledPluginInstaller.installBundledPlugins(appContext, serverDir)
        ensureDummyPluginSupport(serverDir)

        val allFarms = dao.getAll().filter { it.worldName.equals(worldName, ignoreCase = true) }
        val activeFarms = allFarms.filter { it.isActive && it.ownerPlayerUuid.isNotBlank() }

        val dummiesFile = File(serverDir, "plugins/dummyplayers/dummies.yml")
        val managedNames = allFarms.flatMap {
            listOf(
                it.dummyEntityName.lowercase(Locale.getDefault()),
                "$DUMMY_PREFIX${it.dummyEntityName}".lowercase(Locale.getDefault())
            )
        }.toSet()
        val activeNames = activeFarms.flatMap {
            listOf(
                it.dummyEntityName.lowercase(Locale.getDefault()),
                "$DUMMY_PREFIX${it.dummyEntityName}".lowercase(Locale.getDefault())
            )
        }.toSet()

        val mergedEntries = mutableListOf<DummyYamlEntry>()
        parseDummiesYaml(dummiesFile).forEach { entry ->
            val nameLower = entry.name.lowercase(Locale.getDefault())
            if (nameLower !in managedNames || nameLower in activeNames) {
                if (nameLower !in activeNames) {
                    mergedEntries += entry
                }
            }
        }

        val configuredWorldName = resolveConfiguredLevelName(serverDir)
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
                world = configuredWorldName,
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
        val dataDir = File(serverDir, "plugins/dummyplayers").also { it.mkdirs() }
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

    private fun AfkFarmLocationEntity.resolveOwnerOrDefault(): PlayerInfo {
        val existingOwner = resolveOnlineOwner(this)
            ?: knownPlayersProvider().firstOrNull {
                ownerPlayerUuid.isNotBlank() && it.uuid == ownerPlayerUuid
            }
            ?: knownPlayersProvider().firstOrNull {
                ownerPlayerName.isNotBlank() && it.name.equals(ownerPlayerName, ignoreCase = true)
            }
            ?: resolveDefaultOwner()
        return existingOwner ?: PlayerInfo(
            name = ownerPlayerName.ifBlank { "Server" },
            uuid = ownerPlayerUuid.ifBlank { UUID.randomUUID().toString() }
        )
    }

    private suspend fun isDummyLive(entity: AfkFarmLocationEntity): Boolean {
        val botName = dummyDisplayName(entity)
        if (onlinePlayersProvider().any {
            it.name.equals(botName, ignoreCase = true) ||
            it.name.equals(entity.dummyEntityName, ignoreCase = true) ||
            it.name.equals("AFK_${entity.dummyEntityName}", ignoreCase = true) ||
            it.name.equals(entity.name, ignoreCase = true)
        }) {
            return true
        }

        var response = runCatching {
            sendRconCommand("data get entity ${dummySelector(entity)} Pos")
        }.getOrDefault("")
        if (NBTParser.parsePosition(response) != null) return true

        val botTagSelector = """@e[tag=pocketcraft_afk_bot,name="${escapeSelectorName(dummyDisplayName(entity))}",limit=1]"""
        response = runCatching {
            sendRconCommand("data get entity $botTagSelector Pos")
        }.getOrDefault("")
        if (NBTParser.parsePosition(response) != null) return true

        val forceloadQuery = runCatching {
            sendRconCommand("forceload query ${entity.x} ${entity.z}")
        }.getOrDefault("")
        return forceloadQuery.contains("marked for force loading", ignoreCase = true) || forceloadQuery.contains("force loaded", ignoreCase = true)
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
        val dummiesFile = File(worldServerDir(worldName), "plugins/dummyplayers/dummies.yml")
        parseDummiesYaml(dummiesFile).forEach { entry ->
            if (entry.name.equals(dummyName, ignoreCase = true) || entry.name.equals("$DUMMY_PREFIX$dummyName", ignoreCase = true)) {
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

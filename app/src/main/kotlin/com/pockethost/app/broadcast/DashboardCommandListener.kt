package com.pockethost.app.broadcast

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.Timestamp
import com.pockethost.app.server.ServerHostService
import com.pockethost.app.data.preferences.AppPreferences
import com.pockethost.app.data.preferences.AppPreferencesStore
import com.pockethost.app.data.repository.ServerConfigRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import java.net.Socket
import org.json.JSONObject
import java.io.File
import org.json.JSONArray

class DashboardCommandListener(
    private val context: Context,
    private val scope: CoroutineScope,
    private val isMainProcess: Boolean,
    private val sendRconCommand: (suspend (String) -> String)? = null,
    private val toggleAfkBot: (suspend (Boolean) -> Unit)? = null,
    private val onPropertyUpdated: (suspend () -> Unit)? = null
) {
    private val db by lazy { FirebaseFirestore.getInstance() }
    private var registration: ListenerRegistration? = null

    fun start() {
        // Web Dashboard feature removed/discontinued. Listener disabled.
    }

    fun stop() {
        registration?.remove()
        registration = null
    }

    private suspend fun handleCommand(type: String, payload: Map<String, Any>, uid: String, commandId: String) {
        if (isMainProcess) {
            when (type) {
                "start_server" -> {
                    val versionId = AppPreferencesStore.getSelectedVersionFlow(context).first().orEmpty()
                    val worldName = AppPreferencesStore.getSelectedWorldFlow(context).first()
                    withContext(Dispatchers.Main) {
                        val ok = ServerHostService.start(context, versionId, worldName)
                        if (ok) {
                            updateCommandResult(uid, commandId, status = "done", result = "Server start initiated.")
                        } else {
                            updateCommandResult(uid, commandId, status = "failed", errorMsg = "Android OS restriction: Cannot start server from background. Please enable 'Always Alive in Background' in app settings first.")
                        }
                    }
                }
                "restart_server" -> {
                    withContext(Dispatchers.Main) {
                        val ok = ServerHostService.restart(context)
                        if (ok) {
                            updateCommandResult(uid, commandId, status = "done", result = "Server restart initiated.")
                        } else {
                            updateCommandResult(uid, commandId, status = "failed", errorMsg = "Android OS restriction: Cannot restart server from background. Please enable 'Always Alive in Background' in app settings first.")
                        }
                    }
                }
                "update_property" -> {
                    // Allow offline property writes — forward to service process if running,
                    // otherwise handle directly in main process.
                    if (!ServerHostService.isServiceRunning(context)) {
                        handleUpdateProperty(payload, uid, commandId)
                    }
                    // If service is running, let the service process handle it (fall-through skip)
                }
                "switch_world" -> {
                    val worldName = payload["worldName"] as? String ?: throw IllegalArgumentException("Missing worldName parameter")
                    val cleanWorld = worldName.trim()
                    if (cleanWorld.isEmpty()) throw IllegalArgumentException("Invalid world name")
                    if (ServerHostService.isServiceRunning(context)) {
                        throw IllegalStateException("Cannot switch worlds while the server is running. Stop the server first.")
                    }
                    AppPreferencesStore.setSelectedWorld(context, cleanWorld)
                    ServerHostService.persistRuntimeState(context, ServerHostService.getPersistedActiveVersion(context), cleanWorld, ServerHostService.getPersistedRuntimeState(context, ServerHostService.getPersistedActiveVersion(context)))
                    updateCommandResult(uid, commandId, status = "done", result = "Switched active world to $cleanWorld.")
                    onPropertyUpdated?.invoke()
                }
                "create_world" -> {
                    val worldName = payload["worldName"] as? String ?: throw IllegalArgumentException("Missing worldName parameter")
                    val cleanWorld = worldName.trim()
                    if (cleanWorld.isEmpty()) throw IllegalArgumentException("Invalid world name")
                    if (ServerHostService.isServiceRunning(context)) {
                        throw IllegalStateException("Cannot create worlds while the server is running. Stop the server first.")
                    }
                    AppPreferencesStore.setSelectedWorld(context, cleanWorld)
                    com.pockethost.app.service.ServerFileManager.getServerDir(context, cleanWorld)
                    updateCommandResult(uid, commandId, status = "done", result = "World $cleanWorld created and selected.")
                }
                "create_afk_bot" -> {
                    if (!ServerHostService.isServiceRunning(context)) {
                        handleCreateAfkBot(payload, uid, commandId)
                    }
                }
                "delete_afk_bot" -> {
                    if (!ServerHostService.isServiceRunning(context)) {
                        handleDeleteAfkBot(payload, uid, commandId)
                    }
                }
                "toggle_afk_bot_individual" -> {
                    if (!ServerHostService.isServiceRunning(context)) {
                        handleToggleAfkBotIndividual(payload, uid, commandId)
                    }
                }
                else -> {
                    if (ServerHostService.isServiceRunning(context)) {
                        // Skip updating or marking so the service process can read and handle it!
                        return
                    } else {
                        updateCommandResult(uid, commandId, status = "failed", errorMsg = "Server is stopped.")
                    }
                }
            }
            return
        }

        // Service Process Command Handling
        when (type) {
            "start_server" -> {
                val versionId = AppPreferencesStore.getSelectedVersionFlow(context).first().orEmpty()
                val worldName = AppPreferencesStore.getSelectedWorldFlow(context).first()
                withContext(Dispatchers.Main) {
                    val hostService = context as? ServerHostService
                    if (hostService != null) {
                        val startIntent = Intent(context, ServerHostService::class.java).apply {
                            action = ServerHostService.ACTION_START
                            putExtra(ServerHostService.EXTRA_VERSION_ID, versionId)
                            putExtra(ServerHostService.EXTRA_WORLD_NAME, worldName)
                        }
                        hostService.onStartCommand(startIntent, 0, 0)
                        updateCommandResult(uid, commandId, status = "done", result = "Server start initiated in-process.")
                    } else {
                        val ok = ServerHostService.start(context, versionId, worldName)
                        if (ok) {
                            updateCommandResult(uid, commandId, status = "done", result = "Server start initiated.")
                        } else {
                            updateCommandResult(uid, commandId, status = "failed", errorMsg = "Android OS restriction: Cannot start server from background. Please enable 'Always Alive in Background' in app settings first.")
                        }
                    }
                }
            }
            "stop_server" -> {
                updateCommandResult(uid, commandId, status = "done", result = "Server stop initiated.")
                withContext(Dispatchers.Main) {
                    val hostService = context as? ServerHostService
                    if (hostService != null) {
                        val stopIntent = Intent(context, ServerHostService::class.java).apply {
                            action = ServerHostService.ACTION_STOP
                            putExtra("keep_listener_alive", true)
                        }
                        hostService.onStartCommand(stopIntent, 0, 0)
                    } else {
                        val stopIntent = Intent(context, ServerHostService::class.java).apply {
                            action = ServerHostService.ACTION_STOP
                            putExtra("keep_listener_alive", true)
                        }
                        try {
                            context.startService(stopIntent)
                        } catch (e: Exception) {
                            Log.e("DashboardCommandListener", "Failed to stop service: ${e.message}")
                        }
                    }
                }
            }
            "restart_server" -> {
                withContext(Dispatchers.Main) {
                    val hostService = context as? ServerHostService
                    if (hostService != null) {
                        val restartIntent = Intent(context, ServerHostService::class.java).apply {
                            action = ServerHostService.ACTION_RESTART
                        }
                        hostService.onStartCommand(restartIntent, 0, 0)
                        updateCommandResult(uid, commandId, status = "done", result = "Server restart initiated in-process.")
                    } else {
                        val ok = ServerHostService.restart(context)
                        if (ok) {
                            updateCommandResult(uid, commandId, status = "done", result = "Server restart initiated.")
                        } else {
                            updateCommandResult(uid, commandId, status = "failed", errorMsg = "Android OS restriction: Cannot restart server from background. Please enable 'Always Alive in Background' in app settings first.")
                        }
                    }
                }
            }
            "rcon" -> {
                val command = payload["command"] as? String ?: throw IllegalArgumentException("Missing command parameter")
                val response = sendRconCommand?.invoke(command) ?: "RCON not available."
                updateCommandResult(uid, commandId, status = "done", result = response)
            }
            "toggle_afk_bot" -> {
                val enabled = payload["enabled"] as? Boolean ?: throw IllegalArgumentException("Missing enabled parameter")
                toggleAfkBot?.invoke(enabled)
                updateCommandResult(uid, commandId, status = "done", result = "AFK bot toggled to $enabled.")
            }
            "create_afk_bot" -> {
                handleCreateAfkBot(payload, uid, commandId)
            }
            "delete_afk_bot" -> {
                handleDeleteAfkBot(payload, uid, commandId)
            }
            "toggle_afk_bot_individual" -> {
                handleToggleAfkBotIndividual(payload, uid, commandId)
            }
            "kick" -> {
                val playerName = payload["playerName"] as? String ?: throw IllegalArgumentException("Missing playerName parameter")
                val response = sendRconCommand?.invoke("kick @a[name=\"${escapeSelectorName(playerName)}\",limit=1] Removed by PocketHost Web Dashboard") ?: "RCON not available."
                updateCommandResult(uid, commandId, status = "done", result = response)
            }
            "ban" -> {
                val playerName = payload["playerName"] as? String ?: throw IllegalArgumentException("Missing playerName parameter")
                val reason = payload["reason"] as? String ?: "Banned from PocketHost Web Dashboard"
                val response = sendRconCommand?.invoke("ban $playerName $reason") ?: "RCON not available."
                updateCommandResult(uid, commandId, status = "done", result = response)
            }
            "whitelist_add" -> {
                val playerName = payload["playerName"] as? String ?: throw IllegalArgumentException("Missing playerName parameter")
                val response = sendRconCommand?.invoke("whitelist add $playerName") ?: "RCON not available."
                updateCommandResult(uid, commandId, status = "done", result = response)
            }
            "whitelist_remove" -> {
                val playerName = payload["playerName"] as? String ?: throw IllegalArgumentException("Missing playerName parameter")
                val response = sendRconCommand?.invoke("whitelist remove $playerName") ?: "RCON not available."
                updateCommandResult(uid, commandId, status = "done", result = response)
            }
            "set_subdomain" -> {
                val subdomain = payload["subdomain"] as? String ?: throw IllegalArgumentException("Missing subdomain parameter")
                val normalized = subdomain.trim().lowercase()
                if (!normalized.matches(Regex("^[a-z0-9-]{3,32}$"))) {
                    throw IllegalArgumentException("Invalid subdomain format.")
                }
                val prefs = AppPreferences(context)
                val detectedRegion = if (prefs.relayHost == "eu.pocketcraft.online") "eu" else "as"
                val oldSubdomain = prefs.customSubdomain

                if (oldSubdomain != null && oldSubdomain != normalized) {
                    db.collection("subdomains").document(oldSubdomain).delete().await()
                }

                db.runTransaction { transaction ->
                    val docRef = db.collection("subdomains").document(normalized)
                    val existing = transaction.get(docRef)
                    if (existing.exists()) {
                        val existingOwner = existing.getString("ownerId")
                        if (existingOwner != uid) {
                            throw IllegalStateException("That IP is already taken.")
                        }
                    }
                    transaction.set(docRef, mapOf(
                        "serverId" to uid,
                        "ownerId" to uid,
                        "createdAt" to (existing.getTimestamp("createdAt") ?: Timestamp.now()),
                        "region" to detectedRegion
                    ))
                }.await()

                prefs.customSubdomain = normalized
                prefs.customSubdomainRegion = detectedRegion
                updateCommandResult(uid, commandId, status = "done", result = "Subdomain set to $normalized.")
            }
            "update_property" -> {
                handleUpdateProperty(payload, uid, commandId)
            }
            "switch_world" -> {
                val worldName = payload["worldName"] as? String ?: throw IllegalArgumentException("Missing worldName parameter")
                val cleanWorld = worldName.trim()
                if (cleanWorld.isEmpty()) throw IllegalArgumentException("Invalid world name")
                if (ServerHostService.isServiceRunning(context)) {
                    throw IllegalStateException("Cannot switch worlds while the server is running. Stop the server first.")
                }
                AppPreferencesStore.setSelectedWorld(context, cleanWorld)
                ServerHostService.persistRuntimeState(context, ServerHostService.getPersistedActiveVersion(context), cleanWorld, ServerHostService.getPersistedRuntimeState(context, ServerHostService.getPersistedActiveVersion(context)))
                updateCommandResult(uid, commandId, status = "done", result = "Switched active world to $cleanWorld.")
                onPropertyUpdated?.invoke()
            }
            "create_world" -> {
                val worldName = payload["worldName"] as? String ?: throw IllegalArgumentException("Missing worldName parameter")
                val cleanWorld = worldName.trim()
                if (cleanWorld.isEmpty()) throw IllegalArgumentException("Invalid world name")
                if (ServerHostService.isServiceRunning(context)) {
                    throw IllegalStateException("Cannot create worlds while the server is running. Stop the server first.")
                }
                AppPreferencesStore.setSelectedWorld(context, cleanWorld)
                com.pockethost.app.service.ServerFileManager.getServerDir(context, cleanWorld)
                updateCommandResult(uid, commandId, status = "done", result = "World $cleanWorld created and selected.")
            }
            else -> {
                throw IllegalArgumentException("Unknown command type: $type")
            }
        }
    }

    /**
     * Writes a key=value pair to the active world's server.properties file.
     * This works even when the server is offline, allowing the dashboard to
     * pre-configure settings before the next server start.
     *
     * If the server is running, we also send the equivalent RCON command so
     * the change takes effect immediately without requiring a restart.
     */
    private suspend fun handleUpdateProperty(payload: Map<String, Any>, uid: String, commandId: String) {
        val key = payload["key"] as? String ?: throw IllegalArgumentException("Missing key parameter")
        val value = payload["value"] as? String ?: throw IllegalArgumentException("Missing value parameter")

        val worldName = AppPreferencesStore.getSelectedWorldFlow(context).first()
        val serverDir = com.pockethost.app.service.ServerFileManager.getServerDir(context, worldName)
        val propsFile = File(serverDir, "server.properties")

        if (!propsFile.exists()) {
            updateCommandResult(uid, commandId, status = "failed", errorMsg = "server.properties not found. Start the server at least once first.")
            return
        }

        // Read existing properties preserving order
        val lines = propsFile.readLines().toMutableList()
        var found = false
        for (i in lines.indices) {
            val trimmed = lines[i].trim()
            if (!trimmed.startsWith("#") && trimmed.startsWith("$key=")) {
                lines[i] = "$key=$value"
                found = true
                break
            }
        }
        if (!found) {
            lines.add("$key=$value")
        }
        propsFile.writeText(lines.joinToString("\n"))

        // Sync configuration change back to the ServerConfigRepository profile as well
        runCatching {
            val repository = ServerConfigRepository(context).apply {
                setWorldNameOverride(worldName)
            }
            val currentConfig = repository.loadConfig()
            val updatedConfig = when (key) {
                "level-name" -> currentConfig.copy(worldName = value)
                "level-seed" -> currentConfig.copy(worldSeed = value)
                "max-players" -> currentConfig.copy(maxPlayers = value.toIntOrNull() ?: currentConfig.maxPlayers)
                "difficulty" -> currentConfig.copy(difficulty = value)
                "gamemode" -> currentConfig.copy(gameMode = value)
                "online-mode" -> currentConfig.copy(onlineMode = value.toBoolean())
                "motd" -> currentConfig.copy(motd = value)
                "pvp" -> currentConfig.copy(pvp = value.toBoolean())
                "view-distance", "pocketcraft-desired-view-distance" -> currentConfig.copy(viewDistance = value.toIntOrNull() ?: currentConfig.viewDistance)
                "simulation-distance", "pocketcraft-desired-simulation-distance" -> currentConfig.copy(simulationDistance = value.toIntOrNull() ?: currentConfig.simulationDistance)
                "spawn-protection" -> currentConfig.copy(spawnProtection = value.toIntOrNull() ?: currentConfig.spawnProtection)
                "allow-flight" -> currentConfig.copy(allowFlight = value.toBoolean())
                "white-list" -> currentConfig.copy(whiteList = value.toBoolean())
                "enforce-whitelist" -> currentConfig.copy(enforceWhitelist = value.toBoolean())
                "enable-command-block" -> currentConfig.copy(commandBlocks = value.toBoolean())
                "allow-nether" -> currentConfig.copy(netherEnabled = value.toBoolean())
                "spawn-monsters" -> currentConfig.copy(spawnMonsters = value.toBoolean())
                "spawn-animals" -> currentConfig.copy(spawnAnimals = value.toBoolean())
                "spawn-npcs" -> currentConfig.copy(spawnNpcs = value.toBoolean())
                "hardcore" -> currentConfig.copy(hardcore = value.toBoolean())
                "pocketcraft-max-ram-mb" -> currentConfig.copy(maxRamMb = value.toIntOrNull() ?: currentConfig.maxRamMb)
                "pocketcraft-ram-mode" -> currentConfig.copy(ramMode = value)
                "entity-broadcast-range-percentage" -> currentConfig.copy(entityBroadcastRangePercentage = value.toIntOrNull() ?: currentConfig.entityBroadcastRangePercentage)
                "max-world-size" -> currentConfig.copy(maxWorldSize = value.toIntOrNull() ?: currentConfig.maxWorldSize)
                "use-native-transport" -> currentConfig.copy(useNativeTransport = value.toBoolean())
                "max-build-height" -> currentConfig.copy(maxBuildHeight = value.toIntOrNull() ?: currentConfig.maxBuildHeight)
                "generate-structures" -> currentConfig.copy(generateStructures = value.toBoolean())
                "level-type" -> currentConfig.copy(levelType = value)
                "pocketcraft-server-type" -> currentConfig.copy(serverType = com.pockethost.app.data.model.ServerType.fromString(value))
                "pocketcraft-game-version" -> currentConfig.copy(gameVersion = value)
                "pocketcraft-custom-jar-path" -> currentConfig.copy(customJarPath = value.takeIf { it.isNotBlank() })
                else -> currentConfig
            }
            repository.saveConfig(updatedConfig)
        }.onFailure { e ->
            Log.e("DashboardCommandListener", "Failed to sync config to repository: ${e.message}")
        }

        // If server is running, also push via RCON for live effect
        val rconCall = sendRconCommand
        val rconResult = if (rconCall != null && ServerHostService.isServiceRunning(context)) {
            runCatching {
                // Map property keys to their equivalent RCON commands
                when (key) {
                    "difficulty" -> rconCall.invoke("difficulty $value")
                    "gamemode" -> rconCall.invoke("defaultgamemode $value")
                    "view-distance" -> rconCall.invoke("view-distance $value")
                    "simulation-distance" -> rconCall.invoke("simulation-distance $value")
                    "white-list" -> rconCall.invoke(if (value == "true") "whitelist on" else "whitelist off")
                    else -> null
                }
            }.getOrNull()
        } else null

        val msg = if (rconResult != null) {
            "Property $key=$value saved and applied live via RCON."
        } else {
            "Property $key=$value saved to server.properties. Restart server to apply."
        }
        updateCommandResult(uid, commandId, status = "done", result = msg)
        pushOfflineStatusUpdate(uid)
        onPropertyUpdated?.invoke()
    }

    private suspend fun pushOfflineStatusUpdate(uid: String) = withContext(Dispatchers.IO) {
        val prefs = com.pockethost.app.data.preferences.AppPreferences(context)
        val worldName = AppPreferencesStore.getSelectedWorldFlow(context).first()
        
        // 1. Read server properties
        val serverDir = com.pockethost.app.service.ServerFileManager.getServerDir(context, worldName)
        val propsFile = File(serverDir, "server.properties")
        val properties = mutableMapOf<String, String>()
        if (propsFile.exists()) {
            propsFile.readLines().forEach { line ->
                val trimmed = line.trim()
                if (!trimmed.startsWith("#") && trimmed.contains("=")) {
                    val parts = trimmed.split("=", limit = 2)
                    if (parts.size == 2) {
                        val key = parts[0].trim()
                        val value = parts[1].trim()
                        properties[key] = value
                    }
                }
            }
        }
        
        // 2. Read AFK bots
        val dbDao = com.pockethost.app.afk.AfkHelperDatabase.getInstance(context).afkFarmLocationDao()
        val rawBots = dbDao.getAll().filter { it.worldName.equals(worldName, ignoreCase = true) }
        val afkBotEnabled = rawBots.any { it.isActive }
        val afkBotsList = rawBots.map { bot ->
            mapOf(
                "id" to bot.id,
                "name" to bot.name,
                "dummyName" to bot.dummyEntityName,
                "x" to bot.x,
                "y" to bot.y,
                "z" to bot.z,
                "world" to bot.worldName,
                "active" to bot.isActive,
                "owner" to bot.ownerPlayerName,
                "ownerUuid" to bot.ownerPlayerUuid
            )
        }
        
        val localIp = com.pockethost.app.server.ServerAddressResolver.getLocalIpAddress() ?: ""
        
        val statusDoc = mapOf(
            "properties" to properties,
            "afkBotEnabled" to afkBotEnabled,
            "afkBots" to afkBotsList,
            "localIp" to localIp,
            "currentWorld" to worldName,
            "lastSeen" to Timestamp.now()
        )
        
        try {
            db.collection("users").document(uid).collection("dashboard_status").document("status")
                .set(statusDoc, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.e("DashboardCommandListener", "Failed to push offline status update", e)
        }
    }

    private suspend fun handleCreateAfkBot(payload: Map<String, Any>, uid: String, commandId: String) {
        val name = payload["name"] as? String ?: throw IllegalArgumentException("Missing name parameter")
        val x = (payload["x"] as? Number)?.toInt() ?: 0
        val y = (payload["y"] as? Number)?.toInt() ?: 64
        val z = (payload["z"] as? Number)?.toInt() ?: 0
        
        val hostService = context as? ServerHostService
        if (hostService != null && ServerHostService.isServiceRunning(context)) {
            val resultMsg = hostService.afkHelperManager.addFarm(name, x, y, z)
            updateCommandResult(uid, commandId, status = "done", result = resultMsg)
        } else {
            withContext(Dispatchers.IO) {
                val dbDao = com.pockethost.app.afk.AfkHelperDatabase.getInstance(context).afkFarmLocationDao()
                val currentWorld = AppPreferencesStore.getSelectedWorldFlow(context).first()
                val isPremium = com.pockethost.app.billing.BillingManager.getInstance(context.applicationContext).isPremium.value
                val existingCount = dbDao.getAll().filter { it.worldName.equals(currentWorld, ignoreCase = true) }.size
                if (!isPremium && existingCount >= 1) {
                    updateCommandResult(uid, commandId, status = "failed", errorMsg = "Free plan is limited to 1 AFK bot. Upgrade to Pro to unlock unlimited AFK bots!")
                } else {
                    val entity = com.pockethost.app.afk.AfkFarmLocationEntity(
                        id = java.util.UUID.randomUUID().toString(),
                        worldName = currentWorld,
                        name = name.trim(),
                        x = x,
                        y = y,
                        z = z,
                        isActive = false,
                        dummyEntityName = name.trim().lowercase().replace(Regex("[^a-z0-9_]+"), "_").take(12),
                        ownerPlayerName = "",
                        ownerPlayerUuid = "",
                        createdAt = System.currentTimeMillis()
                    )
                    dbDao.upsert(entity)
                    updateCommandResult(uid, commandId, status = "done", result = "Saved $name to AFK Helpers (Offline).")
                }
            }
        }
        pushOfflineStatusUpdate(uid)
    }

    private suspend fun handleDeleteAfkBot(payload: Map<String, Any>, uid: String, commandId: String) {
        val botId = payload["id"] as? String ?: throw IllegalArgumentException("Missing id parameter")
        val hostService = context as? ServerHostService
        if (hostService != null && ServerHostService.isServiceRunning(context)) {
            val resultMsg = hostService.afkHelperManager.deleteFarm(botId)
            updateCommandResult(uid, commandId, status = "done", result = resultMsg)
        } else {
            withContext(Dispatchers.IO) {
                val dbDao = com.pockethost.app.afk.AfkHelperDatabase.getInstance(context).afkFarmLocationDao()
                val entity = dbDao.getAll().firstOrNull { it.id == botId }
                if (entity != null) {
                    dbDao.delete(entity)
                    updateCommandResult(uid, commandId, status = "done", result = "Deleted ${entity.name} (Offline).")
                } else {
                    updateCommandResult(uid, commandId, status = "failed", errorMsg = "Bot not found.")
                }
            }
        }
        pushOfflineStatusUpdate(uid)
    }

    private suspend fun handleToggleAfkBotIndividual(payload: Map<String, Any>, uid: String, commandId: String) {
        val botId = payload["id"] as? String ?: throw IllegalArgumentException("Missing id parameter")
        val hostService = context as? ServerHostService
        if (hostService != null && ServerHostService.isServiceRunning(context)) {
            val resultMsg = hostService.afkHelperManager.toggleFarm(botId)
            updateCommandResult(uid, commandId, status = "done", result = resultMsg)
        } else {
            withContext(Dispatchers.IO) {
                val dbDao = com.pockethost.app.afk.AfkHelperDatabase.getInstance(context).afkFarmLocationDao()
                val entity = dbDao.getAll().firstOrNull { it.id == botId }
                if (entity != null) {
                    val updated = entity.copy(isActive = !entity.isActive)
                    dbDao.upsert(updated)
                    updateCommandResult(uid, commandId, status = "done", result = "Toggled ${entity.name} active state to ${updated.isActive} (Offline).")
                } else {
                    updateCommandResult(uid, commandId, status = "failed", errorMsg = "Bot not found.")
                }
            }
        }
        pushOfflineStatusUpdate(uid)
    }

    private fun updateCommandResult(uid: String, commandId: String, status: String, result: String? = null, errorMsg: String? = null) {
        val updates = mutableMapOf<String, Any?>(
            "status" to status,
            "result" to result,
            "errorMessage" to errorMsg
        )
        db.collection("users").document(uid).collection("dashboard_commands")
            .document(commandId)
            .update(updates)
    }

    private fun escapeSelectorName(name: String): String =
        name.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun shouldHandleCommand(type: String): Boolean {
        if (isMainProcess) {
            // If the background service is running, let the service process handle all commands.
            if (ServerHostService.isServiceRunning(context)) {
                return false
            }
            // If the server is stopped, the main process handles everything (start_server, update_property,
            // or failing other commands since the server is offline).
            return true
        }
        // The service process handles all commands when it is active.
        return true
    }
}

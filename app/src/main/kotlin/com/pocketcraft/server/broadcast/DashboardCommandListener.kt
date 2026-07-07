package com.pocketcraft.server.broadcast

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.Timestamp
import com.pocketcraft.server.server.ServerHostService
import com.pocketcraft.server.data.preferences.AppPreferences
import com.pocketcraft.server.data.preferences.AppPreferencesStore
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
        val prefs = AppPreferences(context)
        val uid = FirebaseAuth.getInstance().currentUser?.uid
            ?: prefs.firebaseUserUid
            ?: return
        val secret = prefs.dashboardSecret ?: ""
        Log.d("DashboardCommandListener", "Starting listener for user: $uid")

        registration = db.collection("users").document(uid).collection("dashboard_commands")
            .whereEqualTo("status", "pending")
            .whereEqualTo("secret", secret)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e("DashboardCommandListener", "Error listening to dashboard commands", error)
                    return@addSnapshotListener
                }
                if (snapshots == null || snapshots.isEmpty) return@addSnapshotListener

                val sortedDocs = snapshots.documents.sortedBy { doc ->
                    doc.getTimestamp("createdAt")
                }
                for (doc in sortedDocs) {
                    val commandId = doc.id
                    val type = doc.getString("type") ?: continue
                    val payload = doc.get("payload") as? Map<String, Any> ?: emptyMap()

                    if (!shouldHandleCommand(type)) {
                        continue
                    }

                    // Immediately mark as acked
                    db.collection("users").document(uid).collection("dashboard_commands")
                        .document(commandId)
                        .update("status", "acked")

                    scope.launch(Dispatchers.IO) {
                        try {
                            handleCommand(type, payload, uid, commandId)
                        } catch (e: Exception) {
                            Log.e("DashboardCommandListener", "Error processing command $commandId", e)
                            updateCommandResult(uid, commandId, status = "failed", errorMsg = e.message)
                        }
                    }
                }
            }
    }

    fun stop() {
        registration?.remove()
        registration = null
    }

    private suspend fun handleCommand(type: String, payload: Map<String, Any>, uid: String, commandId: String) {
        if (isMainProcess) {
            when (type) {
                "start_server" -> {
                    updateCommandResult(uid, commandId, status = "done", result = "Server start initiated.")
                    val versionId = AppPreferencesStore.getSelectedVersionFlow(context).first().orEmpty()
                    val worldName = AppPreferencesStore.getSelectedWorldFlow(context).first()
                    withContext(Dispatchers.Main) {
                        ServerHostService.start(context, versionId, worldName)
                    }
                }
                "restart_server" -> {
                    updateCommandResult(uid, commandId, status = "done", result = "Server restart initiated.")
                    withContext(Dispatchers.Main) {
                        ServerHostService.restart(context)
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
                    com.pocketcraft.server.service.ServerFileManager.getServerDir(context, cleanWorld)
                    updateCommandResult(uid, commandId, status = "done", result = "World $cleanWorld created and selected.")
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
                updateCommandResult(uid, commandId, status = "done", result = "Server start initiated.")
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
                    } else {
                        ServerHostService.start(context, versionId, worldName)
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
                        context.startService(stopIntent)
                    }
                }
            }
            "restart_server" -> {
                updateCommandResult(uid, commandId, status = "done", result = "Server restart initiated.")
                withContext(Dispatchers.Main) {
                    val hostService = context as? ServerHostService
                    if (hostService != null) {
                        val restartIntent = Intent(context, ServerHostService::class.java).apply {
                            action = ServerHostService.ACTION_RESTART
                        }
                        hostService.onStartCommand(restartIntent, 0, 0)
                    } else {
                        ServerHostService.restart(context)
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
            "kick" -> {
                val playerName = payload["playerName"] as? String ?: throw IllegalArgumentException("Missing playerName parameter")
                val response = sendRconCommand?.invoke("kick @a[name=\"${escapeSelectorName(playerName)}\",limit=1] Removed by PocketCraft Web Dashboard") ?: "RCON not available."
                updateCommandResult(uid, commandId, status = "done", result = response)
            }
            "ban" -> {
                val playerName = payload["playerName"] as? String ?: throw IllegalArgumentException("Missing playerName parameter")
                val reason = payload["reason"] as? String ?: "Banned from PocketCraft Web Dashboard"
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
                com.pocketcraft.server.service.ServerFileManager.getServerDir(context, cleanWorld)
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
        val serverDir = com.pocketcraft.server.service.ServerFileManager.getServerDir(context, worldName)
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
        onPropertyUpdated?.invoke()
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

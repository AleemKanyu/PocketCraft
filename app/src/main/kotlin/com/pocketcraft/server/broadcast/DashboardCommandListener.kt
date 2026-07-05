package com.pocketcraft.server.broadcast

import android.content.Context
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
    private val sendRconCommand: suspend (String) -> String,
    private val toggleAfkBot: suspend (Boolean) -> Unit
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
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e("DashboardCommandListener", "Error listening to dashboard commands", error)
                    return@addSnapshotListener
                }
                if (snapshots == null || snapshots.isEmpty) return@addSnapshotListener

                for (doc in snapshots.documents) {
                    val commandId = doc.id
                    val type = doc.getString("type") ?: continue
                    val payload = doc.get("payload") as? Map<String, Any> ?: emptyMap()

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
        when (type) {
            "start_server" -> {
                val versionId = AppPreferencesStore.getSelectedVersionFlow(context).first().orEmpty()
                val worldName = AppPreferencesStore.getSelectedWorldFlow(context).first()
                withContext(Dispatchers.Main) {
                    ServerHostService.start(context, versionId, worldName)
                }
                updateCommandResult(uid, commandId, status = "done", result = "Server start initiated.")
            }
            "stop_server" -> {
                withContext(Dispatchers.Main) {
                    ServerHostService.stop(context)
                }
                updateCommandResult(uid, commandId, status = "done", result = "Server stop initiated.")
            }
            "rcon" -> {
                val command = payload["command"] as? String ?: throw IllegalArgumentException("Missing command parameter")
                val response = sendRconCommand(command)
                updateCommandResult(uid, commandId, status = "done", result = response)
            }
            "toggle_afk_bot" -> {
                val enabled = payload["enabled"] as? Boolean ?: throw IllegalArgumentException("Missing enabled parameter")
                toggleAfkBot(enabled)
                updateCommandResult(uid, commandId, status = "done", result = "AFK bot toggled to $enabled.")
            }
            "kick" -> {
                val playerName = payload["playerName"] as? String ?: throw IllegalArgumentException("Missing playerName parameter")
                val response = sendRconCommand("kick @a[name=\"${escapeSelectorName(playerName)}\",limit=1] Removed by PocketCraft Web Dashboard")
                updateCommandResult(uid, commandId, status = "done", result = response)
            }
            "ban" -> {
                val playerName = payload["playerName"] as? String ?: throw IllegalArgumentException("Missing playerName parameter")
                val reason = payload["reason"] as? String ?: "Banned from PocketCraft Web Dashboard"
                val response = sendRconCommand("ban $playerName $reason")
                updateCommandResult(uid, commandId, status = "done", result = response)
            }
            "whitelist_add" -> {
                val playerName = payload["playerName"] as? String ?: throw IllegalArgumentException("Missing playerName parameter")
                val response = sendRconCommand("whitelist add $playerName")
                updateCommandResult(uid, commandId, status = "done", result = response)
            }
            "whitelist_remove" -> {
                val playerName = payload["playerName"] as? String ?: throw IllegalArgumentException("Missing playerName parameter")
                val response = sendRconCommand("whitelist remove $playerName")
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
            else -> {
                throw IllegalArgumentException("Unknown command type: $type")
            }
        }
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
}

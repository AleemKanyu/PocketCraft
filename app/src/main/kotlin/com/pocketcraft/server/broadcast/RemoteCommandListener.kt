package com.pocketcraft.server.broadcast

import android.content.Context
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ListenerRegistration
import com.pocketcraft.server.BuildConfig
import com.pocketcraft.server.config.RemoteConfigManager
import com.pocketcraft.server.data.preferences.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Date

object RemoteCommandListener {
    private const val TAG = "RemoteCommandListener"
    private val db by lazy { FirebaseFirestore.getInstance() }
    private var listenerRegistration: ListenerRegistration? = null
    
    private val _forceReconnectTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val forceReconnectTrigger: SharedFlow<Unit> = _forceReconnectTrigger.asSharedFlow()

    private val _triggerRatingPromptFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val triggerRatingPromptFlow: SharedFlow<Unit> = _triggerRatingPromptFlow.asSharedFlow()

    private val _commandBroadcasts = MutableStateFlow<List<BroadcastMessage>>(emptyList())
    val commandBroadcasts: StateFlow<List<BroadcastMessage>> = _commandBroadcasts.asStateFlow()

    fun startListening(context: Context) {
        if (listenerRegistration != null) return
        val appContext = context.applicationContext
        val preferences = AppPreferences(appContext)

        val oneDayAgo = Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000)
        val timestampOneDayAgo = Timestamp(oneDayAgo)

        Log.d(TAG, "Starting remote_commands listener for commands since $oneDayAgo")

        listenerRegistration = db.collection("remote_commands")
            .whereGreaterThan("createdAt", timestampOneDayAgo)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(10)
            .addSnapshotListener { snapshots, error ->
                if (snapshots == null) {
                    if (error != null) {
                        Log.e(TAG, "Error listening to remote_commands: ${error.message}", error)
                    }
                    return@addSnapshotListener
                }

                val currentUid = FirebaseAuth.getInstance().currentUser?.uid?.trim() ?: ""
                val currentVersionCode = BuildConfig.VERSION_CODE
                val now = Timestamp.now()

                for (doc in snapshots.documents) {
                    val commandId = doc.id
                    if (commandId == preferences.lastProcessedCommandId) continue

                    val target = doc.getString("target") ?: ""
                    val expiresAt = doc.getTimestamp("expiresAt")
                    val type = doc.getString("type") ?: ""
                    val payload = doc.get("payload") as? Map<*, *>

                    if (expiresAt != null && expiresAt.compareTo(now) < 0) {
                        Log.d(TAG, "Command $commandId ignored: expired.")
                        continue
                    }

                    val targetMatches = when {
                        target.equals("all", ignoreCase = true) -> true
                        target.startsWith("version:") -> {
                            val targetCode = target.substringAfter("version:").trim().toIntOrNull()
                            currentVersionCode == targetCode
                        }
                        target.startsWith("uid:") -> {
                            val targetUid = target.substringAfter("uid:").trim()
                            currentUid.equals(targetUid, ignoreCase = true)
                        }
                        else -> false
                    }

                    if (!targetMatches) continue

                    Log.i(TAG, "Executing remote command $commandId: type=$type, target=$target")
                    preferences.lastProcessedCommandId = commandId

                    when (type) {
                        "clear_cache" -> {
                            clearLocalCaches(appContext)
                        }
                        "trigger_rating_prompt" -> {
                            preferences.pendingRatingPopup = true
                            preferences.ratingPopupDismissedForever = false
                            preferences.ratingPopupShowCount = 0
                            preferences.ratingPopupLastShownAt = 0L
                            _triggerRatingPromptFlow.tryEmit(Unit)
                        }
                        "force_relay_refetch" -> {
                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    RemoteConfigManager.refreshConfig(appContext)
                                    Log.d(TAG, "force_relay_refetch command completed.")
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error executing force_relay_refetch: ${e.message}", e)
                                }
                            }
                        }
                        "force_reconnect" -> {
                            _forceReconnectTrigger.tryEmit(Unit)
                        }
                        "show_message" -> {
                            val message = payload?.get("message") as? String ?: ""
                            val title = payload?.get("title") as? String ?: "Notification"
                            if (message.isNotBlank()) {
                                val commandMsg = BroadcastMessage(
                                    id = commandId,
                                    active = true,
                                    title = title,
                                    body = message,
                                    type = "info",
                                    dismissible = true,
                                    createdAt = now
                                )
                                _commandBroadcasts.value = _commandBroadcasts.value + commandMsg
                            }
                        }
                    }
                }
            }
    }

    fun stopListening() {
        listenerRegistration?.remove()
        listenerRegistration = null
    }

    fun dismissCommandBroadcast(id: String) {
        _commandBroadcasts.value = _commandBroadcasts.value.filter { it.id != id }
    }

    private fun clearLocalCaches(context: Context) {
        try {
            AppPreferences(context).cachedRelayServersJson = null
            val cacheDir = context.cacheDir
            if (cacheDir != null && cacheDir.exists()) {
                cacheDir.deleteRecursively()
            }
            Log.i(TAG, "clear_cache command completed successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing local caches: ${e.message}", e)
        }
    }
}

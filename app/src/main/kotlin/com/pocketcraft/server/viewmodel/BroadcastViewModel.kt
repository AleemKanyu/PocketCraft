package com.pocketcraft.server.viewmodel

import android.content.Context
import android.content.pm.PackageInfo
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketcraft.server.broadcast.BroadcastManager
import com.pocketcraft.server.broadcast.BroadcastMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BroadcastViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val prefs = context.getSharedPreferences("broadcast_dismissed", Context.MODE_PRIVATE)
    private val DISMISSED_KEY = "dismissed_ids"

    private val versionCode: Int = runCatching {
        val packageInfo: PackageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        packageInfo.longVersionCode.toInt()
    }.getOrDefault(0)

    val broadcasts: StateFlow<List<BroadcastMessage>> =
        BroadcastManager.getBroadcastsFlow(context, versionCode)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** IDs of broadcasts the user has dismissed — persisted across app restarts. */
    private val dismissed = MutableStateFlow<Set<String>>(loadDismissedIds())

    private val _configBanner = MutableStateFlow<BroadcastMessage?>(null)
    val configBanner: StateFlow<BroadcastMessage?> = _configBanner

    private val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
    private val answeredMap = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    val visibleBroadcasts: StateFlow<List<BroadcastMessage>> =
        combine(
            broadcasts,
            dismissed,
            answeredMap,
            com.pocketcraft.server.broadcast.RemoteCommandListener.commandBroadcasts
        ) { liveBroadcasts, dismissedIds, answeredStates, commandBroadcasts ->
            val allMessages = liveBroadcasts + commandBroadcasts
            allMessages
                .filter { it.id !in dismissedIds }
                .map { msg ->
                    val isAlreadyAnswered = answeredStates[msg.id] == true
                    if (isAlreadyAnswered) {
                        msg.copy(interactionType = "none")
                    } else {
                        msg
                    }
                }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        BroadcastManager.initRemoteConfig { message ->
            _configBanner.value = message
        }

        viewModelScope.launch {
            while (isActive) {
                delay(60_000)
                BroadcastManager.refreshRemoteConfig { message ->
                    _configBanner.value = message
                }
            }
        }

        viewModelScope.launch {
            broadcasts.collect { messages ->
                val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                if (uid.isNullOrBlank()) return@collect

                messages.forEach { msg ->
                    if ((msg.interactionType == "question" || msg.interactionType == "opt_in")
                        && !answeredMap.value.containsKey(msg.id)
                    ) {
                        // Check broadcast_responses/{broadcastId}_{uid} for both polls and opt-ins
                        db.collection("broadcast_responses")
                            .document("${msg.id}_$uid")
                            .get()
                            .addOnSuccessListener { doc ->
                                answeredMap.value = answeredMap.value + (msg.id to doc.exists())
                            }
                    }
                }
            }
        }
    }

    fun dismiss(id: String) {
        val updated = dismissed.value + id
        dismissed.value = updated
        saveDismissedIds(updated)
        com.pocketcraft.server.broadcast.RemoteCommandListener.dismissCommandBroadcast(id)
    }

    fun dismissConfigBanner() {
        _configBanner.value = null
    }

    /** Purge stale dismissed IDs that no longer exist in live broadcasts, to prevent unbounded growth. */
    fun purgeStaleDismissed(activeBroadcastIds: Set<String>) {
        val current = dismissed.value
        val pruned = current.intersect(activeBroadcastIds)
        if (pruned != current) {
            dismissed.value = pruned
            saveDismissedIds(pruned)
        }
    }

    private fun loadDismissedIds(): Set<String> {
        val raw = prefs.getString(DISMISSED_KEY, null) ?: return emptySet()
        return raw.split(",").filter { it.isNotBlank() }.toSet()
    }

    private fun saveDismissedIds(ids: Set<String>) {
        prefs.edit().putString(DISMISSED_KEY, ids.joinToString(",")).apply()
    }
}

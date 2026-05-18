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

    val visibleBroadcasts: StateFlow<List<BroadcastMessage>> =
        combine(broadcasts, dismissed) { messages, dismissedIds ->
            messages.filter { it.id !in dismissedIds }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        BroadcastManager.initRemoteConfig { message ->
            // Only show remote config banner if it hasn't been dismissed this session
            // Config banners are session-only (they change content regularly)
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
    }

    fun dismiss(id: String) {
        val updated = dismissed.value + id
        dismissed.value = updated
        saveDismissedIds(updated)
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

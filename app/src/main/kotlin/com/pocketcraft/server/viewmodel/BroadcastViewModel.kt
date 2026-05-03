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

    private val versionCode: Int = runCatching {
        val packageInfo: PackageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        packageInfo.longVersionCode.toInt()
    }.getOrDefault(0)

    val broadcasts: StateFlow<List<BroadcastMessage>> =
        BroadcastManager.getBroadcastsFlow(context, versionCode)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val dismissed = MutableStateFlow<Set<String>>(emptySet())

    private val _configBanner = MutableStateFlow<BroadcastMessage?>(null)
    val configBanner: StateFlow<BroadcastMessage?> = _configBanner

    val visibleBroadcasts: StateFlow<List<BroadcastMessage>> =
        combine(broadcasts, dismissed) { messages, dismissedIds ->
            messages.filter { it.id !in dismissedIds }
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
    }

    fun dismiss(id: String) {
        dismissed.value = dismissed.value + id
    }

    fun dismissConfigBanner() {
        _configBanner.value = null
    }
}

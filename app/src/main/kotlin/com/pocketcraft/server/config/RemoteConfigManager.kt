package com.pocketcraft.server.config

import android.content.Context
import com.pocketcraft.server.BuildConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.pocketcraft.server.data.model.RelayRegion
import com.pocketcraft.server.data.preferences.AppPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

object RemoteConfigManager {
    private val _showDiscordButton = MutableStateFlow(true)
    private val _showInstagramButton = MutableStateFlow(false)
    private val _relayRegions = MutableStateFlow(RelayServers.defaultRegions())

    val showDiscordButton: Flow<Boolean> = _showDiscordButton.asStateFlow()
    val showInstagramButton: Flow<Boolean> = _showInstagramButton.asStateFlow()
    val relayRegions: Flow<List<RelayRegion>> = _relayRegions.asStateFlow()

    private var isInitialized = false

    suspend fun initialize(context: Context) {
        if (isInitialized) return

        try {
            val remoteConfig = FirebaseRemoteConfig.getInstance()
            hydrateRelayRegionsFromCache(context)

            // Set default values
            remoteConfig.setConfigSettingsAsync(
                FirebaseRemoteConfigSettings.Builder()
                    .setMinimumFetchIntervalInSeconds(if (BuildConfig.DEBUG) 0 else 3600)
                    .build()
            ).await()

            val defaultRelayRegionsJson = serializeRelayRegions(RelayServers.defaultRegions())

            // Set in-app defaults
            remoteConfig.setDefaultsAsync(
                mapOf(
                    "show_discord_button" to true,
                    "show_instagram_button" to false,
                    "relay_servers" to defaultRelayRegionsJson
                )
            ).await()

            // Fetch and activate remote config
            remoteConfig.fetchAndActivate().await()

            // Update state
            _showDiscordButton.value = remoteConfig.getBoolean("show_discord_button")
            _showInstagramButton.value = remoteConfig.getBoolean("show_instagram_button")
            updateRelayRegions(remoteConfig.getString("relay_servers"), context)

            isInitialized = true
        } catch (e: Exception) {
            hydrateRelayRegionsFromCache(context)
            isInitialized = true
        }
    }

    suspend fun refreshConfig(context: Context) {
        try {
            val remoteConfig = FirebaseRemoteConfig.getInstance()
            remoteConfig.fetchAndActivate().await()

            _showDiscordButton.value = remoteConfig.getBoolean("show_discord_button")
            _showInstagramButton.value = remoteConfig.getBoolean("show_instagram_button")
            updateRelayRegions(remoteConfig.getString("relay_servers"), context)
        } catch (e: Exception) {
            // Keep existing values on error
        }
    }

    fun getShowDiscordButtonSync(): Boolean {
        return FirebaseRemoteConfig.getInstance().getBoolean("show_discord_button")
    }

    fun getShowInstagramButtonSync(): Boolean {
        return FirebaseRemoteConfig.getInstance().getBoolean("show_instagram_button")
    }

    fun currentRelayRegions(): List<RelayRegion> = _relayRegions.value

    private fun hydrateRelayRegionsFromCache(context: Context) {
        val cachedRaw = AppPreferences(context).cachedRelayServersJson
        updateRelayRegions(cachedRaw ?: serializeRelayRegions(RelayServers.defaultRegions()), context)
    }

    private fun updateRelayRegions(raw: String, context: Context) {
        val parsed = parseRelayRegions(raw).ifEmpty { RelayServers.defaultRegions() }
        RelayServers.updateRegions(parsed)
        _relayRegions.value = RelayServers.currentRegions()
        AppPreferences(context).cachedRelayServersJson = serializeRelayRegions(_relayRegions.value)
    }

    private fun parseRelayRegions(raw: String): List<RelayRegion> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val label = item.optString("label").trim()
                    val host = item.optString("host").trim()
                    if (label.isNotBlank() && host.isNotBlank()) {
                        add(RelayRegion(label = label, host = host))
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun serializeRelayRegions(regions: List<RelayRegion>): String {
        return JSONArray().apply {
            regions.forEach { region ->
                put(
                    JSONObject().apply {
                        put("label", region.label)
                        put("host", region.host)
                    }
                )
            }
        }.toString()
    }
}

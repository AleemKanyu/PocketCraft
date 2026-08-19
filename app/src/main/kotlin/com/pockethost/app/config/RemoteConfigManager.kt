package com.pockethost.app.config

import android.content.Context
import com.pockethost.app.BuildConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.pockethost.app.data.model.RelayRegion
import com.pockethost.app.data.preferences.AppPreferences
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
    private val _customSubdomainEnabled = MutableStateFlow(false)
    private val _premiumPurchaseEnabled = MutableStateFlow(false)
    private val _freeTrialEnabled = MutableStateFlow(true)

    val showDiscordButton: Flow<Boolean> = _showDiscordButton.asStateFlow()
    val showInstagramButton: Flow<Boolean> = _showInstagramButton.asStateFlow()
    val relayRegions: Flow<List<RelayRegion>> = _relayRegions.asStateFlow()
    val customSubdomainEnabled: Flow<Boolean> = _customSubdomainEnabled.asStateFlow()
    val premiumPurchaseEnabled: Flow<Boolean> = _premiumPurchaseEnabled.asStateFlow()
    val freeTrialEnabled: Flow<Boolean> = _freeTrialEnabled.asStateFlow()

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
            remoteConfig.setDefaultsAsync(com.pockethost.app.R.xml.remote_config_defaults).await()
            remoteConfig.setDefaultsAsync(mapOf("relay_servers" to defaultRelayRegionsJson)).await()

            // Fetch and activate remote config
            remoteConfig.fetchAndActivate().await()

            // Update state
            _showDiscordButton.value = remoteConfig.getBoolean("show_discord_button")
            _showInstagramButton.value = remoteConfig.getBoolean("show_instagram_button")
            _customSubdomainEnabled.value = remoteConfig.getBoolean("feature_custom_subdomain_enabled")
            _premiumPurchaseEnabled.value = remoteConfig.getBoolean("feature_premium_purchase_enabled")
            _freeTrialEnabled.value = remoteConfig.getBoolean("feature_free_trial_enabled")
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
            _customSubdomainEnabled.value = remoteConfig.getBoolean("feature_custom_subdomain_enabled")
            _premiumPurchaseEnabled.value = remoteConfig.getBoolean("feature_premium_purchase_enabled")
            _freeTrialEnabled.value = remoteConfig.getBoolean("feature_free_trial_enabled")
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

    fun isCustomSubdomainEnabledSync(): Boolean {
        return FirebaseRemoteConfig.getInstance().getBoolean("feature_custom_subdomain_enabled")
    }

    fun isPremiumPurchaseEnabledSync(): Boolean {
        return FirebaseRemoteConfig.getInstance().getBoolean("feature_premium_purchase_enabled")
    }

    fun isFreeTrialEnabledSync(): Boolean {
        return runCatching { FirebaseRemoteConfig.getInstance().getBoolean("feature_free_trial_enabled") }.getOrDefault(true)
    }

    private fun hydrateRelayRegionsFromCache(context: Context) {
        val cachedRaw = AppPreferences(context).cachedRelayServersJson
        updateRelayRegions(cachedRaw ?: serializeRelayRegions(RelayServers.defaultRegions()), context)
    }

    private fun updateRelayRegions(raw: String, context: Context) {
        val parsed = parseRelayRegions(raw).ifEmpty { RelayServers.defaultRegions() }
        val disabledSet = FeatureGate.getDisabledRegions()
        val filtered = if (disabledSet.isEmpty()) {
            parsed
        } else {
            parsed.filter { region ->
                val host = region.host.lowercase()
                val label = region.label.lowercase()
                val hostPrefix = host.substringBefore('.')
                disabledSet.none { code ->
                    val c = code.trim()
                    c.isNotBlank() && (
                        hostPrefix == c ||
                        host.contains(c) ||
                        label.contains(c)
                    )
                }
            }
        }
        RelayServers.updateRegions(filtered)
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

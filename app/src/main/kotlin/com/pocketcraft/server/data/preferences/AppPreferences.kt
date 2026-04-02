package com.pocketcraft.server.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pocketcraft.server.config.RelayServers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_prefs")

object AppPreferencesKeys {
    val SETUP_COMPLETE = booleanPreferencesKey("setup_complete")
    val SELECTED_VERSION = stringPreferencesKey("selected_version")
    val WORLD_SEED = stringPreferencesKey("world_seed")
    val SEED_SETUP_SHOWN = booleanPreferencesKey("seed_setup_shown")
    val DARK_MODE_OVERRIDE = stringPreferencesKey("dark_mode_override") // "SYSTEM", "LIGHT", "DARK"
    val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
    val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
    val AUTO_RESTART = booleanPreferencesKey("auto_restart")
    val AUTO_RESTART_DELAY_SECONDS = stringPreferencesKey("auto_restart_delay_seconds")
    val RELAY_HOST = stringPreferencesKey("relay_host")
    val OPEN_SERVER_RISK_ACKNOWLEDGED = booleanPreferencesKey("open_server_risk_acknowledged")
}

class AppPreferences(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("app_relay_prefs", Context.MODE_PRIVATE)

    val userId: String
        get() {
            var id = prefs.getString("user_id", null)
            if (id == null) {
                id = java.util.UUID.randomUUID().toString()
                prefs.edit().putString("user_id", id).apply()
            }
            return id
        }

    var ramMode: String
        get() = prefs.getString("ram_mode", "full") ?: "full"
        set(value) = prefs.edit().putString("ram_mode", value).apply()

    var manualRamMb: Int
        get() = prefs.getInt("manual_ram_mb", 1024)
        set(value) = prefs.edit().putInt("manual_ram_mb", value).apply()

    var selectedWorldPath: String?
        get() = prefs.getString("selected_world_path", null)
        set(value) = prefs.edit().putString("selected_world_path", value).apply()

    var relayHost: String
        get() = prefs.getString("relay_host", null)
            ?: RelayServers.getBestForTimeZone(java.util.TimeZone.getDefault().id).host
        set(value) = prefs.edit().putString("relay_host", value).apply()
}

// Keep object-based API for backward compatibility with existing code
object AppPreferencesStore {

    fun isSetupCompleteFlow(context: Context): Flow<Boolean> =
        context.appPreferencesDataStore.data.map { prefs ->
            prefs[AppPreferencesKeys.SETUP_COMPLETE] ?: false
        }

    suspend fun setSetupComplete(context: Context, complete: Boolean) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[AppPreferencesKeys.SETUP_COMPLETE] = complete
        }
    }

    fun getDarkModeOverrideFlow(context: Context): Flow<String?> =
        context.appPreferencesDataStore.data.map { prefs ->
            prefs[AppPreferencesKeys.DARK_MODE_OVERRIDE]
        }

    suspend fun setDarkModeOverride(context: Context, mode: String?) {
        context.appPreferencesDataStore.edit { prefs ->
            if (mode == null) {
                prefs.remove(AppPreferencesKeys.DARK_MODE_OVERRIDE)
            } else {
                prefs[AppPreferencesKeys.DARK_MODE_OVERRIDE] = mode
            }
        }
    }

    fun isSoundEnabledFlow(context: Context): Flow<Boolean> =
        context.appPreferencesDataStore.data.map { prefs ->
            prefs[AppPreferencesKeys.SOUND_ENABLED] ?: true // Default enabled
        }

    suspend fun setSoundEnabled(context: Context, enabled: Boolean) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[AppPreferencesKeys.SOUND_ENABLED] = enabled
        }
    }

    fun isNotificationsEnabledFlow(context: Context): Flow<Boolean> =
        context.appPreferencesDataStore.data.map { prefs ->
            prefs[AppPreferencesKeys.NOTIFICATIONS_ENABLED] ?: true // Default enabled
        }

    suspend fun setNotificationsEnabled(context: Context, enabled: Boolean) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[AppPreferencesKeys.NOTIFICATIONS_ENABLED] = enabled
        }
    }

    fun isAutoRestartFlow(context: Context): Flow<Boolean> =
        context.appPreferencesDataStore.data.map { prefs ->
            prefs[AppPreferencesKeys.AUTO_RESTART] ?: false
        }

    suspend fun setAutoRestart(context: Context, enabled: Boolean) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[AppPreferencesKeys.AUTO_RESTART] = enabled
        }
    }

    fun getAutoRestartDelayFlow(context: Context): Flow<Int> =
        context.appPreferencesDataStore.data.map { prefs ->
            prefs[AppPreferencesKeys.AUTO_RESTART_DELAY_SECONDS]?.toIntOrNull() ?: 10
        }

    suspend fun setAutoRestartDelay(context: Context, delaySeconds: Int) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[AppPreferencesKeys.AUTO_RESTART_DELAY_SECONDS] = delaySeconds.toString()
        }
    }

    fun getSelectedVersionFlow(context: Context): Flow<String> =
        context.appPreferencesDataStore.data.map { prefs ->
            prefs[AppPreferencesKeys.SELECTED_VERSION] ?: "1.21.1"
        }

    suspend fun setSelectedVersion(context: Context, version: String) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[AppPreferencesKeys.SELECTED_VERSION] = version
        }
    }

    fun getWorldSeedFlow(context: Context): Flow<String> =
        context.appPreferencesDataStore.data.map { prefs ->
            prefs[AppPreferencesKeys.WORLD_SEED] ?: ""
        }

    suspend fun setWorldSeed(context: Context, seed: String) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[AppPreferencesKeys.WORLD_SEED] = seed
        }
    }

    fun isSeedSetupShownFlow(context: Context): Flow<Boolean> =
        context.appPreferencesDataStore.data.map { prefs ->
            prefs[AppPreferencesKeys.SEED_SETUP_SHOWN] ?: false
        }

    suspend fun setSeedSetupShown(context: Context, shown: Boolean) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[AppPreferencesKeys.SEED_SETUP_SHOWN] = shown
        }
    }

    fun getRelayHostFlow(context: Context): Flow<String> =
        context.appPreferencesDataStore.data.map { prefs ->
            prefs[AppPreferencesKeys.RELAY_HOST] ?: "play.pocketcraft.online"
        }

    suspend fun setRelayHost(context: Context, host: String) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[AppPreferencesKeys.RELAY_HOST] = host
        }
    }

    fun isOpenServerRiskAcknowledgedFlow(context: Context): Flow<Boolean> =
        context.appPreferencesDataStore.data.map { prefs ->
            prefs[AppPreferencesKeys.OPEN_SERVER_RISK_ACKNOWLEDGED] ?: false
        }

    suspend fun setOpenServerRiskAcknowledged(context: Context, acknowledged: Boolean) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[AppPreferencesKeys.OPEN_SERVER_RISK_ACKNOWLEDGED] = acknowledged
        }
    }
}

package com.pocketcraft.server.data.preferences

import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pocketcraft.server.config.RelayServers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.appPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_prefs")
val RELAY_SECRET = com.pocketcraft.server.BuildConfig.RELAY_SECRET
const val KEY_MAX_POWER_MODE = "max_power_mode"

object AppPreferencesKeys {
    val SETUP_COMPLETE = booleanPreferencesKey("setup_complete")
    val SELECTED_VERSION = stringPreferencesKey("selected_version")
    val SELECTED_SERVER_TYPE = stringPreferencesKey("selected_server_type")
    val SELECTED_WORLD = stringPreferencesKey("selected_world")
    val WORLD_SEED = stringPreferencesKey("world_seed")
    val SEED_SETUP_SHOWN = booleanPreferencesKey("seed_setup_shown")
    val DARK_MODE_OVERRIDE = stringPreferencesKey("dark_mode_override") // "SYSTEM", "LIGHT", "DARK"
    val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
    val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
    val AUTO_RESTART = booleanPreferencesKey("auto_restart")
    val AUTO_RESTART_DELAY_SECONDS = stringPreferencesKey("auto_restart_delay_seconds")
    val RELAY_HOST = stringPreferencesKey("selected_relay_host")
    val LEGACY_RELAY_HOST = stringPreferencesKey("relay_host")
    val RELAY_HOST_REGION_MIGRATED = booleanPreferencesKey("relay_host_region_migrated")
    val CACHED_RELAY_SERVERS_JSON = stringPreferencesKey("cached_relay_servers_json")
    val RELAY_HOST_USER_OVERRIDDEN = booleanPreferencesKey("relay_host_user_overridden")
    val RELAY_AUTO_SELECTED_ONCE = booleanPreferencesKey("relay_auto_selected_once")
    val OPEN_SERVER_RISK_ACKNOWLEDGED = booleanPreferencesKey("open_server_risk_acknowledged")
    val INITIAL_WORLD_SETUP_SHOWN = booleanPreferencesKey("initial_world_setup_shown")
    val PENDING_AUTO_DOWNLOAD_VERSION = stringPreferencesKey("pending_auto_download_version")
    val SOCIAL_PROMO_SHOWN = booleanPreferencesKey("social_promo_shown")
    val SOCIAL_LINKS_JOINED = booleanPreferencesKey("social_links_joined")
    val SOCIAL_PROMO_LAST_SHOWN_LAUNCH = intPreferencesKey("social_promo_last_shown_launch")
    val APP_LAUNCH_COUNT = intPreferencesKey("app_launch_count")
    val ANALYTICS_CONSENT = booleanPreferencesKey("analytics_consent")
    val CRASH_DIAGNOSTICS_CONSENT = booleanPreferencesKey("crash_diagnostics_consent")
    val LEGAL_VERSION_ACCEPTED = stringPreferencesKey("legal_version_accepted")
    val DISCORD_POPUP_SHOWN_ON_FIRST_LAUNCH = booleanPreferencesKey("discord_popup_shown_on_first_launch")
    val INSTAGRAM_POPUP_SHOWN = booleanPreferencesKey("instagram_popup_shown")
    val SHOW_RC_VERSIONS = booleanPreferencesKey("show_rc_versions")
    val FAST_START_ENABLED = booleanPreferencesKey("fast_start_enabled")
    val FIRST_SERVER_START_WARNING_DISMISSED = booleanPreferencesKey("first_server_start_warning_dismissed")
    val FLIGHT_MODE_ENABLED = booleanPreferencesKey("flight_mode_enabled")
    val FIRST_BOOT_COMPLETE = booleanPreferencesKey("first_boot_complete")
    val NAV_INDICATOR_SHAPE = stringPreferencesKey("nav_indicator_shape")
    val SERVER_TYPE_SETUP_GUIDE_SHOWN = booleanPreferencesKey("server_type_setup_guide_shown")
    val FLOATING_CHAT_ENABLED = booleanPreferencesKey("floating_chat_enabled")
    val FLOATING_CHAT_FIRST_TIME_SHOWN = booleanPreferencesKey("floating_chat_first_time_shown")
    val WIDGET_THEME = stringPreferencesKey("widget_theme")
    val WIDGET_THEME_MANUAL_OVERRIDE = booleanPreferencesKey("widget_theme_manual_override")
    val WIDGET_CUSTOM_BACKGROUND = intPreferencesKey("widget_custom_background")
    val WIDGET_CUSTOM_ACCENT = intPreferencesKey("widget_custom_accent")
    val WIDGET_CUSTOM_TEXT_ON_ACCENT = intPreferencesKey("widget_custom_text_on_accent")
    val WIDGET_CUSTOM_ICON_TINT = intPreferencesKey("widget_custom_icon_tint")
    val SERVER_STARTED_AT_MILLIS = longPreferencesKey("server_started_at_millis")
    val ALWAYS_ALIVE_BACKGROUND = booleanPreferencesKey("always_alive_background")
}

data class WidgetThemeSettings(
    val selectedTheme: String = "follow_app_theme",
    val manualOverride: Boolean = false,
    val customBackground: Int? = null,
    val customAccent: Int? = null,
    val customTextOnAccent: Int? = null,
    val customIconTint: Int? = null
)

class AppPreferences(context: Context) {
    private val appContext = context.applicationContext

    private val prefs: SharedPreferences
        get() = getPrefs(appContext)

    private val relayPrefs: SharedPreferences
        get() = getRelayPrefs(appContext)

    companion object {
        @Volatile
        private var prefsInstance: SharedPreferences? = null
        @Volatile
        private var relayPrefsInstance: SharedPreferences? = null

        fun getDefaultRamMb(context: Context): Int {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memInfo)
            val totalRamMb = (memInfo.totalMem / 1024 / 1024).toInt()
            return (totalRamMb * 0.50).toInt().coerceIn(512, 4096)
        }

        @Synchronized
        fun init(context: Context) {
            val appContext = context.applicationContext
            if (prefsInstance == null) {
                prefsInstance = appContext.getSharedPreferences("app_relay_prefs", Context.MODE_PRIVATE)
            }
            if (relayPrefsInstance == null) {
                relayPrefsInstance = appContext.getSharedPreferences("app_relay_session_prefs", Context.MODE_PRIVATE)
            }
        }

        private fun getPrefs(context: Context): SharedPreferences {
            if (prefsInstance == null || relayPrefsInstance == null) {
                init(context)
            }
            return prefsInstance!!
        }

        private fun getRelayPrefs(context: Context): SharedPreferences {
            if (prefsInstance == null || relayPrefsInstance == null) {
                init(context)
            }
            return relayPrefsInstance!!
        }
    }

    val userId: String
        get() {
            val androidId = android.provider.Settings.Secure.getString(
                appContext.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            )
            if (!androidId.isNullOrBlank() && androidId != "9774d56d682e549c") {
                return androidId
            }
            var id = prefs.getString("user_id", null)
            if (id == null) {
                id = java.util.UUID.randomUUID().toString().replace("-", "")
                prefs.edit().putString("user_id", id).commit()
            }
            return id
        }

    var ramMode: String
        get() {
            val stored = prefs.getString("ram_mode", null)
            if (stored.isNullOrBlank()) {
                prefs.edit().putString("ram_mode", "manual").apply()
                return "manual"
            }
            return stored
        }
        set(value) = prefs.edit().putString("ram_mode", value).apply()

    var manualRamMb: Int
        get() {
            if (!prefs.contains("manual_ram_mb")) {
                val defaultMb = getDefaultRamMb(appContext)
                prefs.edit().putInt("manual_ram_mb", defaultMb).apply()
                return defaultMb
            }
            return prefs.getInt("manual_ram_mb", 1024)
        }
        set(value) = prefs.edit().putInt("manual_ram_mb", value).apply()

    var isMaxPowerMode: Boolean
        get() = prefs.getBoolean(KEY_MAX_POWER_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_MAX_POWER_MODE, value).apply()

    var autoRestart: Boolean
        get() = prefs.getBoolean("auto_restart", false)
        set(value) = prefs.edit().putBoolean("auto_restart", value).apply()

    var isUserStopped: Boolean
        get() = prefs.getBoolean("is_user_stopped", false)
        set(value) { prefs.edit().putBoolean("is_user_stopped", value).commit() }

    var forceExternalJvm: Boolean
        get() = prefs.getBoolean("force_external_jvm", android.os.Build.VERSION.SDK_INT >= 34)
        set(value) = prefs.edit().putBoolean("force_external_jvm", value).apply()

    fun saveRuntimeSettings(
        autoRestart: Boolean,
        maxPowerMode: Boolean,
        forceExternalJvm: Boolean
    ): Boolean {
        return prefs.edit()
            .putBoolean("auto_restart", autoRestart)
            .putBoolean(KEY_MAX_POWER_MODE, maxPowerMode)
            .putBoolean("force_external_jvm", forceExternalJvm)
            .commit()
    }

    var selectedWorldPath: String?
        get() = prefs.getString("selected_world_path", null)
        set(value) = prefs.edit().putString("selected_world_path", value).apply()

    var selectedWorld: String
        get() = prefs.getString("selected_world", "world")?.ifBlank { "world" } ?: "world"
        set(value) = prefs.edit().putString("selected_world", value).apply()

    var fcmToken: String?
        get() = prefs.getString("fcm_token", null)
        set(value) = prefs.edit().putString("fcm_token", value).apply()

    var relayPort: Int?
        get() {
            if (!relayPrefs.contains("relay_port")) return null
            return relayPrefs.getInt("relay_port", -1).takeIf { it > 0 }
        }
        set(value) {
            relayPrefs.edit().apply {
                if (value == null || value <= 0) {
                    remove("relay_port")
                } else {
                    putInt("relay_port", value)
                }
            }.apply()
        }

    var bedrockRelayRegion: String
        get() = prefs.getString("bedrock_relay_region", "MUMBAI").orEmpty()
        set(value) = prefs.edit().putString("bedrock_relay_region", value).apply()

    var relayHost: String
        get() = prefs.getString("selected_relay_host", null)
            ?: prefs.getString("relay_host", null)
            ?: RelayServers.getBestForTimeZone(java.util.TimeZone.getDefault().id).host
        set(value) = prefs.edit()
            .putString("selected_relay_host", value)
            .putString("relay_host", value)
            .apply()

    var cachedRelayServersJson: String?
        get() = prefs.getString("cached_relay_servers_json", null)
        set(value) = prefs.edit().apply {
            if (value.isNullOrBlank()) {
                remove("cached_relay_servers_json")
            } else {
                putString("cached_relay_servers_json", value)
            }
        }.apply()

    var successfulServerStarts: Int
        get() = prefs.getInt("successful_server_starts", 0)
        set(value) = prefs.edit().putInt("successful_server_starts", value).apply()

    var reviewRequestedForVersion: String
        get() = prefs.getString("review_requested_for_version", "").orEmpty()
        set(value) = prefs.edit().putString("review_requested_for_version", value).apply()

    var lastProcessedCommandId: String
        get() = prefs.getString("last_processed_command_id", "").orEmpty()
        set(value) = prefs.edit().putString("last_processed_command_id", value).apply()

    var relayHostUserOverridden: Boolean
        get() = prefs.getBoolean("relay_host_user_overridden", false)
        set(value) = prefs.edit().putBoolean("relay_host_user_overridden", value).apply()

    var relayAutoSelectedOnce: Boolean
        get() = prefs.getBoolean("relay_auto_selected_once", false)
        set(value) = prefs.edit().putBoolean("relay_auto_selected_once", value).apply()

    fun setManualRelayHost(host: String) {
        relayHost = host
        relayHostUserOverridden = true
        relayAutoSelectedOnce = true
        updateBedrockRelayRegionForHost(host)
    }

    fun setAutoSelectedRelayHost(host: String) {
        relayHost = host
        relayHostUserOverridden = false
        relayAutoSelectedOnce = true
        updateBedrockRelayRegionForHost(host)
    }

    fun updateBedrockRelayRegionForHost(host: String) {
        bedrockRelayRegion = RelayServers.resolveBedrockRegion(host)
    }

    fun migrateLegacyRelayHostForRegion() {
        if (prefs.getBoolean("relay_host_region_migrated", false)) return

        val bestHost = RelayServers.getBestForTimeZone(java.util.TimeZone.getDefault().id).host
        val currentHost = prefs.getString("selected_relay_host", null)
            ?: prefs.getString("relay_host", null)
        prefs.edit().apply {
            if (bestHost != "play.pocketcraft.online" &&
                (currentHost == null || currentHost == "play.pocketcraft.online")
            ) {
                putString("selected_relay_host", bestHost)
                putString("relay_host", bestHost)
            } else if (!currentHost.isNullOrBlank()) {
                putString("selected_relay_host", currentHost)
            }
            putBoolean("relay_host_region_migrated", true)
        }.apply()
    }

    fun migrateSingaporeRelayRemoval() {
        if (prefs.getBoolean("singapore_removal_migrated", false)) {
            val currentHost = prefs.getString("selected_relay_host", null)
                ?: prefs.getString("relay_host", null)
            if (currentHost != "play.pocketcraft.online") {
                return
            }
        }

        val bestHost = RelayServers.getBestForTimeZone(java.util.TimeZone.getDefault().id).host
        val currentHost = prefs.getString("selected_relay_host", null)
            ?: prefs.getString("relay_host", null)
        if (currentHost == "play.pocketcraft.online") {
            prefs.edit().apply {
                putString("selected_relay_host", bestHost)
                putString("relay_host", bestHost)
                if (prefs.getString("bedrock_relay_region", "") == "SINGAPORE") {
                    putString("bedrock_relay_region", RelayServers.resolveBedrockRegion(bestHost))
                }
            }.apply()
        }
        prefs.edit().putBoolean("singapore_removal_migrated", true).apply()
    }

    var onboardingCompleted: Boolean
        get() = prefs.getBoolean("onboarding_completed", false)
        set(value) = prefs.edit().putBoolean("onboarding_completed", value).apply()

    var eulaAccepted: Boolean
        get() = prefs.getBoolean("eula_accepted", false)
        set(value) = prefs.edit().putBoolean("eula_accepted", value).apply()

    var openWorldSetupNextLaunch: Boolean
        get() = prefs.getBoolean("open_world_setup_next_launch", false)
        set(value) = prefs.edit().putBoolean("open_world_setup_next_launch", value).apply()

    var appLaunchCount: Int
        get() = prefs.getInt("app_launch_count", 0)
        set(value) = prefs.edit().putInt("app_launch_count", value).apply()

    var ratingPopupLastShownAt: Long
        get() = prefs.getLong("rating_popup_last_shown_at", 0L)
        set(value) = prefs.edit().putLong("rating_popup_last_shown_at", value).apply()

    var ratingPopupShowCount: Int
        get() = prefs.getInt("rating_popup_show_count", 0)
        set(value) = prefs.edit().putInt("rating_popup_show_count", value).apply()

    var ratingPopupDismissedForever: Boolean
        get() = prefs.getBoolean("rating_popup_dismissed_forever", false)
        set(value) = prefs.edit().putBoolean("rating_popup_dismissed_forever", value).apply()

    var socialPromoShown: Boolean
        get() = prefs.getBoolean("social_promo_shown", false)
        set(value) = prefs.edit().putBoolean("social_promo_shown", value).apply()

    var socialLinksJoined: Boolean
        get() = prefs.getBoolean("social_links_joined", false)
        set(value) = prefs.edit().putBoolean("social_links_joined", value).apply()

    var socialPromoLastShownLaunch: Int
        get() = prefs.getInt("social_promo_last_shown_launch", 0)
        set(value) = prefs.edit().putInt("social_promo_last_shown_launch", value).apply()

    var discordPopupShownOnFirstLaunch: Boolean
        get() = prefs.getBoolean("discord_popup_shown_on_first_launch", false)
        set(value) = prefs.edit().putBoolean("discord_popup_shown_on_first_launch", value).apply()

    var instagramPopupShown: Boolean
        get() = prefs.getBoolean("instagram_popup_shown", false)
        set(value) = prefs.edit().putBoolean("instagram_popup_shown", value).apply()

    var lastSeenChangelogVersion: String
        get() = prefs.getString("last_seen_changelog_version", "").orEmpty()
        set(value) = prefs.edit().putString("last_seen_changelog_version", value).apply()

    var lastLaunchedAppVersion: String
        get() = prefs.getString("last_launched_app_version", "").orEmpty()
        set(value) = prefs.edit().putString("last_launched_app_version", value).apply()

    var appLanguage: String
        get() = prefs.getString("app_language", "system").orEmpty()
        set(value) = prefs.edit().putString("app_language", value).apply()


    var pendingFeedbackPromptTitle: String
        get() = prefs.getString("pending_feedback_prompt_title", "").orEmpty()
        set(value) = prefs.edit().putString("pending_feedback_prompt_title", value).apply()

    var pendingFeedbackPromptBody: String
        get() = prefs.getString("pending_feedback_prompt_body", "").orEmpty()
        set(value) = prefs.edit().putString("pending_feedback_prompt_body", value).apply()

    var pendingFeedbackPromptCta: String
        get() = prefs.getString("pending_feedback_prompt_cta", "").orEmpty()
        set(value) = prefs.edit().putString("pending_feedback_prompt_cta", value).apply()

    fun clearPendingFeedbackPrompt() {
        prefs.edit()
            .remove("pending_feedback_prompt_title")
            .remove("pending_feedback_prompt_body")
            .remove("pending_feedback_prompt_cta")
            .apply()
    }

    fun clearUserId() {
        prefs.edit().remove("user_id").apply()
    }

    fun clearFcmToken() {
        prefs.edit().remove("fcm_token").apply()
    }

    fun clearRelayPort() {
        relayPrefs.edit().remove("relay_port").apply()
    }

    fun clearAccountIdentityData() {
        clearUserId()
        clearFcmToken()
        clearRelayPort()
    }

    fun recordAppLaunch(): Int {
        val next = appLaunchCount + 1
        appLaunchCount = next
        return next
    }

    fun getLastUpdateCheckTime(): Long {
        return prefs.getLong("last_update_check_time", 0)
    }

    fun setLastUpdateCheckTime(timeMs: Long) {
        prefs.edit().putLong("last_update_check_time", timeMs).apply()
    }

    fun isFirstLaunchAfterOnboarding(): Boolean {
        return prefs.getBoolean("first_launch_after_onboarding", true)
    }

    fun setFirstLaunchAfterOnboarding(isFirst: Boolean) {
        prefs.edit().putBoolean("first_launch_after_onboarding", isFirst).apply()
    }

    var batteryOptimizationRequested: Boolean
        get() = prefs.getBoolean("battery_optimization_requested", false)
        set(value) = prefs.edit().putBoolean("battery_optimization_requested", value).apply()

    var isFloatingChatEnabled: Boolean
        get() = prefs.getBoolean("floating_chat_enabled", false)
        set(value) = prefs.edit().putBoolean("floating_chat_enabled", value).apply()

    var isFloatingChatFirstTimeShown: Boolean
        get() = prefs.getBoolean("floating_chat_first_time_shown", false)
        set(value) = prefs.edit().putBoolean("floating_chat_first_time_shown", value).apply()

    var consecutiveCrashCount: Int
        get() = prefs.getInt("consecutive_crash_count", 0)
        set(value) = prefs.edit().putInt("consecutive_crash_count", value).apply()

    var lastStartTimestamp: Long
        get() = prefs.getLong("last_start_timestamp", 0L)
        set(value) = prefs.edit().putLong("last_start_timestamp", value).apply()

    var isPremiumUser: Boolean
        get() = prefs.getBoolean("is_premium_user", false)
        set(value) = prefs.edit().putBoolean("is_premium_user", value).apply()

    var lastSeenMembershipTier: String
        get() = prefs.getString("last_seen_membership_tier", "none").orEmpty()
        set(value) = prefs.edit().putString("last_seen_membership_tier", value).apply()

    var freeToProUpsellTrackingStarted: Boolean
        get() = prefs.getBoolean("free_to_pro_upsell_tracking_started", false)
        set(value) = prefs.edit().putBoolean("free_to_pro_upsell_tracking_started", value).apply()

    var freeToProUpsellStartLaunchCount: Int
        get() = prefs.getInt("free_to_pro_upsell_start_launch_count", 0)
        set(value) = prefs.edit().putInt("free_to_pro_upsell_start_launch_count", value).apply()

    var freeToProUpsellLastShownLaunchCount: Int
        get() = prefs.getInt("free_to_pro_upsell_last_shown_launch_count", 0)
        set(value) = prefs.edit().putInt("free_to_pro_upsell_last_shown_launch_count", value).apply()

    fun resetFreeToProUpsellTracking() {
        prefs.edit()
            .putBoolean("free_to_pro_upsell_tracking_started", false)
            .putInt("free_to_pro_upsell_start_launch_count", 0)
            .putInt("free_to_pro_upsell_last_shown_launch_count", 0)
            .apply()
    }

    var proToMemberUpsellTrackingStarted: Boolean
        get() = prefs.getBoolean("pro_to_member_upsell_tracking_started", false)
        set(value) = prefs.edit().putBoolean("pro_to_member_upsell_tracking_started", value).apply()

    var proToMemberUpsellStartLaunchCount: Int
        get() = prefs.getInt("pro_to_member_upsell_start_launch_count", 0)
        set(value) = prefs.edit().putInt("pro_to_member_upsell_start_launch_count", value).apply()

    var proToMemberUpsellLastShownLaunchCount: Int
        get() = prefs.getInt("pro_to_member_upsell_last_shown_launch_count", 0)
        set(value) = prefs.edit().putInt("pro_to_member_upsell_last_shown_launch_count", value).apply()

    var proToMemberUpsellShown: Boolean
        get() = prefs.getBoolean("pro_to_member_upsell_shown", false)
        set(value) = prefs.edit().putBoolean("pro_to_member_upsell_shown", value).apply()

    fun resetProToMemberUpsellTracking(lastSeenTier: String = "none") {
        prefs.edit()
            .putString("last_seen_membership_tier", lastSeenTier)
            .putBoolean("pro_to_member_upsell_tracking_started", false)
            .putInt("pro_to_member_upsell_start_launch_count", 0)
            .putInt("pro_to_member_upsell_last_shown_launch_count", 0)
            .putBoolean("pro_to_member_upsell_shown", false)
            .apply()
    }

    var debugPremiumOverride: Boolean
        get() = prefs.getBoolean("debug_premium_override", false)
        set(value) = prefs.edit().putBoolean("debug_premium_override", value).apply()

    var firebaseUserUid: String?
        get() = prefs.getString("firebase_user_uid", null)
        set(value) = prefs.edit().putString("firebase_user_uid", value).apply()

    var dashboardSecret: String?
        get() = prefs.getString("dashboard_secret", null)
        set(value) = prefs.edit().putString("dashboard_secret", value).apply()

    var customSubdomain: String?
        get() = prefs.getString("custom_subdomain", null)
        set(value) = prefs.edit().putString("custom_subdomain", value).apply()

    var customSubdomainRegion: String?
        get() = prefs.getString("custom_subdomain_region", null)
        set(value) = prefs.edit().putString("custom_subdomain_region", value).apply()

    var hasStartedServer: Boolean
        get() = prefs.getBoolean("has_started_server", false)
        set(value) = prefs.edit().putBoolean("has_started_server", value).apply()

    var hasCompletedStartStopCycle: Boolean
        get() = prefs.getBoolean("has_completed_start_stop_cycle", false)
        set(value) = prefs.edit().putBoolean("has_completed_start_stop_cycle", value).apply()

    var alwaysAliveBackground: Boolean
        get() = prefs.getBoolean("always_alive_background", false)
        set(value) = prefs.edit().putBoolean("always_alive_background", value).apply()

    /**
     * Set to true when the server stops after a real player has joined that session.
     * Cleared once the rating popup has been shown on the next app launch.
     */
    var pendingRatingPopup: Boolean
        get() = prefs.getBoolean("pending_rating_popup", false)
        set(value) = prefs.edit().putBoolean("pending_rating_popup", value).apply()

    var autoBackupOnStop: Boolean
        get() = prefs.getBoolean("auto_backup_on_stop", false)
        set(value) = prefs.edit().putBoolean("auto_backup_on_stop", value).apply()

    var pendingAutoBackup: Boolean
        get() = prefs.getBoolean("pending_auto_backup", false)
        set(value) = prefs.edit().putBoolean("pending_auto_backup", value).apply()

    var autoBackupTimeEnabled: Boolean
        get() = prefs.getBoolean("auto_backup_time_enabled", false)
        set(value) = prefs.edit().putBoolean("auto_backup_time_enabled", value).apply()

    var autoBackupTimeHour: Int
        get() = prefs.getInt("auto_backup_time_hour", 2)
        set(value) = prefs.edit().putInt("auto_backup_time_hour", value).apply()

    var autoBackupTimeMinute: Int
        get() = prefs.getInt("auto_backup_time_minute", 0)
        set(value) = prefs.edit().putInt("auto_backup_time_minute", value).apply()

    fun getShownPromotions(): Set<String> {
        return prefs.getStringSet("shown_promotions", emptySet()) ?: emptySet()
    }

    fun markPromotionShown(promoId: String) {
        val current = getShownPromotions().toMutableSet()
        current.add(promoId)
        prefs.edit().putStringSet("shown_promotions", current).apply()
    }
}


// Keep object-based API for backward compatibility with existing code
object AppPreferencesStore {

    fun getWidgetThemeSettingsFlow(context: Context): Flow<WidgetThemeSettings> =
        context.appPreferencesDataStore.data.map { prefs ->
            WidgetThemeSettings(
                selectedTheme = prefs[AppPreferencesKeys.WIDGET_THEME] ?: "follow_app_theme",
                manualOverride = prefs[AppPreferencesKeys.WIDGET_THEME_MANUAL_OVERRIDE] ?: false,
                customBackground = prefs[AppPreferencesKeys.WIDGET_CUSTOM_BACKGROUND],
                customAccent = prefs[AppPreferencesKeys.WIDGET_CUSTOM_ACCENT],
                customTextOnAccent = prefs[AppPreferencesKeys.WIDGET_CUSTOM_TEXT_ON_ACCENT],
                customIconTint = prefs[AppPreferencesKeys.WIDGET_CUSTOM_ICON_TINT]
            )
        }

    suspend fun getWidgetThemeSettings(context: Context): WidgetThemeSettings =
        getWidgetThemeSettingsFlow(context).first()

    suspend fun setWidgetThemeSelection(
        context: Context,
        themeKey: String,
        manualOverride: Boolean
    ) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[AppPreferencesKeys.WIDGET_THEME] = themeKey
            prefs[AppPreferencesKeys.WIDGET_THEME_MANUAL_OVERRIDE] = manualOverride
        }
    }

    suspend fun setWidgetCustomColors(
        context: Context,
        background: Int,
        accent: Int,
        textOnAccent: Int,
        iconTint: Int
    ) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[AppPreferencesKeys.WIDGET_CUSTOM_BACKGROUND] = background
            prefs[AppPreferencesKeys.WIDGET_CUSTOM_ACCENT] = accent
            prefs[AppPreferencesKeys.WIDGET_CUSTOM_TEXT_ON_ACCENT] = textOnAccent
            prefs[AppPreferencesKeys.WIDGET_CUSTOM_ICON_TINT] = iconTint
        }
    }

    fun getServerStartedAtMillisFlow(context: Context): Flow<Long> =
        context.appPreferencesDataStore.data.map { prefs ->
            prefs[AppPreferencesKeys.SERVER_STARTED_AT_MILLIS] ?: 0L
        }

    suspend fun getServerStartedAtMillis(context: Context): Long =
        getServerStartedAtMillisFlow(context).first()

    suspend fun setServerStartedAtMillis(context: Context, startedAtMillis: Long) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[AppPreferencesKeys.SERVER_STARTED_AT_MILLIS] = startedAtMillis
        }
    }

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

    fun getSelectedVersionFlow(context: Context): Flow<String?> =
        context.appPreferencesDataStore.data.map { prefs ->
            prefs[AppPreferencesKeys.SELECTED_VERSION]
        }

    fun getServerVersionFlow(context: Context): Flow<String?> =
        getSelectedVersionFlow(context)

    fun getStoredSelectedVersionFlow(context: Context): Flow<String?> =
        context.appPreferencesDataStore.data.map { prefs ->
            prefs[AppPreferencesKeys.SELECTED_VERSION]
        }

    suspend fun setSelectedVersion(context: Context, version: String) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[AppPreferencesKeys.SELECTED_VERSION] = version
        }
    }

    suspend fun setServerVersion(context: Context, version: String) {
        setSelectedVersion(context, version)
    }

    fun getSelectedServerTypeFlow(context: Context): Flow<String> =
        context.appPreferencesDataStore.data.map { prefs ->
            prefs[AppPreferencesKeys.SELECTED_SERVER_TYPE] ?: "PAPER"
        }

    suspend fun setSelectedServerType(context: Context, serverType: String) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[AppPreferencesKeys.SELECTED_SERVER_TYPE] = serverType
        }
    }

    fun getSelectedWorldFlow(context: Context): Flow<String> =
        context.appPreferencesDataStore.data.map { prefs ->
            prefs[AppPreferencesKeys.SELECTED_WORLD] ?: "world"
        }

    suspend fun setSelectedWorld(context: Context, worldName: String) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[AppPreferencesKeys.SELECTED_WORLD] = worldName
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
            prefs[AppPreferencesKeys.RELAY_HOST]
                ?: prefs[AppPreferencesKeys.LEGACY_RELAY_HOST]
                ?: RelayServers.getBestForTimeZone(java.util.TimeZone.getDefault().id).host
        }

    suspend fun setRelayHost(context: Context, host: String) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[AppPreferencesKeys.RELAY_HOST] = host
            prefs[AppPreferencesKeys.LEGACY_RELAY_HOST] = host
        }
    }

    suspend fun migrateLegacyRelayHostForRegion(context: Context) {
        val bestHost = RelayServers.getBestForTimeZone(java.util.TimeZone.getDefault().id).host
        context.appPreferencesDataStore.edit { prefs ->
            if (prefs[AppPreferencesKeys.RELAY_HOST_REGION_MIGRATED] == true) return@edit

            val currentHost = prefs[AppPreferencesKeys.RELAY_HOST]
                ?: prefs[AppPreferencesKeys.LEGACY_RELAY_HOST]
            if (bestHost != "play.pocketcraft.online" &&
                (currentHost == null || currentHost == "play.pocketcraft.online")
            ) {
                prefs[AppPreferencesKeys.RELAY_HOST] = bestHost
                prefs[AppPreferencesKeys.LEGACY_RELAY_HOST] = bestHost
            } else if (!currentHost.isNullOrBlank()) {
                prefs[AppPreferencesKeys.RELAY_HOST] = currentHost
            }
            prefs[AppPreferencesKeys.RELAY_HOST_REGION_MIGRATED] = true
        }
    }

    suspend fun migrateSingaporeRelayRemoval(context: Context) {
        val bestHost = RelayServers.getBestForTimeZone(java.util.TimeZone.getDefault().id).host
        context.appPreferencesDataStore.edit { prefs ->
            val currentHost = prefs[AppPreferencesKeys.RELAY_HOST]
                ?: prefs[AppPreferencesKeys.LEGACY_RELAY_HOST]
            if (currentHost == "play.pocketcraft.online") {
                prefs[AppPreferencesKeys.RELAY_HOST] = bestHost
                prefs[AppPreferencesKeys.LEGACY_RELAY_HOST] = bestHost
            }
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

    fun isInitialWorldSetupShownFlow(context: Context): Flow<Boolean> =
        context.appPreferencesDataStore.data.map { prefs ->
            prefs[AppPreferencesKeys.INITIAL_WORLD_SETUP_SHOWN] ?: false
        }

    suspend fun setInitialWorldSetupShown(context: Context, shown: Boolean) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[AppPreferencesKeys.INITIAL_WORLD_SETUP_SHOWN] = shown
        }
    }

    fun getPendingAutoDownloadVersionFlow(context: Context): Flow<String?> =
        context.appPreferencesDataStore.data.map { prefs ->
            prefs[AppPreferencesKeys.PENDING_AUTO_DOWNLOAD_VERSION]
        }

    suspend fun setPendingAutoDownloadVersion(context: Context, version: String?) {
        context.appPreferencesDataStore.edit { prefs ->
            if (version.isNullOrBlank()) {
                prefs.remove(AppPreferencesKeys.PENDING_AUTO_DOWNLOAD_VERSION)
            } else {
                prefs[AppPreferencesKeys.PENDING_AUTO_DOWNLOAD_VERSION] = version
            }
        }
    }

    fun getAppLaunchCount(context: Context): Flow<Int> =
        context.appPreferencesDataStore.data.map { prefs ->
            prefs[AppPreferencesKeys.APP_LAUNCH_COUNT] ?: 0
        }

    suspend fun setAppLaunchCount(context: Context, count: Int) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[AppPreferencesKeys.APP_LAUNCH_COUNT] = count
        }
    }

    fun isSocialPromoShownFlow(context: Context): Flow<Boolean> =
        context.appPreferencesDataStore.data.map { prefs ->
            prefs[AppPreferencesKeys.SOCIAL_PROMO_SHOWN] ?: false
        }

    suspend fun setSocialPromoShown(context: Context, shown: Boolean) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[AppPreferencesKeys.SOCIAL_PROMO_SHOWN] = shown
        }
    }

    fun isAnalyticsConsentFlow(context: Context): Flow<Boolean> =
        context.appPreferencesDataStore.data.map { prefs ->
            prefs[AppPreferencesKeys.ANALYTICS_CONSENT] ?: false
        }

    suspend fun setAnalyticsConsent(context: Context, granted: Boolean) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[AppPreferencesKeys.ANALYTICS_CONSENT] = granted
        }
    }


    fun isCrashDiagnosticsConsentFlow(context: Context): Flow<Boolean> =
        context.appPreferencesDataStore.data.map { prefs ->
            prefs[AppPreferencesKeys.CRASH_DIAGNOSTICS_CONSENT] ?: true
        }

    suspend fun setCrashDiagnosticsConsent(context: Context, granted: Boolean) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[AppPreferencesKeys.CRASH_DIAGNOSTICS_CONSENT] = granted
        }
    }


    fun getLegalVersionAcceptedFlow(context: Context): Flow<String?> =
        context.appPreferencesDataStore.data.map { prefs ->
            prefs[AppPreferencesKeys.LEGAL_VERSION_ACCEPTED]
        }

    suspend fun setLegalVersionAccepted(context: Context, version: String) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[AppPreferencesKeys.LEGAL_VERSION_ACCEPTED] = version
        }
    }

    fun showRcVersionsFlow(context: Context): Flow<Boolean> =
        context.appPreferencesDataStore.data.map { prefs ->
            prefs[AppPreferencesKeys.SHOW_RC_VERSIONS] ?: false
        }

    suspend fun setShowRcVersions(context: Context, enabled: Boolean) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[AppPreferencesKeys.SHOW_RC_VERSIONS] = enabled
        }
    }

    fun isFastStartEnabledFlow(context: Context): Flow<Boolean> =
        context.appPreferencesDataStore.data.map { prefs ->
            prefs[AppPreferencesKeys.FAST_START_ENABLED] ?: true
        }

    suspend fun setFastStartEnabled(context: Context, enabled: Boolean) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[AppPreferencesKeys.FAST_START_ENABLED] = enabled
        }
    }

    fun isFirstServerStartWarningDismissedFlow(context: Context): Flow<Boolean> =
        context.appPreferencesDataStore.data.map { prefs ->
            prefs[AppPreferencesKeys.FIRST_SERVER_START_WARNING_DISMISSED] ?: false
        }

    suspend fun setFirstServerStartWarningDismissed(context: Context, dismissed: Boolean) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[AppPreferencesKeys.FIRST_SERVER_START_WARNING_DISMISSED] = dismissed
        }
    }

    fun isFlightModeEnabledFlow(context: Context): Flow<Boolean> =
        context.appPreferencesDataStore.data.map { prefs ->
            prefs[AppPreferencesKeys.FLIGHT_MODE_ENABLED] ?: true
        }

    suspend fun setFlightModeEnabled(context: Context, enabled: Boolean) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[AppPreferencesKeys.FLIGHT_MODE_ENABLED] = enabled
        }
    }

    fun isFirstBootCompleteFlow(context: Context): Flow<Boolean> =
        context.appPreferencesDataStore.data.map { prefs ->
            prefs[AppPreferencesKeys.FIRST_BOOT_COMPLETE] ?: false
        }

    suspend fun setFirstBootComplete(context: Context, complete: Boolean) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[AppPreferencesKeys.FIRST_BOOT_COMPLETE] = complete
        }
    }

    fun getNavIndicatorShapeFlow(context: Context): Flow<String> =
        context.appPreferencesDataStore.data.map { prefs ->
            prefs[AppPreferencesKeys.NAV_INDICATOR_SHAPE] ?: "PILL"
        }

    suspend fun setNavIndicatorShape(context: Context, shape: String) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[AppPreferencesKeys.NAV_INDICATOR_SHAPE] = shape
        }
    }

    fun isServerTypeSetupGuideShownFlow(context: Context): Flow<Boolean> =
        context.appPreferencesDataStore.data.map { prefs ->
            prefs[AppPreferencesKeys.SERVER_TYPE_SETUP_GUIDE_SHOWN] ?: false
        }

    suspend fun setServerTypeSetupGuideShown(context: Context, shown: Boolean) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[AppPreferencesKeys.SERVER_TYPE_SETUP_GUIDE_SHOWN] = shown
        }
    }

    fun isFloatingChatEnabledFlow(context: Context): Flow<Boolean> =
        context.appPreferencesDataStore.data.map { prefs ->
            prefs[AppPreferencesKeys.FLOATING_CHAT_ENABLED] ?: false
        }

    suspend fun setFloatingChatEnabled(context: Context, enabled: Boolean) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[AppPreferencesKeys.FLOATING_CHAT_ENABLED] = enabled
        }
    }

    fun isFloatingChatFirstTimeShownFlow(context: Context): Flow<Boolean> =
        context.appPreferencesDataStore.data.map { prefs ->
            prefs[AppPreferencesKeys.FLOATING_CHAT_FIRST_TIME_SHOWN] ?: false
        }

    suspend fun setFloatingChatFirstTimeShown(context: Context, shown: Boolean) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[AppPreferencesKeys.FLOATING_CHAT_FIRST_TIME_SHOWN] = shown
        }
    }

    fun isAlwaysAliveBackgroundFlow(context: Context): Flow<Boolean> =
        context.appPreferencesDataStore.data.map { prefs ->
            prefs[AppPreferencesKeys.ALWAYS_ALIVE_BACKGROUND] ?: false
        }

    suspend fun setAlwaysAliveBackground(context: Context, enabled: Boolean) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[AppPreferencesKeys.ALWAYS_ALIVE_BACKGROUND] = enabled
        }
    }

    suspend fun clearAll(context: Context) {
        context.appPreferencesDataStore.edit { it.clear() }
    }
}

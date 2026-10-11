package com.pockethost.app.ads

import android.content.Context
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.pockethost.app.BuildConfig
import com.pockethost.app.billing.PremiumTier
import com.pockethost.app.data.preferences.AppPreferences
import com.pockethost.app.util.RamUtils

/** One place an ad can appear, with the Remote Config key that switches it on or off. */
enum class AdPlacement(val remoteKey: String) {
    BOOT("ads_spot_boot"),
    DOWNLOAD("ads_spot_download"),
    BACKUP("ads_spot_backup"),
    IMPORT("ads_spot_import"),
    PLUGIN_LIST("ads_spot_plugin_list"),
    REWARDED_SUPPORT("ads_spot_rewarded_support"),
    STOP_INTERSTITIAL("ads_spot_stop_interstitial")
}

/**
 * Decides whether ads may show at all, and where. Everything is driven by Remote Config
 * so ads can be switched on, off or thinned out without shipping an app update.
 */
object AdsConfig {
    // The Play Store build and the side-loaded build share one Firebase app, so each
    // gets its own master switch: ads can run in one while staying off in the other.
    private val KEY_ENABLED = if (BuildConfig.IS_EXTERNAL_BUILD) "ads_enabled_external" else "ads_enabled"
    private const val KEY_CHILD_DIRECTED = "ads_child_directed"
    private const val KEY_MIN_RAM_MB = "ads_min_ram_mb"

    // A debug build on Google's test ad units always shows ads, as does a release built
    // with -PpocketcraftForceTestAds=true, so the placements can be checked on a device
    // without touching the production switch.
    private val forcedOn = BuildConfig.ADMOB_USE_TEST_ADS && (BuildConfig.DEBUG || BuildConfig.ADS_FORCE_TEST)

    private val remoteConfig: FirebaseRemoteConfig?
        get() = runCatching { FirebaseRemoteConfig.getInstance() }.getOrNull()

    private fun flag(key: String): Boolean = remoteConfig?.getBoolean(key) ?: false

    /** Master switch, minus supporters and phones too short on memory to spare any for ads. */
    fun adsEnabled(context: Context): Boolean {
        if (!forcedOn && !flag(KEY_ENABLED)) return false
        if (isSupporter(context)) return false
        return hasSpareRam(context)
    }

    fun isEnabled(context: Context, placement: AdPlacement): Boolean =
        adsEnabled(context) && (forcedOn || flag(placement.remoteKey))

    /**
     * True until Remote Config says otherwise, including before the first fetch: treating
     * the audience as children by mistake only costs revenue, the reverse breaks policy.
     */
    fun childDirected(): Boolean {
        val config = remoteConfig ?: return true
        if (config.getValue(KEY_CHILD_DIRECTED).source == FirebaseRemoteConfig.VALUE_SOURCE_STATIC) return true
        return config.getBoolean(KEY_CHILD_DIRECTED)
    }

    private fun isSupporter(context: Context): Boolean {
        val tier = AppPreferences(context).lastSeenMembershipTier
        return tier.isNotBlank() && tier != PremiumTier.NONE.wireValue
    }

    // An ad runs in a WebView that takes memory the Minecraft server needs more.
    private fun hasSpareRam(context: Context): Boolean {
        val minRamMb = remoteConfig?.getLong(KEY_MIN_RAM_MB) ?: 0L
        return RamUtils.getTotalRamMb(context) >= minRamMb
    }
}

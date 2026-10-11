package com.pockethost.app.ads

import android.app.Activity
import android.content.Context
import android.provider.Settings
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.pockethost.app.BuildConfig
import com.pockethost.app.PocketHostApp
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * All AdMob loading and showing goes through here rather than scattered across
 * screens, for one reason: an ad must be *preloaded* before the moment it is
 * shown, because a fresh network load takes a few seconds and nobody should
 * stare at a spinner after tapping "watch an ad". So every show call re-arms
 * the next load immediately, and callers only ever see "ready or not yet".
 *
 * Whether an ad may show is [AdsConfig]'s call; nothing here runs until
 * [start] has found ads enabled and the user's consent in order.
 */
object AdManager {
    private const val TAG = "AdManager"
    private const val PREFS = "pockethost_ads"
    private const val KEY_LAST_INTERSTITIAL_MS = "last_interstitial_shown_ms"

    // "Once a day" with some slack, so an evening session is not skipped for being
    // 23 hours after yesterday's.
    private const val INTERSTITIAL_MIN_GAP_MS = 20L * 60 * 60 * 1000

    // AdMob expires a loaded ad after an hour; replace cached ones well before that.
    private const val NATIVE_AD_MAX_AGE_MS = 30L * 60 * 1000

    @Volatile private var started = false
    private val _ready = MutableStateFlow(false)

    /** True once consent is settled and the SDK is up; ad cards wait for this. */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _privacyOptionsRequired = MutableStateFlow(false)

    /**
     * True where the law gives the user a right to change their ad consent later, which is
     * when AdMob requires the app to offer a way back to the consent form.
     */
    val privacyOptionsRequired: StateFlow<Boolean> = _privacyOptionsRequired.asStateFlow()

    private var rewardedAd: RewardedAd? = null
    private var rewardedAdLoading = false
    private var interstitialAd: InterstitialAd? = null
    private var interstitialAdLoading = false

    private class CachedNativeAd(val ad: NativeAd, val loadedAtMs: Long)
    private val nativeAds = mutableMapOf<AdPlacement, CachedNativeAd>()
    private val nativeAdWaiters = mutableMapOf<AdPlacement, MutableList<(NativeAd) -> Unit>>()

    /**
     * Asks for ad consent where the law requires it, then brings the SDK up. Safe to call
     * on every launch; does nothing when ads are switched off.
     */
    fun start(activity: Activity) {
        if (started || !AdsConfig.adsEnabled(activity)) return
        started = true

        val consent = UserMessagingPlatform.getConsentInformation(activity)
        val params = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(AdsConfig.childDirected())
            .apply { if (BuildConfig.DEBUG) setConsentDebugSettings(debugConsentSettings(activity)) }
            .build()
        consent.requestConsentInfoUpdate(
            activity,
            params,
            {
                updatePrivacyOptionsRequired(consent)
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error ->
                    error?.let { Log.w(TAG, "Consent form: ${it.message}") }
                    updatePrivacyOptionsRequired(consent)
                    if (consent.canRequestAds()) initializeSdk(activity.applicationContext)
                }
            },
            { error -> Log.w(TAG, "Consent info update failed: ${error.message}") }
        )
        // Consent given in an earlier session still stands while the update is in flight.
        if (consent.canRequestAds()) initializeSdk(activity.applicationContext)
    }

    /** Reopens the consent form so the user can change or withdraw their ad consent. */
    fun showPrivacyOptionsForm(activity: Activity, onError: (String) -> Unit = {}) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
            updatePrivacyOptionsRequired(UserMessagingPlatform.getConsentInformation(activity))
            error?.let {
                Log.w(TAG, "Privacy options form: ${it.message}")
                onError(it.message)
            }
        }
    }

    private fun updatePrivacyOptionsRequired(consent: ConsentInformation) {
        _privacyOptionsRequired.value = consent.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    }

    /**
     * Debug builds only: pretends this phone is in Europe so the consent form and the
     * privacy options entry can be checked from anywhere. The override only applies to
     * devices registered as test devices, so the phone registers itself by the same hash
     * the consent SDK prints to logcat (the MD5 of its Android ID).
     */
    private fun debugConsentSettings(context: Context): ConsentDebugSettings {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
        val hashedId = MessageDigest.getInstance("MD5").digest(androidId.toByteArray())
            .joinToString("") { "%02X".format(it) }
        return ConsentDebugSettings.Builder(context)
            .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
            .addTestDeviceHashedId(hashedId)
            .build()
    }

    private fun initializeSdk(context: Context) {
        if (_ready.value) return
        val childDirected = AdsConfig.childDirected()
        MobileAds.setRequestConfiguration(
            RequestConfiguration.Builder()
                .setTagForChildDirectedTreatment(
                    if (childDirected) RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_TRUE
                    else RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_UNSPECIFIED
                )
                .setMaxAdContentRating(
                    if (childDirected) RequestConfiguration.MAX_AD_CONTENT_RATING_G
                    else RequestConfiguration.MAX_AD_CONTENT_RATING_T
                )
                .build()
        )
        PocketHostApp.applicationScope.launch {
            runCatching { MobileAds.initialize(context) }
                .onFailure { Log.e(TAG, "MobileAds.initialize failed", it) }
                .onSuccess {
                    withContext(Dispatchers.Main) {
                        if (_ready.value) return@withContext
                        Log.d(TAG, "MobileAds initialized (testAds=${BuildConfig.ADMOB_USE_TEST_ADS})")
                        _ready.value = true
                        if (AdsConfig.isEnabled(context, AdPlacement.REWARDED_SUPPORT)) preloadRewardedAd(context)
                        if (AdsConfig.isEnabled(context, AdPlacement.STOP_INTERSTITIAL)) preloadInterstitialAd(context)
                    }
                }
        }
    }

    fun isRewardedAdReady(): Boolean = rewardedAd != null

    private fun preloadRewardedAd(context: Context) {
        if (!_ready.value || rewardedAd != null || rewardedAdLoading) return
        rewardedAdLoading = true
        RewardedAd.load(
            context.applicationContext,
            BuildConfig.ADMOB_REWARDED_UNIT_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAdLoading = false
                    rewardedAd = ad
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAdLoading = false
                    rewardedAd = null
                    Log.w(TAG, "Rewarded ad failed to load: ${error.message}")
                }
            }
        )
    }

    /**
     * Shows the rewarded ad if one is ready. [onEarnedReward] fires only from
     * the SDK's own onUserEarnedReward callback, never on ad-closed, or someone
     * who closes the ad early would get the reward anyway.
     */
    fun showRewardedAd(
        activity: Activity,
        onEarnedReward: () -> Unit,
        onNotReady: () -> Unit,
        onDismissed: () -> Unit = {}
    ) {
        val ad = rewardedAd
        if (ad == null) {
            onNotReady()
            preloadRewardedAd(activity)
            return
        }
        rewardedAd = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                preloadRewardedAd(activity)
                onDismissed()
            }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.w(TAG, "Rewarded ad failed to show: ${error.message}")
                preloadRewardedAd(activity)
                onDismissed()
            }
        }
        ad.show(activity) { onEarnedReward() }
    }

    private fun preloadInterstitialAd(context: Context) {
        if (!_ready.value || interstitialAd != null || interstitialAdLoading) return
        interstitialAdLoading = true
        InterstitialAd.load(
            context.applicationContext,
            BuildConfig.ADMOB_INTERSTITIAL_UNIT_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAdLoading = false
                    interstitialAd = ad
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAdLoading = false
                    interstitialAd = null
                    Log.w(TAG, "Interstitial ad failed to load: ${error.message}")
                }
            }
        )
    }

    /**
     * Shows the full-screen ad for a server the host just stopped by hand, at most once a
     * day. Call it only from the Stop button: a stop caused by a crash or a restart must
     * never bring up an ad.
     */
    fun showStopInterstitial(activity: Activity) {
        if (!AdsConfig.isEnabled(activity, AdPlacement.STOP_INTERSTITIAL)) return
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(KEY_LAST_INTERSTITIAL_MS, 0L) < INTERSTITIAL_MIN_GAP_MS) return
        val ad = interstitialAd
        if (ad == null) {
            preloadInterstitialAd(activity)
            return
        }
        interstitialAd = null
        prefs.edit().putLong(KEY_LAST_INTERSTITIAL_MS, now).apply()
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.w(TAG, "Interstitial ad failed to show: ${error.message}")
            }
        }
        ad.show(activity)
    }

    /** The ad already loaded for [placement], if it is still fresh enough to show. */
    fun cachedNativeAd(placement: AdPlacement): NativeAd? =
        nativeAds[placement]?.takeIf { System.currentTimeMillis() - it.loadedAtMs < NATIVE_AD_MAX_AGE_MS }?.ad

    /**
     * Hands [onLoaded] a native ad for [placement], from the cache when it has a fresh one.
     * One ad is kept per placement and reused, so a list scrolling a card in and out of
     * view does not request a new ad each time. Nothing is called if no ad can be had.
     */
    fun loadNativeAd(context: Context, placement: AdPlacement, onLoaded: (NativeAd) -> Unit) {
        if (!_ready.value) return
        cachedNativeAd(placement)?.let { onLoaded(it); return }
        val waiters = nativeAdWaiters[placement]
        if (waiters != null) {
            waiters += onLoaded
            return
        }
        nativeAdWaiters[placement] = mutableListOf(onLoaded)
        AdLoader.Builder(context.applicationContext, BuildConfig.ADMOB_NATIVE_UNIT_ID)
            .forNativeAd { ad ->
                nativeAds.put(placement, CachedNativeAd(ad, System.currentTimeMillis()))?.ad?.destroy()
                nativeAdWaiters.remove(placement)?.forEach { it(ad) }
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    nativeAdWaiters.remove(placement)
                    Log.w(TAG, "Native ad for $placement failed to load: ${error.message}")
                }
            })
            .build()
            .loadAd(AdRequest.Builder().build())
    }
}

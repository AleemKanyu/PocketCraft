package com.pockethost.app.ads

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.pockethost.app.R
import com.pockethost.app.ui.components.PocketHostCard
import com.pockethost.app.ui.theme.PocketColors

/**
 * A native ad drawn as one of the app's own cards and labelled "Ad". Takes up no space
 * at all while ads are off for [placement] or until an ad has actually loaded, so a
 * screen never shows an empty slot.
 */
@Composable
fun NativeAdCard(placement: AdPlacement, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sdkReady by AdManager.ready.collectAsState()
    if (!sdkReady || !AdsConfig.isEnabled(context, placement)) return

    var nativeAd by remember(placement) { mutableStateOf(AdManager.cachedNativeAd(placement)) }
    LaunchedEffect(placement) {
        if (nativeAd == null) AdManager.loadNativeAd(context, placement) { nativeAd = it }
    }
    val ad = nativeAd ?: return

    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val accentColor = PocketColors.Primary.toArgb()
    val onAccentColor = PocketColors.PrimaryText.toArgb()

    PocketHostCard(modifier = modifier.fillMaxWidth()) {
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { LayoutInflater.from(it).inflate(R.layout.native_ad_card, null) as NativeAdView },
            update = { view -> view.bind(ad, textColor, mutedColor, accentColor, onAccentColor) }
        )
    }
}

private fun NativeAdView.bind(ad: NativeAd, textColor: Int, mutedColor: Int, accentColor: Int, onAccentColor: Int) {
    val density = resources.displayMetrics.density

    val headline = findViewById<TextView>(R.id.ad_headline)
    headline.text = ad.headline
    headline.setTextColor(textColor)
    headlineView = headline

    val badge = findViewById<TextView>(R.id.ad_badge)
    badge.setTextColor(onAccentColor)
    badge.background = GradientDrawable().apply {
        cornerRadius = 4 * density
        setColor(accentColor)
    }

    val advertiser = findViewById<TextView>(R.id.ad_advertiser)
    advertiser.text = ad.advertiser.orEmpty()
    advertiser.setTextColor(mutedColor)
    advertiserView = advertiser

    val body = findViewById<TextView>(R.id.ad_body)
    body.text = ad.body
    body.setTextColor(mutedColor)
    body.visibility = if (ad.body.isNullOrBlank()) View.GONE else View.VISIBLE
    bodyView = body

    val icon = findViewById<ImageView>(R.id.ad_icon)
    icon.setImageDrawable(ad.icon?.drawable)
    icon.visibility = if (ad.icon == null) View.GONE else View.VISIBLE
    iconView = icon

    mediaView = findViewById<MediaView>(R.id.ad_media)

    val callToAction = findViewById<Button>(R.id.ad_call_to_action)
    callToAction.text = ad.callToAction
    callToAction.setTextColor(onAccentColor)
    callToAction.backgroundTintList = ColorStateList.valueOf(accentColor)
    callToAction.visibility = if (ad.callToAction.isNullOrBlank()) View.GONE else View.VISIBLE
    callToActionView = callToAction

    setNativeAd(ad)
}

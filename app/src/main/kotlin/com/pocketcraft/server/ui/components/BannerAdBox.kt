package com.pocketcraft.server.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.pocketcraft.server.data.preferences.AppPreferencesStore

@Composable
fun BannerAdBox(
    modifier: Modifier = Modifier,
    adUnitId: String = "ca-app-pub-7133828334952044/3136140315"
) {
    val context = LocalContext.current
    val adsConsentGranted by AppPreferencesStore.isAdsConsentFlow(context).collectAsState(initial = false)
    var adView by remember { mutableStateOf<AdView?>(null) }

    DisposableEffect(adView) {
        onDispose { adView?.destroy() }
    }

    GameCard(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(
                text = "Sponsored",
                fontSize = MaterialTheme.typography.labelSmall.fontSize,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

            if (!adsConsentGranted) {
                Text(
                    text = "Ads disabled until you enable ad consent in Settings > Privacy & Legal.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = MaterialTheme.typography.bodySmall.fontSize
                )
            } else {
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    factory = { viewContext ->
                        AdView(viewContext).apply {
                            setAdSize(AdSize.BANNER)
                            this.adUnitId = adUnitId
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            adView = this
                            loadAd(AdRequest.Builder().build())
                        }
                    },
                    update = { view ->
                        if (view.adUnitId != adUnitId) {
                            view.adUnitId = adUnitId
                        }
                        if (adView == null) {
                            adView = view
                            view.loadAd(AdRequest.Builder().build())
                        }
                    }
                )
            }
        }
    }
}
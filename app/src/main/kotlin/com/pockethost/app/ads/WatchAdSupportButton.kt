package com.pockethost.app.ads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pockethost.app.ui.theme.ButtonFont

/**
 * Lets someone who cannot or will not donate support the app by choosing to watch one
 * rewarded ad. Draws nothing while that placement is switched off.
 */
@Composable
fun WatchAdSupportButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sdkReady by AdManager.ready.collectAsState()
    if (!sdkReady || !AdsConfig.isEnabled(context, AdPlacement.REWARDED_SUPPORT)) return

    OutlinedButton(
        onClick = {
            val activity = context.findActivity() ?: return@OutlinedButton
            AdManager.showRewardedAd(
                activity = activity,
                onEarnedReward = {
                    Toast.makeText(context, "Thank you for supporting PocketHost!", Toast.LENGTH_LONG).show()
                },
                onNotReady = {
                    Toast.makeText(context, "No ad is ready right now. Please try again later.", Toast.LENGTH_LONG).show()
                }
            )
        },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(
            text = "Watch an ad to support us",
            modifier = Modifier.padding(vertical = 6.dp),
            fontWeight = FontWeight.Bold,
            fontFamily = ButtonFont,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

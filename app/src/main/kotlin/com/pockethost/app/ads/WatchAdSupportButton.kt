package com.pockethost.app.ads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        modifier = modifier.fillMaxWidth().height(42.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.SmartDisplay,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Watch an ad to support us",
                fontWeight = FontWeight.Bold,
                fontFamily = ButtonFont,
                fontSize = 12.5.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

package com.pocketcraft.server.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.pocketcraft.server.ui.theme.PocketColors

@Composable
fun SupportAdDialog(
    onDismiss: () -> Unit,
    onWatchAd: () -> Unit
) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val dialogColor = if (isDarkTheme) MaterialTheme.colorScheme.surface else Color(0xFF12121A)
    val titleColor = if (isDarkTheme) PocketColors.TextDark else PocketColors.Primary
    val bodyColor = if (isDarkTheme) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFFCCCCCC)
    val buttonColor = if (isDarkTheme) PocketColors.SurfaceVarDark else PocketColors.Primary
    val buttonTextColor = if (isDarkTheme) PocketColors.TextDark else Color.White
    val skipColor = if (isDarkTheme) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF888888)
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = dialogColor,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "☕  Support PocketCraft",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = titleColor,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "PocketCraft is completely free. Watching a short ad while your server starts helps us cover server costs. Thank you! 🙏",
                    fontSize = 14.sp,
                    color = bodyColor,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onWatchAd,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = buttonColor,
                            contentColor = buttonTextColor
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Watch Ad",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Skip",
                            color = skipColor,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

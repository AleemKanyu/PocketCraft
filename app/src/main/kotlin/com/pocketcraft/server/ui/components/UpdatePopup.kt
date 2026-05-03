package com.pocketcraft.server.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pocketcraft.server.ui.theme.Monocraft
import com.pocketcraft.server.ui.theme.PocketColors
import com.pocketcraft.server.update.UpdateConfig

@Composable
fun UpdatePopup(
    config: UpdateConfig,
    onUpdateNow: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = { if (!config.isForced) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !config.isForced,
            dismissOnClickOutside = !config.isForced
        )
    ) {
        PocketCraftCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FlatEmojiIcon(
                    symbol = "🎮",
                    modifier = Modifier.size(64.dp)
                )

                Text(
                    text = "Update Available",
                    fontFamily = Monocraft,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "A new version of the app is available. Please update to continue.",
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 22.sp
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DuoButton(
                        text = "UPDATE NOW",
                        onClick = onUpdateNow,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (!config.isForced) {
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Maybe Later",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                                fontFamily = Monocraft
                            )
                        }
                    }
                }
            }
        }
    }
}

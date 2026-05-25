package com.pocketcraft.server.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.pocketcraft.server.broadcast.BroadcastMessage
import com.pocketcraft.server.ui.theme.Monocraft
import com.pocketcraft.server.ui.theme.PocketColors

@Composable
fun BroadcastPopup(
    broadcast: BroadcastMessage,
    onDismiss: () -> Unit
) {
    val emoji = when (broadcast.type) {
        "critical" -> "🚨"
        "warning" -> "⚠️"
        else -> "📢"
    }

    val borderStroke = when (broadcast.type) {
        "critical" -> BorderStroke(2.dp, PocketColors.ConsoleError)
        "warning" -> BorderStroke(2.dp, PocketColors.ConsoleWarn)
        else -> BorderStroke(2.dp, PocketColors.PrimaryDark)
    }

    val buttonVariant = when (broadcast.type) {
        "critical" -> DuoButtonVariant.Danger
        else -> DuoButtonVariant.Primary
    }

    Dialog(
        onDismissRequest = { if (broadcast.dismissible) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = broadcast.dismissible,
            dismissOnClickOutside = broadcast.dismissible
        )
    ) {
        PocketCraftCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            border = borderStroke
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FlatEmojiIcon(
                    symbol = emoji,
                    modifier = Modifier.size(64.dp)
                )

                Text(
                    text = broadcast.title.ifBlank { "Announcement" },
                    fontFamily = Monocraft,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (broadcast.body.isNotBlank()) {
                    Text(
                        text = broadcast.body,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 22.sp
                    )
                }

                if (broadcast.dismissible) {
                    DuoButton(
                        text = "DISMISS",
                        variant = buttonVariant,
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    // Non-dismissible message visual helper
                    Text(
                        text = "This is a mandatory system notice.",
                        fontSize = 12.sp,
                        fontFamily = Monocraft,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

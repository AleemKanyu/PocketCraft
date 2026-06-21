package com.pocketcraft.server.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketcraft.server.broadcast.BroadcastMessage
import com.pocketcraft.server.ui.theme.Monocraft
import com.pocketcraft.server.ui.theme.PocketColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BroadcastPopup(
    broadcast: BroadcastMessage,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { broadcast.dismissible }
    )

    fun dismissWithAnimation() {
        scope.launch {
            sheetState.hide()
            onDismiss()
        }
    }

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

    ModalBottomSheet(
        onDismissRequest = { if (broadcast.dismissible) onDismiss() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { IosDragHandle() },
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
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

            val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            val uid = currentUser?.uid.orEmpty()

            if (broadcast.interactionType == "question") {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    broadcast.questionOptions.forEach { option ->
                        DuoButton(
                            text = option,
                            variant = DuoButtonVariant.Primary,
                            onClick = {
                                com.pocketcraft.server.broadcast.BroadcastManager.submitResponse(
                                    broadcastId = broadcast.id,
                                    selectedOption = option,
                                    interactionType = "question",
                                    uid = uid
                                )
                                dismissWithAnimation()
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            } else if (broadcast.interactionType == "opt_in") {
                val buttonText = broadcast.questionOptions.firstOrNull() ?: "Get Early Access"
                DuoButton(
                    text = buttonText,
                    variant = DuoButtonVariant.Primary,
                    onClick = {
                        com.pocketcraft.server.broadcast.BroadcastManager.submitResponse(
                            broadcastId = broadcast.id,
                            selectedOption = "",
                            interactionType = "opt_in",
                            uid = uid
                        )
                        dismissWithAnimation()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            } else if (broadcast.dismissible) {
                DuoButton(
                    text = "DISMISS",
                    variant = buttonVariant,
                    onClick = ::dismissWithAnimation,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
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

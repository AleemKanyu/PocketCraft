package com.pockethost.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pockethost.app.ui.theme.ButtonFont
import com.pockethost.app.ui.theme.PocketColors
import kotlinx.coroutines.launch

/**
 * A short reminder that PocketHost is funded by voluntary support, shown every
 * [LAUNCH_INTERVAL] app launches.
 */
object SupportUsPopup {
    const val LAUNCH_INTERVAL = 4

    /** True on every [LAUNCH_INTERVAL]th launch that has not already shown the reminder. */
    fun isDue(launchCount: Int, lastShownLaunchCount: Int): Boolean =
        launchCount > 0 && launchCount % LAUNCH_INTERVAL == 0 && lastShownLaunchCount != launchCount

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun Content(onSupport: () -> Unit, onDismiss: () -> Unit) {
        val scope = rememberCoroutineScope()
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        // The sheet slides back down before it leaves the composition; removing it outright
        // would make it vanish with no exit animation.
        fun close(then: () -> Unit) {
            scope.launch {
                sheetState.hide()
                then()
            }
        }

        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            dragHandle = { IosDragHandle() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(PocketColors.Primary.copy(alpha = 0.14f), CircleShape)
                        .border(1.dp, PocketColors.Primary.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = PocketColors.Primary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Text(
                    text = "Enjoying PocketHost?",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "PocketHost is free, with every feature unlocked. If it is useful to you, " +
                        "a small contribution helps keep the relay servers running.",
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = { close(onSupport) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PocketColors.Primary,
                        contentColor = PocketColors.PrimaryText
                    )
                ) {
                    Text(
                        text = "Support PocketHost",
                        modifier = Modifier.padding(vertical = 6.dp),
                        fontWeight = FontWeight.Bold,
                        fontFamily = ButtonFont
                    )
                }

                TextButton(onClick = { close(onDismiss) }) {
                    Text(
                        text = "Maybe later",
                        fontWeight = FontWeight.Bold,
                        fontFamily = ButtonFont,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

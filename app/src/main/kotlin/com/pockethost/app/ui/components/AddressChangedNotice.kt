package com.pockethost.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pockethost.app.ui.theme.PocketColors
import com.pockethost.app.ui.theme.card3d
import kotlinx.coroutines.launch

/**
 * Shown once to people who used the app before, the first time their server comes online
 * after the move to pockethost.online addresses: friends need the new address to join.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddressChangedNotice(address: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val close: () -> Unit = {
        scope.launch {
            sheetState.hide()
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { IosDragHandle() },
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(PocketColors.Primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Public, contentDescription = null, tint = PocketColors.Primary)
                }
                Column {
                    Text("NEW ADDRESS", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = PocketColors.Primary)
                    Text(
                        "Your server address changed",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = PocketColors.TextPrimary
                    )
                }
            }

            Text(
                "The old addresses were having connection problems, so we moved every server to our " +
                    "new, more reliable ones. This is a one-time change: share the new address with your friends.",
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = PocketColors.TextSecondary
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .card3d(elevation = 3.dp, cornerRadius = 14.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Text(
                    address,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = PocketColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.weight(1f)) {
                    DuoButton(
                        text = "Copy",
                        onClick = { copyAddress(context, address) },
                        variant = DuoButtonVariant.SecondaryGray
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    DuoButton(text = "Share", onClick = { shareAddress(context, address) })
                }
            }
            DuoButton(text = "Got it", onClick = close, variant = DuoButtonVariant.Secondary)
        }
    }
}

private fun copyAddress(context: Context, address: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("Server address", address))
    Toast.makeText(context, "Address copied", Toast.LENGTH_SHORT).show()
}

private fun shareAddress(context: Context, address: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "My Minecraft server has a new address: $address")
    }
    runCatching { context.startActivity(Intent.createChooser(intent, "Share server address")) }
}

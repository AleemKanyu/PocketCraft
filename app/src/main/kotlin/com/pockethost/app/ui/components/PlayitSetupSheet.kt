package com.pockethost.app.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pockethost.app.ui.screens.ServerStateHolder
import com.pockethost.app.ui.theme.PocketColors
import com.pockethost.app.ui.theme.pocketSheetBorderColor

/**
 * Guided setup for the worldwide network. The user approves this phone once on
 * playit.gg; the sheet notices the approval by itself and every later start is automatic.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayitSetupSheet(stateHolder: ServerStateHolder) {
    val context = LocalContext.current
    val openUrl: (String) -> Unit = { url ->
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }
    val claimUrl = stateHolder.playitClaimUrl
    val error = stateHolder.playitSetupError
    val helpUrl = stateHolder.tunnelHelpUrl
    val serverActive = stateHolder.isRunning || stateHolder.isStarting

    ModalBottomSheet(
        onDismissRequest = { stateHolder.closePlayitSetup() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 10.dp,
            border = BorderStroke(1.dp, pocketSheetBorderColor())
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.Public, contentDescription = null, tint = PocketColors.Primary)
                    Text("Worldwide network", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                }

                when {
                    stateHolder.playitLinked && helpUrl != null -> {
                        Body(stateHolder.tunnelError ?: "playit.gg needs a quick fix on your account.")
                        DuoButton(text = "Open playit.gg", onClick = { openUrl(helpUrl) })
                        DuoButton(
                            text = "Close",
                            onClick = { stateHolder.closePlayitSetup() },
                            variant = DuoButtonVariant.Secondary
                        )
                    }
                    stateHolder.playitLinked -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = PocketColors.Online)
                            Text("Linked. You are all set.", fontWeight = FontWeight.Bold)
                        }
                        Body(
                            if (serverActive) {
                                "Your server is going online. The address shows on the home screen."
                            } else {
                                "Start your server and it goes online by itself, every time."
                            }
                        )
                        DuoButton(text = "Done", onClick = { stateHolder.closePlayitSetup() })
                    }
                    error != null -> {
                        Body(error)
                        DuoButton(text = "Try again", onClick = { stateHolder.startPlayitLink() })
                    }
                    claimUrl == null -> {
                        Body("Players anywhere can join through playit.gg's network. It is free and you link it once.")
                        Waiting("Getting your link...")
                    }
                    else -> {
                        Body("Players anywhere can join through playit.gg's network. It is free and you link it once.")
                        Step("1", "Tap the button and sign in to playit.gg (free).")
                        Step("2", "Tap Continue, then Add Agent.")
                        Step("3", "Come back here. We do the rest.")
                        DuoButton(text = "Open playit.gg", onClick = { openUrl(claimUrl) })
                        Waiting("Waiting for you to approve...")
                    }
                }
            }
        }
    }
}

@Composable
private fun Body(text: String) {
    Text(text, fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Step(number: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(number, fontWeight = FontWeight.ExtraBold, color = PocketColors.Primary)
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Waiting(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        Text(text, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

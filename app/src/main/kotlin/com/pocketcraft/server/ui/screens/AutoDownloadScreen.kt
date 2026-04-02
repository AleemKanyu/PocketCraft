package com.pocketcraft.server.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketcraft.server.server.ServerDownloader
import com.pocketcraft.server.ui.components.DuoButton
import com.pocketcraft.server.ui.components.GameCard
import com.pocketcraft.server.ui.theme.PocketColors

@Composable
fun AutoDownloadScreen(
    versionId: String,
    onReady: () -> Unit
) {
    var progress by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("Connecting...") }
    var failed by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    LaunchedEffect(versionId) {
        try {
            status = "Downloading Paper $versionId..."
            ServerDownloader.downloadPaperJarOnMain(
                context = context,
                versionId = versionId,
                onStatus = { status = it },
                onProgress = { progress = it }
            )
            onReady()
        } catch (error: Exception) {
            failed = error.message ?: "Unknown download error"
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
                        MaterialTheme.colorScheme.background
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        GameCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(text = "📦", fontSize = 52.sp)
                Text(
                    text = if (failed == null) "Building Your Server" else "Setup Hit A Wall",
                    style = MaterialTheme.typography.headlineLarge,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = failed ?: status,
                    color = if (failed == null) MaterialTheme.colorScheme.onSurfaceVariant else PocketColors.Offline,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (progress / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp),
                    color = PocketColors.Primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Text(
                    text = "$progress%",
                    color = PocketColors.PrimaryDark,
                    fontWeight = FontWeight.ExtraBold
                )
                if (failed != null) {
                    DuoButton(
                        text = "TRY AGAIN",
                        onClick = onReady,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        text = "This only happens once per version.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

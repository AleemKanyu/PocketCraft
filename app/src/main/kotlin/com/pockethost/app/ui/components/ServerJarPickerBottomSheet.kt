package com.pockethost.app.ui.components

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pockethost.app.data.model.ServerType
import com.pockethost.app.data.preferences.AppPreferences
import com.pockethost.app.server.ServerTypeDownloadUrls
import com.pockethost.app.ui.theme.PocketColors
import kotlinx.coroutines.launch

import androidx.compose.material3.LinearProgressIndicator
import com.pockethost.app.service.ServerFileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerJarPickerBottomSheet(
    serverType: ServerType,
    version: String,
    onDismiss: () -> Unit,
    onJarSelected: (Uri) -> Unit
) {
    val context = LocalContext.current
    val appPrefs = remember { AppPreferences(context) }
    val relayHost = remember { appPrefs.relayHost }
    var downloadUrl by remember { mutableStateOf(ServerTypeDownloadUrls.getDownloadPageUrl(serverType, version)) }
    var isResolving by remember { mutableStateOf(true) }

    LaunchedEffect(serverType, version, relayHost) {
        isResolving = true
        downloadUrl = ServerTypeDownloadUrls.resolveDownloadUrl(serverType, version, relayHost)
        isResolving = false
    }

    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var downloadStatusText by remember { mutableStateOf("") }
    var downloadError by remember { mutableStateOf<String?>(null) }

    val jarPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onJarSelected(uri)
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val downloadBtnAlpha by animateFloatAsState(
        targetValue = if (isResolving || isDownloading) 0.85f else 1f,
        animationSpec = tween(300),
        label = "dlBtnAlpha"
    )

    var hasTriggeredDownload by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 14.dp, bottom = 6.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .background(
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                        CircleShape
                    )
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            val isFabric = serverType == ServerType.FABRIC
            // ── Header ────────────────────────────────────────────────────────────
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Server-type pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(PocketColors.PrimaryMuted)
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = serverType.displayName.uppercase(),
                        color = PocketColors.PrimaryDark,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.5.sp
                    )
                }
                Text(
                    text = when {
                        isFabric -> "Install Fabric $version Server JAR"
                        else     -> "Install $version Server JAR"
                    },
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Download directly in the app or choose a downloaded JAR file from storage.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 19.sp
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

            if (downloadError != null) {
                Text(
                    text = downloadError!!,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // ── Option 1 – Direct In-App Download ─────────────────────────────────
            InstallStep(
                stepNumber = 1,
                title = "⚡ Direct In-App Download (Recommended)",
                description = if (isDownloading) downloadStatusText else "Automatically download and install $version in one tap."
            ) {
                if (isDownloading) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = PocketColors.Primary,
                            trackColor = PocketColors.Primary.copy(alpha = 0.2f)
                        )
                        Text(
                            text = downloadStatusText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = PocketColors.Primary
                        )
                    }
                } else {
                    DuoButton(
                        text = if (isResolving) "CONNECTING…" else "⚡  1-TAP AUTO DOWNLOAD & INSTALL",
                        onClick = {
                            if (isResolving || isDownloading) return@DuoButton
                            isDownloading = true
                            downloadError = null
                            downloadStatusText = "Connecting…"
                            coroutineScope.launch(Dispatchers.IO) {
                                try {
                                    val targetFile = ServerFileManager.getServerJarFile(
                                        context = context.applicationContext,
                                        gameVersion = version,
                                        serverType = serverType
                                    )
                                    targetFile.parentFile?.mkdirs()
                                    val tempFile = File(targetFile.parentFile, "${targetFile.name}.downloading")
                                    val conn = java.net.URL(downloadUrl).openConnection() as java.net.HttpURLConnection
                                    conn.setRequestProperty("User-Agent", "PocketHost/1.0")
                                    conn.connectTimeout = 15000
                                    conn.readTimeout = 60000
                                    val totalLength = conn.contentLengthLong
                                    var bytesReadTotal = 0L
                                    conn.inputStream.use { input ->
                                        tempFile.outputStream().use { output ->
                                            val buffer = ByteArray(32768)
                                            var n: Int
                                            while (input.read(buffer).also { n = it } != -1) {
                                                output.write(buffer, 0, n)
                                                bytesReadTotal += n
                                                if (totalLength > 0) {
                                                    val pct = (bytesReadTotal.toFloat() / totalLength.toFloat()).coerceIn(0f, 1f)
                                                    downloadProgress = pct
                                                    downloadStatusText = "Downloading: ${(pct * 100).toInt()}%"
                                                } else {
                                                    downloadStatusText = "Downloaded ${(bytesReadTotal / 1048576)} MB…"
                                                }
                                            }
                                        }
                                    }
                                    if (tempFile.exists() && tempFile.length() > 5000) {
                                        tempFile.renameTo(targetFile)
                                        val uri = Uri.fromFile(targetFile)
                                        withContext(Dispatchers.Main) {
                                            isDownloading = false
                                            onJarSelected(uri)
                                        }
                                    } else {
                                        throw Exception("Downloaded file too small or empty")
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        isDownloading = false
                                        downloadError = "Direct download error (${e.localizedMessage ?: "timeout"}). Use browser download below."
                                    }
                                }
                            }
                        },
                        enabled = !isResolving && !isDownloading,
                        variant = DuoButtonVariant.Primary,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // ── Option 2 – Browser Download ─────────────────────────────────────
            InstallStep(
                stepNumber = 2,
                title = "🌐 Download in Browser",
                description = "Download via your web browser if direct download is restricted."
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .border(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                        .clickable {
                            hasTriggeredDownload = true
                            runCatching {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            }
                        }
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🌐  Open Browser Download Page",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }

            // ── Option 3 – Select Downloaded JAR ─────────────────────────────────
            InstallStep(
                stepNumber = 3,
                title = "📂 Select Downloaded JAR From Storage",
                description = "Choose any .jar file downloaded from browser or stored on your device."
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .border(
                            width = 1.5.dp,
                            color = PocketColors.Primary.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable {
                            jarPickerLauncher.launch(arrayOf("application/java-archive", "*/*"))
                        }
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "📂  Select .JAR File From Device",
                        color = PocketColors.PrimaryDark,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun InstallStep(
    stepNumber: Int,
    title: String,
    description: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(PocketColors.PrimaryMuted, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$stepNumber",
                    color = PocketColors.PrimaryDark,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(text = title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    text = description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }
        // Indent the action to align with text
        Box(modifier = Modifier.padding(start = 40.dp)) {
            content()
        }
    }
}

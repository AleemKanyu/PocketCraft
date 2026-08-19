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
            verticalArrangement = Arrangement.spacedBy(22.dp)
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
                        isFabric -> "Download Fabric $version Server JAR"
                        else     -> "Download $version Server JAR"
                    },
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Download the server JAR automatically in your browser, then select the file from storage.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 19.sp
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

            // ── Step 1 – Download in Browser ─────────────────────────────────────
            InstallStep(
                stepNumber = 1,
                title = when {
                    isFabric -> "Download Official Fabric Server JAR"
                    else     -> "Download Official Server JAR"
                },
                description = if (isResolving) "Resolving official download URL…" else if (hasTriggeredDownload) "Download started in browser. Tap again if needed." else "Tap to download the official server JAR in your browser."
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(if (hasTriggeredDownload) 0.75f else downloadBtnAlpha)
                        .clip(RoundedCornerShape(14.dp))
                        .then(
                            if (hasTriggeredDownload) {
                                Modifier
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                    .border(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                            } else {
                                Modifier.background(
                                    Brush.horizontalGradient(
                                        listOf(PocketColors.Primary, Color(0xFF4CAF50))
                                    )
                                )
                            }
                        )
                        .then(
                            if (!isResolving) Modifier.clickable {
                                hasTriggeredDownload = true
                                runCatching {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                }
                            } else Modifier
                        )
                        .padding(vertical = 15.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isResolving) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                            Text(
                                text = "Resolving download URL…",
                                color = Color.White.copy(alpha = 0.7f),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        Text(
                            text = if (hasTriggeredDownload) "🌐  Download Again" else "🌐  Download in Browser",
                            color = if (hasTriggeredDownload) MaterialTheme.colorScheme.onSurfaceVariant else Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp
                        )
                    }
                }
            }

            // ── Step 2 – Select Downloaded JAR ───────────────────────────────────
            InstallStep(
                stepNumber = 2,
                title = "Select Downloaded JAR",
                description = "After downloading the file in your browser, choose it from your device storage."
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .then(
                            if (hasTriggeredDownload) {
                                Modifier
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(PocketColors.Primary, Color(0xFF4CAF50))
                                        )
                                    )
                            } else {
                                Modifier
                                    .border(
                                        width = 1.5.dp,
                                        color = PocketColors.Primary.copy(alpha = 0.45f),
                                        shape = RoundedCornerShape(14.dp)
                                    )
                            }
                        )
                        .clickable {
                            jarPickerLauncher.launch(arrayOf("application/java-archive", "*/*"))
                        }
                        .padding(vertical = 15.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (hasTriggeredDownload) "📂  Select File From Device ➜" else "📂  Select File From Device",
                        color = if (hasTriggeredDownload) Color.White else PocketColors.PrimaryDark,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp
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

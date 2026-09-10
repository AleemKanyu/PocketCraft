package com.pockethost.app.ui.components

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pockethost.app.ui.theme.PocketColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerModpackPickerBottomSheet(
    modpackId: String,
    pageUrl: String?,
    onDismiss: () -> Unit,
    onZipSelected: (Uri) -> Unit
) {
    val context = LocalContext.current
    val downloadUrl = pageUrl ?: "https://modrinth.com/modpacks"

    val zipPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onZipSelected(uri)
    }

    val coroutineScope = rememberCoroutineScope()
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var downloadStatusText by remember { mutableStateOf("") }
    var downloadError by remember { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
            // ── Header ────────────────────────────────────────────────────────────
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(PocketColors.PrimaryMuted)
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "MODPACK",
                        color = PocketColors.PrimaryDark,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.5.sp
                    )
                }
                Text(
                    text = "Import Modpack Server Pack",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Download the Modpack / Server Pack file in your browser, then select the file from storage.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 19.sp
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

            // ── Step 1 – Download in Browser ─────────────────────────────────────
            ModpackInstallStep(
                stepNumber = 1,
                title = "Download Modpack in Browser",
                description = if (hasTriggeredDownload) "Download started in browser. Tap again if needed." else "Tap to open the modpack download in your browser."
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(if (hasTriggeredDownload) 0.75f else 1f)
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
                        .clickable {
                            hasTriggeredDownload = true
                            runCatching {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            }
                        }
                        .padding(vertical = 15.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (hasTriggeredDownload) "Download Again" else "Download Modpack in Browser",
                        color = if (hasTriggeredDownload) MaterialTheme.colorScheme.onSurfaceVariant else Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp
                    )
                }
            }

            // ── Step 2 – Select Downloaded File ──────────────────────────────────
            ModpackInstallStep(
                stepNumber = 2,
                title = "Select Downloaded File",
                description = "After downloading in your browser, choose the .mrpack or Server Pack ZIP from your device storage."
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
                            zipPickerLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream", "*/*"))
                        }
                        .padding(vertical = 15.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (hasTriggeredDownload) "Select Modpack File From Device" else "Select Modpack File From Device",
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
private fun ModpackInstallStep(
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
        Box(modifier = Modifier.padding(start = 40.dp)) {
            content()
        }
    }
}

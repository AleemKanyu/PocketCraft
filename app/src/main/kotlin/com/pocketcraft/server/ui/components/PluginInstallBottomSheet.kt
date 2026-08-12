package com.pocketcraft.server.ui.components

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketcraft.server.service.PluginManager
import com.pocketcraft.server.ui.theme.PocketColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginInstallBottomSheet(
    itemName: String,
    itemPageUrl: String,
    pickerMimeTypes: Array<String>,
    onDismiss: () -> Unit,
    onFileSelected: (Uri) -> Unit,
    dependencies: List<PluginManager.ModDependency>,
    isLoadingDependencies: Boolean,
    contentTypeLabel: String,
    worldName: String,
    onDependencyFileSelected: (PluginManager.ModDependency, Uri, onComplete: () -> Unit) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Tracks which mod/dependency the file picker is currently targeting.
    // null means the main mod; otherwise it points to the specific ModDependency.
    var activeFilePickerTarget by remember { mutableStateOf<PluginManager.ModDependency?>(null) }
    var refreshCounter by remember { mutableStateOf(0) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val target = activeFilePickerTarget
            if (target == null) {
                onFileSelected(uri)
            } else {
                onDependencyFileSelected(target, uri) {
                    refreshCounter++
                }
            }
        }
    }

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
                        text = contentTypeLabel.uppercase(),
                        color = PocketColors.PrimaryDark,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.5.sp
                    )
                }
                Text(
                    text = "Install $itemName",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Download the $contentTypeLabel file, then select it from your device storage to install.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 19.sp
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

            // We wrap the steps in a Scrollable column since dependencies list might be long
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // ── Step 1 – Download Main ───────────────────────────────────────────
                item {
                    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
                    var isDownloading by remember { mutableStateOf(false) }
                    var downloadProgress by remember { mutableStateOf(0f) }
                    var downloadStatusText by remember { mutableStateOf("") }
                    var downloadError by remember { mutableStateOf<String?>(null) }

                    PluginInstallStep(
                        stepNumber = 1,
                        title = "Download $contentTypeLabel File",
                        description = when {
                            isDownloading -> "Downloading $contentTypeLabel directly: $downloadStatusText"
                            downloadError != null -> "Download error: $downloadError. Tap to retry."
                            else -> "Download the $contentTypeLabel file directly inside PocketCraft."
                        }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(PocketColors.Primary, Color(0xFF4CAF50))
                                    )
                                )
                                .clickable(enabled = !isDownloading) {
                                    isDownloading = true
                                    downloadError = null
                                    downloadProgress = 0f
                                    downloadStatusText = "Connecting…"

                                    val sanitizedName = itemName.replace(Regex("[^a-zA-Z0-9_-]"), "_")
                                    val fileName = "$sanitizedName.jar"

                                    coroutineScope.launch {
                                        val result = com.pocketcraft.server.network.InAppDownloader.downloadToPublicDownloads(
                                            context = context,
                                            url = itemPageUrl,
                                            fileName = fileName,
                                            onProgress = { bytesRead, total, pct ->
                                                downloadProgress = (pct / 100f).coerceIn(0f, 1f)
                                                val readMB = String.format("%.1f", bytesRead / (1024f * 1024f))
                                                val totalMB = if (total > 0) String.format("%.1f MB", total / (1024f * 1024f)) else "MB"
                                                downloadStatusText = "$readMB MB / $totalMB"
                                            }
                                        )

                                        isDownloading = false
                                        result.onSuccess { file ->
                                            activeFilePickerTarget = null
                                            onFileSelected(Uri.fromFile(file))
                                        }.onFailure { err ->
                                            downloadError = err.message ?: "Download failed"
                                        }
                                    }
                                }
                                .padding(vertical = 15.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isDownloading) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    CircularProgressIndicator(
                                        progress = { downloadProgress },
                                        modifier = Modifier.size(18.dp),
                                        color = Color.White,
                                        strokeWidth = 2.5.dp,
                                    )
                                    Text(
                                        text = "Downloading… (${(downloadProgress * 100).toInt()}%)",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            } else {
                                Text(
                                    text = if (downloadError != null) "⚡ Retry Direct Download" else "⬇  Download $contentTypeLabel In-App",
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                }

                // ── Step 2 – Select Main ─────────────────────────────────────────────
                item {
                    PluginInstallStep(
                        stepNumber = 2,
                        title = "Select Downloaded File",
                        description = "Come back after downloading the file and pick it from your device storage."
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .border(
                                    width = 1.5.dp,
                                    color = PocketColors.Primary.copy(alpha = 0.45f),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable {
                                    activeFilePickerTarget = null
                                    filePickerLauncher.launch(pickerMimeTypes)
                                }
                                .padding(vertical = 15.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "📂  Select Downloaded File",
                                color = PocketColors.PrimaryDark,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }

                // ── Required Dependencies ───────────────────────────────────────────
                if (isLoadingDependencies) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Text("Checking for required dependencies...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else if (dependencies.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Required Dependencies",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "This mod requires the following extra dependencies to work properly. Install each one by downloading and selecting the file:",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    items(dependencies) { dep ->
                        val isInstalled = remember(dep, refreshCounter) {
                            PluginManager.isDependencyInstalled(
                                context = context,
                                worldName = worldName,
                                slug = dep.slug,
                                title = dep.title,
                                projectId = dep.projectId
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = dep.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = dep.slug,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (isInstalled) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFE8F5E9))
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = "✓ Installed",
                                        color = Color(0xFF2E7D32),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Button(
                                        onClick = {
                                            runCatching {
                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(dep.pageUrl)))
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = PocketColors.PrimaryMuted,
                                            contentColor = PocketColors.PrimaryDark
                                        ),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text("⬇ Download", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            activeFilePickerTarget = dep
                                            filePickerLauncher.launch(pickerMimeTypes)
                                        },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, PocketColors.Primary.copy(alpha = 0.4f)),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = PocketColors.PrimaryDark
                                        ),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text("📂 Select", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PluginInstallStep(
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

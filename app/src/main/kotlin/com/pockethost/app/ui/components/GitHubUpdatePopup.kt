package com.pockethost.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pockethost.app.BuildConfig
import com.pockethost.app.ui.theme.Monocraft
import com.pockethost.app.ui.theme.PocketColors
import com.pockethost.app.update.ApkInstaller
import com.pockethost.app.update.GitHubRelease
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Sealed class representing the different states of the GitHub update flow.
 */
private sealed class UpdateState {
    data object Idle : UpdateState()
    data class Downloading(val progress: Float, val bytesDownloaded: Long, val totalBytes: Long) : UpdateState()
    data object Installing : UpdateState()
    data class Failed(val message: String) : UpdateState()
    data object NeedsPermission : UpdateState()
}

/**
 * A bottom sheet that shows GitHub release info and allows the user to
 * download and install the APK update in-app.
 *
 * @param release The GitHub release to offer for download.
 * @param onDismiss Called when the user dismisses the sheet.
 * @param onInstallStarted Called when the install intent has been launched successfully.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitHubUpdatePopup(
    release: GitHubRelease,
    onDismiss: () -> Unit,
    onInstallStarted: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var updateState by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }

    fun dismissWithAnimation() {
        if (updateState is UpdateState.Downloading) return // Don't dismiss while downloading
        scope.launch {
            sheetState.hide()
            onDismiss()
        }
    }

    fun startDownloadAndInstall() {
        scope.launch {
            updateState = UpdateState.Downloading(0f, 0, 0)

            val apkFile = withContext(Dispatchers.IO) {
                ApkInstaller.downloadApk(
                    context = context,
                    url = release.apkDownloadUrl,
                    onProgress = { bytesDownloaded, totalBytes, percentage ->
                        updateState = UpdateState.Downloading(
                            progress = if (percentage >= 0f) percentage / 100f else -1f,
                            bytesDownloaded = bytesDownloaded,
                            totalBytes = totalBytes
                        )
                    }
                )
            }

            if (apkFile == null) {
                updateState = UpdateState.Failed("Download failed. Please check your connection and try again.")
                return@launch
            }

            updateState = UpdateState.Installing

            if (!ApkInstaller.canInstallApks(context)) {
                updateState = UpdateState.NeedsPermission
                ApkInstaller.openInstallPermissionSettings(context)
                return@launch
            }

            val launched = ApkInstaller.installApk(context, apkFile)
            if (launched) {
                onInstallStarted()
            } else {
                updateState = UpdateState.Failed("Could not launch the installer. Please try again.")
            }
        }
    }

    fun retryInstall() {
        val cachedApk = ApkInstaller.getCachedApk(context)
        if (cachedApk != null && ApkInstaller.canInstallApks(context)) {
            updateState = UpdateState.Installing
            val launched = ApkInstaller.installApk(context, cachedApk)
            if (launched) {
                onInstallStarted()
            } else {
                updateState = UpdateState.Failed("Could not launch the installer.")
            }
        } else if (cachedApk != null) {
            updateState = UpdateState.NeedsPermission
            ApkInstaller.openInstallPermissionSettings(context)
        } else {
            startDownloadAndInstall()
        }
    }

    ModalBottomSheet(
        onDismissRequest = { dismissWithAnimation() },
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
            // Header icon
            Icon(
                imageVector = Icons.Default.SystemUpdate,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = PocketColors.PrimaryDark
            )

            // Title
            Text(
                text = "Update Available",
                fontFamily = Monocraft,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 22.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Version info
            Text(
                text = "v${BuildConfig.VERSION_NAME}  →  v${release.versionName}",
                fontFamily = Monocraft,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                color = PocketColors.PrimaryDark,
                fontWeight = FontWeight.Bold
            )

            // Release notes (if available)
            if (release.releaseNotes.isNotBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                ) {
                    Text(
                        text = "What's New",
                        fontFamily = Monocraft,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = release.releaseNotes,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp,
                        maxLines = 10,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Action area
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (val state = updateState) {
                    is UpdateState.Idle -> {
                        DuoButton(
                            text = "DOWNLOAD & INSTALL",
                            onClick = { startDownloadAndInstall() },
                            modifier = Modifier.fillMaxWidth()
                        )
                        TextButton(
                            onClick = { dismissWithAnimation() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Maybe Later",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                                fontFamily = Monocraft
                            )
                        }
                    }

                    is UpdateState.Downloading -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Downloading update...",
                                fontFamily = Monocraft,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (state.progress >= 0f) {
                                LinearProgressIndicator(
                                    progress = { state.progress },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = PocketColors.PrimaryDark,
                                    trackColor = PocketColors.PrimaryDark.copy(alpha = 0.15f)
                                )
                                Text(
                                    text = "${(state.progress * 100).toInt()}%  •  ${formatBytes(state.bytesDownloaded)} / ${formatBytes(state.totalBytes)}",
                                    fontSize = 11.sp,
                                    fontFamily = Monocraft,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            } else {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = PocketColors.PrimaryDark,
                                    trackColor = PocketColors.PrimaryDark.copy(alpha = 0.15f)
                                )
                            }
                        }
                    }

                    is UpdateState.Installing -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Launching installer...",
                                fontFamily = Monocraft,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = PocketColors.PrimaryDark,
                                trackColor = PocketColors.PrimaryDark.copy(alpha = 0.15f)
                            )
                        }
                    }

                    is UpdateState.Failed -> {
                        Text(
                            text = state.message,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        DuoButton(
                            text = "RETRY",
                            onClick = { startDownloadAndInstall() },
                            modifier = Modifier.fillMaxWidth()
                        )
                        TextButton(
                            onClick = { dismissWithAnimation() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Cancel",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                                fontFamily = Monocraft
                            )
                        }
                    }

                    is UpdateState.NeedsPermission -> {
                        Text(
                            text = "Please allow installing apps from this source, then tap the button below.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                        DuoButton(
                            text = "CONTINUE INSTALL",
                            onClick = { retryInstall() },
                            modifier = Modifier.fillMaxWidth()
                        )
                        TextButton(
                            onClick = { dismissWithAnimation() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Cancel",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                                fontFamily = Monocraft
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Formats byte counts into human-readable strings (KB, MB, etc.).
 */
private fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    }
}

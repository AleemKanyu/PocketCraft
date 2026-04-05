package com.pocketcraft.server.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.pocketcraft.server.ui.components.DuoButton
import com.pocketcraft.server.ui.theme.PocketColors
import com.pocketcraft.server.update.GitHubUpdateChecker
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppUpdateScreen(
    updateInfo: GitHubUpdateChecker.ReleaseInfo,
    isDownloading: Boolean,
    downloadProgress: Int,
    downloadStatus: String,
    isAwaitingInstallPermission: Boolean,
    errorMessage: String?,
    onUpdateNow: () -> Unit,
    onLater: () -> Unit,
    onOpenInstallSettings: () -> Unit,
    onInstallPermissionEnabled: () -> Unit,
    onDismissError: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isBusy = isDownloading || isAwaitingInstallPermission

    ModalBottomSheet(
        onDismissRequest = {
            if (isBusy) return@ModalBottomSheet
            scope.launch {
                sheetState.hide()
                onLater()
            }
        },
        sheetState = sheetState,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = PocketColors.PrimaryMuted,
                tonalElevation = 0.dp
            ) {
                Box(
                    modifier = Modifier.size(72.dp),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Filled.Update,
                        contentDescription = null,
                        modifier = Modifier.size(34.dp),
                        tint = PocketColors.PrimaryDark
                    )
                }
            }

            when {
                errorMessage != null -> {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "UPDATE FAILED",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PocketColors.Primary,
                            letterSpacing = 1.2.sp,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = errorMessage,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    DuoButton(
                        text = "TRY AGAIN",
                        icon = null,
                        onClick = onDismissError,
                        modifier = Modifier.fillMaxWidth()
                    )

                    TextButton(
                        onClick = {
                            scope.launch {
                                sheetState.hide()
                                onLater()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Later",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                isAwaitingInstallPermission -> {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "ALLOW INSTALL PERMISSION",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PocketColors.Primary,
                            letterSpacing = 1.2.sp,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Enable 'Install unknown apps' for PocketCraft. When you return, installer opens automatically.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    DuoButton(
                        text = "OPEN SETTINGS",
                        icon = null,
                        onClick = onOpenInstallSettings,
                        modifier = Modifier.fillMaxWidth()
                    )

                    TextButton(
                        onClick = onInstallPermissionEnabled,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "I enabled it",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                isDownloading -> {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "UPDATING POCKETCRAFT",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PocketColors.Primary,
                            letterSpacing = 1.2.sp,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = downloadStatus,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    LinearProgressIndicator(
                        progress = { (downloadProgress / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "$downloadProgress%",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }

                else -> {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "NEW UPDATE IS AVAILABLE",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PocketColors.Primary,
                            letterSpacing = 1.2.sp,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Update to version ${updateInfo.tagName}",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            lineHeight = 32.sp
                        )
                        Text(
                            text = "A new version of PocketCraft is available. Please update to the latest version to enjoy new features and improvements.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    DuoButton(
                        text = "UPDATE NOW",
                        icon = null,
                        onClick = onUpdateNow,
                        modifier = Modifier.fillMaxWidth()
                    )

                    TextButton(
                        onClick = {
                            scope.launch {
                                sheetState.hide()
                                onLater()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Later",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

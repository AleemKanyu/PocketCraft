package com.pocketcraft.server.ui.components

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
import com.pocketcraft.server.data.model.ServerType
import com.pocketcraft.server.data.preferences.AppPreferences
import com.pocketcraft.server.server.ServerTypeDownloadUrls
import com.pocketcraft.server.ui.theme.PocketColors

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

    val jarPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onJarSelected(uri)
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val downloadBtnAlpha by animateFloatAsState(
        targetValue = if (isResolving) 0.55f else 1f,
        animationSpec = tween(300),
        label = "dlBtnAlpha"
    )

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
                        else     -> "Install $version"
                    },
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = when {
                        isFabric -> "Tap below to download the Fabric server JAR directly — ready to import into PocketCraft."
                        else     -> "Download the server JAR, then bring it back here."
                    },
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 19.sp
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

            // ── Step 1 – Download ─────────────────────────────────────────────────
            InstallStep(
                stepNumber = 1,
                title = when {
                    isFabric -> "Download Fabric Server JAR"
                    else     -> "Download Server JAR"
                },
                description = when {
                    isResolving -> "Looking up the latest ${serverType.displayName} $version build…"
                    isFabric    -> "The Fabric server JAR will download directly to your Downloads folder. " +
                                   "It's ready to use — just pick it in Step 2 to import into PocketCraft."
                    else        -> "Tap to download directly — the file will be saved to your Downloads."
                }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(downloadBtnAlpha)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(PocketColors.Primary, Color(0xFF4CAF50))
                            )
                        )
                        .then(
                            if (!isResolving) Modifier.clickable {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl))
                                )
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
                                text = "Resolving download…",
                                color = Color.White.copy(alpha = 0.7f),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        Text(
                            text = "⬇  Download ${serverType.displayName} $version",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp
                        )
                    }
                }
            }

            // ── Step 2 – Select ───────────────────────────────────────────────────
            InstallStep(
                stepNumber = 2,
                title = "Select the Downloaded JAR",
                description = "Come back after downloading and choose the file from your device."
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
                            jarPickerLauncher.launch(arrayOf("application/java-archive", "*/*"))
                        }
                        .padding(vertical = 15.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "📂  Select Downloaded JAR",
                        color = PocketColors.PrimaryDark,
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

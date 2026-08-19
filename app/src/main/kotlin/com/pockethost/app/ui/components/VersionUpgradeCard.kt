package com.pockethost.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pockethost.app.ui.components.FlatEmojiIcon
import com.pockethost.app.ui.theme.PocketColors
import com.pockethost.app.ui.theme.pill3d

@Composable
fun VersionUpgradeCard(
    serverTypeName: String,
    currentVersion: String,
    availableVersions: List<String>,
    onUpgrade: (String) -> Unit,
    onInstallCurrent: (() -> Unit)? = null,
    serverIsRunning: Boolean,
    isVersionDownloaded: Boolean
) {
    GameCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 12.dp)
    ) {
        val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
        val isModpack = serverTypeName.equals("Modpack", ignoreCase = true)
        val action = {
            if (!isVersionDownloaded && onInstallCurrent != null) {
                onInstallCurrent()
            } else {
                onUpgrade(currentVersion)
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !serverIsRunning, onClick = action),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.SportsEsports,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = PocketColors.Primary
                )
                Column(modifier = Modifier.weight(1f)) {
                    val displayVersion = if (currentVersion.isBlank()) "Select Version" else "$serverTypeName $currentVersion"
                    Text(
                        text = displayVersion,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 18.sp
                    )
                    Text(
                        if (!isVersionDownloaded && isModpack) "Modpack not installed"
                        else if (!isVersionDownloaded) "No version downloaded"
                        else if (serverIsRunning) "Stop server to upgrade"
                        else if (isModpack) "Tap to change modpack"
                        else "Tap to change version",
                        fontSize = 12.sp,
                        color = if (!isVersionDownloaded) Color(0xFFFF7373) else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (!isVersionDownloaded) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            val actionText = if (isVersionDownloaded && isModpack) "Change"
                else if (isVersionDownloaded) "Upgrade"
                else if (isModpack) "Install"
                else "Download"
            Box(
                modifier = Modifier
                    .widthIn(min = 76.dp)
                    .pill3d(
                        elevation = 4.dp,
                        borderColor = if (serverIsRunning) {
                            if (isDark) PocketColors.CardBorderDark.copy(alpha = 0.56f) else PocketColors.InactiveBorder
                        } else if (!isVersionDownloaded) {
                            Color(0xFFFF8A8A)
                        } else {
                            if (isDark) PocketColors.CardBorderDark else PocketColors.TagBorder
                        },
                        depthColor = if (serverIsRunning) {
                            if (isDark) PocketColors.CardBorderBottomDark.copy(alpha = 0.66f) else PocketColors.InactiveBorderBottom
                        } else if (!isVersionDownloaded) {
                            Color(0xFFE05252)
                        } else {
                            if (isDark) PocketColors.CardBorderBottomDark else PocketColors.TagBorderBottom
                        },
                        borderWidth = 1.5.dp
                    )
                    .clip(RoundedCornerShape(50.dp))
                    .background(
                        if (serverIsRunning) {
                            if (isDark) PocketColors.SurfaceVarDark.copy(alpha = 0.76f) else PocketColors.InactiveBg
                        }
                        else if (!isVersionDownloaded) Color(0xFFFFE2E2)
                        else if (isDark) PocketColors.SurfaceVarDark else PocketColors.TagBg
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    actionText,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    color = if (serverIsRunning)
                        if (isDark) PocketColors.TextDark.copy(alpha = 0.52f) else PocketColors.TextMuted
                    else if (!isVersionDownloaded)
                        Color(0xFFFF5252)
                    else if (isDark) PocketColors.TextDark.copy(alpha = 0.82f) else PocketColors.TagText,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 11.sp,
                    letterSpacing = 0.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip
                )
            }
        }
    }

}

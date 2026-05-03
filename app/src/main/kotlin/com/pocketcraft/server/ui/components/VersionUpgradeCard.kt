package com.pocketcraft.server.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pocketcraft.server.ui.components.FlatEmojiIcon
import com.pocketcraft.server.ui.theme.PocketColors

@Composable
fun VersionUpgradeCard(
    serverTypeName: String,
    currentVersion: String,
    availableVersions: List<String>,
    onUpgrade: (String) -> Unit,
    serverIsRunning: Boolean,
    isVersionDownloaded: Boolean
) {
    GameCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !serverIsRunning) { onUpgrade(currentVersion) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FlatEmojiIcon("🎮", modifier = Modifier.size(28.dp), tint = PocketColors.PrimaryDark)
                Column {
                    val displayVersion = if (currentVersion.isBlank()) "Select Version" else "$serverTypeName $currentVersion"
                    Text(
                        text = displayVersion,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp
                    )
                    Text(
                        if (!isVersionDownloaded) "No version downloaded"
                        else if (serverIsRunning) "Stop server to upgrade"
                        else "Tap to change version",
                        fontSize = 12.sp,
                        color = if (!isVersionDownloaded) Color(0xFFFF5252) else MaterialTheme.colorScheme.onSurface.copy(0.5f),
                        fontWeight = if (!isVersionDownloaded) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(50),
                color = if (serverIsRunning)
                    MaterialTheme.colorScheme.surfaceVariant
                else if (!isVersionDownloaded)
                    Color(0xFFFF5252).copy(0.15f)
                else PocketColors.Primary.copy(0.15f)
            ) {
                Text(
                    if (isVersionDownloaded) "UPGRADE" else "DOWNLOAD",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    color = if (serverIsRunning)
                        MaterialTheme.colorScheme.onSurface.copy(0.3f)
                    else if (!isVersionDownloaded)
                        Color(0xFFFF5252)
                    else PocketColors.Primary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp
                )
            }
        }
    }

}

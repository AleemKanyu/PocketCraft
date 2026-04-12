package com.pocketcraft.server.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketcraft.server.ui.screens.ServerStatus
import com.pocketcraft.server.ui.theme.PocketColors

/**
 * Status badge overlay that displays server status in corner without affecting layout.
 * Uses pulsing animation for ONLINE status, static background for others.
 */
@Composable
fun StatusBadge(
    status: ServerStatus,
    bedrockBridgeEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        // Pulsing status indicator dot (top-right corner)
        val statusColor = when (status) {
            ServerStatus.ONLINE -> PocketColors.Online
            ServerStatus.STARTING -> PocketColors.Starting
            ServerStatus.OFFLINE -> MaterialTheme.colorScheme.onSurfaceVariant
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 8.dp, top = 8.dp)
                .size(20.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(statusColor)
                .border(3.dp, MaterialTheme.colorScheme.surface, RoundedCornerShape(7.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (status == ServerStatus.ONLINE) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White)
                )
            }
        }

        // Status text badge (positioned left of the dot)
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 34.dp, top = 8.dp),
            shape = RoundedCornerShape(10.dp),
            color = when (status) {
                                ServerStatus.ONLINE -> PocketColors.PrimaryMuted
                ServerStatus.STARTING -> PocketColors.Starting.copy(alpha = 0.18f)
                ServerStatus.OFFLINE -> PocketColors.Offline.copy(alpha = 0.14f)
            }
        ) {
            Text(
                text = status.name,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                color = when (status) {
                    ServerStatus.ONLINE -> PocketColors.PrimaryDark
                    ServerStatus.STARTING -> PocketColors.Starting
                    ServerStatus.OFFLINE -> PocketColors.Offline
                },
                fontWeight = FontWeight.ExtraBold,
                fontSize = 10.sp,
                letterSpacing = 0.8.sp
            )
        }

    }
}

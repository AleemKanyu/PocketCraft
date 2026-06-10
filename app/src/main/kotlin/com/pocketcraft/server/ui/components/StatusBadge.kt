package com.pocketcraft.server.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketcraft.server.ui.screens.ServerStatus
import com.pocketcraft.server.ui.theme.PocketColors
import com.pocketcraft.server.ui.theme.card3d

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
            ServerStatus.STARTING, ServerStatus.RESTARTING -> PocketColors.Starting
            ServerStatus.OFFLINE -> MaterialTheme.colorScheme.onSurfaceVariant
        }

        val pulse = rememberInfiniteTransition(label = "online_status_pulse")
        val ringAlpha by pulse.animateFloat(
            initialValue = if (status == ServerStatus.ONLINE) 0.45f else 0f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1800),
                repeatMode = RepeatMode.Restart
            ),
            label = "online_status_alpha"
        )
        val ringScale by pulse.animateFloat(
            initialValue = 1f,
            targetValue = if (status == ServerStatus.ONLINE) 2.1f else 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1800),
                repeatMode = RepeatMode.Restart
            ),
            label = "online_status_scale"
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 8.dp, top = 8.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(statusColor)
                .border(3.dp, MaterialTheme.colorScheme.surface, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (status == ServerStatus.ONLINE) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .scale(ringScale)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = ringAlpha))
                )
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            }
        }

        // Status text badge (positioned left of the dot)
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 34.dp, top = 8.dp)
                .card3d(elevation = 4.dp, cornerRadius = 10.dp),
            shape = RoundedCornerShape(10.dp),
            shadowElevation = 0.dp,
            color = when (status) {
                ServerStatus.ONLINE -> PocketColors.PrimaryMuted
                ServerStatus.STARTING, ServerStatus.RESTARTING -> PocketColors.Starting.copy(alpha = 0.18f)
                ServerStatus.OFFLINE -> PocketColors.Offline.copy(alpha = 0.14f)
            }
        ) {
            Text(
                text = status.name,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                color = when (status) {
                    ServerStatus.ONLINE -> PocketColors.PrimaryDark
                    ServerStatus.STARTING, ServerStatus.RESTARTING -> PocketColors.Starting
                    ServerStatus.OFFLINE -> PocketColors.Offline
                },
                fontWeight = FontWeight.ExtraBold,
                fontSize = 10.sp,
                letterSpacing = 0.8.sp
            )
        }

    }
}

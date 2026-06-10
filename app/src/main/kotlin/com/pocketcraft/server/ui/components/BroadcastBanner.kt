package com.pocketcraft.server.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import com.pocketcraft.server.ui.theme.card3d
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketcraft.server.broadcast.BroadcastMessage
import com.pocketcraft.server.ui.theme.PocketColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BroadcastBanner(
    message: BroadcastMessage,
    onDismiss: () -> Unit,
    enableDetailsSheet: Boolean = true,
    outerPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
    modifier: Modifier = Modifier
) {
    fun normalizeBroadcastBody(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return trimmed
        val discordRegex = Regex("(https?://(?:www\\.)?(?:discord\\.gg|discord\\.com/invite)/\\S+)", RegexOption.IGNORE_CASE)
        val match = discordRegex.find(trimmed) ?: return trimmed
        if (trimmed.contains("join our discord server", ignoreCase = true)) return trimmed
        val link = match.value
        val withoutLink = trimmed.replace(link, "").trim().trimEnd('-', ':')
        return buildString {
            if (withoutLink.isNotBlank()) {
                append(withoutLink)
                append('\n')
            }
            append("Join our Discord server:")
            append('\n')
            append(link)
        }
    }

    val normalizedBody = remember(message.body) { normalizeBroadcastBody(message.body) }
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val (accent, container, headline) = when (message.type) {
        "critical" -> Triple(PocketColors.ConsoleError, PocketColors.ConsoleError.copy(alpha = 0.1f), "Critical Broadcast")
        "warning" -> Triple(PocketColors.ConsoleWarn, PocketColors.ConsoleWarn.copy(alpha = 0.14f), "Important Broadcast")
        else -> Triple(PocketColors.PrimaryDark, PocketColors.Primary.copy(alpha = 0.12f), "Broadcast")
    }
    val bannerColor = when (message.type) {
        "critical" -> if (isDarkTheme) Color(0xFF2F1A22) else Color(0xFFFFE1E7)
        "warning" -> if (isDarkTheme) Color(0xFF3B2214) else Color(0xFFFFE8CC)
        else -> if (isDarkTheme) Color(0xFF342017) else Color(0xFFFFE7D4)
    }
    val bannerBorder = when (message.type) {
        "critical" -> if (isDarkTheme) PocketColors.ConsoleError.copy(alpha = 0.42f) else Color(0xFFFFB3C1)
        "warning" -> if (isDarkTheme) Color(0xFFE6853B).copy(alpha = 0.55f) else Color(0xFFEA580C).copy(alpha = 0.4f)
        else -> if (isDarkTheme) Color(0xFFE69A5E).copy(alpha = 0.48f) else Color(0xFFEA7A35).copy(alpha = 0.32f)
    }
    val headingColor = when (message.type) {
        "critical" -> if (isDarkTheme) PocketColors.TextDark else Color(0xFF7F1D1D)
        "warning" -> if (isDarkTheme) Color(0xFFFFD6B0) else Color(0xFF78350F)
        else -> if (isDarkTheme) Color(0xFFFFD9BF) else Color(0xFF9A4F1A)
    }
    val bodyColor = when (message.type) {
        "critical" -> if (isDarkTheme) PocketColors.TextDark.copy(alpha = 0.84f) else Color(0xFF991B1B)
        "warning" -> if (isDarkTheme) Color(0xFFFFE7D6).copy(alpha = 0.88f) else Color(0xFF92400E)
        else -> if (isDarkTheme) Color(0xFFFFE9DA).copy(alpha = 0.84f) else Color(0xFF9A4F1A)
    }
    val chipColor = when (message.type) {
        "critical" -> if (isDarkTheme) PocketColors.ConsoleError.copy(alpha = 0.18f) else Color(0xFFFFD5DB)
        "warning" -> if (isDarkTheme) Color(0xFF5A331A) else Color(0xFFFEDBA8)
        else -> if (isDarkTheme) Color(0xFF5B311D) else Color(0xFFFFD9BF)
    }
    val icon = if (message.type == "info") Icons.Default.Info else Icons.Default.Warning
    var showDetails by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AnimatedVisibility(
        visible = message.title.isNotBlank() || message.body.isNotBlank(),
        enter = slideInVertically(initialOffsetY = { -it / 2 }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it / 2 }) + fadeOut()
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .padding(outerPadding)
                .card3d(
                    elevation = 4.dp,
                    cornerRadius = 16.dp,
                    borderColor = bannerBorder,
                    depthColor = bannerBorder.copy(alpha = (bannerBorder.alpha * 1.35f).coerceAtMost(1f))
                )
                .let { base -> if (enableDetailsSheet) base.clickable { showDetails = true } else base },
            shape = RoundedCornerShape(16.dp),
            color = bannerColor,
            shadowElevation = 0.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(20.dp)
                )

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = message.title.ifBlank { headline },
                            color = headingColor,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = chipColor
                        ) {
                            Text(
                                text = "From Dev",
                                color = headingColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (normalizedBody.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = normalizedBody,
                            color = bodyColor,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (message.dismissible) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = headingColor,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }

    if (showDetails && enableDetailsSheet) {
        val detailsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = {
                scope.launch {
                    detailsSheetState.hide()
                    showDetails = false
                }
            },
            sheetState = detailsSheetState,
            dragHandle = null,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = container,
                        border = BorderStroke(1.dp, accent.copy(alpha = 0.3f))
                    ) {
                        Box(modifier = Modifier.padding(8.dp), contentAlignment = Alignment.Center) {
                            Icon(imageVector = icon, contentDescription = null, tint = accent)
                        }
                    }
                    Text(
                        text = message.title.ifBlank { headline },
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp
                    )
                }

                if (normalizedBody.isNotBlank()) {
                    Text(
                        text = normalizedBody,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 22.sp,
                        fontSize = 15.sp
                    )
                }

                if (message.dismissible) {
                    DuoButton(
                        text = "DISMISS",
                        icon = null,
                        onClick = {
                            scope.launch {
                                detailsSheetState.hide()
                                showDetails = false
                                onDismiss()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    TextButton(
                        onClick = {
                            scope.launch {
                                detailsSheetState.hide()
                                showDetails = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

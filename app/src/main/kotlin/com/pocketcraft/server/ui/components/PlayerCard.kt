package com.pocketcraft.server.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.pocketcraft.server.ui.theme.PocketColors

data class PlayerCardAction(
    val label: String,
    val onClick: () -> Unit,
    val tint: Color? = null
)

/**
 * Resolves the best avatar URL for a player.
 * Prefers Crafatar (UUID-based, works reliably for online-mode Mojang players).
 * Falls back to mc-heads by username for offline/Bedrock players who lack a UUID.
 */
fun resolvePlayerAvatarUrl(username: String, uuid: String? = null, size: Int = 64): String {
    return if (!uuid.isNullOrBlank()) {
        "https://crafatar.com/avatars/${uuid}?size=${size}&overlay"
    } else {
        "https://mc-heads.net/avatar/${username}/${size}"
    }
}

@Composable
fun PlayerCard(
    username: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    avatarUrl: String? = null,
    uuid: String? = null,
    badgeText: String? = null,
    badgeColor: Color = PocketColors.Primary,
    onClick: (() -> Unit)? = null,
    actions: List<PlayerCardAction> = emptyList(),
    trailingContent: (@Composable () -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }

    GameCard(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AsyncImage(
                model = avatarUrl ?: resolvePlayerAvatarUrl(username, uuid, 64),
                contentDescription = "$username avatar",
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = username,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    if (badgeText != null) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = badgeColor.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = badgeText,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                color = badgeColor,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 9.sp,
                                letterSpacing = 0.8.sp
                            )
                        }
                    }
                }
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (trailingContent != null) {
                trailingContent()
            } else if (actions.isNotEmpty()) {
                Box {
                    IconButton(onClick = { expanded = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Player actions",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    PocketDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        actions.forEach { action ->
                            PocketDropdownMenuItem(
                                text = {
                                    Text(
                                        text = action.label,
                                        color = action.tint ?: MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                onClick = {
                                    expanded = false
                                    action.onClick()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

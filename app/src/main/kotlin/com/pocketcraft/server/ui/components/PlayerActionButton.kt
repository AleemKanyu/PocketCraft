package com.pocketcraft.server.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.pocketcraft.server.ui.theme.ButtonFont
import com.pocketcraft.server.ui.theme.button3d
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Action types for player actions with associated colors and shadow tones.
 * Matches Duolingo-style 3D button design.
 */
enum class PlayerActionType(
    val backgroundColor: Color,
    val shadowColor: Color
) {
    DAMAGE(
        backgroundColor = Color(0xFFFF6B6B),  // Red
        shadowColor = Color(0xFFE63946)       // Dark Red
    ),
    HEAL(
        backgroundColor = Color(0xFF4CAF50),  // Green
        shadowColor = Color(0xFF2E7D32)       // Dark Green
    ),
    STARVE(
        backgroundColor = Color(0xFFFF9800),  // Orange
        shadowColor = Color(0xFFE65100)       // Dark Orange
    ),
    FEED(
        backgroundColor = Color(0xFF2196F3),  // Blue
        shadowColor = Color(0xFF1565C0)       // Dark Blue
    )
}

/**
 * Player action button with Duolingo-style 3D shadow effect.
 * Used for Kill, Heal, Starve, Feed actions in player detail screen.
 */
@Composable
fun PlayerActionButton(
    label: String,
    actionType: PlayerActionType,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val restingBorder = 3.dp
    val offsetY by animateDpAsState(
        targetValue = if (pressed && enabled) (restingBorder - 1.5.dp) else 0.dp,
        animationSpec = tween(80),
        label = "action_button_offset"
    )
    val alpha = if (enabled) 1f else 0.55f
    val shape = RoundedCornerShape(18.dp)

    Box(
        modifier = modifier
            .padding(bottom = 4.dp)
            .offset(y = offsetY)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .button3d(
                    elevation = 8.dp,
                    borderColor = actionType.shadowColor.copy(alpha = alpha),
                    depthColor = actionType.shadowColor.copy(alpha = alpha)
                )
                .clip(shape)
                .background(actionType.backgroundColor.copy(alpha = alpha))
                .clickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontFamily = ButtonFont,
                    fontWeight = FontWeight.Normal,
                    fontSize = 14.sp,
                    letterSpacing = 0.sp
                ),
                maxLines = 2,
                softWrap = true,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

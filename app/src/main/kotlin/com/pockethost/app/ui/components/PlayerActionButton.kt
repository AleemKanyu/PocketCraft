package com.pockethost.app.ui.components

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
import com.pockethost.app.ui.theme.ButtonFont
import com.pockethost.app.ui.theme.button3d
import com.pockethost.app.ui.theme.card3d
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
        backgroundColor = Color(0xFFE53935),  // Vibrant Red
        shadowColor = Color(0xFFB71C1C)       // Deep Red 3D Shadow
    ),
    HEAL(
        backgroundColor = Color(0xFF43A047),  // Vibrant Green
        shadowColor = Color(0xFF1B5E20)       // Deep Green 3D Shadow
    ),
    STARVE(
        backgroundColor = Color(0xFFFB8C00),  // Vibrant Orange
        shadowColor = Color(0xFFE65100)       // Deep Orange 3D Shadow
    ),
    FEED(
        backgroundColor = Color(0xFF1E88E5),  // Vibrant Blue
        shadowColor = Color(0xFF0D47A1)       // Deep Blue 3D Shadow
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
    val cornerRadius = 14.dp
    val shape = RoundedCornerShape(cornerRadius)
    val alpha = if (enabled) 1f else 0.55f

    val offsetY by animateDpAsState(
        targetValue = if (pressed && enabled) 3.dp else 0.dp,
        animationSpec = tween(80),
        label = "action_button_offset"
    )

    Box(
        modifier = modifier
            .padding(bottom = if (pressed && enabled) 0.dp else 3.dp)
            .offset(y = offsetY)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .card3d(
                    elevation = if (pressed && enabled) 2.dp else 5.dp,
                    cornerRadius = cornerRadius,
                    borderColor = actionType.shadowColor.copy(alpha = alpha),
                    depthColor = actionType.shadowColor.copy(alpha = alpha),
                    borderWidth = 1.5.dp
                )
                .clip(shape)
                .background(actionType.backgroundColor.copy(alpha = alpha))
                .clickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontFamily = ButtonFont,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    letterSpacing = 0.5.sp
                ),
                maxLines = 1,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

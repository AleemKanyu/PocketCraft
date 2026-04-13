package com.pocketcraft.server.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketcraft.server.ui.theme.PocketColors
import com.pocketcraft.server.ui.theme.Monocraft

enum class DuoButtonVariant { Primary, Danger }

@Composable
fun DuoButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    variant: DuoButtonVariant = DuoButtonVariant.Primary,
    isLoading: Boolean = false
) {
    val background = when (variant) {
        DuoButtonVariant.Primary -> PocketColors.PrimaryLight
        DuoButtonVariant.Danger -> PocketColors.Danger
    }
    val shadow = when (variant) {
        DuoButtonVariant.Primary -> Color(0xFF58CC02)
        DuoButtonVariant.Danger -> Color(0xFFC33542)
    }
    val contentColor = Color.Black
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val offsetY by animateDpAsState(
        targetValue = if (pressed && enabled) 4.dp else 0.dp,
        label = "duo_button_offset"
    )
    val alpha = if (enabled) 1f else 0.55f
    val shape = RoundedCornerShape(18.dp)

    val infiniteTransition = rememberInfiniteTransition(label = "loading_rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Box(
        modifier = modifier
            .padding(bottom = 8.dp)
            .offset(y = offsetY)
    ) {
        if (!pressed) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .offset(y = 6.dp)
                    .clip(shape)
                    .background(shadow.copy(alpha = alpha))
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(background.copy(alpha = alpha))
                .border(2.dp, shadow.copy(alpha = alpha), shape)
                .clickable(
                    enabled = enabled && !isLoading,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (icon != null || isLoading) {
                    val iconToShow = icon ?: Icons.Default.Refresh
                    Icon(
                        imageVector = iconToShow,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier
                            .size(24.dp)
                            .then(
                                if (isLoading) Modifier.rotate(rotation) else Modifier
                            )
                    )
                    androidx.compose.foundation.layout.Spacer(Modifier.size(10.dp))
                }
                Text(
                    text = text,
                    color = contentColor,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily = Monocraft,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp,
                        letterSpacing = 0.4.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

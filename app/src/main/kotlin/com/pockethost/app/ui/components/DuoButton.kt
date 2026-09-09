package com.pockethost.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Brush
import com.pockethost.app.ui.theme.ButtonFont
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pockethost.app.ui.theme.PocketMotion
import com.pockethost.app.ui.theme.PocketColors
import com.pockethost.app.ui.theme.button3d

enum class DuoButtonVariant { StartServer, Primary, Secondary, Danger, Warning, Info, Discord, Pro, SecondaryGray }

@Composable
fun DuoButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconContent: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    variant: DuoButtonVariant = DuoButtonVariant.Primary,
    isLoading: Boolean = false,
    fillMaxWidth: Boolean = true,
    minHeight: Dp = 56.dp,
    backgroundBrush: Brush? = null,
    fontWeight: FontWeight = FontWeight.ExtraBold
) {
    // ── Colors ──────────────────────────────────────────────────────────────
    val bgColor: Color
    val borderColor: Color
    val bottomBorderColor: Color
    val contentColor: Color

    if (!enabled) {
        bgColor = PocketColors.InactiveBg.copy(alpha = 0.72f)
        borderColor = PocketColors.InactiveBorder.copy(alpha = 0.52f)
        bottomBorderColor = PocketColors.InactiveBorderBottom.copy(alpha = 0.62f)
        contentColor = PocketColors.InactiveText.copy(alpha = 0.48f)
    } else {
        when (variant) {
            DuoButtonVariant.StartServer -> {
                bgColor = PocketColors.Primary
                borderColor = PocketColors.PrimaryBorder
                bottomBorderColor = PocketColors.PrimaryBorderBottom
                contentColor = PocketColors.PrimaryText
            }
            DuoButtonVariant.Primary -> {
                bgColor = PocketColors.Primary
                borderColor = PocketColors.PrimaryBorder
                bottomBorderColor = PocketColors.PrimaryBorderBottom
                contentColor = PocketColors.PrimaryText
            }
            DuoButtonVariant.Secondary -> {
                bgColor = Color(0xFF58CC02)
                borderColor = Color(0xFF4BAC02)
                bottomBorderColor = Color(0xFF3B8801)
                contentColor = Color.White
            }
            DuoButtonVariant.Danger -> {
                bgColor = PocketColors.DangerBg
                borderColor = PocketColors.DangerBorder
                bottomBorderColor = PocketColors.DangerBorderBottom
                contentColor = PocketColors.DangerText
            }
            DuoButtonVariant.Warning -> {
                bgColor = Color(0xFFF59E0B)
                borderColor = Color(0xFFD97706)
                bottomBorderColor = Color(0xFF92400E)
                contentColor = Color.White
            }
            DuoButtonVariant.Info -> {
                bgColor = Color(0xFF2F80ED)
                borderColor = Color(0xFF1B5EAA)
                bottomBorderColor = Color(0xFF113867)
                contentColor = Color.White
            }
            DuoButtonVariant.Discord -> {
                bgColor = Color(0xFF5865F2)
                borderColor = Color(0xFF404EED)
                bottomBorderColor = Color(0xFF303BBA)
                contentColor = Color.White
            }
            DuoButtonVariant.Pro -> {
                bgColor = Color(0xFF8C4DFF)
                borderColor = Color(0xFF753CE0)
                bottomBorderColor = Color(0xFF5A25B8)
                contentColor = Color.White
            }
            DuoButtonVariant.SecondaryGray -> {
                bgColor = Color(0xFFE0E0E0)
                borderColor = Color(0xFFBDBDBD)
                bottomBorderColor = Color(0xFF9E9E9E)
                contentColor = Color(0xFF616161)
            }
        }
    }

    // ── Press state ──────────────────────────────────────────────────────────
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    
    val restingBorder = 3.dp
    val targetBorder = if (pressed && enabled) 2.dp else restingBorder
    val targetOffset = if (pressed && enabled) (restingBorder - 2.dp) else 0.dp

    val offsetY by animateDpAsState(
        targetValue  = targetOffset,
        animationSpec = PocketMotion.softDpTween(durationMillis = 170),
        label = "duo_btn_offset"
    )
    val bottomBorderDp by animateDpAsState(
        targetValue  = targetBorder,
        animationSpec = PocketMotion.softDpTween(durationMillis = 170),
        label = "duo_btn_bottom_border"
    )

    // ── Loading spinner ──────────────────────────────────────────────────────
    val infiniteTransition = rememberInfiniteTransition(label = "loading_rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue  = 360f,
        animationSpec = infiniteRepeatable(
            animation    = tween(1000, easing = LinearEasing),
            repeatMode   = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val shape = RoundedCornerShape(50.dp) // pill shape

    Box(
        modifier = modifier
            .padding(bottom = 4.dp)  // space for bottom border overhang
            .offset(y = offsetY)
    ) {
        Box(
            modifier = (if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier)
                .heightIn(min = minHeight)
                .button3d(
                    elevation = when (variant) {
                        DuoButtonVariant.StartServer, DuoButtonVariant.Primary -> 8.dp
                        else -> 6.dp
                    },
                    borderColor = borderColor,
                    depthColor = bottomBorderColor,
                    depthWidth = bottomBorderDp
                )
                .clip(shape)
                .then(
                    if (backgroundBrush != null && enabled) {
                        Modifier.background(backgroundBrush)
                    } else {
                        Modifier.background(bgColor)
                    }
                )
                .clickable(
                    enabled           = enabled && !isLoading,
                    interactionSource = interactionSource,
                    indication        = null,
                    onClick           = onClick
                )
        ) {
            val verticalPadding = if (minHeight < 48.dp) 10.dp else 16.dp
            val iconSize = if (minHeight < 48.dp) 16.dp else 20.dp
            val iconGap = if (minHeight < 48.dp) 6.dp else 10.dp
            Row(
                modifier = (if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier)
                    .align(Alignment.Center)
                    .padding(horizontal = 22.dp, vertical = verticalPadding),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (iconContent != null || icon != null || isLoading) {
                    if (iconContent != null) {
                        iconContent()
                    } else {
                        val iconToShow = icon ?: Icons.Default.Refresh
                        Icon(
                            imageVector = iconToShow,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier
                                .size(iconSize)
                                .then(if (isLoading) Modifier.rotate(rotation) else Modifier)
                        )
                    }
                    androidx.compose.foundation.layout.Spacer(Modifier.size(iconGap))
                }
                Text(
                    text = text,
                    color = contentColor,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily = ButtonFont,
                        fontWeight = fontWeight,
                        fontSize = if (minHeight < 48.dp) 12.sp else 14.sp,
                        letterSpacing = 0.sp
                    ),
                    maxLines = 2,
                    softWrap = true,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                if (iconContent != null || icon != null || isLoading) {
                    androidx.compose.foundation.layout.Spacer(Modifier.size(iconGap))
                    Box(modifier = Modifier.size(iconSize))
                }
            }
        }
    }
}

package com.pockethost.app.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import com.pockethost.app.ui.theme.PocketColors
import com.pockethost.app.ui.theme.card3d

@Composable
fun DuoToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val trackShape = RoundedCornerShape(12.dp)
    val thumbOffset = animateDpAsState(
        targetValue = if (checked) 24.dp else 0.dp,
        label = "duo_toggle_offset"
    )
    val trackColor = when {
        !enabled -> PocketColors.InactiveBg.copy(alpha = 0.6f)
        checked  -> PocketColors.Primary
        else     -> PocketColors.InactiveBg
    }
    val sideColor = when {
        !enabled -> PocketColors.InactiveBorder.copy(alpha = 0.45f)
        checked  -> PocketColors.PrimaryBorder
        else     -> PocketColors.InactiveBorder
    }
    val bottomColor = when {
        !enabled -> sideColor
        checked  -> PocketColors.PrimaryBorderBottom
        else     -> PocketColors.InactiveBorderBottom
    }
    val thumbColor = if (enabled) PocketColors.PrimaryText else PocketColors.PrimaryText.copy(alpha = 0.5f)

    Box(
        modifier = modifier
            .size(width = 52.dp, height = 30.dp)
            .card3d(
                elevation = 4.dp,
                cornerRadius = 12.dp,
                borderColor = sideColor,
                depthColor = bottomColor
            )
            .clip(trackShape)
            .background(trackColor)
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 3.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset.value)
                .size(width = 20.dp, height = 18.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(thumbColor)
        )
    }
}

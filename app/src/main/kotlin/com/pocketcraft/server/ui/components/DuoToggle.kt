package com.pocketcraft.server.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import com.pocketcraft.server.ui.theme.PocketColors

@Composable
fun DuoToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val trackShape = RoundedCornerShape(12.dp)
    val colorScheme = MaterialTheme.colorScheme
    val thumbOffset = animateDpAsState(
        targetValue = if (checked) 24.dp else 0.dp,
        label = "duo_toggle_offset"
    )
    val trackColor = when {
        !enabled -> colorScheme.surfaceVariant.copy(alpha = 0.6f)
        checked -> PocketColors.Primary
        else -> colorScheme.surfaceVariant
    }
    val borderColor = when {
        !enabled -> colorScheme.outline.copy(alpha = 0.45f)
        checked -> Color(0xFF4AA502)
        else -> colorScheme.outline.copy(alpha = 0.8f)
    }
    val thumbColor = if (enabled) {
        colorScheme.surface
    } else {
        colorScheme.surface.copy(alpha = 0.7f)
    }

    Box(
        modifier = modifier
            .size(width = 52.dp, height = 30.dp)
            .clip(trackShape)
            .background(trackColor)
            .border(2.dp, borderColor, trackShape)
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
                .border(1.5.dp, borderColor.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
        )
    }
}

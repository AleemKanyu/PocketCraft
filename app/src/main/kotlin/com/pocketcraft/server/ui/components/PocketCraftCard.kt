package com.pocketcraft.server.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pocketcraft.server.ui.theme.PocketColors
import com.pocketcraft.server.ui.theme.card3d

@Composable
fun PocketCraftCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(cornerRadius),
    colors: CardColors = CardDefaults.cardColors(containerColor = Color.Transparent),
    elevation: CardElevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    border: androidx.compose.foundation.BorderStroke? = null,
    containerColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val bgColor = containerColor ?: if (isDark) PocketColors.SurfaceCardDark else PocketColors.SurfaceCard

    val finalModifier = modifier
        .then(
            if (border == null) {
                Modifier.card3d(elevation = 6.dp, cornerRadius = cornerRadius)
            } else {
                Modifier.border(border, shape)
            }
        )
        .clip(shape)
        .background(bgColor)

    Column(
        modifier = finalModifier,
        content = content
    )
}

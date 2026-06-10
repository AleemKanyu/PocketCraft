package com.pocketcraft.server.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.pocketcraft.server.ui.theme.PocketColors
import com.pocketcraft.server.ui.theme.card3d

/**
 * GameCard — raised-border card matching the HTML reference design.
 *
 * Light: bg=#F0F8E8  side=#b0cc98 1.5dp  bottom=#90b878 3dp  r=18dp
 * Dark:  bg=#1C331E  side=#3A6A3D 1.5dp  bottom=#244D27 3dp
 *
 * NO blur drop shadow — depth is purely from the thicker bottom border.
 */
@Composable
fun GameCard(
    modifier: Modifier = Modifier,
    containerColor: Color = Color.Unspecified,
    contentPadding: PaddingValues = PaddingValues(14.dp),
    elevation: androidx.compose.ui.unit.Dp = 0.dp,  // unused, kept for API compat
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    val bgColor = when {
        containerColor != Color.Unspecified -> containerColor
        isDark -> PocketColors.SurfaceCardDark
        else   -> PocketColors.SurfaceCard          // #F0F8E8
    }
    val shape = RoundedCornerShape(18.dp)

    Box(
        modifier = modifier
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness    = Spring.StiffnessLow
                )
            )
            .card3d(elevation = 6.dp, cornerRadius = 18.dp)
            .clip(shape)
            .background(bgColor)
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content  = content
        )
    }
}

package com.pockethost.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.pockethost.app.ui.theme.PocketColors

@Composable
fun PocketDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    properties: PopupProperties = PopupProperties(focusable = true),
    content: @Composable ColumnScope.() -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = Modifier,
        offset = offset,
        properties = properties,
        shape = RoundedCornerShape(26.dp),
        containerColor = Color.Transparent,
        shadowElevation = 0.dp
    ) {
        PocketDropdownContainer(
            expanded = expanded,
            modifier = modifier,
            content = content
        )
    }
}

@Composable
fun PocketDropdownContainer(
    expanded: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(expanded) {
        if (expanded) {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        } else {
            progress.snapTo(0f)
        }
    }

    val isDark = com.pockethost.app.ui.theme.pocketIsDarkTheme()

    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = 0.82f + 0.18f * progress.value
                scaleY = 0.82f + 0.18f * progress.value
                alpha = progress.value.coerceIn(0f, 1f)
                transformOrigin = TransformOrigin(0.5f, 0f)
            }
            .shadow(
                elevation = 28.dp,
                shape = RoundedCornerShape(24.dp),
                ambientColor = Color.Black.copy(alpha = 0.22f),
                spotColor = Color.Black.copy(alpha = 0.38f)
            )
            .clip(RoundedCornerShape(24.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        PocketColors.SurfaceCard.copy(alpha = 0.99f),
                        PocketColors.SurfaceHover.copy(alpha = 0.99f)
                    )
                )
            )
            .border(
                width = 1.dp,
                color = PocketColors.CardBorder.copy(alpha = 0.85f),
                shape = RoundedCornerShape(24.dp)
            )
            .padding(vertical = 6.dp)
            .width(IntrinsicSize.Max)
            .then(modifier)
    ) {
        Column {
            content()
        }
    }
}

@Composable
fun PocketDropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (leadingIcon != null) {
                Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                    leadingIcon()
                }
            }
            Box(modifier = Modifier.weight(1f)) {
                text()
            }
            if (trailingIcon != null) {
                Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                    trailingIcon()
                }
            }
        }
    }
}

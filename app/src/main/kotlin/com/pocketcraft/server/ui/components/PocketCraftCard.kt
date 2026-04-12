package com.pocketcraft.server.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.pocketcraft.server.ui.theme.pocketCardShadowColor

@Composable
fun PocketCraftCard(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(12.dp),
    colors: CardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface
    ),
    elevation: CardElevation = CardDefaults.cardElevation(
        defaultElevation = 0.dp
    ),
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shadowColor = pocketCardShadowColor()
    Card(
        modifier = modifier.shadow(
            elevation = 6.dp,
            shape = shape,
            ambientColor = shadowColor,
            spotColor = shadowColor,
            clip = false
        ),
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        content = content
    )
}

package com.pocketcraft.server.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pocketcraft.server.ui.theme.pocketCardBorderColor
import com.pocketcraft.server.ui.theme.pocketCardShadowColor

@Composable
fun GameCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    val shadowColor = pocketCardShadowColor()
    Card(
        modifier = modifier.shadow(
            elevation = 14.dp,
            shape = shape,
            ambientColor = shadowColor,
            spotColor = shadowColor,
            clip = false
        ),
        shape = shape,
        border = BorderStroke(2.dp, pocketCardBorderColor()),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(18.dp),
            content = content
        )
    }
}

package com.pocketcraft.server.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = PocketColors.Primary,
    onPrimary = PocketColors.TextLight,
    primaryContainer = PocketColors.PrimaryMuted,
    secondary = PocketColors.PrimaryDark,
    background = PocketColors.BgLight,
    surface = PocketColors.SurfaceLight,
    surfaceVariant = PocketColors.SurfaceVarLight,
    onBackground = PocketColors.TextLight,
    onSurface = PocketColors.TextLight,
    onSurfaceVariant = PocketColors.TextMuted,
    outline = PocketColors.BorderLight,
    error = PocketColors.Danger,
    tertiary = PocketColors.Starting
)

private val PocketShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(14.dp)
)

@Composable
fun PocketCraftTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = PocketCraftTypography,
        shapes = PocketShapes,
        content = content
    )
}

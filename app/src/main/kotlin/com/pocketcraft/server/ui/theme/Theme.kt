package com.pocketcraft.server.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
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

private val DarkColorScheme = darkColorScheme(
    primary = PocketColors.SurfaceVarDark,
    onPrimary = PocketColors.TextDark,
    primaryContainer = PocketColors.SurfaceDark,
    onPrimaryContainer = PocketColors.TextDark,
    secondary = PocketColors.TextDark,
    background = PocketColors.BgDark,
    surface = PocketColors.SurfaceDark,
    surfaceVariant = PocketColors.SurfaceVarDark,
    onBackground = PocketColors.TextDark,
    onSurface = PocketColors.TextDark,
    onSurfaceVariant = PocketColors.TextDark.copy(alpha = 0.9f),
    outline = PocketColors.BorderDark,
    outlineVariant = Color(0xFF447D69),
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
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = PocketCraftTypography,
        shapes = PocketShapes,
        content = content
    )
}

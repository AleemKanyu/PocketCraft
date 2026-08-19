package com.pockethost.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.pockethost.app.ui.util.MobTheme

private fun pocketColorScheme() = with(PocketColors.currentPalette) {
    val darkSurface = bgApp.luminance() < 0.5f
    val secondaryAccent = when (PocketColors.activeMobTheme) {
        MobTheme.CREEPER -> if (PocketColors.isDark) Color(0xFF8ACC8A) else primaryBorder
        else -> primaryBorder
    }

    if (darkSurface) {
        darkColorScheme(
            background           = bgApp,
            surface              = surfaceCard,
            surfaceVariant       = surfaceHover,
            surfaceTint          = navActivePillBg,
            primary              = primary,
            onPrimary            = bgApp,
            primaryContainer     = navActivePillBg,
            onPrimaryContainer   = primary,
            secondary            = tagText,
            onSecondary          = bgApp,
            secondaryContainer   = surfaceHover,
            onSecondaryContainer = tagText,
            tertiary             = primaryBorder,
            onTertiary           = bgApp,
            tertiaryContainer    = tagBg,
            onTertiaryContainer  = tagText,
            onBackground         = textPrimary,
            onSurface            = textPrimary,
            onSurfaceVariant     = textMuted,
            outline              = cardBorder,
            outlineVariant       = inactiveBorder,
            error                = danger,
            onError              = textPrimary,
            errorContainer       = dangerBg,
            onErrorContainer     = dangerText
        )
    } else {
        lightColorScheme(
            primary              = primary,
            onPrimary            = primaryText,
            primaryContainer     = navActivePillBg,
            onPrimaryContainer   = textPrimary,
            secondary            = secondaryAccent,
            onSecondary          = primaryText,
            secondaryContainer   = surfaceHover,
            onSecondaryContainer = textPrimary,
            tertiary             = primaryBorder,
            onTertiary           = primaryText,
            tertiaryContainer    = tagBg,
            onTertiaryContainer  = tagText,
            background           = bgApp,
            surface              = surfaceCard,
            surfaceVariant       = surfaceHover,
            surfaceTint          = primary,
            onBackground         = textPrimary,
            onSurface            = textPrimary,
            onSurfaceVariant     = textSecondary,
            outline              = cardBorder,
            outlineVariant       = cardBorder.copy(alpha = 0.45f),
            error                = danger,
            errorContainer       = dangerBg,
            onErrorContainer     = dangerText
        )
    }
}

private val PocketShapes = Shapes(
    small  = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large  = RoundedCornerShape(20.dp)
)

@Composable
fun PocketHostTheme(
    darkTheme: Boolean = false,
    mobTheme: MobTheme = PocketColors.activeMobTheme,
    content: @Composable () -> Unit
) {
    PocketColors.isDark = darkTheme
    PocketColors.activeMobTheme = mobTheme

    MaterialTheme(
        colorScheme = pocketColorScheme(),
        typography  = PocketCraftTypography,
        shapes      = PocketShapes,
        content     = content
    )
}

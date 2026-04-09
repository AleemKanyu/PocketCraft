package com.pocketcraft.server.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

@Composable
fun pocketIsDarkTheme(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

@Composable
fun pocketCardBorderColor(): Color = if (pocketIsDarkTheme()) {
    PocketColors.SurfaceVarDark.copy(alpha = 0.72f)
} else {
    PocketColors.Primary.copy(alpha = 0.14f)
}

@Composable
fun pocketCardShadowColor(): Color = if (pocketIsDarkTheme()) {
    PocketColors.SurfaceVarDark.copy(alpha = 0.42f)
} else {
    PocketColors.Primary.copy(alpha = 0.12f)
}

@Composable
fun pocketSheetBorderColor(): Color = if (pocketIsDarkTheme()) {
    PocketColors.SurfaceVarDark.copy(alpha = 0.7f)
} else {
    PocketColors.BorderLight
}

@Composable
fun pocketPopupAccentContainerColor(): Color = if (pocketIsDarkTheme()) {
    PocketColors.SurfaceVarDark.copy(alpha = 0.34f)
} else {
    PocketColors.PrimaryMuted
}

@Composable
fun pocketPopupAccentTintColor(): Color = if (pocketIsDarkTheme()) {
    PocketColors.TextDark
} else {
    PocketColors.PrimaryDark
}

@Composable
fun pocketWarningSurfaceColor(): Color = if (pocketIsDarkTheme()) {
    Color(0xFF21362D)
} else {
    Color(0xFFF9F3E7)
}

@Composable
fun pocketWarningBorderColor(): Color = if (pocketIsDarkTheme()) {
    Color(0xFF4C7B66)
} else {
    Color(0xFFE7C88B)
}

@Composable
fun pocketWarningIconChipColor(): Color = if (pocketIsDarkTheme()) {
    Color(0xFF2F4F42)
} else {
    Color(0xFFFFDFAE)
}

@Composable
fun pocketWarningAccentColor(): Color = if (pocketIsDarkTheme()) {
    Color(0xFFE7C96C)
} else {
    Color(0xFFCD8A00)
}

@Composable
fun pocketWarningTitleColor(): Color = if (pocketIsDarkTheme()) {
    PocketColors.TextDark
} else {
    Color(0xFF6F4E00)
}

@Composable
fun pocketWarningBodyColor(): Color = if (pocketIsDarkTheme()) {
    PocketColors.TextDark.copy(alpha = 0.82f)
} else {
    Color(0xFF7C6536)
}

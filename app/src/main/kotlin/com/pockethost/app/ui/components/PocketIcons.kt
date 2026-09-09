package com.pockethost.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.pockethost.app.R
import com.pockethost.app.ui.theme.pocketIsDarkTheme

@Composable
fun PocketAppLogo(
    modifier: Modifier = Modifier,
    contentDescription: String? = "PocketCraft",
    onPureBlackBackground: Boolean = false,
    tint: Color = Color.Unspecified
) {
    val isDark = pocketIsDarkTheme()
    val resolvedTint = if (tint != Color.Unspecified) tint else if (isDark || onPureBlackBackground) Color.White else Color.Black
    Icon(
        painter = painterResource(if (onPureBlackBackground) R.drawable.app_logo_dark else R.drawable.app_logo_light),
        contentDescription = contentDescription,
        modifier = modifier,
        tint = resolvedTint
    )
}

@Composable
fun PocketWorldIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    onPureBlackBackground: Boolean = false
) {
    val isDark = pocketIsDarkTheme()
    val resolvedTint = if (tint != Color.Unspecified) tint else if (isDark || onPureBlackBackground) Color.White else Color.Black
    Icon(
        painter = painterResource(if (onPureBlackBackground) R.drawable.app_logo_dark else R.drawable.app_logo_light),
        contentDescription = null,
        modifier = modifier,
        tint = resolvedTint
    )
}

@Composable
fun PocketModsIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified
) {
    Icon(
        painter = painterResource(R.drawable.ic_mods_pixel),
        contentDescription = null,
        modifier = modifier,
        tint = tint
    )
}
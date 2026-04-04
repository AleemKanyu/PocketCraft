package com.pocketcraft.server.ui.components

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.pocketcraft.server.R

@Composable
fun PocketWorldIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified
) {
    Icon(
        painter = painterResource(R.drawable.ic_launcher_foreground),
        contentDescription = null,
        modifier = modifier,
        tint = tint
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
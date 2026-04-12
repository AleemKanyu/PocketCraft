package com.pocketcraft.server.ui.theme

import androidx.compose.ui.graphics.Color

object PocketColors {
    val Primary = Color(0xFF58CC02)
    val PrimaryLight = Color(0xFF7FE620)  // Lighter green for Start Server button
    val PrimaryDark = Color(0xFF4F9B7D)
    val PrimaryMuted = Color(0x2658CC02)

    // Light Theme Colors
    val BgLight = Color(0xFFFFFCF4)
    val BgDark = Color(0xFF132621)

    val SurfaceLight = Color(0xFFFFFFFF)
    val SurfaceDark = Color(0xFF1A352D)

    val SurfaceVarLight = Color(0xFFF4FCE8)
    val SurfaceVarDark = Color(0xFF316854)

    // Text colors (light mode should be dark text)
    val TextLight = Color(0xFF203119)
    val TextDark = Color(0xFFF5FFFB)
    val TextMuted = Color(0xFF596E63)

    val BorderLight = Color(0xFFE2E8D3)
    val BorderDark = Color(0xFF53917B)

    val Online = Color(0xFF35A854)
    val Offline = Color(0xFFE85D75)
    val DownloadBlue = Color(0xFF2F80ED)
    val Starting = Color(0xFFFFC800)
    val Danger = Color(0xFFFF6B6B)

    val ConsoleGreen = Color(0xFFF5FFFB)
    val ConsoleWarn = Color(0xFFFFB142)
    val ConsoleError = Color(0xFFFF4757)
    val ConsoleBg = BgDark

    val HealthRed = Color(0xFFFF4757)
    val XpGreen = Primary
}

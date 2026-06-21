package com.pocketcraft.server.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.pocketcraft.server.ui.util.MobTheme

interface ThemePalette {
    val bgApp: Color
    val surfaceCard: Color
    val surfaceHover: Color
    val cardBorder: Color
    val cardBorderBottom: Color
    val primary: Color
    val primaryBorder: Color
    val primaryBorderBottom: Color
    val primaryText: Color
    val inactiveBg: Color
    val inactiveBorder: Color
    val inactiveBorderBottom: Color
    val inactiveText: Color
    val tagBg: Color
    val tagBorder: Color
    val tagBorderBottom: Color
    val tagText: Color
    val textPrimary: Color
    val textSecondary: Color
    val textMuted: Color
    val textSection: Color
    val iconBtnBg: Color
    val iconBtnBorder: Color
    val iconBtnBorderBottom: Color
    val iconBtnIcon: Color
    val navActivePillBg: Color
    val navActivePillBorder: Color
    val navActivePillBorderBottom: Color
    val footerBg: Color
    val footerLogoBg: Color
    val footerText: Color
    val footerMuted: Color
    val consoleBg: Color
    val consoleBorder: Color
    val consoleBorderBottom: Color
    val consoleDim: Color
    val consoleBright: Color
    val consoleCursor: Color
    val dangerBg: Color
    val dangerBorder: Color
    val dangerBorderBottom: Color
    val dangerText: Color
    val online: Color
    val offline: Color
    val warning: Color
    val downloadBlue: Color
    val starting: Color
    val danger: Color
    val healthRed: Color
}

abstract class BaseThemePalette : ThemePalette {
    override val dangerBg = Color(0xFF8B3030)
    override val dangerBorder = Color(0xFF6B2020)
    override val dangerBorderBottom = Color(0xFF4A1010)
    override val dangerText = Color(0xFFFFE8E8)
    override val online = Color(0xFF35A854)
    override val offline = Color(0xFFE85D75)
    override val warning = Color(0xFFF59E0B)
    override val downloadBlue = Color(0xFF2F80ED)
    override val starting = Color(0xFFFFC800)
    override val danger = Color(0xFFFF6B6B)
    override val healthRed = Color(0xFFFF4757)
}

object SkeletonTheme : BaseThemePalette() {
    override val bgApp = Color(0xFFEEE8DC)
    override val surfaceCard = Color(0xFFF8F4EC)
    override val surfaceHover = Color(0xFFE4DCD0)
    override val cardBorder = Color(0xFFD0C8BC)
    override val cardBorderBottom = Color(0xFFA49C90)
    override val primary = Color(0xFF5A6070)
    override val primaryBorder = Color(0xFF7A8490)
    override val primaryBorderBottom = Color(0xFF2E3440)
    override val primaryText = Color(0xFFF0EBE0)
    override val inactiveBg = Color(0xFFE4DCD0)
    override val inactiveBorder = Color(0xFFC4BCB0)
    override val inactiveBorderBottom = Color(0xFF989088)
    override val inactiveText = Color(0xFF6A7280)
    override val tagBg = Color(0xFFE4DCD0)
    override val tagBorder = Color(0xFF6A7280)
    override val tagBorderBottom = Color(0xFF2E3440)
    override val tagText = Color(0xFF3A4050)
    override val textPrimary = Color(0xFF1E2230)
    override val textSecondary = Color(0xFF6A7280)
    override val textMuted = Color(0xFFA8A098)
    override val textSection = Color(0xFF6A7280)
    override val iconBtnBg = Color(0xFFE4DCD0)
    override val iconBtnBorder = Color(0xFFC4BCB0)
    override val iconBtnBorderBottom = Color(0xFF989088)
    override val iconBtnIcon = Color(0xFF6A7280)
    override val navActivePillBg = Color(0xFFDDD8CC)
    override val navActivePillBorder = Color(0xFFC0B8B0)
    override val navActivePillBorderBottom = Color(0xFF989088)
    override val footerBg = Color(0xFF1E2230)
    override val footerLogoBg = Color(0xFF5A6070)
    override val footerText = Color(0xFF8A94A8)
    override val footerMuted = Color(0xFF3A4050)
    override val consoleBg = Color(0xFF1E2230)
    override val consoleBorder = Color(0xFF323848)
    override val consoleBorderBottom = Color(0xFF0E1018)
    override val consoleDim = Color(0xFF3A4050)
    override val consoleBright = Color(0xFFA8B0C0)
    override val consoleCursor = Color(0xFF7A8490)
}

object CreeperLightTheme : BaseThemePalette() {
    override val bgApp = Color(0xFFE8F0E0)
    override val surfaceCard = Color(0xFFF0F8E8)
    override val surfaceHover = Color(0xFFDDEECE)
    override val cardBorder = Color(0xFFB0CC98)
    override val cardBorderBottom = Color(0xFF90B878)
    override val primary = Color(0xFF4A8A4A)
    override val primaryBorder = Color(0xFF2A6A2A)
    override val primaryBorderBottom = Color(0xFF1A5A1A)
    override val primaryText = Color(0xFFFFFFFF)
    override val inactiveBg = Color(0xFFDDEECE)
    override val inactiveBorder = Color(0xFFB0CC98)
    override val inactiveBorderBottom = Color(0xFF90B878)
    override val inactiveText = Color(0xFF5A7A5A)
    override val tagBg = Color(0xFFDDEECE)
    override val tagBorder = Color(0xFFA8CC88)
    override val tagBorderBottom = Color(0xFF88B868)
    override val tagText = Color(0xFF2A5A2A)
    override val textPrimary = Color(0xFF1A2E1A)
    override val textSecondary = Color(0xFF5A7A5A)
    override val textMuted = Color(0xFF8AAA8A)
    override val textSection = Color(0xFF3A7A3A)
    override val iconBtnBg = Color(0xFFD8E8C8)
    override val iconBtnBorder = Color(0xFFB8D0A8)
    override val iconBtnBorderBottom = Color(0xFFA8C098)
    override val iconBtnIcon = Color(0xFF3A6A3A)
    override val navActivePillBg = Color(0xFFD0E8B8)
    override val navActivePillBorder = Color(0xFFA8CC88)
    override val navActivePillBorderBottom = Color(0xFF88B868)
    override val footerBg = Color(0xFF1A2E1A)
    override val footerLogoBg = Color(0xFF4A8A4A)
    override val footerText = Color(0xFFC8E8B8)
    override val footerMuted = Color(0xFF4A6A4A)
    override val consoleBg = Color(0xFF0F1A0F)
    override val consoleBorder = Color(0xFF1E3A1E)
    override val consoleBorderBottom = Color(0xFF0A100A)
    override val consoleDim = Color(0xFF3A5A3A)
    override val consoleBright = Color(0xFF5AB85A)
    override val consoleCursor = Color(0xFF4A8A4A)
}

object CreeperDarkTheme : BaseThemePalette() {
    override val bgApp = Color(0xFF0B0E0B) // DarkBackground
    override val surfaceCard = Color(0xFF131713) // DarkSurface
    override val surfaceHover = Color(0xFF1A2218) // DarkSurfaceElevated
    override val cardBorder = Color(0xFF222C22) // DarkBorderMedium
    override val cardBorderBottom = Color(0xFF2A3A2A) // DarkBorderStrong
    override val primary = Color(0xFF4ADE80) // GreenAccent
    override val primaryBorder = Color(0xFF4ADE80) // GreenAccentBorder
    override val primaryBorderBottom = Color(0xFF2A3A2A) // DarkBorderStrong
    override val primaryText = Color(0xFF0B0E0B) // DarkBackground
    override val inactiveBg = Color(0xFF0E120E) // NavBackground
    override val inactiveBorder = Color(0xFF1E2A1E) // DarkBorderSubtle
    override val inactiveBorderBottom = Color(0xFF222C22) // DarkBorderMedium
    override val inactiveText = Color(0xFF3A5A3A) // TextDisabled
    override val tagBg = Color(0xFF1A2218) // DarkSurfaceElevated
    override val tagBorder = Color(0xFF2A3A2A) // DarkBorderStrong
    override val tagBorderBottom = Color(0xFF222C22) // DarkBorderMedium
    override val tagText = Color(0xFF8AAA8A) // GreenMuted
    override val textPrimary = Color(0xFFEFF7EF) // TextPrimary
    override val textSecondary = Color(0xFF8AAA8A) // TextSecondary
    override val textMuted = Color(0xFF617B61) // TextTertiary
    override val textSection = Color(0xFF4ADE80) // GreenAccent
    override val iconBtnBg = Color(0xFF1A2218) // DarkSurfaceElevated
    override val iconBtnBorder = Color(0xFF2A3A2A) // DarkBorderStrong
    override val iconBtnBorderBottom = Color(0xFF222C22) // DarkBorderMedium
    override val iconBtnIcon = Color(0xFF4ADE80) // GreenAccent
    override val navActivePillBg = Color(0xFF1A2A1A) // DarkSurfaceSelected
    override val navActivePillBorder = Color(0xFF2A3A2A) // DarkBorderStrong
    override val navActivePillBorderBottom = Color(0xFF222C22) // DarkBorderMedium
    override val footerBg = Color(0xFF0B0E0B) // DarkBackground
    override val footerLogoBg = Color(0xFF1A2218) // DarkSurfaceElevated
    override val footerText = Color(0xFF8AAA8A) // TextSecondary
    override val footerMuted = Color(0xFF617B61) // TextTertiary
    override val consoleBg = Color(0xFF0B0E0B) // DarkBackground
    override val consoleBorder = Color(0xFF222C22) // DarkBorderMedium
    override val consoleBorderBottom = Color(0xFF1E2A1E) // DarkBorderSubtle
    override val consoleDim = Color(0xFF3A5A3A) // TextDisabled
    override val consoleBright = Color(0xFF4ADE80) // GreenAccent
    override val consoleCursor = Color(0xFF4ADE80) // GreenAccent
    override val dangerBg = Color(0xFF1A0808) // DangerBackground
    override val dangerBorder = Color(0xFF4A1818) // DangerBorder
    override val dangerBorderBottom = Color(0xFF4A1818) // DangerBorder
    override val dangerText = Color(0xFFFCA5A5) // DangerText
}

object SimpleWhiteTheme : BaseThemePalette() {
    override val bgApp = Color(0xFFFFFCF4)
    override val surfaceCard = Color(0xFFFFFFFF)
    override val surfaceHover = Color(0xFFF4FCE8)
    override val cardBorder = Color(0xFFE2E8D3)
    override val cardBorderBottom = Color(0xFFCCD4B8)
    override val primary = Color(0xFF58CC02)
    override val primaryBorder = Color(0xFF4A9E02)
    override val primaryBorderBottom = Color(0xFF308000)
    override val primaryText = Color(0xFFFFFFFF)
    override val inactiveBg = Color(0xFFF0F4E8)
    override val inactiveBorder = Color(0xFFE2E8D3)
    override val inactiveBorderBottom = Color(0xFFCCD4B8)
    override val inactiveText = Color(0xFF8A9A7E)
    override val tagBg = Color(0xFFF4FCE8)
    override val tagBorder = Color(0xFFE2E8D3)
    override val tagBorderBottom = Color(0xFFCCD4B8)
    override val tagText = Color(0xFF58CC02)
    override val textPrimary = Color(0xFF203119)
    override val textSecondary = Color(0xFF3C5B36)
    override val textMuted = Color(0xFF596E63)
    override val textSection = Color(0xFF203119)
    override val iconBtnBg = Color(0xFFFFFFFF)
    override val iconBtnBorder = Color(0xFFE2E8D3)
    override val iconBtnBorderBottom = Color(0xFFCCD4B8)
    override val iconBtnIcon = Color(0xFF58CC02)
    override val navActivePillBg = Color(0xFFF4FCE8)
    override val navActivePillBorder = Color(0xFFE2E8D3)
    override val navActivePillBorderBottom = Color(0xFFCCD4B8)
    override val footerBg = Color(0xFF203119)
    override val footerLogoBg = Color(0xFF58CC02)
    override val footerText = Color(0xFFFFFCF4)
    override val footerMuted = Color(0xFF3C5B36)
    override val consoleBg = Color(0xFF0A0A0F)
    override val consoleBorder = Color(0xFF2A2A3A)
    override val consoleBorderBottom = Color(0xFF13131A)
    override val consoleDim = Color(0xFF3C5B36)
    override val consoleBright = Color(0xFF58CC02)
    override val consoleCursor = Color(0xFF58CC02)
}

object SimpleDarkTheme : BaseThemePalette() {
    override val bgApp = Color(0xFF0B0E0B) // DarkBackground
    override val surfaceCard = Color(0xFF131713) // DarkSurface
    override val surfaceHover = Color(0xFF1A2218) // DarkSurfaceElevated
    override val cardBorder = Color(0xFF222C22) // DarkBorderMedium
    override val cardBorderBottom = Color(0xFF2A3A2A) // DarkBorderStrong
    override val primary = Color(0xFF4ADE80) // GreenAccent
    override val primaryBorder = Color(0xFF4ADE80) // GreenAccentBorder
    override val primaryBorderBottom = Color(0xFF2A3A2A) // DarkBorderStrong
    override val primaryText = Color(0xFF0B0E0B) // DarkBackground
    override val inactiveBg = Color(0xFF0E120E) // NavBackground
    override val inactiveBorder = Color(0xFF1E2A1E) // DarkBorderSubtle
    override val inactiveBorderBottom = Color(0xFF222C22) // DarkBorderMedium
    override val inactiveText = Color(0xFF3A5A3A) // TextDisabled
    override val tagBg = Color(0xFF1A2218) // DarkSurfaceElevated
    override val tagBorder = Color(0xFF2A3A2A) // DarkBorderStrong
    override val tagBorderBottom = Color(0xFF222C22) // DarkBorderMedium
    override val tagText = Color(0xFF8AAA8A) // GreenMuted
    override val textPrimary = Color(0xFFEFF7EF) // TextPrimary
    override val textSecondary = Color(0xFF8AAA8A) // TextSecondary
    override val textMuted = Color(0xFF617B61) // TextTertiary
    override val textSection = Color(0xFF4ADE80) // GreenAccent
    override val iconBtnBg = Color(0xFF1A2218) // DarkSurfaceElevated
    override val iconBtnBorder = Color(0xFF2A3A2A) // DarkBorderStrong
    override val iconBtnBorderBottom = Color(0xFF222C22) // DarkBorderMedium
    override val iconBtnIcon = Color(0xFF4ADE80) // GreenAccent
    override val navActivePillBg = Color(0xFF1A2A1A) // DarkSurfaceSelected
    override val navActivePillBorder = Color(0xFF2A3A2A) // DarkBorderStrong
    override val navActivePillBorderBottom = Color(0xFF222C22) // DarkBorderMedium
    override val footerBg = Color(0xFF0B0E0B) // DarkBackground
    override val footerLogoBg = Color(0xFF1A2218) // DarkSurfaceElevated
    override val footerText = Color(0xFF8AAA8A) // TextSecondary
    override val footerMuted = Color(0xFF617B61) // TextTertiary
    override val consoleBg = Color(0xFF0B0E0B) // DarkBackground
    override val consoleBorder = Color(0xFF222C22) // DarkBorderMedium
    override val consoleBorderBottom = Color(0xFF1E2A1E) // DarkBorderSubtle
    override val consoleDim = Color(0xFF3A5A3A) // TextDisabled
    override val consoleBright = Color(0xFF4ADE80) // GreenAccent
    override val consoleCursor = Color(0xFF4ADE80) // GreenAccent
    override val dangerBg = Color(0xFF1A0808) // DangerBackground
    override val dangerBorder = Color(0xFF4A1818) // DangerBorder
    override val dangerBorderBottom = Color(0xFF4A1818) // DangerBorder
    override val dangerText = Color(0xFFFCA5A5) // DangerText
}


object CustomThemePalette : BaseThemePalette() {
    var customPrimary by mutableStateOf(Color(0xFF4ADE80))
    var customBgApp by mutableStateOf(Color(0xFF0B0E0B))
    var customSurfaceCard by mutableStateOf(Color(0xFF131713))
    var customPrimaryText by mutableStateOf(Color(0xFF0B0E0B))
    var customTextPrimary by mutableStateOf(Color(0xFFEFF7EF))

    private fun Color.blend(other: Color, fraction: Float): Color {
        return Color(
            red = (this.red + (other.red - this.red) * fraction).coerceIn(0f, 1f),
            green = (this.green + (other.green - this.green) * fraction).coerceIn(0f, 1f),
            blue = (this.blue + (other.blue - this.blue) * fraction).coerceIn(0f, 1f),
            alpha = (this.alpha + (other.alpha - this.alpha) * fraction).coerceIn(0f, 1f)
        )
    }
    private fun Color.lighten(fraction: Float): Color = this.blend(Color.White, fraction)
    private fun Color.darken(fraction: Float): Color = this.blend(Color.Black, fraction)

    override val bgApp: Color get() = customBgApp
    override val surfaceCard: Color get() = customSurfaceCard
    override val surfaceHover: Color get() = if (customSurfaceCard.lighten(0.15f) == customSurfaceCard) customSurfaceCard.blend(Color.White, 0.1f) else customSurfaceCard.blend(if (customBgApp.red + customBgApp.green + customBgApp.blue > 1.5f) Color.Black else Color.White, 0.08f)
    override val cardBorder: Color get() = customSurfaceCard.blend(if (customBgApp.red + customBgApp.green + customBgApp.blue > 1.5f) Color.Black else Color.White, 0.15f)
    override val cardBorderBottom: Color get() = customSurfaceCard.blend(if (customBgApp.red + customBgApp.green + customBgApp.blue > 1.5f) Color.Black else Color.White, 0.25f)
    
    override val primary: Color get() = customPrimary
    override val primaryBorder: Color get() = customPrimary.darken(0.15f)
    override val primaryBorderBottom: Color get() = customPrimary.darken(0.3f)
    override val primaryText: Color get() = customPrimaryText

    override val inactiveBg: Color get() = customSurfaceCard.blend(customBgApp, 0.5f)
    override val inactiveBorder: Color get() = cardBorder.blend(customBgApp, 0.5f)
    override val inactiveBorderBottom: Color get() = cardBorderBottom.blend(customBgApp, 0.5f)
    override val inactiveText: Color get() = customTextPrimary.blend(customBgApp, 0.5f)

    override val tagBg: Color get() = surfaceHover
    override val tagBorder: Color get() = cardBorder
    override val tagBorderBottom: Color get() = cardBorderBottom
    override val tagText: Color get() = customPrimary

    override val textPrimary: Color get() = customTextPrimary
    override val textSecondary: Color get() = customTextPrimary.blend(customBgApp, 0.25f)
    override val textMuted: Color get() = customTextPrimary.blend(customBgApp, 0.5f)
    override val textSection: Color get() = customPrimary

    override val iconBtnBg: Color get() = customSurfaceCard
    override val iconBtnBorder: Color get() = cardBorder
    override val iconBtnBorderBottom: Color get() = cardBorderBottom
    override val iconBtnIcon: Color get() = customPrimary

    override val navActivePillBg: Color get() = customPrimary.blend(customBgApp, 0.85f)
    override val navActivePillBorder: Color get() = customPrimary.blend(customBgApp, 0.7f)
    override val navActivePillBorderBottom: Color get() = customPrimary.blend(customBgApp, 0.6f)

    override val footerBg: Color get() = customBgApp
    override val footerLogoBg: Color get() = customSurfaceCard
    override val footerText: Color get() = textSecondary
    override val footerMuted: Color get() = textMuted

    override val consoleBg: Color get() = customBgApp.darken(0.2f)
    override val consoleBorder: Color get() = cardBorder
    override val consoleBorderBottom: Color get() = cardBorderBottom
    override val consoleDim: Color get() = textMuted
    override val consoleBright: Color get() = customPrimary
    override val consoleCursor: Color get() = customPrimary
}

object PocketColors {
    var activeMobTheme = MobTheme.CREEPER
    var isDark = false

    val currentPalette: ThemePalette
        get() = when (activeMobTheme) {
            MobTheme.SKELETON -> SkeletonTheme
            MobTheme.CREEPER -> if (isDark) CreeperDarkTheme else CreeperLightTheme
            MobTheme.SIMPLE_WHITE -> SimpleWhiteTheme
            MobTheme.SIMPLE_DARK -> SimpleDarkTheme
            MobTheme.CUSTOM -> CustomThemePalette
        }

    val BgApp: Color get() = currentPalette.bgApp
    val BgDark: Color get() = CreeperDarkTheme.bgApp
    val BgLight: Color get() = CreeperLightTheme.bgApp

    val SurfaceCard: Color get() = currentPalette.surfaceCard
    val SurfaceCardDark: Color get() = CreeperDarkTheme.surfaceCard
    val SurfaceHover: Color get() = currentPalette.surfaceHover
    val SurfaceVarLight: Color get() = CreeperLightTheme.surfaceHover
    val SurfaceVarDark: Color get() = CreeperDarkTheme.surfaceHover

    val SurfaceLight: Color get() = CreeperLightTheme.surfaceCard
    val SurfaceBgDark: Color get() = CreeperDarkTheme.bgApp
    val SurfaceInsetDark: Color get() = CreeperDarkTheme.surfaceHover

    val CardBorder: Color get() = currentPalette.cardBorder
    val CardBorderBottom: Color get() = currentPalette.cardBorderBottom
    val CardBorderDark: Color get() = CreeperDarkTheme.cardBorder
    val CardBorderBottomDark: Color get() = CreeperDarkTheme.cardBorderBottom

    val Primary: Color get() = currentPalette.primary
    val PrimaryBorder: Color get() = currentPalette.primaryBorder
    val PrimaryBorderBottom: Color get() = currentPalette.primaryBorderBottom
    val PrimaryText: Color get() = currentPalette.primaryText

    val DangerBg: Color get() = currentPalette.dangerBg
    val DangerBorder: Color get() = currentPalette.dangerBorder
    val DangerBorderBottom: Color get() = currentPalette.dangerBorderBottom
    val DangerText: Color get() = currentPalette.dangerText

    @get:JvmName("getPrimaryBg_alias")
    val primaryBg: Color get() = Primary
    @get:JvmName("getPrimaryBorder_alias")
    val primaryBorder: Color get() = PrimaryBorder
    @get:JvmName("getPrimaryDepth_alias")
    val primaryDepth: Color get() = PrimaryBorderBottom
    @get:JvmName("getDangerBg_alias")
    val dangerBg: Color get() = DangerBg
    @get:JvmName("getDangerBorder_alias")
    val dangerBorder: Color get() = DangerBorder
    @get:JvmName("getDangerDepth_alias")
    val dangerDepth: Color get() = DangerBorderBottom
    @get:JvmName("getDangerText_alias")
    val dangerText: Color get() = DangerText

    val PrimaryDark: Color get() = currentPalette.primaryBorder
    val PrimaryDarker: Color get() = currentPalette.primaryBorderBottom
    val PrimaryLight: Color get() = currentPalette.primary
    val PrimaryMuted: Color get() = currentPalette.surfaceHover

    val InactiveBg: Color get() = currentPalette.inactiveBg
    val InactiveBorder: Color get() = currentPalette.inactiveBorder
    val InactiveBorderBottom: Color get() = currentPalette.inactiveBorderBottom
    val InactiveText: Color get() = currentPalette.inactiveText

    val TagBg: Color get() = currentPalette.tagBg
    val TagBorder: Color get() = currentPalette.tagBorder
    val TagBorderBottom: Color get() = currentPalette.tagBorderBottom
    val TagText: Color get() = currentPalette.tagText
    val LogoBorderBottom: Color get() = currentPalette.primaryBorderBottom

    val TextPrimary: Color get() = currentPalette.textPrimary
    val TextSecondary: Color get() = currentPalette.textSecondary
    val TextMuted: Color get() = currentPalette.textMuted
    val TextSection: Color get() = currentPalette.textSection
    val TextDark: Color get() = if (activeMobTheme == MobTheme.CREEPER && !isDark) CreeperDarkTheme.textPrimary else currentPalette.textPrimary
    val TextLight: Color get() = TextSecondary
    val TextMutedLight: Color get() = TextMuted

    val IconBtnBg: Color get() = currentPalette.iconBtnBg
    val IconBtnBorder: Color get() = currentPalette.iconBtnBorder
    val IconBtnBorderBottom: Color get() = currentPalette.iconBtnBorderBottom
    val IconBtnIcon: Color get() = currentPalette.iconBtnIcon

    val ConsoleBg: Color get() = currentPalette.consoleBg
    val ConsoleBorder: Color get() = currentPalette.consoleBorder
    val ConsoleBorderBottom: Color get() = currentPalette.consoleBorderBottom
    val ConsoleDim: Color get() = currentPalette.consoleDim
    val ConsoleBright: Color get() = currentPalette.consoleBright
    val ConsoleCursor: Color get() = currentPalette.consoleCursor
    val ConsoleGreen: Color get() = ConsoleBright
    val ConsoleWarn: Color get() = Color(0xFFFFB142)
    val ConsoleError: Color get() = Color(0xFFFF4757)

    val NavBg: Color get() = BgApp
    val NavActivePillBg: Color get() = currentPalette.navActivePillBg
    val NavActivePillBorder: Color get() = currentPalette.navActivePillBorder
    val NavActivePillBorderBottom: Color get() = currentPalette.navActivePillBorderBottom
    val NavActiveText: Color get() = TextPrimary
    val NavInactive: Color get() = TextMuted

    val FooterBg: Color get() = currentPalette.footerBg
    val FooterLogoBg: Color get() = currentPalette.footerLogoBg
    val FooterText: Color get() = currentPalette.footerText
    val FooterMuted: Color get() = currentPalette.footerMuted

    val BorderLight: Color get() = CardBorder
    val BorderStrong: Color get() = CardBorderBottom
    val BorderDark: Color get() = CreeperDarkTheme.cardBorder

    val GlassHighlightLight: Color get() = SurfaceCard
    val GlassHighlightDark: Color get() = Color(0x28FFFFFF)
    val GlassLowlightLight: Color get() = SurfaceHover
    val GlassLowlightDark: Color get() = Color(0xEA0D1A0D)
    val GlassStrokeLight: Color get() = CardBorder
    val GlassStrokeDark: Color get() = ConsoleBorder

    val AmbientBlue: Color get() = Primary
    val AmbientPurple: Color get() = PrimaryBorder
    val AmbientTeal: Color get() = Primary

    val Online: Color get() = currentPalette.online
    val Success: Color get() = Online
    val Offline: Color get() = currentPalette.offline
    val Warning: Color get() = currentPalette.warning
    val DownloadBlue: Color get() = currentPalette.downloadBlue
    val Starting: Color get() = currentPalette.starting
    val Danger: Color get() = currentPalette.danger
    val HealthRed: Color get() = currentPalette.healthRed
    val XpGreen: Color get() = Primary
}

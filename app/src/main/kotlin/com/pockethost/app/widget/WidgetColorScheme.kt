package com.pockethost.app.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.pockethost.app.R
import com.pockethost.app.data.preferences.AppPreferences
import com.pockethost.app.data.preferences.AppPreferencesStore
import com.pockethost.app.data.preferences.WidgetThemeSettings
import com.pockethost.app.ui.theme.CreeperDarkTheme
import com.pockethost.app.ui.theme.CreeperLightTheme
import com.pockethost.app.ui.theme.CustomThemePalette
import com.pockethost.app.ui.theme.SimpleDarkTheme
import com.pockethost.app.ui.theme.SimpleWhiteTheme
import com.pockethost.app.ui.theme.SkeletonTheme
import com.pockethost.app.ui.theme.ThemePalette
import com.pockethost.app.ui.util.MobTheme
import com.pockethost.app.ui.util.ThemePreferenceStore

data class WidgetColorScheme(
    val backgroundDrawableRes: Int,
    val backgroundColor: Int,
    val borderColor: Int,
    val cardGlowTint: Int,
    val iconBadgeColor: Int,
    val iconTint: Int,
    val textPrimary: Int,
    val textSecondary: Int,
    val textMuted: Int,
    val versionChipBg: Int,
    val onlineDotColor: Int,
    val statusBg: Int,
    val statusText: Int,
    val buttonStartBg: Int,
    val buttonStartTint: Int,
    val buttonStopBg: Int,
    val buttonStopTint: Int,
    val buttonRestartBg: Int,
    val buttonRestartTint: Int,
    val buttonDisabledBg: Int,
    val buttonDisabledTint: Int
)

object WidgetThemePrefs {
    const val THEME_FOLLOW_APP = "follow_app_theme"
    const val THEME_CREEPER = "creeper"
    const val THEME_CREEPER_DARK = "creeper_dark"
    const val THEME_DIAMOND = "diamond"
    const val THEME_NETHER = "nether"
    const val THEME_CUSTOM = "custom"

    private val lockedThemes = setOf(THEME_DIAMOND, THEME_NETHER, THEME_CUSTOM)

    fun isLocked(themeKey: String): Boolean = themeKey in lockedThemes

    suspend fun getSettings(context: Context): WidgetThemeSettings =
        AppPreferencesStore.getWidgetThemeSettings(context)

    suspend fun saveSelection(context: Context, themeKey: String, manualOverride: Boolean = true) {
        AppPreferencesStore.setWidgetThemeSelection(context, themeKey, manualOverride)
    }

    suspend fun saveCustomPalette(
        context: Context,
        background: Int,
        accent: Int,
        textOnAccent: Int,
        iconTint: Int
    ) {
        AppPreferencesStore.setWidgetCustomColors(
            context = context,
            background = background,
            accent = accent,
            textOnAccent = textOnAccent,
            iconTint = iconTint
        )
    }

    suspend fun ensureAllowedTheme(context: Context): WidgetThemeSettings {
        val settings = getSettings(context)
        // Widget updates also run inside the dedicated Minecraft process. Reading the
        // persisted entitlement avoids constructing BillingManager/Firestore there.
        val preferences = AppPreferences(context.applicationContext)
        val isPremium = preferences.isPremiumUser || preferences.debugPremiumOverride
        if (!isPremium && isLocked(settings.selectedTheme)) {
            AppPreferencesStore.setWidgetThemeSelection(
                context = context,
                themeKey = THEME_FOLLOW_APP,
                manualOverride = false
            )
            return settings.copy(selectedTheme = THEME_FOLLOW_APP, manualOverride = false)
        }
        return settings
    }

    suspend fun resolveActiveThemeKey(context: Context): String {
        val settings = ensureAllowedTheme(context)
        return resolveActiveThemeKey(context, settings)
    }

    fun resolveActiveThemeKey(context: Context, settings: WidgetThemeSettings): String {
        if (!settings.manualOverride || settings.selectedTheme == THEME_FOLLOW_APP) {
            return THEME_FOLLOW_APP
        }
        return settings.selectedTheme.ifBlank { THEME_FOLLOW_APP }
    }
}

fun resolveWidgetThemeScheme(
    context: Context,
    settings: WidgetThemeSettings
): WidgetColorScheme {
    return when (WidgetThemePrefs.resolveActiveThemeKey(context, settings)) {
        WidgetThemePrefs.THEME_FOLLOW_APP -> appThemeScheme(context)
        else -> themeFor(settings.selectedTheme, settings)
    }
}

fun previewThemeScheme(
    context: Context,
    themeKey: String,
    settings: WidgetThemeSettings
): WidgetColorScheme {
    return if (themeKey == WidgetThemePrefs.THEME_FOLLOW_APP) {
        appThemeScheme(context)
    } else {
        themeFor(themeKey, settings)
    }
}

fun themeFor(
    themeKey: String,
    settings: WidgetThemeSettings
): WidgetColorScheme {
    return when (themeKey) {
        WidgetThemePrefs.THEME_CREEPER -> scheme(
            backgroundDrawableRes = R.drawable.widget_bg_emerald,
            backgroundColor = Color(0xFF171C13),
            borderColor = Color(0x332A4422),
            cardGlowTint = Color(0x223D5F2A),
            iconBadgeColor = Color(0xFF527B2E),
            iconTint = Color(0xFFE7F0DB),
            textPrimary = Color(0xFFF2F4EE),
            textSecondary = Color(0xFFAAB79C),
            textMuted = Color(0xFF62705A),
            versionChipBg = Color(0x2235412C),
            onlineDotColor = Color(0xFF7FD858),
            statusBg = Color(0x1F7FD858),
            statusText = Color(0xFFA8E589),
            buttonStartBg = Color(0x14151A12),
            buttonStartTint = Color(0xFF586451),
            buttonStopBg = Color(0x1FD65D4F),
            buttonStopTint = Color(0xFFE8897B),
            buttonRestartBg = Color(0x1FD8B478),
            buttonRestartTint = Color(0xFFE8C98F),
            buttonDisabledBg = Color(0x14121610),
            buttonDisabledTint = Color(0x80586551)
        )
        WidgetThemePrefs.THEME_CREEPER_DARK -> scheme(
            backgroundDrawableRes = R.drawable.widget_bg_emerald_dark,
            backgroundColor = Color(0xFF11150F),
            borderColor = Color(0x33253C21),
            cardGlowTint = Color(0x1E355226),
            iconBadgeColor = Color(0xFF476829),
            iconTint = Color(0xFFD9E7CD),
            textPrimary = Color(0xFFE9F0E2),
            textSecondary = Color(0xFF92A087),
            textMuted = Color(0xFF5E6A57),
            versionChipBg = Color(0x22323D2D),
            onlineDotColor = Color(0xFF73C751),
            statusBg = Color(0x1B73C751),
            statusText = Color(0xFF9BD981),
            buttonStartBg = Color(0x1410140F),
            buttonStartTint = Color(0xFF4F5A48),
            buttonStopBg = Color(0x1EC95A4F),
            buttonStopTint = Color(0xFFE08B80),
            buttonRestartBg = Color(0x1ECAA666),
            buttonRestartTint = Color(0xFFE3C88C),
            buttonDisabledBg = Color(0x1210120F),
            buttonDisabledTint = Color(0x80525D4C)
        )
        WidgetThemePrefs.THEME_DIAMOND -> scheme(
            backgroundDrawableRes = R.drawable.widget_bg_diamond,
            backgroundColor = Color(0xFF132026),
            borderColor = Color(0x334B8EA3),
            cardGlowTint = Color(0x223FBFDF),
            iconBadgeColor = Color(0xFF2C7182),
            iconTint = Color(0xFFE8FBFF),
            textPrimary = Color(0xFFF0FDFF),
            textSecondary = Color(0xFFA7D4DE),
            textMuted = Color(0xFF6B96A0),
            versionChipBg = Color(0x22376D7D),
            onlineDotColor = Color(0xFF72E2FF),
            statusBg = Color(0x1E49CCE9),
            statusText = Color(0xFF9FEFFF),
            buttonStartBg = Color(0x1622363D),
            buttonStartTint = Color(0xFF7CA0AA),
            buttonStopBg = Color(0x1EE06A60),
            buttonStopTint = Color(0xFFF7B0A8),
            buttonRestartBg = Color(0x1EF3C36E),
            buttonRestartTint = Color(0xFFFFDE9A),
            buttonDisabledBg = Color(0x14213037),
            buttonDisabledTint = Color(0x807CA0AA)
        )
        WidgetThemePrefs.THEME_NETHER -> scheme(
            backgroundDrawableRes = R.drawable.widget_bg_nether,
            backgroundColor = Color(0xFF1B1018),
            borderColor = Color(0x334F2A45),
            cardGlowTint = Color(0x22B33D73),
            iconBadgeColor = Color(0xFF6A2A55),
            iconTint = Color(0xFFFFE8F4),
            textPrimary = Color(0xFFFFF2F9),
            textSecondary = Color(0xFFD3A9C2),
            textMuted = Color(0xFF90697F),
            versionChipBg = Color(0x223C1D32),
            onlineDotColor = Color(0xFFFF7CB0),
            statusBg = Color(0x1FFF5C9C),
            statusText = Color(0xFFFFAED1),
            buttonStartBg = Color(0x161F131D),
            buttonStartTint = Color(0xFF917089),
            buttonStopBg = Color(0x1EE0696A),
            buttonStopTint = Color(0xFFF9ADAE),
            buttonRestartBg = Color(0x1EF0BE65),
            buttonRestartTint = Color(0xFFFFD98D),
            buttonDisabledBg = Color(0x14190F17),
            buttonDisabledTint = Color(0x808E6E84)
        )
        WidgetThemePrefs.THEME_CUSTOM -> customScheme(settings)
        else -> themeFor(WidgetThemePrefs.THEME_CREEPER, settings)
    }
}

private fun appThemeScheme(context: Context): WidgetColorScheme {
    ThemePreferenceStore.loadCustomColors(context)
    val palette = when (ThemePreferenceStore.loadMobThemeSnapshot(context)) {
        MobTheme.SKELETON -> SkeletonTheme
        MobTheme.CREEPER -> CreeperLightTheme
        MobTheme.SIMPLE_WHITE -> SimpleWhiteTheme
        MobTheme.SIMPLE_DARK -> SimpleDarkTheme
        MobTheme.CUSTOM -> CustomThemePalette
    }
    return schemeForPalette(palette)
}

private fun schemeForPalette(palette: ThemePalette): WidgetColorScheme {
    val accent = palette.primary
    val surface = palette.surfaceCard
    val background = palette.bgApp
    val textPrimary = palette.textPrimary
    val textSecondary = palette.textSecondary
    val textMuted = palette.textMuted
    val accentText = if (accent.luminance() > 0.58f) palette.primaryText else Color.White
    val startBg = lerp(surface, Color.Black, 0.08f).copy(alpha = 0.76f)
    val disabledBg = lerp(surface, Color.Black, 0.14f).copy(alpha = 0.72f)
    return scheme(
        backgroundDrawableRes = 0,
        backgroundColor = background,
        borderColor = palette.cardBorder.copy(alpha = 0.86f),
        cardGlowTint = accent.copy(alpha = 0.12f),
        iconBadgeColor = palette.navActivePillBg,
        iconTint = palette.iconBtnIcon,
        textPrimary = textPrimary,
        textSecondary = textSecondary,
        textMuted = textMuted,
        versionChipBg = palette.surfaceHover.copy(alpha = 0.92f),
        onlineDotColor = palette.online,
        statusBg = palette.online.copy(alpha = 0.18f),
        statusText = palette.online,
        buttonStartBg = startBg,
        buttonStartTint = palette.inactiveText,
        buttonStopBg = palette.danger.copy(alpha = 0.18f),
        buttonStopTint = palette.dangerText,
        buttonRestartBg = palette.warning.copy(alpha = 0.18f),
        buttonRestartTint = lerp(palette.warning, Color.White, 0.18f),
        buttonDisabledBg = disabledBg,
        buttonDisabledTint = palette.inactiveText.copy(alpha = 0.84f)
    )
}

private fun customScheme(settings: WidgetThemeSettings): WidgetColorScheme {
    val accent = Color(settings.customAccent ?: Color(0xFF6CBF57).toArgb())
    val background = Color(settings.customBackground ?: lerp(accent, Color(0xFF0B110C), 0.84f).toArgb())
    val accentText = Color(settings.customTextOnAccent ?: Color.White.toArgb())
    val iconTint = Color(settings.customIconTint ?: lerp(Color.White, accent, 0.18f).toArgb())
    val primary = lerp(iconTint, Color.White, 0.18f)
    val secondary = lerp(primary, background, 0.42f)
    val muted = lerp(primary, background, 0.62f)
    val restartBase = lerp(accent, Color(0xFFFFC65C), 0.58f)
    val stopBase = lerp(accent, Color(0xFFFF7262), 0.62f)
    return scheme(
        backgroundDrawableRes = 0,
        backgroundColor = background,
        borderColor = lerp(accent, Color.White, 0.12f).copy(alpha = 0.18f),
        cardGlowTint = accent.copy(alpha = 0.16f),
        iconBadgeColor = lerp(accent, background, 0.28f),
        iconTint = iconTint,
        textPrimary = primary,
        textSecondary = secondary,
        textMuted = muted,
        versionChipBg = lerp(background, Color.White, 0.08f).copy(alpha = 0.18f),
        onlineDotColor = lerp(accent, Color.White, 0.18f),
        statusBg = accent.copy(alpha = 0.14f),
        statusText = lerp(accentText, Color.White, 0.08f),
        buttonStartBg = lerp(background, Color.Black, 0.08f).copy(alpha = 0.68f),
        buttonStartTint = muted,
        buttonStopBg = stopBase.copy(alpha = 0.15f),
        buttonStopTint = lerp(stopBase, Color.White, 0.18f),
        buttonRestartBg = restartBase.copy(alpha = 0.14f),
        buttonRestartTint = lerp(restartBase, Color.White, 0.14f),
        buttonDisabledBg = lerp(background, Color.Black, 0.12f).copy(alpha = 0.72f),
        buttonDisabledTint = muted.copy(alpha = 0.75f)
    )
}

private fun scheme(
    backgroundDrawableRes: Int,
    backgroundColor: Color,
    borderColor: Color,
    cardGlowTint: Color,
    iconBadgeColor: Color,
    iconTint: Color,
    textPrimary: Color,
    textSecondary: Color,
    textMuted: Color,
    versionChipBg: Color,
    onlineDotColor: Color,
    statusBg: Color,
    statusText: Color,
    buttonStartBg: Color,
    buttonStartTint: Color,
    buttonStopBg: Color,
    buttonStopTint: Color,
    buttonRestartBg: Color,
    buttonRestartTint: Color,
    buttonDisabledBg: Color,
    buttonDisabledTint: Color
): WidgetColorScheme {
    return WidgetColorScheme(
        backgroundDrawableRes = backgroundDrawableRes,
        backgroundColor = backgroundColor.toArgb(),
        borderColor = borderColor.toArgb(),
        cardGlowTint = cardGlowTint.toArgb(),
        iconBadgeColor = iconBadgeColor.toArgb(),
        iconTint = iconTint.toArgb(),
        textPrimary = textPrimary.toArgb(),
        textSecondary = textSecondary.toArgb(),
        textMuted = textMuted.toArgb(),
        versionChipBg = versionChipBg.toArgb(),
        onlineDotColor = onlineDotColor.toArgb(),
        statusBg = statusBg.toArgb(),
        statusText = statusText.toArgb(),
        buttonStartBg = buttonStartBg.toArgb(),
        buttonStartTint = buttonStartTint.toArgb(),
        buttonStopBg = buttonStopBg.toArgb(),
        buttonStopTint = buttonStopTint.toArgb(),
        buttonRestartBg = buttonRestartBg.toArgb(),
        buttonRestartTint = buttonRestartTint.toArgb(),
        buttonDisabledBg = buttonDisabledBg.toArgb(),
        buttonDisabledTint = buttonDisabledTint.toArgb()
    )
}

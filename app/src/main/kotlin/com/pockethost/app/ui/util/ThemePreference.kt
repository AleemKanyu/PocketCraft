package com.pockethost.app.ui.util

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.pockethost.app.data.preferences.AppPreferences
import com.pockethost.app.ui.theme.CustomThemePalette
import com.pockethost.app.billing.BillingManager

enum class ThemePreference {
    SYSTEM,
    LIGHT,
    DARK;

    fun resolve(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun fromStorage(value: String?): ThemePreference {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: SYSTEM
        }
    }
}

enum class MobTheme(val id: String, val themeName: String) {
    SKELETON("theme_skeleton", "Skeleton"),
    CREEPER("theme_creeper", "Creeper"),
    SIMPLE_WHITE("theme_simple_white", "Simple White"),
    SIMPLE_DARK("theme_simple_dark", "Simple Dark"),
    CUSTOM("theme_custom", "Custom");

    companion object {
        fun fromId(id: String?): MobTheme {
            return entries.firstOrNull { it.id == id } ?: CREEPER
        }
    }
}

object ThemePreferenceStore {
    private const val PREFS_NAME = "pocketcraft_ui"
    private const val KEY_THEME = "theme_preference"
    private const val KEY_MOB_THEME = "mob_theme_preference"

    fun load(context: Context): ThemePreference {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return ThemePreference.fromStorage(prefs.getString(KEY_THEME, null))
    }

    fun save(context: Context, preference: ThemePreference) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME, preference.name)
            .apply()
    }

    fun loadMobTheme(context: Context): MobTheme {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val theme = MobTheme.fromId(prefs.getString(KEY_MOB_THEME, null))
        val billingManager = BillingManager.getInstance(context)
        if (theme == MobTheme.CUSTOM && !billingManager.isPremium.value) {
            saveMobTheme(context, MobTheme.CREEPER)
            return MobTheme.CREEPER
        }
        return theme
    }

    fun loadMobThemeSnapshot(context: Context): MobTheme {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val theme = MobTheme.fromId(prefs.getString(KEY_MOB_THEME, null))
        val preferences = AppPreferences(context.applicationContext)
        return if (theme == MobTheme.CUSTOM && !preferences.isPremiumUser && !preferences.debugPremiumOverride) {
            MobTheme.CREEPER
        } else {
            theme
        }
    }

    fun resolveDarkMode(context: Context): Boolean {
        return load(context).resolve(systemDark = isSystemDark(context))
    }

    fun saveMobTheme(context: Context, theme: MobTheme) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MOB_THEME, theme.id)
            .apply()
    }

    fun loadCustomColors(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val primaryHex = prefs.getString("custom_primary", null)
        val bgAppHex = prefs.getString("custom_bg_app", null)
        val surfaceCardHex = prefs.getString("custom_surface_card", null)
        val primaryTextHex = prefs.getString("custom_primary_text", null)
        val textPrimaryHex = prefs.getString("custom_text_primary", null)

        try {
            if (primaryHex != null) CustomThemePalette.customPrimary = Color(android.graphics.Color.parseColor(primaryHex))
            if (bgAppHex != null) CustomThemePalette.customBgApp = Color(android.graphics.Color.parseColor(bgAppHex))
            if (surfaceCardHex != null) CustomThemePalette.customSurfaceCard = Color(android.graphics.Color.parseColor(surfaceCardHex))
            if (primaryTextHex != null) CustomThemePalette.customPrimaryText = Color(android.graphics.Color.parseColor(primaryTextHex))
            if (textPrimaryHex != null) CustomThemePalette.customTextPrimary = Color(android.graphics.Color.parseColor(textPrimaryHex))
        } catch (e: Exception) {
            // Ignore color parse errors
        }
    }

    fun saveCustomColors(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString("custom_primary", toHexString(CustomThemePalette.customPrimary))
            .putString("custom_bg_app", toHexString(CustomThemePalette.customBgApp))
            .putString("custom_surface_card", toHexString(CustomThemePalette.customSurfaceCard))
            .putString("custom_primary_text", toHexString(CustomThemePalette.customPrimaryText))
            .putString("custom_text_primary", toHexString(CustomThemePalette.customTextPrimary))
            .apply()
    }

    private fun toHexString(color: Color): String {
        return String.format("#%08X", color.toArgb())
    }

    fun isSystemDark(context: Context): Boolean {
        val nightModeFlags = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES
    }
}

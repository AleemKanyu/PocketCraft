package com.pocketcraft.server.ui.util

import android.content.Context
import android.content.res.Configuration

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
    SIMPLE_DARK("theme_simple_dark", "Simple Dark");

    companion object {
        fun fromId(id: String?): MobTheme {
            return entries.firstOrNull { it.id == id } ?: SKELETON
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
        return MobTheme.fromId(prefs.getString(KEY_MOB_THEME, null))
    }

    fun saveMobTheme(context: Context, theme: MobTheme) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MOB_THEME, theme.id)
            .apply()
    }

    fun isSystemDark(context: Context): Boolean {
        val nightModeFlags = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES
    }
}

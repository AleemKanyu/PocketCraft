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

object ThemePreferenceStore {
    private const val PREFS_NAME = "pocketcraft_ui"
    private const val KEY_THEME = "theme_preference"

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

    fun isSystemDark(context: Context): Boolean {
        val nightModeFlags = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES
    }
}

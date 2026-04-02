package com.pocketcraft.server.ui.util

import android.content.Context

enum class ThemePreference {
    LIGHT;

    fun resolve(systemDark: Boolean): Boolean = false
}

object ThemePreferenceStore {
    private const val PREFS_NAME = "pocketcraft_ui"
    private const val KEY_THEME = "theme_preference"

    fun load(context: Context): ThemePreference {
        return ThemePreference.LIGHT
    }

    fun save(context: Context, preference: ThemePreference) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME, preference.name)
            .apply()
    }
}

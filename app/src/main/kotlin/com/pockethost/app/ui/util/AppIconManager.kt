package com.pockethost.app.ui.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.pockethost.app.R

enum class AppIcon(
    val id: String,
    val aliasName: String,
    val title: String,
    val description: String,
    val previewRes: Int
) {
    GREEN(
        id = "green",
        aliasName = "com.pockethost.app.MainActivity",
        title = "Emerald Green",
        description = "Default vibrant green with PH cube",
        previewRes = R.drawable.icon_preview_green
    ),
    DARK_GREEN(
        id = "dark_green",
        aliasName = "com.pockethost.app.MainActivityDarkGreen",
        title = "Creeper Dark Green",
        description = "Deep dark green theme with PH cube",
        previewRes = R.drawable.icon_preview_dark_green
    ),
    MIDNIGHT(
        id = "midnight",
        aliasName = "com.pockethost.app.MainActivityDark",
        title = "Midnight Black",
        description = "Onyx dark background with golden cube",
        previewRes = R.drawable.icon_preview_dark
    );

    companion object {
        fun fromId(id: String?): AppIcon {
            return entries.firstOrNull { it.id == id } ?: GREEN
        }
    }
}

object AppIconManager {
    private const val PREFS_NAME = "pocketcraft_app_icon"
    private const val KEY_ICON = "selected_app_icon"

    fun getCurrentIcon(context: Context): AppIcon {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return AppIcon.fromId(prefs.getString(KEY_ICON, null))
    }

    fun setAppIcon(context: Context, targetIcon: AppIcon) {
        val pm = context.packageManager
        val packageName = context.packageName

        AppIcon.entries.forEach { icon ->
            val compName = ComponentName(packageName, icon.aliasName)
            val newState = if (icon == targetIcon) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            try {
                pm.setComponentEnabledSetting(
                    compName,
                    newState,
                    PackageManager.DONT_KILL_APP
                )
            } catch (e: Exception) {
                Log.e("AppIconManager", "Failed to update component $compName", e)
            }
        }

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ICON, targetIcon.id)
            .apply()
    }
}

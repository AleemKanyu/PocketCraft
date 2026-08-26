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
    LIGHT(
        id = "light",
        aliasName = "com.pockethost.app.MainActivityDefault",
        title = "Emerald Green",
        description = "Vibrant green with black logo",
        previewRes = R.drawable.icon_preview_light
    ),
    DARK(
        id = "dark",
        aliasName = "com.pockethost.app.MainActivityDark",
        title = "Midnight Black",
        description = "Dark onyx with white logo",
        previewRes = R.drawable.icon_preview_dark
    ),
    WHITE(
        id = "white",
        aliasName = "com.pockethost.app.MainActivityWhite",
        title = "Snow White",
        description = "Pure white with black logo and shadow",
        previewRes = R.drawable.icon_preview_white
    );

    companion object {
        fun fromId(id: String?): AppIcon {
            return entries.firstOrNull { it.id == id } ?: LIGHT
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

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ICON, targetIcon.id)
            .apply()

        val targetComp = ComponentName(packageName, targetIcon.aliasName)
        try {
            pm.setComponentEnabledSetting(
                targetComp,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
        } catch (e: Exception) {
            Log.e("AppIconManager", "Failed to enable component $targetComp", e)
        }

        AppIcon.entries.filter { it != targetIcon }.forEach { icon ->
            val compName = ComponentName(packageName, icon.aliasName)
            try {
                pm.setComponentEnabledSetting(
                    compName,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            } catch (e: Exception) {
                Log.e("AppIconManager", "Failed to disable component $compName", e)
            }
        }
    }
}

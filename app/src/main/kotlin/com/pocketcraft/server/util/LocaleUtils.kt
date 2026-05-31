package com.pocketcraft.server.util

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import com.pocketcraft.server.data.preferences.AppPreferences
import java.util.Locale

object LocaleUtils {
    fun wrapContext(context: Context): Context {
        val preferences = AppPreferences(context)
        val lang = preferences.appLanguage
        if (lang.isEmpty() || lang == "system") {
            return context
        }

        val locale = Locale(lang)
        Locale.setDefault(locale)

        val resources = context.resources
        val config = Configuration(resources.configuration)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocale(locale)
            val localeList = LocaleList(locale)
            config.setLocales(localeList)
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
        }

        return context.createConfigurationContext(config)
    }
}

package com.pockethost.app.util

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import com.pockethost.app.data.preferences.AppPreferences
import java.util.Locale

object LocaleUtils {

    /** Returns the user-selected Locale, or null if set to "system". */
    fun getSavedLocale(context: Context): Locale? {
        val lang = AppPreferences(context).appLanguage
        return if (lang.isEmpty() || lang == "system") null else Locale(lang)
    }

    /**
     * Stamps [locale] into [config] so that Android's override configuration
     * does not silently revert our language choice.
     * Called from applyOverrideConfiguration() in each Activity.
     */
    fun applyToConfig(config: Configuration, locale: Locale) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocale(locale)
            config.setLocales(LocaleList(locale))
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
        }
    }

    /**
     * Wraps [context] so that resource lookups use the saved locale.
     * Called from attachBaseContext() in each Activity / Service.
     */
    fun wrapContext(context: Context): Context {
        val locale = getSavedLocale(context) ?: return context

        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        applyToConfig(config, locale)

        return context.createConfigurationContext(config)
    }
}

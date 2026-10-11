package com.brightnook.nookcalendar.widget

import android.content.Context
import android.content.res.Configuration
import com.brightnook.nookcalendar.domain.AppLanguage
import com.brightnook.nookcalendar.domain.resolveAppLocale

object WidgetLocaleContext {
    fun resolve(context: Context, appLanguage: AppLanguage): Context {
        val locale = widgetLocale(appLanguage, context.resources.configuration.locales[0])
        return context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(locale) })
    }
}

internal fun widgetLocale(appLanguage: AppLanguage, systemLocale: java.util.Locale): java.util.Locale =
    resolveAppLocale(appLanguage, systemLocale)

package com.victor2319.ioscalendar.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.victor2319.ioscalendar.domain.AppLanguage
import java.util.Locale

fun resolveAppLocale(language: AppLanguage, systemLocale: Locale): Locale = when (language) {
    AppLanguage.FOLLOW_SYSTEM -> if (systemLocale.language.equals("zh", ignoreCase = true)) {
        Locale.SIMPLIFIED_CHINESE
    } else {
        Locale.ENGLISH
    }
}

@Composable
fun LocalizedAppContent(language: AppLanguage, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val systemLocale = LocalConfiguration.current.locales[0]
    val locale = resolveAppLocale(language, systemLocale)
    val localizedContext = remember(context, locale) { context.localizedFor(locale) }
    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedContext.resources.configuration,
        content = content,
    )
}

private fun Context.localizedFor(locale: Locale): Context =
    createConfigurationContext(Configuration(resources.configuration).apply { setLocale(locale) })

package com.brightnook.nookcalendar.ui

import android.content.Context
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import com.brightnook.nookcalendar.domain.AppLanguage
import com.brightnook.nookcalendar.domain.resolveAppLocale as resolveDomainAppLocale
import java.util.Locale

fun resolveAppLocale(language: AppLanguage, systemLocale: Locale): Locale =
    resolveDomainAppLocale(language, systemLocale)

@Composable
fun LocalizedAppContent(language: AppLanguage, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val activityResultRegistryOwner = LocalActivityResultRegistryOwner.current
    val systemLocale = LocalConfiguration.current.locales[0]
    val locale = resolveDomainAppLocale(language, systemLocale)
    val localizedContext = remember(context, locale) { context.localizedFor(locale) }
    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedContext.resources.configuration,
        LocalResources provides localizedContext.resources,
    ) {
        if (activityResultRegistryOwner != null) {
            CompositionLocalProvider(
                LocalActivityResultRegistryOwner provides activityResultRegistryOwner,
                content = content,
            )
        } else {
            content()
        }
    }
}

private fun Context.localizedFor(locale: Locale): Context {
    val localizedConfiguration = Configuration(resources.configuration).apply { setLocale(locale) }
    return ContextThemeWrapper(this, 0).apply {
        applyOverrideConfiguration(localizedConfiguration)
    }
}

internal fun <T : Context> Context.findContext(type: Class<T>): T? {
    var current: Context? = this
    while (current != null) {
        if (type.isInstance(current)) return type.cast(current)
        current = (current as? android.content.ContextWrapper)?.baseContext
    }
    return null
}

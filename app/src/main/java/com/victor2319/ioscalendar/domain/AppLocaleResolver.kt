package com.victor2319.ioscalendar.domain

import java.util.Locale

fun resolveAppLocale(language: AppLanguage, systemLocale: Locale): Locale = when (language) {
    AppLanguage.FOLLOW_SYSTEM -> when {
        systemLocale.language.equals(Locale.CHINESE.language, ignoreCase = true) -> Locale.SIMPLIFIED_CHINESE
        systemLocale.language.equals(Locale.JAPANESE.language, ignoreCase = true) -> Locale.JAPANESE
        else -> Locale.ENGLISH
    }
    AppLanguage.CHINESE -> Locale.SIMPLIFIED_CHINESE
    AppLanguage.ENGLISH -> Locale.ENGLISH
    AppLanguage.JAPANESE -> Locale.JAPANESE
}

package com.brightnook.nookcalendar.widget

import com.brightnook.nookcalendar.domain.AppLanguage
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetLocaleContextTest {
    @Test
    fun resolvesTheManualJapaneseAppLanguageOverAnEnglishDeviceLocale() {
        // Catches widget strings and date formatters using the raw English device context.
        assertEquals(Locale.JAPANESE, widgetLocale(AppLanguage.JAPANESE, Locale.US))
    }

    @Test
    fun followsTheJapaneseDeviceLocaleInsteadOfFallingBackToEnglish() {
        // Catches FOLLOW_SYSTEM treating Japanese as an unsupported system locale.
        assertEquals(Locale.JAPANESE, widgetLocale(AppLanguage.FOLLOW_SYSTEM, Locale.JAPANESE))
    }
}

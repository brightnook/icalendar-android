package com.victor2319.ioscalendar.ui.main

import com.victor2319.ioscalendar.domain.AppLanguage
import com.victor2319.ioscalendar.ui.resolveAppLocale
import java.time.YearMonth
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class YearCalendarLocaleTest {
    @Test
    fun yearMonthTitleUsesJapaneseForJapaneseLocale() {
        // Catches mini-month titles falling back to English after the app resolves Japanese.
        assertEquals(
            "8月",
            yearMonthTitle(
                YearMonth.of(2026, 8),
                resolveAppLocale(AppLanguage.FOLLOW_SYSTEM, Locale.JAPANESE),
            ),
        )
    }
}

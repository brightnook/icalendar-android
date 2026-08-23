package com.victor2319.ioscalendar.ui.main

import com.victor2319.ioscalendar.domain.AppLanguage
import com.victor2319.ioscalendar.ui.resolveAppLocale
import java.time.YearMonth
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class YearCalendarLocaleTest {
    @Test
    fun yearMonthTitleUsesEnglishForJapaneseLocale() {
        // Catches mini-month titles falling back to the process locale after the app resolves Japanese to English.
        assertEquals(
            "Aug",
            yearMonthTitle(
                YearMonth.of(2026, 8),
                resolveAppLocale(AppLanguage.FOLLOW_SYSTEM, Locale.JAPANESE),
            ),
        )
    }
}

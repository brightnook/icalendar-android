package com.brightnook.nookcalendar.ui.main

import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class DatePickerTitleTest {
    @Test
    fun titleUsesTheApplicationLocaleInsteadOfTheDeviceLocale() {
        val date = LocalDate.of(2026, 8, 31)

        assertEquals("Monday, August 31, 2026", datePickerTitle(date, Locale.ENGLISH))
        assertEquals("2026年8月31日星期一", datePickerTitle(date, Locale.SIMPLIFIED_CHINESE))
        assertEquals("2026年8月31日月曜日", datePickerTitle(date, Locale.JAPANESE))
    }

    @Test
    fun compactPickerMonthTitleUsesTheApplicationLocale() {
        assertEquals("September 2026", compactDatePickerMonthTitle(YearMonth.of(2026, 9), Locale.ENGLISH))
    }

    @Test
    fun monthGridTitleUsesTheProvidedApplicationLocale() {
        assertEquals("August 2026", monthGridTitle(YearMonth.of(2026, 8), Locale.ENGLISH))
    }
}

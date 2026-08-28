package com.brightnook.nookcalendar.widget

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetDateTimeFormatterTest {
    @Test
    fun timeFormattingUsesTheRequestedTwelveOrTwentyFourHourConvention() {
        // Catches widget rows always rendering a 24-hour time regardless of the device preference.
        val instant = Instant.parse("2026-08-15T09:05:00Z")

        assertEquals("9:05 AM", WidgetDateTimeFormatter.formatTime(instant, ZoneId.of("UTC"), Locale.US, is24Hour = false))
        assertEquals("09:05", WidgetDateTimeFormatter.formatTime(instant, ZoneId.of("UTC"), Locale.GERMANY, is24Hour = true))
    }

    @Test
    fun dateFormattingUsesTheRequestedLocale() {
        // Catches the widget keeping a fixed English month-and-weekday pattern after an app locale change.
        val date = LocalDate.of(2026, 8, 15)

        val american = WidgetDateTimeFormatter.formatDate(date, ZoneId.of("UTC"), Locale.US)
        val chinese = WidgetDateTimeFormatter.formatDate(date, ZoneId.of("UTC"), Locale.SIMPLIFIED_CHINESE)
        val japanese = WidgetDateTimeFormatter.formatDate(date, ZoneId.of("UTC"), Locale.JAPANESE)

        assertTrue(american.contains("Aug"))
        assertTrue(chinese.contains("8月"))
        assertTrue(japanese.contains("8月"))
    }
}

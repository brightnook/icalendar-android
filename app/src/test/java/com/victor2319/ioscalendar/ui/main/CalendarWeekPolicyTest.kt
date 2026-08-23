package com.victor2319.ioscalendar.ui.main

import com.victor2319.ioscalendar.domain.WeekStart
import java.time.DayOfWeek
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarWeekPolicyTest {
    @Test
    fun sundayStartPlacesSundayBeforeMondayInEveryCalendarLayout() {
        // Catches a calendar grid retaining Android's locale-derived Monday start after Sunday is selected.
        assertEquals(
            listOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY),
            calendarWeekdays(WeekStart.SUNDAY),
        )
    }

    @Test
    fun mondayStartPlacesMondayBeforeSundayInEveryCalendarLayout() {
        // Catches a year or month layout ignoring the persisted Monday preference.
        assertEquals(
            listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
            calendarWeekdays(WeekStart.MONDAY),
        )
    }

    @Test
    fun chineseHeadersUseSingleCharactersWithoutWeekPrefix() {
        // Catches headers rendered with Android's longer Chinese weekday form such as \"周一\".
        assertEquals(
            listOf("一", "二", "三", "四", "五", "六", "日"),
            calendarWeekdayHeaders(WeekStart.MONDAY, Locale.SIMPLIFIED_CHINESE),
        )
    }

    @Test
    fun japaneseHeadersUseEnglishRatherThanAndroidLocaleFallback() {
        // Catches Japanese device locales leaking Japanese weekday labels into this Chinese-or-English app.
        assertEquals(
            listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"),
            calendarWeekdayHeaders(WeekStart.MONDAY, Locale.JAPANESE),
        )
    }
}

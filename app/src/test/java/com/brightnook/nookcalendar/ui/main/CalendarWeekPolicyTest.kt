package com.brightnook.nookcalendar.ui.main

import com.brightnook.nookcalendar.domain.WeekStart
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
    fun monthHeadersKeepThreeLetterEnglishNames() {
        // Catches the combined month view inheriting the compact year labels.
        assertEquals(
            listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"),
            calendarWeekdayHeaders(
                weekStart = WeekStart.SUNDAY,
                locale = Locale.US,
                presentation = CalendarWeekdayHeaderPresentation.MONTH,
            ),
        )
    }

    @Test
    fun yearHeadersUseOneLetterEnglishNames() {
        // Catches the compact year view retaining the wider month labels.
        assertEquals(
            listOf("S", "M", "T", "W", "T", "F", "S"),
            calendarWeekdayHeaders(
                weekStart = WeekStart.SUNDAY,
                locale = Locale.US,
                presentation = CalendarWeekdayHeaderPresentation.YEAR,
            ),
        )
    }

    @Test
    fun chineseHeadersUseSingleCharactersWithoutWeekPrefix() {
        // Catches headers rendered with Android's longer Chinese weekday form such as \"周一\".
        assertEquals(
            listOf("一", "二", "三", "四", "五", "六", "日"),
            calendarWeekdayHeaders(
                weekStart = WeekStart.MONDAY,
                locale = Locale.SIMPLIFIED_CHINESE,
                presentation = CalendarWeekdayHeaderPresentation.MONTH,
            ),
        )
    }

    @Test
    fun japaneseHeadersUseSingleCharactersInConfiguredOrder() {
        // Catches Japanese retaining English headers or ignoring the Sunday-first preference.
        assertEquals(
            listOf("日", "月", "火", "水", "木", "金", "土"),
            calendarWeekdayHeaders(
                weekStart = WeekStart.SUNDAY,
                locale = Locale.JAPANESE,
                presentation = CalendarWeekdayHeaderPresentation.YEAR,
            ),
        )
    }
}

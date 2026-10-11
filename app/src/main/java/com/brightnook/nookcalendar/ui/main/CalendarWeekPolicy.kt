package com.brightnook.nookcalendar.ui.main

import com.brightnook.nookcalendar.domain.WeekStart
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

fun calendarWeekdays(weekStart: WeekStart): List<DayOfWeek> =
    generateSequence(weekStart.firstDay) { it.plus(1) }.take(7).toList()

enum class CalendarWeekdayHeaderPresentation {
    MONTH,
    YEAR,
}

fun calendarWeekdayHeaders(
    weekStart: WeekStart,
    locale: Locale,
    presentation: CalendarWeekdayHeaderPresentation,
): List<String> =
    calendarWeekdays(weekStart).map { day ->
        when (locale.language.lowercase(Locale.ROOT)) {
            "zh" -> day.chineseHeader
            "ja" -> day.japaneseHeader
            else -> if (presentation == CalendarWeekdayHeaderPresentation.YEAR) day.englishShortHeader else day.englishHeader
        }
    }

fun calendarGridStart(month: YearMonth, weekStart: WeekStart): LocalDate {
    val firstOfMonth = month.atDay(1)
    val offset = (firstOfMonth.dayOfWeek.value - weekStart.firstDay.value + 7) % 7
    return firstOfMonth.minusDays(offset.toLong())
}

private val WeekStart.firstDay: DayOfWeek
    get() = if (this == WeekStart.SUNDAY) DayOfWeek.SUNDAY else DayOfWeek.MONDAY

private val DayOfWeek.chineseHeader: String
    get() = when (this) {
        DayOfWeek.MONDAY -> "一"
        DayOfWeek.TUESDAY -> "二"
        DayOfWeek.WEDNESDAY -> "三"
        DayOfWeek.THURSDAY -> "四"
        DayOfWeek.FRIDAY -> "五"
        DayOfWeek.SATURDAY -> "六"
        DayOfWeek.SUNDAY -> "日"
    }

private val DayOfWeek.englishHeader: String
    get() = when (this) {
        DayOfWeek.MONDAY -> "Mon"
        DayOfWeek.TUESDAY -> "Tue"
        DayOfWeek.WEDNESDAY -> "Wed"
        DayOfWeek.THURSDAY -> "Thu"
        DayOfWeek.FRIDAY -> "Fri"
        DayOfWeek.SATURDAY -> "Sat"
        DayOfWeek.SUNDAY -> "Sun"
    }

private val DayOfWeek.englishShortHeader: String
    get() = englishHeader.take(1)

private val DayOfWeek.japaneseHeader: String
    get() = when (this) {
        DayOfWeek.MONDAY -> "月"
        DayOfWeek.TUESDAY -> "火"
        DayOfWeek.WEDNESDAY -> "水"
        DayOfWeek.THURSDAY -> "木"
        DayOfWeek.FRIDAY -> "金"
        DayOfWeek.SATURDAY -> "土"
        DayOfWeek.SUNDAY -> "日"
    }

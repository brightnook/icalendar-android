package com.brightnook.nookcalendar.ui.main

import androidx.compose.ui.graphics.Color
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

class MonthGridColorsTest {
    private val month = YearMonth.of(2026, 8)
    private val onPrimary = Color.Red
    private val weekday = Color.Blue
    private val outsideMonth = Color.Gray
    private val saturday = Color.Cyan
    private val sunday = Color.Magenta

    @Test
    fun saturdayAndSundayUseTheirOwnThemeColorsUnlessTheyAreSelected() {
        val saturday = LocalDate.of(2026, 8, 1)
        val sunday = LocalDate.of(2026, 8, 2)
        val monday = LocalDate.of(2026, 8, 3)

        assertEquals(
            this.saturday,
            monthGridDateColor(
                saturday, month, false, onPrimary, weekday, outsideMonth, this.saturday, this.sunday,
            ),
        )
        assertEquals(
            this.sunday,
            monthGridDateColor(
                sunday, month, false, onPrimary, weekday, outsideMonth, this.saturday, this.sunday,
            ),
        )
        assertEquals(
            weekday,
            monthGridDateColor(
                monday, month, false, onPrimary, weekday, outsideMonth, this.saturday, this.sunday,
            ),
        )
        assertEquals(
            onPrimary,
            monthGridDateColor(
                saturday, month, true, onPrimary, weekday, outsideMonth, this.saturday, this.sunday,
            ),
        )
    }

    @Test
    fun weekdayHeadersUseSeparateSaturdayAndSundayColors() {
        assertEquals(saturday, calendarWeekdayTextColor(java.time.DayOfWeek.SATURDAY, weekday, saturday, sunday))
        assertEquals(sunday, calendarWeekdayTextColor(java.time.DayOfWeek.SUNDAY, weekday, saturday, sunday))
        assertEquals(weekday, calendarWeekdayTextColor(java.time.DayOfWeek.MONDAY, weekday, saturday, sunday))
    }
}

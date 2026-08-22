package com.victor2319.ioscalendar.ui.main

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
    private val weekend = Color.Magenta

    @Test
    fun weekendsUseTheThemeWeekendColorUnlessTheyAreSelected() {
        val saturday = LocalDate.of(2026, 8, 1)
        val monday = LocalDate.of(2026, 8, 3)

        assertEquals(
            weekend,
            monthGridDateColor(saturday, month, false, onPrimary, weekday, outsideMonth, weekend),
        )
        assertEquals(
            weekday,
            monthGridDateColor(monday, month, false, onPrimary, weekday, outsideMonth, weekend),
        )
        assertEquals(
            onPrimary,
            monthGridDateColor(saturday, month, true, onPrimary, weekday, outsideMonth, weekend),
        )
    }
}

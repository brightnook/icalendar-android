package com.brightnook.nookcalendar.ui.main

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.brightnook.nookcalendar.domain.WeekStart
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YearCalendarHitTestingTest {
    @Test
    fun miniMonthHitMapSelectsTheDateAtTheTappedGridCoordinate() {
        // Catches the compact three-column year view routing a tap to a neighboring date.
        val selected = miniMonthDateAt(
            month = YearMonth.of(2026, 8),
            weekStart = WeekStart.SUNDAY,
            gridSize = IntSize(width = 140, height = 288),
            tap = Offset(x = 110f, y = 168f),
        )

        assertEquals(LocalDate.of(2026, 8, 21), selected)
    }

    @Test
    fun miniMonthHitMapLeavesLeadingAndTrailingDatesUnselected() {
        // Catches an outside-month tap silently selecting a date from an adjacent month.
        assertNull(
            miniMonthDateAt(
                month = YearMonth.of(2026, 8),
                weekStart = WeekStart.SUNDAY,
                gridSize = IntSize(width = 140, height = 288),
                tap = Offset(x = 10f, y = 24f),
            ),
        )
    }
}

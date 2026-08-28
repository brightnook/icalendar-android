package com.brightnook.nookcalendar.ui.main

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarVisualPolicyTest {
    @Test
    fun dayCellsReserveTheSameMarkerRowWhetherOrNotTheyHaveEvents() {
        val layout = fixedCalendarDayCellLayout()

        assertEquals(32.dp, layout.dateBoxSize)
        assertEquals(6.dp, layout.markerRowHeight)
        assertEquals(8.dp, layout.dateCornerRadius)
    }

    @Test
    fun todayStaysFilledWhileASeparatelySelectedDateUsesAnOutline() {
        assertEquals(CalendarDayHighlight.FILLED_PRIMARY, calendarDayHighlight(isToday = true, isSelected = false))
        assertEquals(CalendarDayHighlight.OUTLINE_PRIMARY, calendarDayHighlight(isToday = false, isSelected = true))
        assertEquals(CalendarDayHighlight.FILLED_PRIMARY, calendarDayHighlight(isToday = true, isSelected = true))
        assertEquals(CalendarDayHighlight.NONE, calendarDayHighlight(isToday = false, isSelected = false))
    }

    @Test
    fun yearCalendarKeepsMiniMonthsAwayFromTheScreenEdgesAndEachOther() {
        val layout = fixedYearCalendarLayout()

        assertEquals(16.dp, layout.horizontalContentPadding)
        assertEquals(12.dp, layout.monthGap)
        assertEquals(22.dp, layout.miniDayRowHeight)
    }
}

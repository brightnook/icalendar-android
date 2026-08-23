package com.victor2319.ioscalendar.ui.main

import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarInteractionPolicyTest {
    @Test
    fun yearMonthActionsUseFullLocalizedDatesAndKeepRowsVisuallyCompact() {
        val date = LocalDate.of(2026, 8, 21)

        assertEquals("Friday, August 21, 2026", yearDateContentDescription(date, Locale.US))
        assertEquals(48.dp, fixedYearCalendarLayout().miniDayRowHeight)
    }

    @Test
    fun calendarPrimaryControlsUsePersistentStateInsteadOfTransientRippleFeedback() {
        assertTrue(primaryControlPressFeedbackIsRippleFree())
    }
}

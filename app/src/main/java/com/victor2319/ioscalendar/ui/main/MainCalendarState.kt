package com.victor2319.ioscalendar.ui.main

import com.victor2319.ioscalendar.domain.CalendarSummary
import com.victor2319.ioscalendar.domain.EventDetail
import com.victor2319.ioscalendar.domain.EventInstance
import java.time.LocalDate
import java.time.YearMonth

data class MainCalendarState(
    val selectedDate: LocalDate,
    val displayedMonth: YearMonth,
    val calendars: List<CalendarSummary> = emptyList(),
    val agenda: List<EventInstance> = emptyList(),
    val dayMarkerColors: Map<LocalDate, List<Int>> = emptyMap(),
    val viewMode: CalendarViewMode = CalendarViewMode.COMBINED,
    val openEvent: EventDetail? = null,
    val message: MainCalendarMessage? = null,
)

enum class CalendarViewMode { COMBINED, YEAR }

enum class MainCalendarMessage {
    SYNC_NOT_READY,
    SYNC_DISABLED,
    SYNC_FAILED,
}

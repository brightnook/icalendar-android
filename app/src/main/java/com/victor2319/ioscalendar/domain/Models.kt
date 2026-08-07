package com.victor2319.ioscalendar.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

data class CalendarSummary(
    val id: String,
    val name: String,
    val colorArgb: Int,
    val isVisible: Boolean,
)

enum class EventStatus { CONFIRMED, TENTATIVE, CANCELLED }

data class CalendarEvent(
    val id: String,
    val calendarId: String,
    val uid: String,
    val recurrenceId: String?,
    val title: String,
    val location: String?,
    val notes: String?,
    val originalTimeZoneId: String?,
    val isAllDay: Boolean,
    val recurrenceRule: String?,
    val status: EventStatus,
)

data class EventInstance(
    val instanceId: String,
    val eventId: String,
    val calendarId: String,
    val title: String,
    val calendarColorArgb: Int,
    val isAllDay: Boolean,
    val startInstant: Instant?,
    val endInstant: Instant?,
    val startDate: LocalDate?,
    val endDateExclusive: LocalDate?,
    val location: String?,
) {
    init {
        requireValidEventTimes(
            isAllDay = isAllDay,
            startInstant = startInstant,
            endInstant = endInstant,
            startDate = startDate,
            endDateExclusive = endDateExclusive,
        )
    }
}

data class EventDetail(
    val instanceId: String,
    val eventId: String,
    val calendarId: String,
    val calendarName: String,
    val calendarColorArgb: Int,
    val title: String,
    val location: String?,
    val notes: String?,
    val isAllDay: Boolean,
    val startInstant: Instant?,
    val endInstant: Instant?,
    val startDate: LocalDate?,
    val endDateExclusive: LocalDate?,
    val originalTimeZoneId: String?,
    val recurrenceRule: String?,
) {
    init {
        requireValidEventTimes(
            isAllDay = isAllDay,
            startInstant = startInstant,
            endInstant = endInstant,
            startDate = startDate,
            endDateExclusive = endDateExclusive,
        )
    }
}

data class TimeRange(
    val start: Instant,
    val endExclusive: Instant,
    val zoneId: ZoneId = ZoneOffset.UTC,
) {
    init {
        require(start.isBefore(endExclusive)) { "TimeRange start must be before endExclusive" }
    }
}

data class SyncCoverage(val calendarId: String, val range: TimeRange)

data class CalendarSnapshot(
    val calendar: CalendarSummary,
    val events: List<CalendarEvent>,
    val instances: List<EventInstance>,
    val syncToken: String?,
    val coverage: TimeRange,
)

data class CalendarDelta(
    val calendarId: String,
    val upsertEvents: List<CalendarEvent>,
    val upsertInstances: List<EventInstance>,
    val deletedEventUids: Set<String>,
    val deletedInstanceIds: Set<String>,
    val syncToken: String?,
)

private fun requireValidEventTimes(
    isAllDay: Boolean,
    startInstant: Instant?,
    endInstant: Instant?,
    startDate: LocalDate?,
    endDateExclusive: LocalDate?,
) {
    if (isAllDay) {
        require(startInstant == null && endInstant == null) {
            "All-day events must not contain instants"
        }
        require(startDate != null && endDateExclusive != null && startDate.isBefore(endDateExclusive)) {
            "All-day events require an increasing date range"
        }
    } else {
        require(startDate == null && endDateExclusive == null) {
            "Timed events must not contain date-only values"
        }
        require(startInstant != null && endInstant != null && startInstant.isBefore(endInstant)) {
            "Timed events require an increasing instant range"
        }
    }
}

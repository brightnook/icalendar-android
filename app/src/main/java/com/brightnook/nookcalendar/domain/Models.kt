package com.brightnook.nookcalendar.domain

import java.time.Instant
import java.time.Duration
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

sealed interface EventReminder {
    data class RelativeToStart(val offset: Duration) : EventReminder
    data class RelativeToEnd(val offset: Duration) : EventReminder
    data class Absolute(val instant: Instant) : EventReminder
}

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
    val remoteResourceHref: String? = null,
    val reminders: List<EventReminder> = emptyList(),
) {
    init {
        require(remoteResourceHref == null || remoteResourceHref.isNotBlank()) {
            "Remote resource href must be null or nonblank"
        }
    }
}

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
    val reminders: List<EventReminder> = emptyList(),
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
    val deletedResourceHrefs: Set<String> = emptySet(),
    val replacedResourceHrefs: Set<String> = emptySet(),
)

data class CalendarSyncState(
    val syncToken: String? = null,
    val coverage: List<TimeRange> = emptyList(),
)

data class ScheduledReminder(
    val reminderId: String,
    val instanceId: String,
    val date: LocalDate,
    val triggerAt: Instant,
    val title: String,
    val location: String?,
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

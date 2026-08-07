package com.victor2319.ioscalendar.caldav

import com.victor2319.ioscalendar.domain.CalendarEvent
import com.victor2319.ioscalendar.domain.EventInstance
import com.victor2319.ioscalendar.domain.EventStatus
import java.time.Instant
import java.time.LocalDate

data class ParsedEvent(
    val id: String,
    val uid: String,
    val recurrenceId: String?,
    val title: String,
    val location: String?,
    val notes: String?,
    val originalTimeZoneId: String?,
    val isAllDay: Boolean,
    val recurrenceRule: String?,
    val status: EventStatus,
) {
    fun toDomain(calendarId: String): CalendarEvent = CalendarEvent(
        id = id,
        calendarId = calendarId,
        uid = uid,
        recurrenceId = recurrenceId,
        title = title,
        location = location,
        notes = notes,
        originalTimeZoneId = originalTimeZoneId,
        isAllDay = isAllDay,
        recurrenceRule = recurrenceRule,
        status = status,
    )
}

data class ParsedInstance(
    val instanceId: String,
    val eventId: String,
    val uid: String,
    val recurrenceId: String?,
    val title: String,
    val location: String?,
    val isAllDay: Boolean,
    val startInstant: Instant?,
    val endInstant: Instant?,
    val startDate: LocalDate?,
    val endDateExclusive: LocalDate?,
) {
    fun toDomain(calendarId: String, calendarColorArgb: Int): EventInstance = EventInstance(
        instanceId = instanceId,
        eventId = eventId,
        calendarId = calendarId,
        title = title,
        calendarColorArgb = calendarColorArgb,
        isAllDay = isAllDay,
        startInstant = startInstant,
        endInstant = endInstant,
        startDate = startDate,
        endDateExclusive = endDateExclusive,
        location = location,
    )
}

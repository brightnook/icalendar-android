package com.brightnook.nookcalendar.data

import com.brightnook.nookcalendar.domain.CalendarEvent
import com.brightnook.nookcalendar.domain.CalendarSummary
import com.brightnook.nookcalendar.domain.EventDetail
import com.brightnook.nookcalendar.domain.EventInstance
import com.brightnook.nookcalendar.domain.EventStatus
import com.brightnook.nookcalendar.domain.TimeRange
import java.time.Instant
import java.time.LocalDate

internal fun CalendarSummary.toEntity() = CalendarEntity(id, name, colorArgb, isVisible)

internal fun CalendarEntity.toDomain() = CalendarSummary(id, name, colorArgb, isVisible)

internal fun CalendarEvent.toEntity() = EventEntity(
    id = id,
    calendarId = calendarId,
    uid = uid,
    recurrenceKey = recurrenceId.orEmpty(),
    title = title,
    location = location,
    notes = notes,
    originalTimeZoneId = originalTimeZoneId,
    isAllDay = isAllDay,
    recurrenceRule = recurrenceRule,
    status = status.name,
    remoteResourceHref = remoteResourceHref,
)

internal fun EventEntity.toDomain() = CalendarEvent(
    id = id,
    calendarId = calendarId,
    uid = uid,
    recurrenceId = recurrenceKey.ifEmpty { null },
    title = title,
    location = location,
    notes = notes,
    originalTimeZoneId = originalTimeZoneId,
    isAllDay = isAllDay,
    recurrenceRule = recurrenceRule,
    status = EventStatus.valueOf(status),
    remoteResourceHref = remoteResourceHref,
)

internal fun EventInstance.toEntity() = EventInstanceEntity(
    instanceId = instanceId,
    eventId = eventId,
    calendarId = calendarId,
    isAllDay = isAllDay,
    startEpochMillis = startInstant?.toEpochMilli(),
    endEpochMillis = endInstant?.toEpochMilli(),
    startDate = startDate?.toString(),
    endDateExclusive = endDateExclusive?.toString(),
)

internal fun EventInstanceRow.toDomain() = EventInstance(
    instanceId = instanceId,
    eventId = eventId,
    calendarId = calendarId,
    title = title,
    calendarColorArgb = calendarColorArgb,
    isAllDay = isAllDay,
    startInstant = startEpochMillis?.let(Instant::ofEpochMilli),
    endInstant = endEpochMillis?.let(Instant::ofEpochMilli),
    startDate = startDate?.let(LocalDate::parse),
    endDateExclusive = endDateExclusive?.let(LocalDate::parse),
    location = location,
)

internal fun EventDetailRow.toDomain() = EventDetail(
    instanceId = instanceId,
    eventId = eventId,
    calendarId = calendarId,
    calendarName = calendarName,
    calendarColorArgb = calendarColorArgb,
    title = title,
    location = location,
    notes = notes,
    isAllDay = isAllDay,
    startInstant = startEpochMillis?.let(Instant::ofEpochMilli),
    endInstant = endEpochMillis?.let(Instant::ofEpochMilli),
    startDate = startDate?.let(LocalDate::parse),
    endDateExclusive = endDateExclusive?.let(LocalDate::parse),
    originalTimeZoneId = originalTimeZoneId,
    recurrenceRule = recurrenceRule,
)

internal fun TimeRange.toCoverageEntity(calendarId: String) = CoverageEntity(
    calendarId = calendarId,
    startEpochMillis = start.toEpochMilli(),
    endEpochMillis = endExclusive.toEpochMilli(),
    zoneId = zoneId.id,
)

internal fun Int.requireSupportedWidgetRangeDays(): Int {
    require(this in setOf(1, 3, 7, 30)) { "Widget rangeDays must be 1, 3, 7, or 30" }
    return this
}

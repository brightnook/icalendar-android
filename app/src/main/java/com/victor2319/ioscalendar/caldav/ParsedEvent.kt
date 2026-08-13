package com.victor2319.ioscalendar.caldav

import com.victor2319.ioscalendar.domain.CalendarEvent
import com.victor2319.ioscalendar.domain.EventInstance
import com.victor2319.ioscalendar.domain.EventStatus
import java.time.Instant
import java.time.LocalDate
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

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
    fun toDomain(calendarId: String, remoteResourceHref: String? = null): CalendarEvent = CalendarEvent(
        id = stableId("domain-event", calendarId, id),
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
        remoteResourceHref = remoteResourceHref,
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
        instanceId = stableId("domain-instance", calendarId, instanceId),
        eventId = stableId("domain-event", calendarId, eventId),
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

internal fun parsedEventId(uid: String, canonicalRecurrenceIdentity: String?): String =
    stableId("parsed-event", uid, canonicalRecurrenceIdentity ?: "MASTER")

internal fun parsedInstanceId(uid: String, canonicalOccurrenceIdentity: String): String =
    stableId("parsed-instance", uid, canonicalOccurrenceIdentity)

private fun stableId(namespace: String, vararg parts: String): String {
    val material = buildString {
        append(namespace.length).append(':').append(namespace)
        parts.forEach { part -> append(part.length).append(':').append(part) }
    }
    return MessageDigest.getInstance("SHA-256")
        .digest(material.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
}

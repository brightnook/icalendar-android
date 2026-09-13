package com.brightnook.nookcalendar.domain

import java.time.Instant
import java.time.LocalDate

data class EventDraft(
    val calendarId: String,
    val title: String,
    val location: String? = null,
    val notes: String? = null,
    val isAllDay: Boolean,
    val startInstant: Instant? = null,
    val endInstant: Instant? = null,
    val startDate: LocalDate? = null,
    val endDateExclusive: LocalDate? = null,
    val timeZoneId: String? = null,
    val reminders: List<EventReminder> = emptyList(),
)

data class EventWriteSource(
    val calendar: CalendarSummary,
    val uid: String,
    val resource: CalendarResource,
    val isRecurring: Boolean,
)

package com.brightnook.nookcalendar.caldav

import com.brightnook.nookcalendar.domain.EventDraft
import com.brightnook.nookcalendar.domain.EventReminder
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class IcsEventWriter {
    fun write(draft: EventDraft, uid: String, now: Instant): String {
        require(draft.calendarId.isNotBlank()) { "Calendar is required" }
        require(draft.title.isNotBlank()) { "Title is required" }
        require(uid.isNotBlank()) { "UID is required" }

        val lines = mutableListOf(
            "BEGIN:VCALENDAR",
            "VERSION:2.0",
            "PRODID:-//BrightNook//NookCalendar Android//EN",
            "CALSCALE:GREGORIAN",
            "BEGIN:VEVENT",
            "UID:${escape(uid)}",
            "DTSTAMP:${UTC_DATE_TIME.format(now)}",
        )
        if (draft.isAllDay) {
            val start = requireNotNull(draft.startDate) { "All-day start date is required" }
            val end = requireNotNull(draft.endDateExclusive) { "All-day end date is required" }
            require(start.isBefore(end)) { "End date must be after start date" }
            require(draft.startInstant == null && draft.endInstant == null) {
                "All-day events cannot contain timed values"
            }
            lines += "DTSTART;VALUE=DATE:${DATE.format(start)}"
            lines += "DTEND;VALUE=DATE:${DATE.format(end)}"
        } else {
            val start = requireNotNull(draft.startInstant) { "Start time is required" }
            val end = requireNotNull(draft.endInstant) { "End time is required" }
            require(start.isBefore(end)) { "End time must be after start time" }
            require(draft.startDate == null && draft.endDateExclusive == null) {
                "Timed events cannot contain date-only values"
            }
            lines += "DTSTART:${UTC_DATE_TIME.format(start)}"
            lines += "DTEND:${UTC_DATE_TIME.format(end)}"
        }
        lines += "SUMMARY:${escape(draft.title.trim())}"
        draft.location?.takeIf(String::isNotBlank)?.let { lines += "LOCATION:${escape(it)}" }
        draft.notes?.takeIf(String::isNotBlank)?.let { lines += "DESCRIPTION:${escape(it)}" }
        draft.reminders.forEach { reminder ->
            lines += "BEGIN:VALARM"
            lines += "ACTION:DISPLAY"
            lines += "DESCRIPTION:${escape(draft.title.trim())}"
            lines += when (reminder) {
                is EventReminder.Absolute -> "TRIGGER;VALUE=DATE-TIME:${UTC_DATE_TIME.format(reminder.instant)}"
                is EventReminder.RelativeToStart -> "TRIGGER:${formatDuration(reminder.offset)}"
                is EventReminder.RelativeToEnd ->
                    "TRIGGER;RELATED=END:${formatDuration(reminder.offset)}"
            }
            lines += "END:VALARM"
        }
        lines += "END:VEVENT"
        lines += "END:VCALENDAR"
        return lines.flatMap(::foldLine).joinToString("\r\n", postfix = "\r\n")
    }

    private fun escape(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\r\n", "\\n")
        .replace("\n", "\\n")
        .replace("\r", "\\n")
        .replace(",", "\\,")
        .replace(";", "\\;")

    private fun formatDuration(duration: Duration): String =
        if (duration.isNegative) "-${duration.abs()}" else duration.toString()

    private fun foldLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var bytes = 0
        line.codePoints().forEach { codePoint ->
            val text = String(Character.toChars(codePoint))
            val size = text.toByteArray(StandardCharsets.UTF_8).size
            val limit = if (result.isEmpty()) 75 else 74
            if (bytes + size > limit && current.isNotEmpty()) {
                result += if (result.isEmpty()) current.toString() else " $current"
                current = StringBuilder()
                bytes = 0
            }
            current.append(text)
            bytes += size
        }
        result += if (result.isEmpty()) current.toString() else " $current"
        return result
    }

    companion object {
        private val UTC_DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC)
        private val DATE = DateTimeFormatter.BASIC_ISO_DATE
    }
}

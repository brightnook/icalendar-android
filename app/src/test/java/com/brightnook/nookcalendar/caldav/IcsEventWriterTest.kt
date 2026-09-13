package com.brightnook.nookcalendar.caldav

import com.brightnook.nookcalendar.domain.EventDraft
import com.brightnook.nookcalendar.domain.EventReminder
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IcsEventWriterTest {
    private val writer = IcsEventWriter()
    private val now = Instant.parse("2026-09-13T00:00:00Z")

    @Test
    fun writesTimedEventWithEscapedTextAndDisplayReminders() {
        val ics = writer.write(
            timedDraft(
                title = "Plan, review; next\\step\nline",
                location = "Tokyo, JP",
                notes = "Bring; notes",
                reminders = listOf(
                    EventReminder.RelativeToStart(Duration.ofMinutes(-15)),
                    EventReminder.Absolute(Instant.parse("2026-09-14T08:00:00Z")),
                ),
            ),
            uid = "event-1@example.test",
            now = now,
        )

        assertTrue(ics.startsWith("BEGIN:VCALENDAR\r\nVERSION:2.0\r\n"))
        assertTrue(ics.contains("UID:event-1@example.test\r\n"))
        assertTrue(ics.contains("DTSTAMP:20260913T000000Z\r\n"))
        assertTrue(ics.contains("DTSTART:20260914T090000Z\r\nDTEND:20260914T100000Z\r\n"))
        assertTrue(ics.contains("SUMMARY:Plan\\, review\\; next\\\\step\\nline\r\n"))
        assertTrue(ics.contains("LOCATION:Tokyo\\, JP\r\n"))
        assertTrue(ics.contains("DESCRIPTION:Bring\\; notes\r\n"))
        assertEquals(2, "BEGIN:VALARM".toRegex().findAll(ics).count())
        assertTrue(ics.contains("TRIGGER:-PT15M\r\n"))
        assertTrue(ics.contains("TRIGGER;VALUE=DATE-TIME:20260914T080000Z\r\n"))
    }

    @Test
    fun writesAllDayEventUsingExclusiveEndDate() {
        val ics = writer.write(
            EventDraft(
                calendarId = "work",
                title = "Holiday",
                isAllDay = true,
                startDate = LocalDate.parse("2026-09-14"),
                endDateExclusive = LocalDate.parse("2026-09-16"),
            ),
            uid = "all-day@example.test",
            now = now,
        )

        assertTrue(ics.contains("DTSTART;VALUE=DATE:20260914\r\n"))
        assertTrue(ics.contains("DTEND;VALUE=DATE:20260916\r\n"))
    }

    @Test
    fun rejectsBlankTitle() {
        assertThrows(IllegalArgumentException::class.java) {
            writer.write(timedDraft(title = "  "), "event@example.test", now)
        }
    }

    @Test
    fun rejectsNonIncreasingTimedRange() {
        assertThrows(IllegalArgumentException::class.java) {
            writer.write(
                timedDraft(endInstant = Instant.parse("2026-09-14T09:00:00Z")),
                "event@example.test",
                now,
            )
        }
    }

    private fun timedDraft(
        title: String = "Planning",
        location: String? = null,
        notes: String? = null,
        endInstant: Instant = Instant.parse("2026-09-14T10:00:00Z"),
        reminders: List<EventReminder> = emptyList(),
    ) = EventDraft(
        calendarId = "work",
        title = title,
        location = location,
        notes = notes,
        isAllDay = false,
        startInstant = Instant.parse("2026-09-14T09:00:00Z"),
        endInstant = endInstant,
        timeZoneId = "Asia/Tokyo",
        reminders = reminders,
    )
}

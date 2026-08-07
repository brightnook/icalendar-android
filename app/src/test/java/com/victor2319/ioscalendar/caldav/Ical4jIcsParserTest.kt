package com.victor2319.ioscalendar.caldav

import com.victor2319.ioscalendar.domain.EventStatus
import com.victor2319.ioscalendar.domain.TimeRange
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Ical4jIcsParserTest {
    private val parser: IcsParser = Ical4jIcsParser()

    @Test
    fun allDayDtendIsExclusiveAndMissingDtendMeansOneDay() {
        val parsed = parser.parse(
            fixture("all-day.ics"),
            utcRange("2026-08-01T00:00:00Z", "2026-09-01T00:00:00Z"),
            ZoneId.of("Asia/Tokyo"),
        )

        val instances = parsed.instances.associateBy { it.uid }
        assertEquals(LocalDate.of(2026, 8, 10), instances.getValue("all-day-range@example.test").startDate)
        assertEquals(LocalDate.of(2026, 8, 13), instances.getValue("all-day-range@example.test").endDateExclusive)
        assertEquals(LocalDate.of(2026, 8, 20), instances.getValue("all-day-single@example.test").startDate)
        assertEquals(LocalDate.of(2026, 8, 21), instances.getValue("all-day-single@example.test").endDateExclusive)
        assertTrue(instances.values.all { it.isAllDay && it.startInstant == null && it.endInstant == null })
    }

    @Test
    fun tzidRecurrenceKeepsWallClockAcrossDstAndDisplayZoneCannotChangeInstant() {
        val range = utcRange("2026-03-01T00:00:00Z", "2026-04-01T00:00:00Z")
        val tokyo = parser.parse(fixture("dst-recurring-with-exception.ics"), range, ZoneId.of("Asia/Tokyo"))
        val losAngeles = parser.parse(fixture("dst-recurring-with-exception.ics"), range, ZoneId.of("America/Los_Angeles"))
        val expectedStarts = listOf(
            Instant.parse("2026-03-01T17:00:00Z"),
            Instant.parse("2026-03-08T16:00:00Z"),
            Instant.parse("2026-03-16T18:30:00Z"),
            Instant.parse("2026-03-25T16:00:00Z"),
        )

        assertEquals(expectedStarts, tokyo.instances.map { it.startInstant })
        assertEquals(expectedStarts, losAngeles.instances.map { it.startInstant })
        val sourceZone = ZoneId.of("America/Los_Angeles")
        assertEquals(
            listOf(LocalTime.of(9, 0), LocalTime.of(9, 0), LocalTime.of(11, 30), LocalTime.of(9, 0)),
            tokyo.instances.map { it.startInstant!!.atZone(sourceZone).toLocalTime() },
        )
    }

    @Test
    fun recurrenceOverrideReplacesOriginalOccurrenceAndPreservesDetails() {
        val parsed = parser.parse(
            fixture("dst-recurring-with-exception.ics"),
            utcRange("2026-03-01T00:00:00Z", "2026-04-01T00:00:00Z"),
            ZoneId.of("Asia/Tokyo"),
        )

        val master = parsed.events.single { it.recurrenceId == null }
        val override = parsed.events.single { it.recurrenceId != null }
        assertEquals("dst-series@example.test", master.uid)
        assertEquals("FREQ=WEEKLY;COUNT=4", master.recurrenceRule)
        assertEquals("America/Los_Angeles", master.originalTimeZoneId)
        assertEquals("Video room", master.location)
        assertEquals("Synthetic recurring meeting", master.notes)
        assertEquals("20260315T090000", override.recurrenceId)
        assertEquals("Moved standup", override.title)
        assertEquals("New room", override.location)
        val domainOverride = override.toDomain("work")
        assertEquals("work", domainOverride.calendarId)
        assertEquals("dst-series@example.test", domainOverride.uid)
        assertEquals("20260315T090000", domainOverride.recurrenceId)
        assertEquals("America/Los_Angeles", domainOverride.originalTimeZoneId)
        assertFalse(parsed.instances.any { it.startInstant == Instant.parse("2026-03-15T16:00:00Z") })
        assertTrue(parsed.instances.any { it.startInstant == Instant.parse("2026-03-16T18:30:00Z") })
    }

    @Test
    fun cancelledOverrideRemovesOriginalOccurrence() {
        val parsed = parser.parse(
            fixture("cancelled-instance.ics"),
            utcRange("2026-03-01T00:00:00Z", "2026-03-20T00:00:00Z"),
            ZoneId.of("America/Los_Angeles"),
        )

        assertEquals(
            listOf(Instant.parse("2026-03-01T22:00:00Z"), Instant.parse("2026-03-15T21:00:00Z")),
            parsed.instances.map { it.startInstant },
        )
        assertEquals(EventStatus.CANCELLED, parsed.events.single { it.recurrenceId != null }.status)
    }

    @Test
    fun instancesUseHalfOpenRangeAndOverridesCanMoveIntoOrOutOfRange() {
        val movedInto = parser.parse(
            fixture("dst-recurring-with-exception.ics"),
            utcRange("2026-03-16T18:30:00Z", "2026-03-16T19:30:00Z"),
            ZoneId.of("Asia/Tokyo"),
        )
        val startsAtEnd = parser.parse(
            fixture("dst-recurring-with-exception.ics"),
            utcRange("2026-03-16T17:30:00Z", "2026-03-16T18:30:00Z"),
            ZoneId.of("Asia/Tokyo"),
        )
        val movedOut = parser.parse(
            fixture("dst-recurring-with-exception.ics"),
            utcRange("2026-03-15T16:00:00Z", "2026-03-15T17:00:00Z"),
            ZoneId.of("Asia/Tokyo"),
        )

        assertEquals(listOf(Instant.parse("2026-03-16T18:30:00Z")), movedInto.instances.map { it.startInstant })
        assertTrue(startsAtEnd.instances.isEmpty())
        assertTrue(movedOut.instances.isEmpty())
    }

    @Test
    fun exdateAndRdateAlterTheExpandedSet() {
        val parsed = parser.parse(
            fixture("dst-recurring-with-exception.ics"),
            utcRange("2026-03-01T00:00:00Z", "2026-04-01T00:00:00Z"),
            ZoneId.of("America/Los_Angeles"),
        )

        assertFalse(parsed.instances.any { it.startInstant == Instant.parse("2026-03-22T16:00:00Z") })
        assertTrue(parsed.instances.any { it.startInstant == Instant.parse("2026-03-25T16:00:00Z") })
    }

    @Test
    fun rejectsRangesTooLargeForBoundedExpansion() {
        val start = Instant.parse("2026-01-01T00:00:00Z")
        val error = assertThrows(IllegalArgumentException::class.java) {
            parser.parse(
                fixture("dst-recurring-with-exception.ics"),
                TimeRange(start, start.plus(Duration.ofDays(3661))),
                ZoneId.of("UTC"),
            )
        }

        assertTrue(error.message.orEmpty().contains("range", ignoreCase = true))
    }

    @Test
    fun malformedIcsFailsWithTypedParseException() {
        val error = assertThrows(IcsParseException::class.java) {
            parser.parse(
                "BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nUID:broken@example.test\r\nEND:VCALENDAR\r\n",
                utcRange("2026-01-01T00:00:00Z", "2026-02-01T00:00:00Z"),
                ZoneId.of("UTC"),
            )
        }

        assertTrue(error.message.orEmpty().contains("iCalendar"))
    }

    @Test
    fun timedEventWithoutEndFailsInsteadOfCreatingAnInvalidDomainInstance() {
        val ics = """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//Synthetic Test//Calendar Parser//EN
            BEGIN:VEVENT
            UID:no-end@example.test
            DTSTAMP:20260801T000000Z
            DTSTART:20260810T090000Z
            SUMMARY:No end
            END:VEVENT
            END:VCALENDAR
        """.trimIndent().replace("\n", "\r\n") + "\r\n"

        assertThrows(IcsParseException::class.java) {
            parser.parse(
                ics,
                utcRange("2026-08-01T00:00:00Z", "2026-09-01T00:00:00Z"),
                ZoneId.of("UTC"),
            )
        }
    }

    private fun fixture(name: String): String =
        requireNotNull(javaClass.getResource("/ics/$name")) { "Missing fixture $name" }.readText()

    private fun utcRange(start: String, endExclusive: String) =
        TimeRange(Instant.parse(start), Instant.parse(endExclusive), ZoneId.of("UTC"))
}

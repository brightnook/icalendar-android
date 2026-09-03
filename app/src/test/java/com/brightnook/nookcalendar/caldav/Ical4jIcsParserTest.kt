package com.brightnook.nookcalendar.caldav

import com.brightnook.nookcalendar.domain.EventStatus
import com.brightnook.nookcalendar.domain.EventReminder
import com.brightnook.nookcalendar.domain.TimeRange
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.Test

class Ical4jIcsParserTest {
    private val parser: IcsParser = Ical4jIcsParser()

    @Test
    fun displayAlarmsPreserveRelativeAndAbsoluteTriggers() {
        val parsed = parser.parse(
            eventWithAlarms(),
            utcRange("2026-08-01T00:00:00Z", "2026-09-01T00:00:00Z"),
            ZoneId.of("UTC"),
        )

        assertEquals(
            listOf(
                EventReminder.RelativeToStart(Duration.ofMinutes(-15)),
                EventReminder.Absolute(Instant.parse("2026-08-10T08:30:00Z")),
                EventReminder.RelativeToEnd(Duration.ofMinutes(5)),
            ),
            parsed.events.single().reminders,
        )
    }

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
    fun standaloneCancelledMasterProducesNoInstancesButRetainsSourceEvent() {
        val parsed = parser.parse(
            cancelledMasterIcs(includeConfirmedOverride = false),
            utcRange("2026-08-01T00:00:00Z", "2026-09-01T00:00:00Z"),
            ZoneId.of("UTC"),
        )

        assertTrue(parsed.instances.isEmpty())
        assertEquals(listOf(EventStatus.CANCELLED), parsed.events.map { it.status })
    }

    @Test
    fun cancelledCanonicalMasterSuppressesConfirmedOverridesForEntireUidFamily() {
        val parsed = parser.parse(
            cancelledMasterIcs(includeConfirmedOverride = true),
            utcRange("2026-08-01T00:00:00Z", "2026-09-01T00:00:00Z"),
            ZoneId.of("UTC"),
        )

        assertTrue(parsed.instances.isEmpty())
        assertEquals(2, parsed.events.size)
        assertEquals(EventStatus.CANCELLED, parsed.events.single { it.recurrenceId == null }.status)
        assertEquals(EventStatus.CONFIRMED, parsed.events.single { it.recurrenceId != null }.status)
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
    fun occurrenceStartingBeforeRangeIsIncludedWhenItsEndOverlaps() {
        val parsed = parser.parse(
            singleEventWithTimeProperties(
                "DTSTART:20260810T093000Z",
                "DTEND:20260810T103000Z",
            ),
            utcRange("2026-08-10T10:00:00Z", "2026-08-10T11:00:00Z"),
            ZoneId.of("UTC"),
        )

        assertEquals(listOf(Instant.parse("2026-08-10T09:30:00Z")), parsed.instances.map { it.startInstant })
    }

    @Test
    fun occurrenceEndingAtRangeStartIsExcluded() {
        val parsed = parser.parse(
            singleEventWithTimeProperties(
                "DTSTART:20260810T090000Z",
                "DTEND:20260810T100000Z",
            ),
            utcRange("2026-08-10T10:00:00Z", "2026-08-10T11:00:00Z"),
            ZoneId.of("UTC"),
        )

        assertTrue(parsed.instances.isEmpty())
    }

    @Test
    fun thisAndFutureOverrideFailsClosedInsteadOfBeingAppliedToOneOccurrence() {
        assertThrows(IcsParseException::class.java) {
            parser.parse(
                thisAndFutureIcs(),
                utcRange("2026-08-01T00:00:00Z", "2026-09-01T00:00:00Z"),
                ZoneId.of("UTC"),
            )
        }
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
        val error = assertThrows(IcsParseException::class.java) {
            parser.parse(
                fixture("dst-recurring-with-exception.ics"),
                TimeRange(start, start.plus(Duration.ofDays(3661))),
                ZoneId.of("UTC"),
            )
        }

        assertTrue(error.message.orEmpty().contains("range", ignoreCase = true))
    }

    @Test
    fun sparseSecondlyRuleFailsClosedInsteadOfSilentlyTruncating() {
        val ics = singleTimedEvent("FREQ=SECONDLY;BYMONTH=12")

        assertTimeoutPreemptively(Duration.ofSeconds(2)) {
            assertThrows(IcsParseException::class.java) {
                parser.parse(
                    ics,
                    utcRange("2026-06-01T00:00:00Z", "2026-06-02T00:00:00Z"),
                    ZoneId.of("UTC"),
                )
            }
        }
    }

    @Test
    fun denseSecondlyRuleFailsClosedBeforeExpansion() {
        val ics = singleTimedEvent("FREQ=SECONDLY")

        assertTimeoutPreemptively(Duration.ofSeconds(2)) {
            assertThrows(IcsParseException::class.java) {
                parser.parse(
                    ics,
                    utcRange("2026-08-10T00:00:00Z", "2026-08-10T01:00:00Z"),
                    ZoneId.of("UTC"),
                )
            }
        }
    }

    @Test
    fun minuteAndSubMinuteRuleShapesFailClosed() {
        listOf(
            "FREQ=MINUTELY",
            "FREQ=DAILY;BYMINUTE=0,30",
            "FREQ=DAILY;BYSECOND=0,30",
        ).forEach { rule ->
            assertThrows(IcsParseException::class.java) {
                parser.parse(
                    singleTimedEvent(rule),
                    utcRange("2026-08-10T00:00:00Z", "2026-08-11T00:00:00Z"),
                    ZoneId.of("UTC"),
                )
            }
        }
    }

    @Test
    fun ordinaryDailyRuleStillExpandsWithinBudget() {
        val parsed = parser.parse(
            singleTimedEvent("FREQ=DAILY;COUNT=3"),
            utcRange("2026-08-10T00:00:00Z", "2026-08-14T00:00:00Z"),
            ZoneId.of("UTC"),
        )

        assertEquals(
            listOf(
                Instant.parse("2026-08-10T09:00:00Z"),
                Instant.parse("2026-08-11T09:00:00Z"),
                Instant.parse("2026-08-12T09:00:00Z"),
            ),
            parsed.instances.map { it.startInstant },
        )
    }

    @Test
    fun filteredHourlyRuleFailsClosedBeforeIcal4jCanSilentlyTruncate() {
        assertThrows(IcsParseException::class.java) {
            parser.parse(
                singleTimedEvent("FREQ=HOURLY;BYMONTH=12"),
                utcRange("2026-06-01T00:00:00Z", "2026-06-02T00:00:00Z"),
                ZoneId.of("UTC"),
            )
        }
    }

    @Test
    fun unsafeExruleCannotBypassRecurrencePreflight() {
        assertThrows(IcsParseException::class.java) {
            parser.parse(
                singleEventWithTimeProperties(
                    "DTSTART:20260810T090000Z",
                    "DTEND:20260810T100000Z",
                    "RRULE:FREQ=DAILY;COUNT=2",
                    "EXRULE:FREQ=SECONDLY",
                ),
                utcRange("2026-08-10T00:00:00Z", "2026-08-11T00:00:00Z"),
                ZoneId.of("UTC"),
            )
        }
    }

    @Test
    fun conservativeOutputBudgetRejectsBeforeMaterializingMoreThanTenThousandInstances() {
        val hours = (0..23).joinToString(",")
        val rdates = (0 until 400).joinToString(",") { offset ->
            LocalDate.of(2026, 1, 1).plusDays(offset.toLong())
                .format(DateTimeFormatter.BASIC_ISO_DATE) + "T120000Z"
        }
        val ics = singleEventWithTimeProperties(
            "DTSTART:20260101T000000Z",
            "DTEND:20260101T010000Z",
            "RRULE:FREQ=DAILY;BYHOUR=$hours",
            "RDATE:$rdates",
        )
        val start = Instant.parse("2026-01-01T00:00:00Z")

        assertThrows(IcsParseException::class.java) {
            parser.parse(
                ics,
                TimeRange(start, start.plus(Duration.ofDays(400))),
                ZoneId.of("UTC"),
            )
        }
    }

    @Test
    fun distantSparseDailyCountFailsBeforeIcal4jCanSilentlyTruncate() {
        val ics = singleEventWithTimeProperties(
            "DTSTART:20000101T090000Z",
            "DTEND:20000101T100000Z",
            "RRULE:FREQ=DAILY;COUNT=100000;BYMONTH=2;BYMONTHDAY=29",
        )

        assertThrows(IcsParseException::class.java) {
            parser.parse(
                ics,
                utcRange("2026-03-01T00:00:00Z", "2026-03-02T00:00:00Z"),
                ZoneId.of("UTC"),
            )
        }
    }

    @Test
    fun distantDenseHourlyCountFailsBeforeScanningHundredsOfThousandsOfIntervals() {
        val ics = singleEventWithTimeProperties(
            "DTSTART:20000101T090000Z",
            "DTEND:20000101T100000Z",
            "RRULE:FREQ=HOURLY;COUNT=1000000",
        )

        assertTimeoutPreemptively(Duration.ofSeconds(5)) {
            assertThrows(IcsParseException::class.java) {
                parser.parse(
                    ics,
                    utcRange("2026-03-01T00:00:00Z", "2026-03-02T00:00:00Z"),
                    ZoneId.of("UTC"),
                )
            }
        }
    }

    @Test
    fun distantUnfilteredHourlySmallCountStopsBeforeTheRequestedRange() {
        val parsed = parser.parse(
            singleEventWithTimeProperties(
                "DTSTART:20000101T090000Z",
                "DURATION:P15D",
                "RRULE:FREQ=HOURLY;COUNT=2",
            ),
            utcRange("2026-01-01T00:00:00Z", "2027-02-05T00:00:00Z"),
            ZoneId.of("UTC"),
        )

        assertTrue(parsed.instances.isEmpty())
    }

    @Test
    fun distantUnfilteredHourlyWithoutCountFastForwardsToTheBoundedWindow() {
        val parsed = parser.parse(
            singleEventWithTimeProperties(
                "DTSTART:20000101T090000Z",
                "DTEND:20000101T100000Z",
                "RRULE:FREQ=HOURLY",
            ),
            utcRange("2026-03-01T00:00:00Z", "2026-03-01T03:00:00Z"),
            ZoneId.of("UTC"),
        )

        assertEquals(
            listOf(
                Instant.parse("2026-03-01T00:00:00Z"),
                Instant.parse("2026-03-01T01:00:00Z"),
                Instant.parse("2026-03-01T02:00:00Z"),
            ),
            parsed.instances.map { it.startInstant },
        )
    }

    @Test
    fun distantHourlyByHourFilterRetainsTheStrictEmptyIncrementGuard() {
        assertThrows(IcsParseException::class.java) {
            parser.parse(
                singleEventWithTimeProperties(
                    "DTSTART:20000101T090000Z",
                    "DTEND:20000101T100000Z",
                    "RRULE:FREQ=HOURLY;COUNT=2;BYHOUR=9",
                ),
                utcRange("2026-03-01T00:00:00Z", "2026-03-01T03:00:00Z"),
                ZoneId.of("UTC"),
            )
        }
    }

    @Test
    fun longDurationRecurrenceFailsBeforeExpandingItsEffectiveQueryWindow() {
        val ics = singleEventWithTimeProperties(
            "DTSTART:20260810T090000Z",
            "DURATION:P10001D",
            "RRULE:FREQ=DAILY;COUNT=2",
        )

        assertThrows(IcsParseException::class.java) {
            parser.parse(
                ics,
                utcRange("2026-08-10T00:00:00Z", "2026-08-11T00:00:00Z"),
                ZoneId.of("UTC"),
            )
        }
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

    @Test
    fun invalidTimedEventOutsideRequestedRangeStillFailsAtSourceValidation() {
        val ics = singleEventWithTimeProperties(
            "DTSTART:20250101T090000Z",
        )

        assertThrows(IcsParseException::class.java) {
            parser.parse(
                ics,
                utcRange("2026-08-01T00:00:00Z", "2026-09-01T00:00:00Z"),
                ZoneId.of("UTC"),
            )
        }
    }

    @Test
    fun allDayEqualOrDecreasingDtendFailsAtSourceValidation() {
        listOf("20260810", "20260809").forEach { endDate ->
            assertThrows(IcsParseException::class.java) {
                parser.parse(
                    singleEventWithTimeProperties(
                        "DTSTART;VALUE=DATE:20260810",
                        "DTEND;VALUE=DATE:$endDate",
                    ),
                    utcRange("2026-09-01T00:00:00Z", "2026-10-01T00:00:00Z"),
                    ZoneId.of("UTC"),
                )
            }
        }
    }

    @Test
    fun timedDurationProducesExclusiveEnd() {
        val parsed = parser.parse(
            singleEventWithTimeProperties(
                "DTSTART:20260810T090000Z",
                "DURATION:PT90M",
            ),
            utcRange("2026-08-10T00:00:00Z", "2026-08-11T00:00:00Z"),
            ZoneId.of("UTC"),
        )

        assertEquals(Instant.parse("2026-08-10T10:30:00Z"), parsed.instances.single().endInstant)
    }

    @Test
    fun allDayDurationProducesExclusiveEndDate() {
        val parsed = parser.parse(
            singleEventWithTimeProperties(
                "DTSTART;VALUE=DATE:20260810",
                "DURATION:P2D",
            ),
            utcRange("2026-08-01T00:00:00Z", "2026-09-01T00:00:00Z"),
            ZoneId.of("UTC"),
        )

        assertEquals(LocalDate.of(2026, 8, 12), parsed.instances.single().endDateExclusive)
    }

    @Test
    fun zeroDurationIsRejectedForTimedAndAllDayEvents() {
        listOf(
            listOf("DTSTART:20260810T090000Z", "DURATION:PT0S"),
            listOf("DTSTART;VALUE=DATE:20260810", "DURATION:P0D"),
        ).forEach { properties ->
            assertThrows(IcsParseException::class.java) {
                parser.parse(
                    singleEventWithTimeProperties(*properties.toTypedArray()),
                    utcRange("2026-09-01T00:00:00Z", "2026-10-01T00:00:00Z"),
                    ZoneId.of("UTC"),
                )
            }
        }
    }

    @Test
    fun floatingEventIdsAreStableAcrossDisplayZones() {
        val ics = singleEventWithTimeProperties(
            "DTSTART:20260810T090000",
            "DTEND:20260810T100000",
        )
        val range = utcRange("2026-08-01T00:00:00Z", "2026-09-01T00:00:00Z")

        val tokyo = parser.parse(ics, range, ZoneId.of("Asia/Tokyo"))
        val losAngeles = parser.parse(ics, range, ZoneId.of("America/Los_Angeles"))

        assertEquals(tokyo.events.single().id, losAngeles.events.single().id)
        assertEquals(tokyo.instances.single().instanceId, losAngeles.instances.single().instanceId)
        assertFalse(tokyo.instances.single().startInstant == losAngeles.instances.single().startInstant)
    }

    @Test
    fun equivalentUtcAndTzidRecurrenceIdentitiesProduceTheSameStableIds() {
        val range = utcRange("2026-03-01T00:00:00Z", "2026-04-01T00:00:00Z")
        val utc = parser.parse(
            equivalentRecurrenceIdentityIcs("RECURRENCE-ID:20260315T160000Z"),
            range,
            ZoneId.of("Asia/Tokyo"),
        )
        val tzid = parser.parse(
            equivalentRecurrenceIdentityIcs(
                "RECURRENCE-ID;TZID=America/Los_Angeles:20260315T090000",
            ),
            range,
            ZoneId.of("Asia/Tokyo"),
        )

        val utcOverride = utc.events.single { it.recurrenceId != null }
        val tzidOverride = tzid.events.single { it.recurrenceId != null }
        assertEquals(utcOverride.id, tzidOverride.id)
        assertEquals(
            utc.instances.single { it.eventId == utcOverride.id }.instanceId,
            tzid.instances.single { it.eventId == tzidOverride.id }.instanceId,
        )
    }

    @Test
    fun domainIdsAreCalendarScopedAndInstanceLinkageMatchesMappedEvent() {
        val parsed = parser.parse(
            fixture("all-day.ics"),
            utcRange("2026-08-01T00:00:00Z", "2026-09-01T00:00:00Z"),
            ZoneId.of("UTC"),
        )
        val parsedEvent = parsed.events.single { it.uid == "all-day-range@example.test" }
        val parsedInstance = parsed.instances.single { it.eventId == parsedEvent.id }

        val workEvent = parsedEvent.toDomain("work")
        val workInstance = parsedInstance.toDomain("work", 0xff000000.toInt())
        val homeEvent = parsedEvent.toDomain("home")
        val homeInstance = parsedInstance.toDomain("home", 0xff000000.toInt())

        assertEquals(workEvent.id, workInstance.eventId)
        assertEquals(homeEvent.id, homeInstance.eventId)
        assertFalse(workEvent.id == homeEvent.id)
        assertFalse(workInstance.instanceId == homeInstance.instanceId)
    }

    private fun fixture(name: String): String =
        requireNotNull(javaClass.getResource("/ics/$name")) { "Missing fixture $name" }.readText()

    private fun eventWithAlarms(): String = """
        BEGIN:VCALENDAR
        VERSION:2.0
        PRODID:-//Synthetic Test//Calendar Parser//EN
        BEGIN:VEVENT
        UID:alarms@example.test
        DTSTAMP:20260801T000000Z
        DTSTART:20260810T090000Z
        DTEND:20260810T100000Z
        SUMMARY:Reminder test
        BEGIN:VALARM
        ACTION:DISPLAY
        TRIGGER:-PT15M
        DESCRIPTION:Before start
        END:VALARM
        BEGIN:VALARM
        ACTION:DISPLAY
        TRIGGER;VALUE=DATE-TIME:20260810T083000Z
        DESCRIPTION:Absolute
        END:VALARM
        BEGIN:VALARM
        ACTION:DISPLAY
        TRIGGER;RELATED=END:PT5M
        DESCRIPTION:After end
        END:VALARM
        BEGIN:VALARM
        ACTION:EMAIL
        TRIGGER:-PT10M
        DESCRIPTION:Ignored
        END:VALARM
        END:VEVENT
        END:VCALENDAR
    """.trimIndent().replace("\n", "\r\n") + "\r\n"

    private fun singleTimedEvent(rule: String): String = """
        BEGIN:VCALENDAR
        VERSION:2.0
        PRODID:-//Synthetic Test//Calendar Parser//EN
        BEGIN:VEVENT
        UID:rule-test@example.test
        DTSTAMP:20260801T000000Z
        DTSTART:20260810T090000Z
        DTEND:20260810T100000Z
        RRULE:$rule
        SUMMARY:Rule test
        END:VEVENT
        END:VCALENDAR
    """.trimIndent().replace("\n", "\r\n") + "\r\n"

    private fun cancelledMasterIcs(includeConfirmedOverride: Boolean): String {
        val master = """
            BEGIN:VEVENT
            UID:cancelled-master@example.test
            DTSTAMP:20260801T000000Z
            DTSTART:20260810T090000Z
            DTEND:20260810T100000Z
            RRULE:FREQ=DAILY;COUNT=3
            STATUS:CANCELLED
            SUMMARY:Cancelled family
            END:VEVENT
        """.trimIndent()
        val override = """
            BEGIN:VEVENT
            UID:cancelled-master@example.test
            DTSTAMP:20260801T000000Z
            RECURRENCE-ID:20260811T090000Z
            DTSTART:20260811T110000Z
            DTEND:20260811T120000Z
            STATUS:CONFIRMED
            SUMMARY:Confirmed override
            END:VEVENT
        """.trimIndent()
        return buildList {
            add("BEGIN:VCALENDAR")
            add("VERSION:2.0")
            add("PRODID:-//Synthetic Test//Calendar Parser//EN")
            add(master)
            if (includeConfirmedOverride) add(override)
            add("END:VCALENDAR")
        }.joinToString("\r\n") + "\r\n"
    }

    private fun singleEventWithTimeProperties(vararg timeProperties: String): String = buildList {
        add("BEGIN:VCALENDAR")
        add("VERSION:2.0")
        add("PRODID:-//Synthetic Test//Calendar Parser//EN")
        add("BEGIN:VEVENT")
        add("UID:source-validation@example.test")
        add("DTSTAMP:20260801T000000Z")
        addAll(timeProperties)
        add("SUMMARY:Source validation")
        add("END:VEVENT")
        add("END:VCALENDAR")
    }.joinToString("\r\n") + "\r\n"

    private fun equivalentRecurrenceIdentityIcs(recurrenceIdLine: String): String = buildList {
        add("BEGIN:VCALENDAR")
        add("VERSION:2.0")
        add("PRODID:-//Synthetic Test//Calendar Parser//EN")
        add("BEGIN:VEVENT")
        add("UID:equivalent-recurrence@example.test")
        add("DTSTAMP:20260201T000000Z")
        add("DTSTART;TZID=America/Los_Angeles:20260315T090000")
        add("DTEND;TZID=America/Los_Angeles:20260315T100000")
        add("RRULE:FREQ=DAILY;COUNT=2")
        add("SUMMARY:Equivalent recurrence")
        add("END:VEVENT")
        add("BEGIN:VEVENT")
        add("UID:equivalent-recurrence@example.test")
        add("DTSTAMP:20260201T000000Z")
        add(recurrenceIdLine)
        add("DTSTART:20260315T180000Z")
        add("DTEND:20260315T190000Z")
        add("SUMMARY:Equivalent override")
        add("END:VEVENT")
        add("END:VCALENDAR")
    }.joinToString("\r\n") + "\r\n"

    private fun thisAndFutureIcs(): String = buildList {
        add("BEGIN:VCALENDAR")
        add("VERSION:2.0")
        add("PRODID:-//Synthetic Test//Calendar Parser//EN")
        add("BEGIN:VEVENT")
        add("UID:this-and-future@example.test")
        add("DTSTAMP:20260801T000000Z")
        add("DTSTART:20260810T090000Z")
        add("DTEND:20260810T100000Z")
        add("RRULE:FREQ=DAILY;COUNT=3")
        add("SUMMARY:Series")
        add("END:VEVENT")
        add("BEGIN:VEVENT")
        add("UID:this-and-future@example.test")
        add("DTSTAMP:20260801T000000Z")
        add("RECURRENCE-ID;RANGE=THISANDFUTURE:20260811T090000Z")
        add("DTSTART:20260811T110000Z")
        add("DTEND:20260811T120000Z")
        add("SUMMARY:Shifted series")
        add("END:VEVENT")
        add("END:VCALENDAR")
    }.joinToString("\r\n") + "\r\n"

    private fun utcRange(start: String, endExclusive: String) =
        TimeRange(Instant.parse(start), Instant.parse(endExclusive), ZoneId.of("UTC"))
}

package com.victor2319.ioscalendar.caldav

import com.victor2319.ioscalendar.domain.TimeRange
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.Temporal
import net.fortuna.ical4j.model.Period
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.DtStart

internal class RecurrenceExpander {
    fun expand(event: VEvent, range: TimeRange, displayZone: ZoneId): List<ExpandedOccurrence> {
        val start = event.getProperty<DtStart<Temporal>>(Property.DTSTART).orElseThrow {
            IcsParseException("VEVENT is missing DTSTART")
        }.date
        val periods = calculatePeriods(event, start, range, displayZone)
        return periods.map { period ->
            ExpandedOccurrence(
                identity = period.start,
                start = period.start,
                end = period.end,
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun calculatePeriods(
        event: VEvent,
        start: Temporal,
        range: TimeRange,
        displayZone: ZoneId,
    ): Set<Period<out Temporal>> =
        when (start) {
            is LocalDate -> event.calculateRecurrenceSet<LocalDate>(
                Period(
                    range.start.atZone(displayZone).toLocalDate(),
                    range.endExclusive.atZone(displayZone).toLocalDate().plusDays(1),
                ),
            ) as Set<Period<out Temporal>>
            is ZonedDateTime -> event.calculateRecurrenceSet<ZonedDateTime>(
                Period(range.start.atZone(start.zone), range.endExclusive.atZone(start.zone)),
            ) as Set<Period<out Temporal>>
            is OffsetDateTime -> event.calculateRecurrenceSet<OffsetDateTime>(
                Period(range.start.atOffset(start.offset), range.endExclusive.atOffset(start.offset)),
            ) as Set<Period<out Temporal>>
            is LocalDateTime -> event.calculateRecurrenceSet<LocalDateTime>(
                Period(
                    range.start.atZone(displayZone).toLocalDateTime(),
                    range.endExclusive.atZone(displayZone).toLocalDateTime(),
                ),
            ) as Set<Period<out Temporal>>
            is Instant -> event.calculateRecurrenceSet<Instant>(
                Period(range.start, range.endExclusive),
            ) as Set<Period<out Temporal>>
            else -> throw IcsParseException("Unsupported DTSTART type: ${start.javaClass.simpleName}")
        }
}

internal data class ExpandedOccurrence(
    val identity: Temporal,
    val start: Temporal,
    val end: Temporal,
)

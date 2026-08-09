package com.victor2319.ioscalendar.caldav

import com.victor2319.ioscalendar.domain.TimeRange
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.Temporal
import kotlin.math.max
import net.fortuna.ical4j.model.Period
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.DateListProperty
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.RDate
import net.fortuna.ical4j.model.property.RRule
import net.fortuna.ical4j.transform.recurrence.Frequency

internal class RecurrenceExpander {
    fun expand(event: VEvent, range: TimeRange, displayZone: ZoneId): List<ExpandedOccurrence> {
        val start = event.getProperty<DtStart<Temporal>>(Property.DTSTART).orElseThrow {
            IcsParseException("VEVENT is missing DTSTART")
        }.date
        validateBudget(event, range)
        val periods = calculatePeriods(event, start, range, displayZone)
        if (periods.size > MAX_OUTPUT_INSTANCES) {
            throw IcsParseException("Recurrence expansion exceeds $MAX_OUTPUT_INSTANCES instances")
        }
        return periods.map { period ->
            ExpandedOccurrence(
                identity = period.start,
                start = period.start,
                end = period.end,
            )
        }
    }

    private fun validateBudget(event: VEvent, range: TimeRange) {
        if (event.getProperties<Property>(Property.EXRULE).isNotEmpty()) {
            throw IcsParseException("EXRULE is not supported safely")
        }
        val rules: List<RRule<Temporal>> = event.getProperties(Property.RRULE)
        if (rules.size > 1) {
            throw IcsParseException("Multiple RRULE properties are not supported safely")
        }
        val rdateCount = event.getProperties<DateListProperty<Temporal>>(Property.RDATE).sumOf { property ->
            val periods = (property as? RDate<Temporal>)?.periods?.orElse(emptySet()) ?: emptySet()
            property.dates.size + periods.size
        }
        val recurrenceUpperBound = rules.singleOrNull()?.let { rule ->
            val recur = rule.recur
            if (recur.frequency !in SAFE_FREQUENCIES) {
                throw IcsParseException("Unsupported recurrence frequency: ${recur.frequency}")
            }
            if (recur.minuteList.isNotEmpty() || recur.secondList.isNotEmpty()) {
                throw IcsParseException("BYMINUTE and BYSECOND recurrence rules are not supported safely")
            }
            if (recur.frequency == Frequency.HOURLY && hasCalendarDateFilter(recur)) {
                throw IcsParseException("Filtered HOURLY recurrence rules are not supported safely")
            }
            val rangeHours = ChronoUnit.HOURS.between(range.start, range.endExclusive) + 2
            val rangeDays = ChronoUnit.DAYS.between(
                range.start.atZone(ZoneId.of("UTC")).toLocalDate(),
                range.endExclusive.atZone(ZoneId.of("UTC")).toLocalDate(),
            ) + 2
            val conservative = if (recur.frequency == Frequency.HOURLY) {
                rangeHours
            } else {
                rangeDays * max(1, recur.hourList.size)
            }
            if (recur.count > 0) minOf(conservative, recur.count.toLong()) else conservative
        } ?: 1L
        if (recurrenceUpperBound + rdateCount > MAX_OUTPUT_INSTANCES) {
            throw IcsParseException("Recurrence expansion exceeds $MAX_OUTPUT_INSTANCES instances")
        }
    }

    private fun hasCalendarDateFilter(recur: net.fortuna.ical4j.model.Recur<Temporal>): Boolean =
        recur.dayList.isNotEmpty() ||
            recur.monthDayList.isNotEmpty() ||
            recur.monthList.isNotEmpty() ||
            recur.weekNoList.isNotEmpty() ||
            recur.yearDayList.isNotEmpty()

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

    private companion object {
        const val MAX_OUTPUT_INSTANCES = 10_000
        val SAFE_FREQUENCIES = setOf(
            Frequency.HOURLY,
            Frequency.DAILY,
            Frequency.WEEKLY,
            Frequency.MONTHLY,
            Frequency.YEARLY,
        )
    }
}

internal data class ExpandedOccurrence(
    val identity: Temporal,
    val start: Temporal,
    val end: Temporal,
)

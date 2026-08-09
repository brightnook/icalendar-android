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
import net.fortuna.ical4j.model.property.RDate
import net.fortuna.ical4j.model.property.RRule
import net.fortuna.ical4j.transform.recurrence.Frequency

internal class RecurrenceExpander {
    fun expand(
        event: VEvent,
        sourceSpan: SourceSpan,
        range: TimeRange,
        displayZone: ZoneId,
    ): List<ExpandedOccurrence> {
        val queryWindow = queryWindow(sourceSpan.start, range, displayZone)
        validateBudget(event, sourceSpan, queryWindow)
        val periods = calculatePeriods(event, sourceSpan.start, queryWindow)
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

    private fun validateBudget(event: VEvent, sourceSpan: SourceSpan, queryWindow: TemporalWindow) {
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
            val interval = max(1, recur.interval)
            val iterationWork = recurrenceSteps(
                sourceSpan.start,
                queryWindow.endExclusive,
                recur.frequency,
                interval,
            )
            val effectiveWindowWork = saturatedAdd(
                recurrenceSteps(queryWindow.start, queryWindow.endExclusive, recur.frequency, interval),
                recurrenceSteps(sourceSpan.start, sourceSpan.endExclusive, recur.frequency, interval),
            )
            if (iterationWork > MAX_RECURRENCE_WORK || effectiveWindowWork > MAX_RECURRENCE_WORK) {
                throw IcsParseException("Recurrence expansion exceeds $MAX_RECURRENCE_WORK work units")
            }
            if (hasPotentiallyEmptyIncrement(recur) && iterationWork > MAX_SAFE_EMPTY_INCREMENT_WORK) {
                throw IcsParseException("Filtered recurrence may exceed iCal4j's safe increment limit")
            }

            val outputUpperBound = if (recur.frequency == Frequency.HOURLY) {
                effectiveWindowWork
            } else {
                saturatedMultiply(
                    saturatedAdd(
                        calendarDays(queryWindow.start, queryWindow.endExclusive),
                        calendarDays(sourceSpan.start, sourceSpan.endExclusive),
                    ),
                    max(1, recur.hourList.size).toLong(),
                )
            }
            if (recur.count > 0) minOf(outputUpperBound, recur.count.toLong()) else outputUpperBound
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

    private fun hasPotentiallyEmptyIncrement(recur: net.fortuna.ical4j.model.Recur<Temporal>): Boolean =
        hasCalendarDateFilter(recur) || recur.setPosList.isNotEmpty()

    private fun recurrenceSteps(
        start: Temporal,
        endExclusive: Temporal,
        frequency: Frequency,
        interval: Int,
    ): Long {
        val startDateTime = start.toBudgetDateTime()
        val endDateTime = endExclusive.toBudgetDateTime()
        if (!startDateTime.isBefore(endDateTime)) return 1L
        val baseUnits = when (frequency) {
            Frequency.HOURLY -> ChronoUnit.HOURS.between(startDateTime, endDateTime)
            Frequency.DAILY -> ChronoUnit.DAYS.between(startDateTime.toLocalDate(), endDateTime.toLocalDate())
            Frequency.WEEKLY -> ceilDiv(
                ChronoUnit.DAYS.between(startDateTime.toLocalDate(), endDateTime.toLocalDate()),
                DAYS_PER_WEEK,
            )
            Frequency.MONTHLY -> ChronoUnit.MONTHS.between(
                startDateTime.withDayOfMonth(1).toLocalDate(),
                endDateTime.withDayOfMonth(1).toLocalDate(),
            )
            Frequency.YEARLY -> ChronoUnit.YEARS.between(
                startDateTime.toLocalDate(),
                endDateTime.toLocalDate(),
            )
            else -> MAX_RECURRENCE_WORK + 1L
        }.coerceAtLeast(0L)
        return ceilDiv(saturatedAdd(baseUnits, WORK_MARGIN), interval.toLong())
    }

    private fun calendarDays(start: Temporal, endExclusive: Temporal): Long {
        val startDateTime = start.toBudgetDateTime()
        val endDateTime = endExclusive.toBudgetDateTime()
        if (!startDateTime.isBefore(endDateTime)) return 1L
        return saturatedAdd(
            ChronoUnit.DAYS.between(startDateTime.toLocalDate(), endDateTime.toLocalDate()).coerceAtLeast(0L),
            WORK_MARGIN,
        )
    }

    private fun Temporal.toBudgetDateTime(): LocalDateTime = when (this) {
        is LocalDate -> atStartOfDay()
        is LocalDateTime -> this
        is Instant -> atZone(UTC).toLocalDateTime()
        is ZonedDateTime -> toLocalDateTime()
        is OffsetDateTime -> toLocalDateTime()
        else -> throw IcsParseException("Unsupported recurrence date type: ${javaClass.simpleName}")
    }

    private fun ceilDiv(value: Long, divisor: Long): Long =
        if (value == 0L) 0L else ((value - 1L) / divisor) + 1L

    private fun saturatedAdd(left: Long, right: Long): Long =
        if (left >= BUDGET_OVERFLOW || right >= BUDGET_OVERFLOW - left) BUDGET_OVERFLOW else left + right

    private fun saturatedMultiply(left: Long, right: Long): Long =
        if (left == 0L || right == 0L) 0L
        else if (left >= BUDGET_OVERFLOW / right) BUDGET_OVERFLOW
        else left * right

    private fun queryWindow(start: Temporal, range: TimeRange, displayZone: ZoneId): TemporalWindow =
        when (start) {
            is LocalDate -> TemporalWindow(
                range.start.atZone(displayZone).toLocalDate(),
                range.endExclusive.atZone(displayZone).toLocalDate().plusDays(1),
            )
            is ZonedDateTime -> TemporalWindow(
                range.start.atZone(start.zone),
                range.endExclusive.atZone(start.zone),
            )
            is OffsetDateTime -> TemporalWindow(
                range.start.atOffset(start.offset),
                range.endExclusive.atOffset(start.offset),
            )
            is LocalDateTime -> TemporalWindow(
                range.start.atZone(displayZone).toLocalDateTime(),
                range.endExclusive.atZone(displayZone).toLocalDateTime(),
            )
            is Instant -> TemporalWindow(range.start, range.endExclusive)
            else -> throw IcsParseException("Unsupported DTSTART type: ${start.javaClass.simpleName}")
        }

    @Suppress("UNCHECKED_CAST")
    private fun calculatePeriods(
        event: VEvent,
        start: Temporal,
        queryWindow: TemporalWindow,
    ): Set<Period<out Temporal>> =
        when (start) {
            is LocalDate -> event.calculateRecurrenceSet<LocalDate>(
                Period(
                    queryWindow.start as LocalDate,
                    queryWindow.endExclusive as LocalDate,
                ),
            ) as Set<Period<out Temporal>>
            is ZonedDateTime -> event.calculateRecurrenceSet<ZonedDateTime>(
                Period(
                    queryWindow.start as ZonedDateTime,
                    queryWindow.endExclusive as ZonedDateTime,
                ),
            ) as Set<Period<out Temporal>>
            is OffsetDateTime -> event.calculateRecurrenceSet<OffsetDateTime>(
                Period(
                    queryWindow.start as OffsetDateTime,
                    queryWindow.endExclusive as OffsetDateTime,
                ),
            ) as Set<Period<out Temporal>>
            is LocalDateTime -> event.calculateRecurrenceSet<LocalDateTime>(
                Period(
                    queryWindow.start as LocalDateTime,
                    queryWindow.endExclusive as LocalDateTime,
                ),
            ) as Set<Period<out Temporal>>
            is Instant -> event.calculateRecurrenceSet<Instant>(
                Period(queryWindow.start as Instant, queryWindow.endExclusive as Instant),
            ) as Set<Period<out Temporal>>
            else -> throw IcsParseException("Unsupported DTSTART type: ${start.javaClass.simpleName}")
        }

    private companion object {
        const val MAX_OUTPUT_INSTANCES = 10_000
        const val MAX_RECURRENCE_WORK = 10_000L
        const val MAX_SAFE_EMPTY_INCREMENT_WORK = 999L
        const val WORK_MARGIN = 2L
        const val DAYS_PER_WEEK = 7L
        const val BUDGET_OVERFLOW = Long.MAX_VALUE
        val UTC: ZoneId = ZoneId.of("UTC")
        val SAFE_FREQUENCIES = setOf(
            Frequency.HOURLY,
            Frequency.DAILY,
            Frequency.WEEKLY,
            Frequency.MONTHLY,
            Frequency.YEARLY,
        )
    }
}

private data class TemporalWindow(val start: Temporal, val endExclusive: Temporal)

internal data class ExpandedOccurrence(
    val identity: Temporal,
    val start: Temporal,
    val end: Temporal,
)

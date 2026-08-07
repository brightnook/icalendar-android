package com.victor2319.ioscalendar.caldav

import com.victor2319.ioscalendar.domain.EventStatus
import com.victor2319.ioscalendar.domain.TimeRange
import java.io.StringReader
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.Temporal
import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.model.Component
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.RecurrenceId

class Ical4jIcsParser : IcsParser {
    private val recurrenceExpander = RecurrenceExpander()
    override fun parse(ics: String, range: TimeRange, displayZone: ZoneId): ParsedCalendar {
        require(Duration.between(range.start, range.endExclusive) <= MAX_EXPANSION_RANGE) {
            "iCalendar expansion range must not exceed ${MAX_EXPANSION_RANGE.toDays()} days"
        }
        if (ics.isBlank()) throw IcsParseException("iCalendar input is empty")

        try {
            val calendar = CalendarBuilder().build(StringReader(ics))
            val validation = calendar.validate()
            if (validation.hasErrors()) {
                throw IcsParseException("Invalid iCalendar data: $validation")
            }
            val components: List<VEvent> = calendar.getComponents(Component.VEVENT)
            val sourceEvents = components.map(::sourceEvent)
            val parsedEvents = sourceEvents.map { it.parsed }
            val instances = sourceEvents
                .groupBy { it.parsed.uid }
                .flatMap { (_, family) -> expandFamily(family, range, displayZone) }
                .sortedWith(instanceComparator)
            return ParsedCalendar(events = parsedEvents, instances = instances)
        } catch (error: IcsParseException) {
            throw error
        } catch (error: Exception) {
            throw IcsParseException("Unable to parse iCalendar data", error)
        }
    }

    private fun expandFamily(
        family: List<SourceEvent>,
        range: TimeRange,
        displayZone: ZoneId,
    ): List<ParsedInstance> {
        val masters = family.filter { it.recurrenceIdentity == null }
        val overrides = family.filter { it.recurrenceIdentity != null }
        val overrideKeys = overrides.mapTo(mutableSetOf()) { temporalKey(it.recurrenceIdentity!!, displayZone) }
        val expanded = mutableListOf<ParsedInstance>()

        masters.filter { it.parsed.status != EventStatus.CANCELLED }.forEach { master ->
            recurrenceExpander.expand(master.component, range, displayZone)
                .asSequence()
                .filterNot { temporalKey(it.identity, displayZone) in overrideKeys }
                .map { master.toInstance(it, displayZone) }
                .filter { it.overlaps(range, displayZone) }
                .forEach(expanded::add)
        }

        overrides.filter { it.parsed.status != EventStatus.CANCELLED }.forEach { override ->
            val occurrence = recurrenceExpander.expand(override.component, range, displayZone).singleOrNull()
                ?: override.singleOccurrence()
            override.toInstance(occurrence, displayZone)
                .takeIf { it.overlaps(range, displayZone) }
                ?.let(expanded::add)
        }

        return expanded.distinctBy { it.instanceId }
    }

    private fun sourceEvent(component: VEvent): SourceEvent {
        val uid = component.getUid().orElseThrow {
            IcsParseException("VEVENT is missing UID")
        }.value
        val startProperty = component.getProperty<DtStart<Temporal>>(Property.DTSTART).orElseThrow {
            IcsParseException("VEVENT $uid is missing DTSTART")
        }
        val recurrenceProperty = component.getProperty<RecurrenceId<Temporal>>(Property.RECURRENCE_ID).orElse(null)
        val recurrenceId = recurrenceProperty?.value
        val status = when (component.status?.value?.uppercase()) {
            "CANCELLED" -> EventStatus.CANCELLED
            "TENTATIVE" -> EventStatus.TENTATIVE
            else -> EventStatus.CONFIRMED
        }
        val parsed = ParsedEvent(
            id = eventId(uid, recurrenceId),
            uid = uid,
            recurrenceId = recurrenceId,
            title = component.summary?.value.orEmpty(),
            location = component.location?.value,
            notes = component.description?.value,
            originalTimeZoneId = startProperty.getParameter<Parameter>(Parameter.TZID).map { it.value }.orElse(null),
            isAllDay = startProperty.date is LocalDate,
            recurrenceRule = component.getProperty<Property>(Property.RRULE).map { it.value }.orElse(null),
            status = status,
        )
        return SourceEvent(
            component = component,
            parsed = parsed,
            recurrenceIdentity = recurrenceProperty?.date,
        )
    }

    private fun SourceEvent.singleOccurrence(): ExpandedOccurrence {
        val start = component.getProperty<DtStart<Temporal>>(Property.DTSTART).orElseThrow().date
        val end = component.getEndDate<Temporal>().map { it.date }.orElseGet {
            if (start is LocalDate) start.plusDays(1) else throw IcsParseException(
                "Timed VEVENT ${parsed.uid} is missing DTEND",
            )
        }
        return ExpandedOccurrence(identity = recurrenceIdentity ?: start, start = start, end = end)
    }

    private fun SourceEvent.toInstance(
        occurrence: ExpandedOccurrence,
        displayZone: ZoneId,
    ): ParsedInstance {
        val occurrenceKey = recurrenceIdentity?.let { temporalKey(it, displayZone) }
            ?: temporalKey(occurrence.identity, displayZone)
        return if (parsed.isAllDay) {
            val start = occurrence.start as? LocalDate
                ?: throw IcsParseException("All-day VEVENT ${parsed.uid} has a non-date DTSTART")
            val end = occurrence.end as? LocalDate
                ?: throw IcsParseException("All-day VEVENT ${parsed.uid} has a non-date DTEND")
            ParsedInstance(
                instanceId = instanceId(parsed.uid, occurrenceKey),
                eventId = parsed.id,
                uid = parsed.uid,
                recurrenceId = parsed.recurrenceId,
                title = parsed.title,
                location = parsed.location,
                isAllDay = true,
                startInstant = null,
                endInstant = null,
                startDate = start,
                endDateExclusive = end,
            )
        } else {
            val start = occurrence.start.toInstant(displayZone)
            val end = occurrence.end.toInstant(displayZone)
            if (!start.isBefore(end)) {
                throw IcsParseException("Timed VEVENT ${parsed.uid} must have an increasing time range")
            }
            ParsedInstance(
                instanceId = instanceId(parsed.uid, occurrenceKey),
                eventId = parsed.id,
                uid = parsed.uid,
                recurrenceId = parsed.recurrenceId,
                title = parsed.title,
                location = parsed.location,
                isAllDay = false,
                startInstant = start,
                endInstant = end,
                startDate = null,
                endDateExclusive = null,
            )
        }
    }

    private fun ParsedInstance.overlaps(range: TimeRange, displayZone: ZoneId): Boolean =
        if (isAllDay) {
            val rangeStartDate = range.start.atZone(displayZone).toLocalDate()
            val rangeEndDateExclusive = range.endExclusive.atZone(displayZone).toLocalDate().let { date ->
                if (range.endExclusive.atZone(displayZone).toLocalTime() == java.time.LocalTime.MIDNIGHT) date else date.plusDays(1)
            }
            startDate!! < rangeEndDateExclusive && endDateExclusive!! > rangeStartDate
        } else {
            startInstant!! < range.endExclusive && endInstant!! > range.start
        }

    private fun Temporal.toInstant(displayZone: ZoneId): Instant = when (this) {
        is Instant -> this
        is ZonedDateTime -> toInstant()
        is OffsetDateTime -> toInstant()
        is LocalDateTime -> atZone(displayZone).toInstant()
        else -> throw IcsParseException("Unsupported date-time type: ${javaClass.simpleName}")
    }

    private fun temporalKey(value: Temporal, displayZone: ZoneId): String = when (value) {
        is LocalDate -> "D:$value"
        is Instant -> "I:$value"
        is ZonedDateTime -> "I:${value.toInstant()}"
        is OffsetDateTime -> "I:${value.toInstant()}"
        is LocalDateTime -> "L:${value.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}@$displayZone"
        else -> throw IcsParseException("Unsupported recurrence date type: ${value.javaClass.simpleName}")
    }

    private data class SourceEvent(
        val component: VEvent,
        val parsed: ParsedEvent,
        val recurrenceIdentity: Temporal?,
    )

    companion object {
        private val MAX_EXPANSION_RANGE: Duration = Duration.ofDays(400)
        private val instanceComparator = compareBy<ParsedInstance>(
            { it.startDate },
            { it.startInstant },
            { it.instanceId },
        )

        private fun eventId(uid: String, recurrenceId: String?): String =
            if (recurrenceId == null) uid else "$uid::override::$recurrenceId"

        private fun instanceId(uid: String, occurrenceKey: String): String = "$uid::instance::$occurrenceKey"
    }
}

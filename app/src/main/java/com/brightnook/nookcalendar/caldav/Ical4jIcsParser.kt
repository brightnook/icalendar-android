package com.brightnook.nookcalendar.caldav

import com.brightnook.nookcalendar.domain.EventStatus
import com.brightnook.nookcalendar.domain.EventReminder
import com.brightnook.nookcalendar.domain.TimeRange
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
import java.time.temporal.TemporalAmount
import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.model.Component
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.TimeZoneRegistry
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.DtEnd
import net.fortuna.ical4j.model.property.RecurrenceId
import net.fortuna.ical4j.model.property.Duration as IcalDuration

class Ical4jIcsParser : IcsParser {
    private val recurrenceExpander = RecurrenceExpander()
    override fun parse(ics: String, range: TimeRange, displayZone: ZoneId): ParsedCalendar {
        if (Duration.between(range.start, range.endExclusive) > MAX_EXPANSION_RANGE) {
            throw IcsParseException(
                "iCalendar expansion range must not exceed ${MAX_EXPANSION_RANGE.toDays()} days",
            )
        }
        if (ics.isBlank()) throw IcsParseException("iCalendar input is empty")

        try {
            // Android blocks ical4j's custom ZoneRulesProvider when a VTIMEZONE is registered.
            // Use Android's platform zone database for canonical TZIDs instead of registering one.
            val calendar = CalendarBuilder(null as TimeZoneRegistry?).build(StringReader(ics))
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
        if (masters.any { it.parsed.status == EventStatus.CANCELLED }) return emptyList()
        val overrideKeys = overrides.mapTo(mutableSetOf()) { canonicalTemporalIdentity(it.recurrenceIdentity!!) }
        val expanded = mutableListOf<ParsedInstance>()

        masters.filter { it.parsed.status != EventStatus.CANCELLED }.forEach { master ->
            recurrenceExpander.expand(master.component, master.sourceSpan, range, displayZone)
                .asSequence()
                .filterNot { canonicalTemporalIdentity(it.identity) in overrideKeys }
                .map { master.toInstance(it, displayZone) }
                .filter { it.overlaps(range, displayZone) }
                .forEach(expanded::add)
        }

        overrides.filter { it.parsed.status != EventStatus.CANCELLED }.forEach { override ->
            val occurrence = recurrenceExpander.expand(
                override.component,
                override.sourceSpan,
                range,
                displayZone,
            ).singleOrNull()
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
        if (recurrenceProperty?.getParameter<Parameter>(Parameter.RANGE)?.isPresent == true) {
            throw IcsParseException("RECURRENCE-ID RANGE=THISANDFUTURE is not supported")
        }
        val recurrenceId = recurrenceProperty?.value
        val recurrenceIdentity = recurrenceProperty?.date
        val sourceSpan = validatedSourceSpan(component, uid, startProperty.date)
        val status = when (component.status?.value?.uppercase()) {
            "CANCELLED" -> EventStatus.CANCELLED
            "TENTATIVE" -> EventStatus.TENTATIVE
            else -> EventStatus.CONFIRMED
        }
        val parsed = ParsedEvent(
            id = parsedEventId(uid, recurrenceIdentity?.let(::canonicalTemporalIdentity)),
            uid = uid,
            recurrenceId = recurrenceId,
            title = component.summary?.value.orEmpty(),
            location = component.location?.value,
            notes = component.description?.value,
            originalTimeZoneId = startProperty.getParameter<Parameter>(Parameter.TZID).map { it.value }.orElse(null),
            isAllDay = startProperty.date is LocalDate,
            recurrenceRule = component.getProperty<Property>(Property.RRULE).map { it.value }.orElse(null),
            status = status,
            reminders = component.getComponents<VAlarm>(Component.VALARM)
                .mapNotNull(::displayReminder),
        )
        return SourceEvent(
            component = component,
            parsed = parsed,
            recurrenceIdentity = recurrenceIdentity,
            sourceSpan = sourceSpan,
        )
    }

    private fun displayReminder(alarm: VAlarm): EventReminder? {
        if (alarm.action.map { it.value.equals("DISPLAY", ignoreCase = true) }.orElse(false).not()) {
            return null
        }
        val trigger = alarm.trigger.orElse(null) ?: return null
        if (trigger.isAbsolute) {
            return (trigger.date as? Instant)?.let(EventReminder::Absolute)
        }
        val offset = runCatching { Duration.from(trigger.duration) }.getOrNull() ?: return null
        val relatedToEnd = trigger.getParameter<Parameter>(Parameter.RELATED)
            .map { it.value.equals("END", ignoreCase = true) }
            .orElse(false)
        return if (relatedToEnd) EventReminder.RelativeToEnd(offset) else EventReminder.RelativeToStart(offset)
    }

    private fun SourceEvent.singleOccurrence(): ExpandedOccurrence {
        val start = sourceSpan.start
        val end = sourceSpan.endExclusive
        return ExpandedOccurrence(identity = recurrenceIdentity ?: start, start = start, end = end)
    }

    private fun validatedSourceSpan(component: VEvent, uid: String, start: Temporal): SourceSpan {
        val explicitEnd = component.getProperty<DtEnd<Temporal>>(Property.DTEND).map { it.date }.orElse(null)
        val duration = component.getProperty<IcalDuration>(Property.DURATION).map { it.duration }.orElse(null)
        val end = when {
            explicitEnd != null -> explicitEnd
            duration != null -> addDuration(start, duration, uid)
            start is LocalDate -> start.plusDays(1)
            else -> throw IcsParseException("Timed VEVENT $uid is missing DTEND or DURATION")
        }
        if (!isStrictlyBefore(start, end)) {
            throw IcsParseException("VEVENT $uid must have an increasing start and exclusive end")
        }
        return SourceSpan(start = start, endExclusive = end)
    }

    private fun addDuration(start: Temporal, amount: TemporalAmount, uid: String): Temporal =
        if (start is LocalDate && amount is Duration) {
            if (amount.nano != 0 || amount.seconds % SECONDS_PER_DAY != 0L) {
                throw IcsParseException("All-day VEVENT $uid requires a whole-day DURATION")
            }
            start.plusDays(amount.toDays())
        } else {
            amount.addTo(start)
        }

    private fun isStrictlyBefore(start: Temporal, end: Temporal): Boolean = when {
        start is LocalDate && end is LocalDate -> start.isBefore(end)
        start is LocalDateTime && end is LocalDateTime -> start.isBefore(end)
        start is Instant && end is Instant -> start.isBefore(end)
        start is ZonedDateTime && end is ZonedDateTime -> start.toInstant().isBefore(end.toInstant())
        start is OffsetDateTime && end is OffsetDateTime -> start.toInstant().isBefore(end.toInstant())
        else -> throw IcsParseException(
            "DTSTART and DTEND use incompatible value types: ${start.javaClass.simpleName}/${end.javaClass.simpleName}",
        )
    }

    private fun SourceEvent.toInstance(
        occurrence: ExpandedOccurrence,
        displayZone: ZoneId,
    ): ParsedInstance {
        val occurrenceKey = recurrenceIdentity?.let(::canonicalTemporalIdentity)
            ?: canonicalTemporalIdentity(occurrence.identity)
        return if (parsed.isAllDay) {
            val start = occurrence.start as? LocalDate
                ?: throw IcsParseException("All-day VEVENT ${parsed.uid} has a non-date DTSTART")
            val end = occurrence.end as? LocalDate
                ?: throw IcsParseException("All-day VEVENT ${parsed.uid} has a non-date DTEND")
            ParsedInstance(
                instanceId = parsedInstanceId(parsed.uid, occurrenceKey),
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
                instanceId = parsedInstanceId(parsed.uid, occurrenceKey),
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

    private fun canonicalTemporalIdentity(value: Temporal): String = when (value) {
        is LocalDate -> "D:$value"
        is Instant -> "I:$value"
        is ZonedDateTime -> "I:${value.toInstant()}"
        is OffsetDateTime -> "I:${value.toInstant()}"
        is LocalDateTime -> "L:${value.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}"
        else -> throw IcsParseException("Unsupported recurrence date type: ${value.javaClass.simpleName}")
    }

    private data class SourceEvent(
        val component: VEvent,
        val parsed: ParsedEvent,
        val recurrenceIdentity: Temporal?,
        val sourceSpan: SourceSpan,
    )

    companion object {
        private val MAX_EXPANSION_RANGE: Duration = Duration.ofDays(400)
        private const val SECONDS_PER_DAY = 86_400L
        private val instanceComparator = compareBy<ParsedInstance>(
            { it.startDate },
            { it.startInstant },
            { it.instanceId },
        )

    }
}

internal data class SourceSpan(val start: Temporal, val endExclusive: Temporal)

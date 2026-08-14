package com.victor2319.ioscalendar.sync

import com.victor2319.ioscalendar.auth.CredentialsStore
import com.victor2319.ioscalendar.caldav.CalDavFailure
import com.victor2319.ioscalendar.caldav.CalDavGateway
import com.victor2319.ioscalendar.caldav.IcsParseException
import com.victor2319.ioscalendar.caldav.IcsParser
import com.victor2319.ioscalendar.caldav.RemoteCalendar
import com.victor2319.ioscalendar.caldav.RemoteResource
import com.victor2319.ioscalendar.domain.CalendarDelta
import com.victor2319.ioscalendar.domain.CalendarEvent
import com.victor2319.ioscalendar.domain.CalendarRepository
import com.victor2319.ioscalendar.domain.CalendarSnapshot
import com.victor2319.ioscalendar.domain.CalendarSummary
import com.victor2319.ioscalendar.domain.ClockProvider
import com.victor2319.ioscalendar.domain.EventInstance
import com.victor2319.ioscalendar.domain.TimeRange
import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Duration
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.withLock

class DefaultSyncCoordinator(
    private val credentialsStore: CredentialsStore,
    private val gateway: CalDavGateway,
    private val parser: IcsParser,
    private val repository: CalendarRepository,
    private val clock: ClockProvider,
) : SyncCoordinator {
    override suspend fun sync(request: SyncRequest): SyncResult = SyncMutex.instance.withLock {
        syncLocked(request)
    }

    private suspend fun syncLocked(request: SyncRequest): SyncResult {
        val credentials = credentialsStore.load() ?: return SyncResult.AuthenticationRequired()
        val calendars = try {
            val principal = gateway.authenticate(credentials)
            gateway.discoverCalendars(principal)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: CalDavFailure.AuthenticationRejected) {
            return SyncResult.AuthenticationRequired()
        } catch (failure: Throwable) {
            return SyncResult.RetryableFailure(failure.category())
        }

        val succeededIds = linkedSetOf<String>()
        val failures = linkedMapOf<String, SyncFailureCategory>()
        var counts = SyncCounts()
        val initialRange = initialRange()

        for (calendar in calendars) {
            val calendarId = stableCalendarId(calendar.href)
            try {
                val calendarCounts = when (request) {
                    SyncRequest.Initial -> replaceSnapshot(calendar, calendarId, initialRange)
                    SyncRequest.Refresh -> refresh(calendar, calendarId, initialRange)
                    is SyncRequest.EnsureMonth -> ensureMonth(calendar, calendarId, request.month)
                }
                succeededIds += calendarId
                counts += calendarCounts
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: CalDavFailure.AuthenticationRejected) {
                return SyncResult.AuthenticationRequired(counts, succeededIds)
            } catch (failure: Throwable) {
                failures[calendarId] = failure.category()
            }
        }

        if (failures.isEmpty()) return SyncResult.Success(counts, succeededIds)
        if (counts.calendars > 0) {
            return SyncResult.PartialSuccess(counts, succeededIds, failures)
        }
        val categories = failures.values.toSet()
        return if (categories.all { it == SyncFailureCategory.TEMPORARY || it == SyncFailureCategory.NETWORK }) {
            SyncResult.RetryableFailure(categories.first())
        } else {
            SyncResult.PartialSuccess(counts, succeededIds, failures)
        }
    }

    private suspend fun refresh(
        calendar: RemoteCalendar,
        calendarId: String,
        parseRange: TimeRange,
    ): SyncCounts {
        val state = repository.getSyncState(calendarId)
        val token = state.syncToken?.takeIf(String::isNotBlank)
            ?: return replaceSnapshot(calendar, calendarId, parseRange)
        val delta = gateway.fetchDelta(calendar, token)
        val parseRanges = state.coverage.ifEmpty { listOf(parseRange) }.flatMap(TimeRange::chunks)
        val parsed = parseResources(delta.upserts, calendarId, calendarColor(calendar.color), parseRanges)
        repository.applyCalendarDelta(
            CalendarDelta(
                calendarId = calendarId,
                upsertEvents = parsed.events,
                upsertInstances = parsed.instances,
                deletedEventUids = emptySet(),
                deletedInstanceIds = emptySet(),
                syncToken = delta.syncToken,
                deletedResourceHrefs = delta.tombstones.mapTo(linkedSetOf()) { canonicalHref(it.href) },
                replacedResourceHrefs = delta.upserts.mapTo(linkedSetOf()) { canonicalHref(it.href) },
            ),
        )
        return SyncCounts(1, parsed.events.size, parsed.instances.size)
    }

    private suspend fun ensureMonth(
        calendar: RemoteCalendar,
        calendarId: String,
        month: YearMonth,
    ): SyncCounts {
        val range = monthRange(month, clock.zoneId)
        val covered = repository.getSyncState(calendarId).coverage.any { coverage ->
            coverage.zoneId == range.zoneId &&
                !coverage.start.isAfter(range.start) &&
                !coverage.endExclusive.isBefore(range.endExclusive)
        }
        if (covered) return SyncCounts(1, 0, 0)
        return replaceSnapshot(calendar, calendarId, range)
    }

    private suspend fun replaceSnapshot(
        calendar: RemoteCalendar,
        calendarId: String,
        range: TimeRange,
    ): SyncCounts {
        val resources = mutableListOf<RemoteResource>()
        var syncToken: String? = null
        for (chunk in range.chunks()) {
            val snapshot = gateway.fetchRange(calendar, chunk)
            resources += snapshot.resources
            syncToken = snapshot.syncToken ?: syncToken
        }
        val colorArgb = calendarColor(calendar.color)
        val parsed = parseResources(
            resources.distinctBy(RemoteResource::href),
            calendarId,
            colorArgb,
            range.chunks(),
        )
        repository.replaceCalendarSnapshot(
            CalendarSnapshot(
                calendar = CalendarSummary(
                    id = calendarId,
                    name = calendar.displayName,
                    colorArgb = colorArgb,
                    isVisible = true,
                ),
                events = parsed.events,
                instances = parsed.instances,
                syncToken = syncToken,
                coverage = range,
            ),
        )
        return SyncCounts(1, parsed.events.size, parsed.instances.size)
    }

    private fun parseResources(
        resources: List<RemoteResource>,
        calendarId: String,
        calendarColorArgb: Int,
        ranges: List<TimeRange>,
    ): ParsedPayload {
        val parsedResources = resources.flatMap { resource ->
            val href = canonicalHref(resource.href)
            ranges.map { range ->
                href to parser.parse(resource.calendarData, range, range.zoneId)
            }
        }
        val ownedEvents = parsedResources.flatMap { (href, parsed) ->
            parsed.events.map { OwnedEvent(href, it.toDomain(calendarId, href)) }
        }
        val events = ownedEvents.groupBy { it.event.id }.map { (_, values) ->
            if (values.map { it.href }.distinct().size != 1 || values.map { it.event }.distinct().size != 1) {
                throw IcsParseException("Conflicting iCalendar event identifiers")
            }
            values.first().event
        }
        val eventOwners = ownedEvents.associate { it.event.id to it.href }
        val ownedInstances = parsedResources.flatMap { (href, parsed) ->
            parsed.instances.map { OwnedInstance(href, it.toDomain(calendarId, calendarColorArgb)) }
        }
        val instances = ownedInstances.groupBy { it.instance.instanceId }.map { (_, values) ->
            if (
                values.map { it.href }.distinct().size != 1 ||
                values.map { it.instance }.distinct().size != 1 ||
                values.any { eventOwners[it.instance.eventId] != it.href }
            ) {
                throw IcsParseException("Conflicting iCalendar instance identifiers")
            }
            values.first().instance
        }
        return ParsedPayload(events, instances)
    }

    private fun initialRange(): TimeRange {
        val current = YearMonth.from(clock.now.atZone(clock.zoneId))
        return TimeRange(
            start = current.atDay(1).atStartOfDay(clock.zoneId).toInstant(),
            endExclusive = current.plusMonths(13).atDay(1).atStartOfDay(clock.zoneId).toInstant(),
            zoneId = clock.zoneId,
        )
    }

    private fun monthRange(month: YearMonth, zoneId: ZoneId) = TimeRange(
        start = month.atDay(1).atStartOfDay(zoneId).toInstant(),
        endExclusive = month.plusMonths(1).atDay(1).atStartOfDay(zoneId).toInstant(),
        zoneId = zoneId,
    )
}

private data class ParsedPayload(
    val events: List<CalendarEvent>,
    val instances: List<EventInstance>,
)

private data class OwnedEvent(val href: String, val event: CalendarEvent)

private data class OwnedInstance(val href: String, val instance: EventInstance)

private fun TimeRange.chunks(): List<TimeRange> {
    val chunks = mutableListOf<TimeRange>()
    var cursor = start
    while (cursor < endExclusive) {
        val next = minOf(cursor.plus(Duration.ofDays(400)), endExclusive)
        chunks += TimeRange(cursor, next, zoneId)
        cursor = next
    }
    return chunks
}

private fun Throwable.category(): SyncFailureCategory = when (this) {
    is CalDavFailure.AuthenticationRejected -> SyncFailureCategory.AUTHENTICATION
    is CalDavFailure.Temporary -> SyncFailureCategory.TEMPORARY
    is CalDavFailure.NetworkUnavailable -> SyncFailureCategory.NETWORK
    is CalDavFailure.InvalidServerData, is IcsParseException -> SyncFailureCategory.INVALID_DATA
    else -> SyncFailureCategory.REPOSITORY
}

private fun stableCalendarId(href: String): String = stableHash("icloud-calendar", canonicalHref(href))

private fun stableHash(namespace: String, value: String): String {
    val material = "$namespace:${value.length}:$value"
    return MessageDigest.getInstance("SHA-256")
        .digest(material.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
}

private fun canonicalHref(href: String): String {
    val normalized = URI(href).normalize()
    if (!normalized.isAbsolute) return normalized.rawPath ?: href
    val scheme = normalized.scheme.lowercase()
    val host = normalized.host?.lowercase() ?: return normalized.toASCIIString()
    val port = normalized.port.takeUnless { it == -1 || scheme == "https" && it == 443 || scheme == "http" && it == 80 }
    return buildString {
        append(scheme).append("://").append(host)
        port?.let { append(':').append(it) }
        append(normalized.rawPath.orEmpty().ifEmpty { "/" })
        normalized.rawQuery?.let { append('?').append(it) }
    }
}

private fun calendarColor(value: String?): Int {
    val hex = value?.removePrefix("#")
    return when (hex?.length) {
        6 -> (0xFF000000L or hex.toLong(16)).toInt()
        8 -> ((hex.takeLast(2).toLong(16) shl 24) or hex.take(6).toLong(16)).toInt()
        else -> 0xFF4F6BED.toInt()
    }
}

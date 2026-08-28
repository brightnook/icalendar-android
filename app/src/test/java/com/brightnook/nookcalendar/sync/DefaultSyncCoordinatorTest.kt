package com.brightnook.nookcalendar.sync

import com.brightnook.nookcalendar.auth.Credentials
import com.brightnook.nookcalendar.auth.CredentialsStore
import com.brightnook.nookcalendar.caldav.CalDavFailure
import com.brightnook.nookcalendar.caldav.CalDavGateway
import com.brightnook.nookcalendar.caldav.IcsParseException
import com.brightnook.nookcalendar.caldav.IcsParser
import com.brightnook.nookcalendar.caldav.Ical4jIcsParser
import com.brightnook.nookcalendar.caldav.ParsedCalendar
import com.brightnook.nookcalendar.caldav.ParsedEvent
import com.brightnook.nookcalendar.caldav.ParsedInstance
import com.brightnook.nookcalendar.caldav.Principal
import com.brightnook.nookcalendar.caldav.RemoteCalendar
import com.brightnook.nookcalendar.caldav.RemoteDelta
import com.brightnook.nookcalendar.caldav.RemoteResource
import com.brightnook.nookcalendar.caldav.RemoteSnapshot
import com.brightnook.nookcalendar.caldav.RemoteTombstone
import com.brightnook.nookcalendar.domain.CalendarDelta
import com.brightnook.nookcalendar.domain.CalendarEvent
import com.brightnook.nookcalendar.domain.CalendarRepository
import com.brightnook.nookcalendar.domain.CalendarSnapshot
import com.brightnook.nookcalendar.domain.CalendarSummary
import com.brightnook.nookcalendar.domain.CalendarSyncState
import com.brightnook.nookcalendar.domain.ClockProvider
import com.brightnook.nookcalendar.domain.EventDetail
import com.brightnook.nookcalendar.domain.EventInstance
import com.brightnook.nookcalendar.domain.EventStatus
import com.brightnook.nookcalendar.domain.TimeRange
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultSyncCoordinatorTest {
    private val tokyo = ZoneId.of("Asia/Tokyo")
    private val now = Instant.parse("2026-03-15T08:00:00Z")

    @Test
    fun initialUsesDstSafeThirteenMonthBoundsAndOneSession() = runTest {
        // Catches UTC month boundaries, a twelve-month end, oversized gateway calls, and per-calendar login.
        val gateway = FakeGateway(
            calendars = listOf(
                remote("work", "/calendars/work/", "Work", "#112233FF"),
                remote("home", "/calendars/home/", "Home", null),
            ),
        )
        val parser = FakeParser()
        val repository = FakeRepository()
        val coordinator = coordinator(gateway, parser, repository)

        val result = coordinator.sync(SyncRequest.Initial) as SyncResult.Success

        val expected = TimeRange(
            start = Instant.parse("2026-02-28T15:00:00Z"),
            endExclusive = Instant.parse("2027-03-31T15:00:00Z"),
            zoneId = tokyo,
        )
        assertEquals(1, gateway.authenticateCount)
        assertEquals(1, gateway.discoverCount)
        assertEquals(listOf(expected, expected), gateway.rangeCalls.map { it.second })
        assertTrue(gateway.rangeCalls.all { Duration.between(it.second.start, it.second.endExclusive) <= Duration.ofDays(400) })
        assertEquals(listOf(expected, expected), parser.calls.map { it.range })
        assertTrue(parser.calls.all { it.zoneId == tokyo })
        assertEquals(2, repository.snapshots.size)
        assertEquals(setOf("Work", "Home"), repository.snapshots.map { it.calendar.name }.toSet())
        assertEquals(2, result.counts.calendars)
    }

    @Test
    fun ensureMonthFetchesOnlyTheExactUncoveredMonth() = runTest {
        // Catches on-demand sync downloading adjacent or older months.
        val gateway = FakeGateway(calendars = listOf(remote("work", "/calendars/work/")))
        val repository = FakeRepository()
        val coordinator = coordinator(gateway, FakeParser(), repository)

        coordinator.sync(SyncRequest.EnsureMonth(YearMonth.of(2026, 10)))

        assertEquals(
            TimeRange(
                Instant.parse("2026-09-30T15:00:00Z"),
                Instant.parse("2026-10-31T15:00:00Z"),
                tokyo,
            ),
            gateway.rangeCalls.single().second,
        )
    }

    @Test
    fun ensureMonthSkipsACompleteCoverageInTheSameZone() = runTest {
        // Catches treating coverage in the requested zone as missing or accepting another zone's coverage.
        val calendar = remote("work", "/calendars/work/")
        val gateway = FakeGateway(calendars = listOf(calendar))
        val repository = FakeRepository()
        val id = stableIdFor(calendar)
        repository.states[id] = CalendarSyncState(
            syncToken = "saved",
            coverage = listOf(
                TimeRange(
                    Instant.parse("2026-09-01T00:00:00Z"),
                    Instant.parse("2026-12-01T00:00:00Z"),
                    ZoneId.of("UTC"),
                ),
                TimeRange(
                    Instant.parse("2026-09-30T15:00:00Z"),
                    Instant.parse("2026-10-31T15:00:00Z"),
                    tokyo,
                ),
            ),
        )

        val result = coordinator(gateway, FakeParser(), repository)
            .sync(SyncRequest.EnsureMonth(YearMonth.of(2026, 10))) as SyncResult.Success

        assertTrue(gateway.rangeCalls.isEmpty())
        assertTrue(repository.snapshots.isEmpty())
        assertEquals(setOf(id), result.calendarIds)
        assertEquals(SyncCounts(calendars = 1, events = 0, instances = 0), result.counts)
    }

    @Test
    fun refreshUsesSavedNonblankTokenAndMapsTheWholeDelta() = runTest {
        // Catches preferring discovery tokens, dropping delta resources, or guessing UIDs from resource names.
        val calendar = remote("work", "/calendars/work/", syncToken = "discovery-token")
        val gateway = FakeGateway(
            calendars = listOf(calendar),
            deltas = mutableMapOf(
                "work" to RemoteDelta(
                    upserts = listOf(
                        resource("/calendars/work/alpha.ics", "alpha"),
                        resource("/calendars/work/beta.ics", "beta"),
                    ),
                    tombstones = listOf(RemoteTombstone("HTTPS://CALDAV.ICLOUD.COM:443/calendars/a/../work/not-the-uid.ics")),
                    syncToken = "next-token",
                ),
            ),
        )
        val repository = FakeRepository()
        val id = stableIdFor(calendar)
        repository.states[id] = CalendarSyncState("saved-token", emptyList())

        val result = coordinator(gateway, FakeParser(), repository).sync(SyncRequest.Refresh) as SyncResult.Success

        assertEquals(listOf("saved-token"), gateway.deltaCalls.map { it.second })
        assertTrue(gateway.rangeCalls.isEmpty())
        val delta = repository.deltas.single()
        assertEquals(setOf("alpha", "beta"), delta.upsertEvents.map { it.uid }.toSet())
        assertEquals(
            setOf("https://caldav.icloud.com/calendars/work/not-the-uid.ics"),
            delta.deletedResourceHrefs,
        )
        assertEquals(
            setOf(
                "/calendars/work/alpha.ics",
                "/calendars/work/beta.ics",
            ),
            delta.upsertEvents.map { it.remoteResourceHref }.toSet(),
        )
        assertTrue(delta.deletedEventUids.isEmpty())
        assertEquals("next-token", delta.syncToken)
        assertEquals(SyncCounts(1, 2, 2), result.counts)
    }

    @Test
    fun refreshWithBlankSavedTokenUsesTheBoundedInitialSnapshot() = runTest {
        // Catches trusting a remote discovery token before local state has committed a matching baseline.
        val calendar = remote("work", "/calendars/work/", syncToken = "remote-only")
        val gateway = FakeGateway(calendars = listOf(calendar))
        val repository = FakeRepository()
        val id = stableIdFor(calendar)
        repository.states[id] = CalendarSyncState("   ", emptyList())

        coordinator(gateway, FakeParser(), repository).sync(SyncRequest.Refresh)

        assertTrue(gateway.deltaCalls.isEmpty())
        assertEquals(1, gateway.rangeCalls.size)
        assertEquals(1, repository.snapshots.size)
    }

    @Test
    fun refreshRebuildsChangedResourcesAcrossEveryCommittedCoverageChunk() = runTest {
        // Catches parsing a delta only in the initial window or passing more than 400 days to the parser.
        val calendar = remote("work", "/calendars/work/")
        val gateway = FakeGateway(
            calendars = listOf(calendar),
            deltas = mutableMapOf(
                "work" to RemoteDelta(
                    upserts = listOf(resource("/calendars/work/a.ics", "shared")),
                    tombstones = emptyList(),
                    syncToken = "next-token",
                ),
            ),
        )
        val repository = FakeRepository()
        val id = stableIdFor(calendar)
        repository.states[id] = CalendarSyncState(
            syncToken = "saved-token",
            coverage = listOf(
                TimeRange(
                    Instant.parse("2025-01-01T00:00:00Z"),
                    Instant.parse("2026-02-06T00:00:00Z"),
                    tokyo,
                ),
                TimeRange(
                    Instant.parse("2027-01-01T00:00:00Z"),
                    Instant.parse("2027-01-02T00:00:00Z"),
                    tokyo,
                ),
            ),
        )
        val parser = FakeParser()

        val result = coordinator(gateway, parser, repository).sync(SyncRequest.Refresh)

        assertTrue(result is SyncResult.Success)
        assertEquals(
            listOf(
                TimeRange(
                    Instant.parse("2025-01-01T00:00:00Z"),
                    Instant.parse("2026-02-05T00:00:00Z"),
                    tokyo,
                ),
                TimeRange(
                    Instant.parse("2026-02-05T00:00:00Z"),
                    Instant.parse("2026-02-06T00:00:00Z"),
                    tokyo,
                ),
                TimeRange(
                    Instant.parse("2027-01-01T00:00:00Z"),
                    Instant.parse("2027-01-02T00:00:00Z"),
                    tokyo,
                ),
            ),
            parser.calls.map { it.range },
        )
        assertTrue(parser.calls.all { it.zoneId == tokyo })
        assertEquals("next-token", repository.states.getValue(id).syncToken)
    }

    @Test
    fun refreshNormalizesOverlappingMixedZoneCoverageToTheClockZoneForFloatingEvents() = runTest {
        // Catches parsing one floating occurrence in two display zones and rejecting its stable instance ID.
        val calendar = remote("work", "/calendars/work/")
        val gateway = FakeGateway(
            calendars = listOf(calendar),
            deltas = mutableMapOf(
                "work" to RemoteDelta(
                    upserts = listOf(resource("/calendars/work/floating.ics", floatingEventIcs())),
                    tombstones = emptyList(),
                    syncToken = "next-token",
                ),
            ),
        )
        val repository = FakeRepository()
        val id = stableIdFor(calendar)
        val start = Instant.parse("2026-03-01T00:00:00Z")
        val end = Instant.parse("2026-04-01T00:00:00Z")
        repository.states[id] = CalendarSyncState(
            syncToken = "saved-token",
            coverage = listOf(
                TimeRange(start, end, ZoneId.of("America/Los_Angeles")),
                TimeRange(start, end, tokyo),
            ),
        )

        val result = coordinator(gateway, Ical4jIcsParser(), repository).sync(SyncRequest.Refresh)

        assertTrue(result is SyncResult.Success)
        val delta = repository.deltas.single()
        assertEquals(1, delta.upsertInstances.size)
        assertEquals(Instant.parse("2026-03-15T00:00:00Z"), delta.upsertInstances.single().startInstant)
        assertEquals("next-token", repository.states.getValue(id).syncToken)
    }

    @Test
    fun refreshRejectsTheSameStableEventIdFromTwoCanonicalResourcesWithoutAdvancingToken() = runTest {
        // Catches list-order ownership of one UID when two resources produce the same stable event and instance IDs.
        val calendar = remote("work", "/calendars/work/")
        val gateway = FakeGateway(
            calendars = listOf(calendar),
            deltas = mutableMapOf(
                "work" to RemoteDelta(
                    upserts = listOf(
                        resource("/calendars/work/a.ics", "shared"),
                        resource("/calendars/work/b.ics", "shared"),
                    ),
                    tombstones = emptyList(),
                    syncToken = "must-not-commit",
                ),
            ),
        )
        val repository = FakeRepository()
        val id = stableIdFor(calendar)
        repository.states[id] = CalendarSyncState("saved-token", emptyList())

        val result = coordinator(gateway, FakeParser(), repository).sync(SyncRequest.Refresh)

        assertTrue(result is SyncResult.PartialSuccess)
        assertEquals(
            mapOf(id to SyncFailureCategory.INVALID_DATA),
            (result as SyncResult.PartialSuccess).failures,
        )
        assertTrue(repository.deltas.isEmpty())
        assertEquals("saved-token", repository.states.getValue(id).syncToken)
    }

    @Test
    fun refreshRejectsTheSameStableInstanceIdFromTwoCanonicalResourcesWithoutAdvancingToken() = runTest {
        // Catches independently deduplicating instances and pairing one resource's instance with another owner.
        val calendar = remote("work", "/calendars/work/")
        val gateway = FakeGateway(
            calendars = listOf(calendar),
            deltas = mutableMapOf(
                "work" to RemoteDelta(
                    upserts = listOf(
                        resource("/calendars/work/a.ics", "a"),
                        resource("/calendars/work/b.ics", "b"),
                    ),
                    tombstones = emptyList(),
                    syncToken = "must-not-commit",
                ),
            ),
        )
        val repository = FakeRepository()
        val id = stableIdFor(calendar)
        repository.states[id] = CalendarSyncState("saved-token", emptyList())

        val result = coordinator(gateway, InstanceCollisionParser(), repository).sync(SyncRequest.Refresh)

        assertTrue(result is SyncResult.PartialSuccess)
        assertEquals(
            mapOf(id to SyncFailureCategory.INVALID_DATA),
            (result as SyncResult.PartialSuccess).failures,
        )
        assertTrue(repository.deltas.isEmpty())
        assertEquals("saved-token", repository.states.getValue(id).syncToken)
    }

    @Test
    fun repositoryFailureDoesNotPublishTheNewTokenAndOtherCalendarsContinue() = runTest {
        // Catches saving a token before its transaction or aborting the loop after one calendar fails.
        val failed = remote("failed", "/calendars/failed/")
        val good = remote("good", "/calendars/good/")
        val gateway = FakeGateway(calendars = listOf(failed, good))
        val repository = FakeRepository()
        val failedId = stableIdFor(failed)
        val goodId = stableIdFor(good)
        repository.failSnapshotIds += failedId

        val result = coordinator(gateway, FakeParser(), repository).sync(SyncRequest.Initial) as SyncResult.PartialSuccess

        assertEquals(setOf(goodId), result.calendarIds)
        assertEquals(mapOf(failedId to SyncFailureCategory.REPOSITORY), result.failures)
        assertFalse(repository.states.containsKey(failedId))
        assertTrue(repository.states.containsKey(goodId))
        assertEquals(SyncCounts(1, 1, 1), result.counts)
    }

    @Test
    fun authenticationRejectedMidRunStopsLaterCalendarsButKeepsCompletedCounts() = runTest {
        // Catches retrying authentication, continuing after rejection, or discarding earlier committed counts.
        val good = remote("good", "/calendars/good/")
        val rejected = remote("rejected", "/calendars/rejected/")
        val never = remote("never", "/calendars/never/")
        val gateway = FakeGateway(calendars = listOf(good, rejected, never)).apply {
            failures["rejected"] = CalDavFailure.AuthenticationRejected()
        }
        val repository = FakeRepository()
        val goodId = stableIdFor(good)

        val result = coordinator(gateway, FakeParser(), repository).sync(SyncRequest.Initial) as SyncResult.AuthenticationRequired

        assertEquals(setOf("good", "rejected"), gateway.rangeCalls.map { it.first.id }.toSet())
        assertEquals(setOf(goodId), result.calendarIds)
        assertEquals(SyncCounts(1, 1, 1), result.counts)
        assertEquals(1, gateway.authenticateCount)
    }

    @Test
    fun missingCredentialsRequiresAuthenticationWithoutGatewayWork() = runTest {
        // Catches attempting network operations with no credentials.
        val gateway = FakeGateway(calendars = listOf(remote("work", "/calendars/work/")))
        val result = coordinator(gateway, FakeParser(), FakeRepository(), credentials = null)
            .sync(SyncRequest.Initial)

        assertEquals(SyncResult.AuthenticationRequired(), result)
        assertEquals(0, gateway.authenticateCount)
        assertEquals(0, gateway.discoverCount)
    }

    @Test
    fun temporaryFailureIsRetryableWithZeroSuccessAndPartialAfterOneSuccess() = runTest {
        // Catches reporting a per-calendar outage as permanent or losing partial work.
        val down = remote("down", "/calendars/down/")
        val zeroGateway = FakeGateway(calendars = listOf(down)).apply {
            failures["down"] = CalDavFailure.Temporary(30)
        }
        val zero = coordinator(zeroGateway, FakeParser(), FakeRepository()).sync(SyncRequest.Initial)
        assertEquals(SyncResult.RetryableFailure(SyncFailureCategory.TEMPORARY), zero)

        val good = remote("good", "/calendars/good/")
        val partialGateway = FakeGateway(calendars = listOf(good, down)).apply {
            failures["down"] = CalDavFailure.NetworkUnavailable()
        }
        val repository = FakeRepository()
        val downId = stableIdFor(down)
        val partial = coordinator(partialGateway, FakeParser(), repository)
            .sync(SyncRequest.Initial) as SyncResult.PartialSuccess
        assertEquals(mapOf(downId to SyncFailureCategory.NETWORK), partial.failures)
        assertEquals(1, partial.counts.calendars)
    }

    @Test
    fun invalidResourceIsRecordedWithoutLeakingItsPayload() = runTest {
        // Catches a parser failure terminating discovery siblings or exposing ICS content in results.
        val bad = remote("bad", "/calendars/bad/")
        val good = remote("good", "/calendars/good/")
        val gateway = FakeGateway(calendars = listOf(bad, good)).apply {
            resourceContents["bad"] = "SECRET-TITLE"
        }
        val repository = FakeRepository()
        val badId = stableIdFor(bad)
        val parser = FakeParser(failContent = "SECRET-TITLE")

        val result = coordinator(gateway, parser, repository).sync(SyncRequest.Initial) as SyncResult.PartialSuccess

        assertEquals(mapOf(badId to SyncFailureCategory.INVALID_DATA), result.failures)
        assertEquals(1, result.counts.calendars)
        assertFalse(result.toString().contains("SECRET-TITLE"))
        assertFalse(result.toString().contains("Work"))
    }

    @Test
    fun stableCalendarIdsUseCanonicalHrefNotNameSessionOrRemoteId() = runTest {
        // Catches persisted IDs changing across authentication sessions or colliding on display names.
        val first = remote("server-id-1", "HTTPS://CALDAV.ICLOUD.COM:443/a/../calendars/work/", "Same").copy(sessionId = "session-1")
        val second = remote("server-id-2", "https://caldav.icloud.com/calendars/work/", "Renamed").copy(sessionId = "session-2")
        val other = remote("server-id-1", "https://caldav.icloud.com/calendars/other/", "Renamed").copy(sessionId = "session-2")
        val repository = FakeRepository()

        val firstResult = coordinator(FakeGateway(listOf(first)), FakeParser(), repository)
            .sync(SyncRequest.Initial) as SyncResult.Success
        val secondResult = coordinator(FakeGateway(listOf(second, other)), FakeParser(), repository)
            .sync(SyncRequest.Initial) as SyncResult.Success

        assertTrue(firstResult.calendarIds.single() in secondResult.calendarIds)
        assertEquals(2, secondResult.calendarIds.size)
        assertNotEquals(secondResult.calendarIds.first(), secondResult.calendarIds.last())
    }

    @Test
    fun zeroDiscoveredCalendarsIsAnEmptySuccess() = runTest {
        // Catches treating a valid empty account as partial or retryable failure.
        val result = coordinator(FakeGateway(emptyList()), FakeParser(), FakeRepository())
            .sync(SyncRequest.Initial)

        assertEquals(SyncResult.Success(), result)
    }

    @Test
    fun concurrentRequestsShareOneProcessMutexAndBothFinish() = runTest {
        // Catches coordinator instances using separate locks or dropping a queued request.
        val operations = OperationCounter(delayMillis = 25)
        val first = coordinator(FakeGateway(listOf(remote("one", "/one/")), operations), FakeParser(), FakeRepository())
        val second = coordinator(FakeGateway(listOf(remote("two", "/two/")), operations), FakeParser(), FakeRepository())

        val results = listOf(
            async { first.sync(SyncRequest.Initial) },
            async { second.sync(SyncRequest.Initial) },
        ).map { it.await() }

        assertEquals(1, operations.maximum)
        assertTrue(results.all { it is SyncResult.Success })
        assertEquals(0, operations.active)
    }

    @Test
    fun cancellingAMutexWaiterDoesNotBlockTheNextRequest() = runTest {
        // Catches cancellation leaving the shared mutex queued or locked.
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val blocker = BlockingGateway(entered, release)
        val first = coordinator(blocker, FakeParser(), FakeRepository())
        val queued = coordinator(FakeGateway(emptyList()), FakeParser(), FakeRepository())
        val after = coordinator(FakeGateway(emptyList()), FakeParser(), FakeRepository())

        val holder = launch { first.sync(SyncRequest.Initial) }
        entered.await()
        val cancelled = launch { queued.sync(SyncRequest.Initial) }
        cancelled.cancelAndJoin()
        release.complete(Unit)
        holder.join()

        assertTrue(after.sync(SyncRequest.Initial) is SyncResult.Success)
    }

    private fun coordinator(
        gateway: CalDavGateway,
        parser: IcsParser,
        repository: CalendarRepository,
        credentials: Credentials? = Credentials("person@example.com", "app-password".toCharArray()),
    ) = DefaultSyncCoordinator(
        credentialsStore = FakeCredentialsStore(credentials),
        gateway = gateway,
        parser = parser,
        repository = repository,
        clock = FixedClock(now, tokyo),
    )

    private suspend fun stableIdFor(calendar: RemoteCalendar): String {
        val repository = FakeRepository()
        coordinator(FakeGateway(listOf(calendar)), FakeParser(), repository)
            .sync(SyncRequest.EnsureMonth(YearMonth.of(2026, 1)))
        return repository.snapshots.single().calendar.id
    }
}

private class FixedClock(
    override val now: Instant,
    override val zoneId: ZoneId,
) : ClockProvider

private class FakeCredentialsStore(private val credentials: Credentials?) : CredentialsStore {
    override suspend fun save(credentials: Credentials) = Unit
    override suspend fun load(): Credentials? = credentials
    override suspend fun clear() = Unit
}

private data class ParseCall(val content: String, val range: TimeRange, val zoneId: ZoneId)

private class FakeParser(private val failContent: String? = null) : IcsParser {
    val calls = mutableListOf<ParseCall>()

    override fun parse(ics: String, range: TimeRange, displayZone: ZoneId): ParsedCalendar {
        calls += ParseCall(ics, range, displayZone)
        if (ics == failContent) throw IcsParseException("invalid fixture")
        return parsed(ics)
    }
}

private class InstanceCollisionParser : IcsParser {
    override fun parse(ics: String, range: TimeRange, displayZone: ZoneId): ParsedCalendar {
        val event = ParsedEvent(
            id = "event-$ics",
            uid = "uid-$ics",
            recurrenceId = null,
            title = "title-$ics",
            location = null,
            notes = null,
            originalTimeZoneId = null,
            isAllDay = false,
            recurrenceRule = null,
            status = EventStatus.CONFIRMED,
        )
        return ParsedCalendar(
            events = listOf(event),
            instances = listOf(
                ParsedInstance(
                    instanceId = "shared-instance",
                    eventId = event.id,
                    uid = event.uid,
                    recurrenceId = null,
                    title = event.title,
                    location = null,
                    isAllDay = false,
                    startInstant = Instant.parse("2026-03-15T09:00:00Z"),
                    endInstant = Instant.parse("2026-03-15T10:00:00Z"),
                    startDate = null,
                    endDateExclusive = null,
                ),
            ),
        )
    }
}

private class OperationCounter(private val delayMillis: Long) {
    var active = 0
        private set
    var maximum = 0
        private set

    suspend fun <T> track(block: () -> T): T {
        active++
        maximum = maxOf(maximum, active)
        try {
            delay(delayMillis)
            return block()
        } finally {
            active--
        }
    }
}

private open class FakeGateway(
    val calendars: List<RemoteCalendar>,
    private val operations: OperationCounter? = null,
    val deltas: MutableMap<String, RemoteDelta> = mutableMapOf(),
) : CalDavGateway {
    var authenticateCount = 0
    var discoverCount = 0
    val rangeCalls = mutableListOf<Pair<RemoteCalendar, TimeRange>>()
    val deltaCalls = mutableListOf<Pair<RemoteCalendar, String>>()
    val failures = mutableMapOf<String, Throwable>()
    val resourceContents = mutableMapOf<String, String>()

    override suspend fun authenticate(credentials: Credentials): Principal = tracked {
        authenticateCount++
        Principal("/principal/", "authenticated-session")
    }

    override suspend fun discoverCalendars(principal: Principal): List<RemoteCalendar> = tracked {
        discoverCount++
        calendars
    }

    override suspend fun fetchRange(calendar: RemoteCalendar, range: TimeRange): RemoteSnapshot = tracked {
        rangeCalls += calendar to range
        failures[calendar.id]?.let { throw it }
        RemoteSnapshot(
            listOf(resource("${calendar.href}event.ics", resourceContents[calendar.id] ?: calendar.id)),
            "snapshot-${calendar.id}",
        )
    }

    override suspend fun fetchDelta(calendar: RemoteCalendar, syncToken: String): RemoteDelta = tracked {
        deltaCalls += calendar to syncToken
        failures[calendar.id]?.let { throw it }
        deltas.getValue(calendar.id)
    }

    private suspend fun <T> tracked(block: () -> T): T = operations?.track(block) ?: block()
}

private class BlockingGateway(
    private val entered: CompletableDeferred<Unit>,
    private val release: CompletableDeferred<Unit>,
) : CalDavGateway {
    override suspend fun authenticate(credentials: Credentials): Principal {
        entered.complete(Unit)
        release.await()
        return Principal("/principal/", "session")
    }

    override suspend fun discoverCalendars(principal: Principal): List<RemoteCalendar> = emptyList()
    override suspend fun fetchRange(calendar: RemoteCalendar, range: TimeRange): RemoteSnapshot = error("unused")
    override suspend fun fetchDelta(calendar: RemoteCalendar, syncToken: String): RemoteDelta = error("unused")
}

private class FakeRepository : CalendarRepository {
    val states = mutableMapOf<String, CalendarSyncState>()
    val snapshots = mutableListOf<CalendarSnapshot>()
    val deltas = mutableListOf<CalendarDelta>()
    val failSnapshotIds = mutableSetOf<String>()
    val failDeltaIds = mutableSetOf<String>()

    override fun observeCalendars(): Flow<List<CalendarSummary>> = emptyFlow()
    override fun observeAgenda(range: TimeRange, calendarIds: Set<String>): Flow<List<EventInstance>> = emptyFlow()
    override fun observeDay(day: LocalDate, zoneId: ZoneId): Flow<List<EventInstance>> = emptyFlow()
    override fun observeEvent(instanceId: String): Flow<EventDetail?> = emptyFlow()
    override suspend fun getSyncState(calendarId: String): CalendarSyncState = states[calendarId] ?: CalendarSyncState()

    override suspend fun replaceCalendarSnapshot(snapshot: CalendarSnapshot) {
        if (snapshot.calendar.id in failSnapshotIds) error("repository fixture failure")
        snapshots += snapshot
        states[snapshot.calendar.id] = CalendarSyncState(
            syncToken = snapshot.syncToken,
            coverage = (states[snapshot.calendar.id]?.coverage.orEmpty() + snapshot.coverage),
        )
    }

    override suspend fun applyCalendarDelta(delta: CalendarDelta) {
        if (delta.calendarId in failDeltaIds) error("repository fixture failure")
        deltas += delta
        states[delta.calendarId] = (states[delta.calendarId] ?: CalendarSyncState()).copy(syncToken = delta.syncToken)
    }

    override suspend fun setCalendarVisible(calendarId: String, visible: Boolean) = Unit
    override suspend fun recordCoverage(calendarId: String, range: TimeRange) = Unit
    override suspend fun clearCalendarHistory(before: com.brightnook.nookcalendar.domain.CalendarHistoryBoundary) = Unit
    override suspend fun clearCalendarCache() = Unit
}

private fun remote(
    id: String,
    href: String,
    name: String = id,
    color: String? = null,
    syncToken: String? = null,
) = RemoteCalendar(id, href, name, color, syncToken, "remote-session")

private fun resource(href: String, content: String) = RemoteResource(href, "etag-$content", content)

private fun floatingEventIcs() = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Task 6 mixed-zone test//EN
    BEGIN:VEVENT
    UID:floating-overlap@example.test
    DTSTAMP:20260301T000000Z
    DTSTART:20260315T090000
    DTEND:20260315T100000
    SUMMARY:Floating event
    END:VEVENT
    END:VCALENDAR
""".trimIndent().replace("\n", "\r\n") + "\r\n"

private fun parsed(uid: String): ParsedCalendar {
    val event = ParsedEvent(
        id = "event-$uid",
        uid = uid,
        recurrenceId = null,
        title = "title-$uid",
        location = null,
        notes = "notes-$uid",
        originalTimeZoneId = null,
        isAllDay = false,
        recurrenceRule = null,
        status = EventStatus.CONFIRMED,
    )
    val instance = ParsedInstance(
        instanceId = "instance-$uid",
        eventId = event.id,
        uid = uid,
        recurrenceId = null,
        title = event.title,
        location = null,
        isAllDay = false,
        startInstant = Instant.parse("2026-03-15T09:00:00Z"),
        endInstant = Instant.parse("2026-03-15T10:00:00Z"),
        startDate = null,
        endDateExclusive = null,
    )
    return ParsedCalendar(listOf(event), listOf(instance))
}

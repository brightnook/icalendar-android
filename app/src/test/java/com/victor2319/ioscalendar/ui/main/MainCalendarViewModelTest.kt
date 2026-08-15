package com.victor2319.ioscalendar.ui.main

import com.victor2319.ioscalendar.domain.CalendarDelta
import com.victor2319.ioscalendar.domain.CalendarEvent
import com.victor2319.ioscalendar.domain.CalendarRepository
import com.victor2319.ioscalendar.domain.CalendarSnapshot
import com.victor2319.ioscalendar.domain.CalendarSummary
import com.victor2319.ioscalendar.domain.CalendarSyncState
import com.victor2319.ioscalendar.domain.ClockProvider
import com.victor2319.ioscalendar.domain.EventDetail
import com.victor2319.ioscalendar.domain.EventInstance
import com.victor2319.ioscalendar.domain.SyncCoverage
import com.victor2319.ioscalendar.domain.TimeRange
import com.victor2319.ioscalendar.sync.SyncCoordinator
import com.victor2319.ioscalendar.sync.SyncCounts
import com.victor2319.ioscalendar.sync.SyncDisabledException
import com.victor2319.ioscalendar.sync.SyncRequest
import com.victor2319.ioscalendar.sync.SyncResult
import com.victor2319.ioscalendar.sync.SyncScheduler
import com.victor2319.ioscalendar.sync.SyncSchedulerReadiness
import java.time.Instant
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainCalendarViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val zone = ZoneId.of("Asia/Tokyo")
    private val today = LocalDate.of(2026, 8, 15)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun selectingAnotherDayCancelsTheOldQueryAndPublishesOnlyTheNewAgenda() = runTest(dispatcher) {
        // Catches collecting every selected day forever and allowing stale agenda rows to win.
        val repository = FlowRepository()
        val oldDayCancelled = MutableStateFlow(false)
        repository.dayFlows[today] = flow {
            try {
                emit(listOf(instance("old", today, title = "Old")))
                awaitCancellation()
            } finally {
                oldDayCancelled.value = true
            }
        }
        val nextDay = today.plusDays(1)
        repository.dayFlows[nextDay] = MutableStateFlow(listOf(instance("new", nextDay, title = "New")))
        val viewModel = viewModel(repository = repository)
        advanceUntilIdle()

        viewModel.selectDate(nextDay)
        advanceUntilIdle()

        assertTrue(oldDayCancelled.value)
        assertEquals(nextDay, viewModel.uiState.value.selectedDate)
        assertEquals(listOf("new"), viewModel.uiState.value.agenda.map { it.instanceId })
    }

    @Test
    fun exactMonthCoverageInTheCurrentZoneDoesNotRequestAnotherSync() = runTest(dispatcher) {
        // Catches redundant network sync for an already fully covered local month.
        val repository = FlowRepository(calendars = listOf(calendar("work")))
        repository.syncStates["work"] = CalendarSyncState(
            coverage = listOf(monthRange(YearMonth.of(2026, 8), zone)),
        )
        val coordinator = RecordingSyncCoordinator()
        viewModel(repository, coordinator = coordinator)

        advanceUntilIdle()

        assertEquals(emptyList<SyncRequest>(), coordinator.requests)
    }

    @Test
    fun broaderOrDifferentZoneCoverageRequestsEnsureMonthOnlyOnce() = runTest(dispatcher) {
        // Catches accepting approximate or another-zone coverage as the requested local month.
        val month = YearMonth.of(2026, 9)
        val repository = FlowRepository(calendars = listOf(calendar("exact"), calendar("wrong-zone"), calendar("broad")))
        val currentMonth = monthRange(YearMonth.of(2026, 8), zone)
        repository.syncStates["exact"] = CalendarSyncState(coverage = listOf(currentMonth, monthRange(month, zone)))
        repository.syncStates["wrong-zone"] = CalendarSyncState(
            coverage = listOf(currentMonth, monthRange(month, ZoneId.of("UTC"))),
        )
        repository.syncStates["broad"] = CalendarSyncState(
            coverage = listOf(
                currentMonth,
                TimeRange(
                    month.minusMonths(1).atDay(1).atStartOfDay(zone).toInstant(),
                    month.plusMonths(2).atDay(1).atStartOfDay(zone).toInstant(),
                    zone,
                ),
            ),
        )
        val coordinator = RecordingSyncCoordinator()
        val viewModel = viewModel(repository, coordinator = coordinator)
        advanceUntilIdle()

        viewModel.showMonth(month)
        advanceUntilIdle()

        assertEquals(listOf(SyncRequest.EnsureMonth(month)), coordinator.requests)
    }

    @Test
    fun switchingMonthsCancelsOutdatedEnsureBeforeSynchronizingTheNewMonth() = runTest(dispatcher) {
        // Catches a slow old month remaining ahead of the month the user is now viewing.
        val august = YearMonth.of(2026, 8)
        val september = YearMonth.of(2026, 9)
        val repository = FlowRepository(calendars = listOf(calendar("work")))
        val coordinator = SwitchingMonthCoordinator(blockedMonth = august)
        val viewModel = viewModel(repository, coordinator = coordinator)
        advanceUntilIdle()

        viewModel.showMonth(september)
        advanceUntilIdle()

        assertEquals(listOf(august), coordinator.cancelledMonths.value)
        assertEquals(listOf(september), coordinator.completedMonths.value)
        assertEquals(september, viewModel.uiState.value.displayedMonth)
    }

    @Test
    fun partialMonthSyncCanRetryAfterLeavingAndRevisitingTheMonth() = runTest(dispatcher) {
        // Catches permanent de-duplication after a failed or partial EnsureMonth result.
        val august = YearMonth.of(2026, 8)
        val september = YearMonth.of(2026, 9)
        val repository = FlowRepository(calendars = listOf(calendar("work")))
        repository.syncStates["work"] = CalendarSyncState(coverage = listOf(monthRange(september, zone)))
        val coordinator = RetryingMonthCoordinator(august)
        val viewModel = viewModel(repository, coordinator = coordinator)
        advanceUntilIdle()
        assertEquals(MainCalendarMessage.SYNC_FAILED, viewModel.uiState.value.message)

        viewModel.showMonth(september)
        advanceUntilIdle()
        viewModel.showMonth(august)
        advanceUntilIdle()

        assertEquals(listOf(august), coordinator.successfulMonths.value)
        assertNull(viewModel.uiState.value.message)
    }

    @Test
    fun calendarMetadataChangeDoesNotRestartTheSameInFlightMonthSync() = runTest(dispatcher) {
        // Catches duplicate same-month work when non-coverage calendar metadata changes.
        val repository = FlowRepository(calendars = listOf(calendar("work")))
        val coordinator = StableMonthCoordinator()
        viewModel(repository, coordinator = coordinator)
        advanceUntilIdle()
        assertEquals(1, coordinator.started.value)

        repository.calendarFlow.value = listOf(calendar("work").copy(name = "Renamed"))
        advanceUntilIdle()

        assertEquals(1, coordinator.started.value)
        assertEquals(0, coordinator.cancelled.value)
        coordinator.release.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun zoneChangeRequeriesTheSameSelectedDateAndRechecksCurrentMonthCoverage() = runTest(dispatcher) {
        // Catches repository bounds and coverage remaining pinned to the process-start timezone.
        val utc = ZoneId.of("UTC")
        val zones = MutableStateFlow(zone)
        val repository = FlowRepository(calendars = listOf(calendar("work")))
        repository.syncStates["work"] = CalendarSyncState(
            coverage = listOf(monthRange(YearMonth.of(2026, 8), zone)),
        )
        repository.zonedDayFlows[today to zone] = MutableStateFlow(listOf(instance("tokyo", today)))
        repository.zonedDayFlows[today to utc] = MutableStateFlow(listOf(instance("utc", today)))
        val coordinator = RecordingSyncCoordinator()
        val viewModel = MainCalendarViewModel(
            repository,
            coordinator,
            RecordingScheduler(ready = true),
            FixedClock(),
            zones,
        )
        advanceUntilIdle()
        assertEquals(listOf("tokyo"), viewModel.uiState.value.agenda.map { it.instanceId })

        zones.value = utc
        advanceUntilIdle()

        assertEquals(today, viewModel.uiState.value.selectedDate)
        assertEquals(listOf("utc"), viewModel.uiState.value.agenda.map { it.instanceId })
        assertEquals(listOf(SyncRequest.EnsureMonth(YearMonth.of(2026, 8))), coordinator.requests)
    }

    @Test
    fun manualRefreshFailsClosedUntilReadyAndMapsDisabledAndUnexpectedErrors() = runTest(dispatcher) {
        // Catches queuing work while settings are unknown or exposing scheduler exception details.
        val scheduler = RecordingScheduler()
        val viewModel = viewModel(scheduler = scheduler)
        advanceUntilIdle()

        viewModel.refresh()
        advanceUntilIdle()
        assertEquals(0, scheduler.enqueueCount)
        assertEquals(MainCalendarMessage.SYNC_NOT_READY, viewModel.uiState.value.message)

        scheduler.readiness.value = SyncSchedulerReadiness.READY
        scheduler.failure = SyncDisabledException()
        viewModel.refresh()
        advanceUntilIdle()
        assertEquals(MainCalendarMessage.SYNC_DISABLED, viewModel.uiState.value.message)

        scheduler.failure = IllegalArgumentException("secret internal value")
        viewModel.refresh()
        advanceUntilIdle()
        assertEquals(MainCalendarMessage.SYNC_FAILED, viewModel.uiState.value.message)
        assertFalse(viewModel.uiState.value.toString().contains("secret internal value"))

        scheduler.failure = null
        viewModel.refresh()
        advanceUntilIdle()
        assertEquals(3, scheduler.enqueueCount)
        assertNull(viewModel.uiState.value.message)
    }

    @Test
    fun launchTargetSelectsItsDateOpensTheSharedDetailAndClosePreservesSelection() = runTest(dispatcher) {
        // Catches widget/deep-link launches navigating elsewhere or resetting calendar state on close.
        val targetDate = LocalDate.of(2026, 10, 8)
        val repository = FlowRepository()
        repository.eventFlows["instance-8"] = MutableStateFlow(detail("instance-8", targetDate))
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.handleLaunchTarget(CalendarLaunchTarget(targetDate, "instance-8"))
        advanceUntilIdle()

        assertEquals(targetDate, viewModel.uiState.value.selectedDate)
        assertEquals(YearMonth.of(2026, 10), viewModel.uiState.value.displayedMonth)
        assertEquals("instance-8", viewModel.uiState.value.openEvent?.instanceId)

        viewModel.closeEvent()
        advanceUntilIdle()

        assertEquals(targetDate, viewModel.uiState.value.selectedDate)
        assertNull(viewModel.uiState.value.openEvent)
    }

    @Test
    fun launchTargetParserRejectsMalformedBlankAndDuplicateDeliveries() {
        // Catches crashing on hostile extras and reopening the same sheet from onCreate plus onNewIntent.
        val delivery = CalendarLaunchTargetDelivery()

        assertNull(delivery.parseAndAccept("not-a-date", "id"))
        assertNull(delivery.parseAndAccept("2026-08-15", "  "))
        val first = CalendarLaunchTarget(LocalDate.of(2026, 8, 15), "safe-id")
        assertEquals(first, delivery.parseAndAccept("2026-08-15", "safe-id"))
        assertNull(delivery.parseAndAccept("2026-08-15", "safe-id"))
        delivery.markConsumed(first)
        assertNull(delivery.parseAndAccept("2026-08-15", "safe-id"))
        assertEquals(
            CalendarLaunchTarget(LocalDate.of(2026, 8, 16), null),
            delivery.parseAndAccept("2026-08-16", null),
        )
    }

    @Test
    fun acceptedLaunchTargetsRestoreAcrossActivityRecreationWithoutBlockingDifferentTargets() {
        // Catches configuration recreation forgetting an already delivered intent and reopening its closed sheet.
        val first = CalendarLaunchTarget(LocalDate.of(2026, 8, 15), "id|with spaces")
        val delivery = CalendarLaunchTargetDelivery()
        assertEquals(first, delivery.parseAndAccept("2026-08-15", "id|with spaces"))

        val restored = CalendarLaunchTargetDelivery.fromSavedState(delivery.savedState())

        assertNull(restored.parseAndAccept("2026-08-15", "id|with spaces"))
        assertEquals(
            CalendarLaunchTarget(LocalDate.of(2026, 8, 16), "different"),
            restored.parseAndAccept("2026-08-16", "different"),
        )
        val malformedRestore = CalendarLaunchTargetDelivery.fromSavedState(listOf("malformed private value"))
        assertEquals(first, malformedRestore.parseAndAccept("2026-08-15", "id|with spaces"))
    }

    @Test
    fun safeExternalActionParserAllowsOnlyExplicitWebTelephoneAndGeoValues() {
        // Catches notes/location content launching unsafe schemes or values that only look like links.
        assertEquals(ExternalAction.Web("https://example.test/path"), parseExternalAction("https://example.test/path"))
        assertEquals(ExternalAction.Telephone("+81 3 1234 5678"), parseExternalAction("tel:+81 3 1234 5678"))
        assertEquals(ExternalAction.Geo("35.6812,139.7671"), parseExternalAction("geo:35.6812,139.7671"))
        assertNull(parseExternalAction("javascript:alert(1)"))
        assertNull(parseExternalAction("content://private/data"))
        assertNull(parseExternalAction("see https://example.test"))
        assertNull(parseExternalAction("geo:not-coordinates"))
    }

    @Test
    fun recurrenceParserPreservesIntervalAndHumanWeekdaysWithoutExposingRawRuleText() {
        // Catches calling every recurrence simply weekly and losing ordinal weekday meaning.
        assertEquals(
            RecurrenceDescription(
                frequency = RecurrenceFrequency.WEEKLY,
                interval = 2,
                days = listOf(
                    RecurrenceDay(DayOfWeek.MONDAY, null),
                    RecurrenceDay(DayOfWeek.SUNDAY, null),
                ),
            ),
            parseRecurrenceRule("FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,SU"),
        )
        assertNull(parseRecurrenceRule("FREQ=PRIVATE-SERVER-VALUE"))
    }

    @Test
    fun recurrenceParserPreservesFirstAndLastOrdinalsAndRejectsInaccurateWeeklyOrdinals() {
        // Catches displaying 1MO or -1SU as ordinary Monday or Sunday and changing its meaning.
        assertEquals(
            RecurrenceDescription(
                frequency = RecurrenceFrequency.MONTHLY,
                interval = 1,
                days = listOf(
                    RecurrenceDay(DayOfWeek.MONDAY, 1),
                    RecurrenceDay(DayOfWeek.SUNDAY, -1),
                ),
            ),
            parseRecurrenceRule("FREQ=MONTHLY;BYDAY=1MO,-1SU"),
        )
        assertEquals(
            listOf(RecurrenceDay(DayOfWeek.FRIDAY, 2)),
            parseRecurrenceRule("FREQ=YEARLY;BYMONTH=3;BYDAY=2FR")?.days,
        )
        assertNull(parseRecurrenceRule("FREQ=WEEKLY;BYDAY=1MO"))
    }

    private fun viewModel(
        repository: FlowRepository = FlowRepository(),
        coordinator: SyncCoordinator = RecordingSyncCoordinator(),
        scheduler: RecordingScheduler = RecordingScheduler(ready = true),
    ) = MainCalendarViewModel(repository, coordinator, scheduler, FixedClock())

    private inner class FixedClock : ClockProvider {
        override val now: Instant = today.atStartOfDay(zone).toInstant()
        override val zoneId: ZoneId = zone
    }
}

private class FlowRepository(
    calendars: List<CalendarSummary> = emptyList(),
) : CalendarRepository {
    val calendarFlow = MutableStateFlow(calendars)
    val dayFlows = mutableMapOf<LocalDate, Flow<List<EventInstance>>>()
    val zonedDayFlows = mutableMapOf<Pair<LocalDate, ZoneId>, Flow<List<EventInstance>>>()
    val eventFlows = mutableMapOf<String, Flow<EventDetail?>>()
    val syncStates = mutableMapOf<String, CalendarSyncState>()

    override fun observeCalendars(): Flow<List<CalendarSummary>> = calendarFlow
    override fun observeDay(day: LocalDate, zoneId: ZoneId): Flow<List<EventInstance>> =
        zonedDayFlows[day to zoneId] ?: dayFlows[day] ?: MutableStateFlow(emptyList())
    override fun observeEvent(instanceId: String): Flow<EventDetail?> =
        eventFlows[instanceId] ?: MutableStateFlow(null)
    override suspend fun getSyncState(calendarId: String): CalendarSyncState =
        syncStates[calendarId] ?: CalendarSyncState()

    override fun observeAgenda(range: TimeRange, calendarIds: Set<String>): Flow<List<EventInstance>> =
        error("Not used by main calendar")
    override suspend fun replaceCalendarSnapshot(snapshot: CalendarSnapshot) = Unit
    override suspend fun applyCalendarDelta(delta: CalendarDelta) = Unit
    override suspend fun setCalendarVisible(calendarId: String, visible: Boolean) = Unit
    override suspend fun recordCoverage(calendarId: String, range: TimeRange) = Unit
    override suspend fun clearCalendarHistory(before: com.victor2319.ioscalendar.domain.CalendarHistoryBoundary) = Unit
    override suspend fun clearCalendarCache() = Unit
}

private class RecordingSyncCoordinator : SyncCoordinator {
    val requests = mutableListOf<SyncRequest>()
    override suspend fun sync(request: SyncRequest): SyncResult {
        requests += request
        return SyncResult.Success(SyncCounts())
    }
}

private class SwitchingMonthCoordinator(
    private val blockedMonth: YearMonth,
) : SyncCoordinator {
    val cancelledMonths = MutableStateFlow<List<YearMonth>>(emptyList())
    val completedMonths = MutableStateFlow<List<YearMonth>>(emptyList())

    override suspend fun sync(request: SyncRequest): SyncResult {
        val month = (request as SyncRequest.EnsureMonth).month
        if (month == blockedMonth) {
            try {
                awaitCancellation()
            } finally {
                cancelledMonths.value = cancelledMonths.value + month
            }
        }
        completedMonths.value = completedMonths.value + month
        return SyncResult.Success()
    }
}

private class RetryingMonthCoordinator(
    private val retryMonth: YearMonth,
) : SyncCoordinator {
    private var attempts = 0
    val successfulMonths = MutableStateFlow<List<YearMonth>>(emptyList())

    override suspend fun sync(request: SyncRequest): SyncResult {
        val month = (request as SyncRequest.EnsureMonth).month
        if (month == retryMonth && attempts++ == 0) {
            return SyncResult.PartialSuccess(
                counts = SyncCounts(),
                calendarIds = emptySet(),
                failures = mapOf("work" to com.victor2319.ioscalendar.sync.SyncFailureCategory.NETWORK),
            )
        }
        successfulMonths.value = successfulMonths.value + month
        return SyncResult.Success()
    }
}

private class StableMonthCoordinator : SyncCoordinator {
    val started = MutableStateFlow(0)
    val cancelled = MutableStateFlow(0)
    val release = CompletableDeferred<Unit>()

    override suspend fun sync(request: SyncRequest): SyncResult {
        started.value += 1
        try {
            release.await()
        } finally {
            if (!release.isCompleted) cancelled.value += 1
        }
        return SyncResult.Success()
    }
}

private class RecordingScheduler(ready: Boolean = false) : SyncScheduler {
    override val readiness = MutableStateFlow(
        if (ready) SyncSchedulerReadiness.READY else SyncSchedulerReadiness.INITIALIZING,
    )
    var enqueueCount = 0
    var failure: RuntimeException? = null

    override suspend fun applyMode(mode: com.victor2319.ioscalendar.domain.SyncMode) = Unit
    override fun enqueueManual(): UUID {
        enqueueCount += 1
        failure?.let { throw it }
        return UUID.fromString("00000000-0000-0000-0000-000000000009")
    }
    override suspend fun cancelAll() = Unit
}

private fun calendar(id: String) = CalendarSummary(id, id, 0xff336699.toInt(), true)

private fun monthRange(month: YearMonth, zone: ZoneId) = TimeRange(
    month.atDay(1).atStartOfDay(zone).toInstant(),
    month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant(),
    zone,
)

private fun instance(id: String, day: LocalDate, title: String = id, allDay: Boolean = false) =
    if (allDay) {
        EventInstance(id, "event-$id", "work", title, 0xff336699.toInt(), true, null, null, day, day.plusDays(1), null)
    } else {
        EventInstance(
            id, "event-$id", "work", title, 0xff336699.toInt(), false,
            day.atTime(9, 0).atZone(ZoneId.of("Asia/Tokyo")).toInstant(),
            day.atTime(10, 0).atZone(ZoneId.of("Asia/Tokyo")).toInstant(),
            null, null, null,
        )
    }

private fun detail(id: String, day: LocalDate) = EventDetail(
    instanceId = id,
    eventId = "event-$id",
    calendarId = "work",
    calendarName = "Work",
    calendarColorArgb = 0xff336699.toInt(),
    title = "Target",
    location = "Tokyo",
    notes = "Notes",
    isAllDay = false,
    startInstant = day.atTime(9, 0).atZone(ZoneId.of("Asia/Tokyo")).toInstant(),
    endInstant = day.atTime(10, 0).atZone(ZoneId.of("Asia/Tokyo")).toInstant(),
    startDate = null,
    endDateExclusive = null,
    originalTimeZoneId = "Asia/Tokyo",
    recurrenceRule = "FREQ=WEEKLY;BYDAY=TH",
)

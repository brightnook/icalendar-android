package com.brightnook.nookcalendar.ui.settings

import com.brightnook.nookcalendar.auth.Credentials
import com.brightnook.nookcalendar.auth.CredentialsStore
import com.brightnook.nookcalendar.data.SettingsStore
import com.brightnook.nookcalendar.domain.AppSettings
import com.brightnook.nookcalendar.domain.AccountAuthenticationState
import com.brightnook.nookcalendar.domain.AccountManager
import com.brightnook.nookcalendar.domain.AppLanguage
import com.brightnook.nookcalendar.domain.CalendarDelta
import com.brightnook.nookcalendar.domain.CalendarEvent
import com.brightnook.nookcalendar.domain.CalendarHistoryBoundary
import com.brightnook.nookcalendar.domain.CalendarRepository
import com.brightnook.nookcalendar.domain.CalendarSnapshot
import com.brightnook.nookcalendar.domain.CalendarSummary
import com.brightnook.nookcalendar.domain.CalendarSyncState
import com.brightnook.nookcalendar.domain.ClockProvider
import com.brightnook.nookcalendar.domain.EventDetail
import com.brightnook.nookcalendar.domain.EventInstance
import com.brightnook.nookcalendar.domain.SyncMode
import com.brightnook.nookcalendar.domain.TimeRange
import com.brightnook.nookcalendar.sync.SyncScheduler
import com.brightnook.nookcalendar.sync.SyncSchedulerReadiness
import com.brightnook.nookcalendar.sync.AutomaticSyncOutcome
import com.brightnook.nookcalendar.sync.AutomaticSyncStatus
import com.brightnook.nookcalendar.sync.AutomaticSyncStatusStore
import com.brightnook.nookcalendar.widget.WidgetRefreshNotifier
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
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
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun selectingHourlyAppliesAndPersistsTheExactSyncMode() = runTest(dispatcher) {
        // Catches treating a periodic choice as display-only instead of applying and persisting HOURLY.
        val settings = InMemorySettingsStore()
        val scheduler = PersistingScheduler(settings)
        val viewModel = viewModel(settings = settings, scheduler = scheduler)
        advanceUntilIdle()

        viewModel.selectSyncMode(SyncMode.HOURLY)
        advanceUntilIdle()

        assertEquals(listOf(SyncMode.HOURLY), scheduler.appliedModes)
        assertEquals(SyncMode.HOURLY, settings.settings.value.syncMode)
        assertEquals(SyncMode.HOURLY, viewModel.uiState.value.syncMode)
    }

    @Test
    fun changingSyncModeEmitsOneSettingsSavedEvent() = runTest(dispatcher) {
        // Catches a successful persisted setting change that leaves the UI without any acknowledgement.
        val settings = InMemorySettingsStore()
        val viewModel = viewModel(settings = settings, scheduler = PersistingScheduler(settings))
        advanceUntilIdle()
        val saved = async { viewModel.settingsSavedEvents.first() }

        viewModel.selectSyncMode(SyncMode.DAILY)
        advanceUntilIdle()

        assertEquals(Unit, saved.await())
    }

    @Test
    fun selectingJapanesePersistsAndImmediatelyUpdatesTheSettingsUiState() = runTest(dispatcher) {
        // Catches a language selection that never persists, or leaves the visible settings value stale.
        val settings = InMemorySettingsStore()
        val viewModel = viewModel(settings = settings)
        advanceUntilIdle()

        viewModel.selectLanguage(AppLanguage.JAPANESE)
        advanceUntilIdle()

        assertEquals(AppLanguage.JAPANESE, settings.settings.value.language)
        assertEquals(AppLanguage.JAPANESE, viewModel.uiState.value.language)
    }

    @Test
    fun exposesTheMostRecentAutomaticSyncForTheSettingsScreen() = runTest(dispatcher) {
        // Catches a persisted background-sync result never reaching the settings UI.
        val expected = AutomaticSyncStatus(
            completedAt = Instant.parse("2026-09-10T08:00:00Z"),
            outcome = AutomaticSyncOutcome.COMPLETED,
        )
        val viewModel = viewModel(automaticSyncStatusStore = InMemoryAutomaticSyncStatusStore(expected))

        advanceUntilIdle()

        assertEquals(expected, viewModel.uiState.value.automaticSyncStatus)
    }

    @Test
    fun historicalFailureIsSeparateFromCurrentAuthenticationAndSuccessfulRetryClearsOnlyCurrentWarning() = runTest(dispatcher) {
        val oldFailure = AutomaticSyncStatus(Instant.parse("2026-09-10T08:00:00Z"),
            AutomaticSyncOutcome.AUTHENTICATION_REQUIRED)
        val store = InMemoryAutomaticSyncStatusStore(oldFailure)
        val model = viewModel(automaticSyncStatusStore = store)
        advanceUntilIdle()
        assertFalse(model.uiState.value.authenticationRequired)
        store.recordAuthenticationRequired(true)
        advanceUntilIdle()
        assertTrue(model.uiState.value.authenticationRequired)
        store.recordAuthenticationRequired(false)
        advanceUntilIdle()
        assertFalse(model.uiState.value.authenticationRequired)
        assertEquals(oldFailure, model.uiState.value.automaticSyncStatus)
    }

    @Test
    fun hidingCalendarUpdatesTheObservedCalendarVisibility() = runTest(dispatcher) {
        // Catches a settings switch changing only transient UI state rather than repository-backed visibility.
        val repository = InMemoryCalendarRepository(listOf(calendar("work", true)))
        val viewModel = viewModel(repository = repository)
        advanceUntilIdle()

        viewModel.setCalendarVisible("work", false)
        advanceUntilIdle()

        assertFalse(repository.calendars.value.single().isVisible)
        assertFalse(viewModel.uiState.value.calendars.single().isVisible)
    }

    @Test
    fun confirmedHistoryDeletionKeepsTheCurrentMonthAndFutureRows() = runTest(dispatcher) {
        // Catches deleting all cache rows, or using today rather than the current month's first day as the boundary.
        val repository = InMemoryCalendarRepository(
            listOf(calendar("past", true), calendar("current", true), calendar("future", true)),
        )
        repository.historyRows += listOf(
            LocalDate.of(2026, 7, 31),
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 9, 1),
        )
        val viewModel = viewModel(repository = repository)
        advanceUntilIdle()

        viewModel.requestClearHistory()
        viewModel.confirmDestructiveAction()
        advanceUntilIdle()

        assertEquals(
            listOf(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 1)),
            repository.historyRows,
        )
        assertEquals(LocalDate.of(2026, 8, 1), repository.historyBoundary?.localDate)
        assertNull(viewModel.uiState.value.confirmation)
    }

    @Test
    fun confirmedCacheDeletionRemovesAllCalendarRows() = runTest(dispatcher) {
        // Catches the destructive cache action retaining calendars after the user explicitly selected full deletion.
        val repository = InMemoryCalendarRepository(listOf(calendar("work", true)))
        val viewModel = viewModel(repository = repository)
        advanceUntilIdle()

        viewModel.requestClearCache()
        viewModel.confirmDestructiveAction()
        advanceUntilIdle()

        assertTrue(repository.calendars.value.isEmpty())
        assertNull(viewModel.uiState.value.confirmation)
    }

    @Test
    fun confirmedCacheDeletionRefreshesWidgetsToRemoveStaleRows() = runTest(dispatcher) {
        // Catches clearing the cache while widget RemoteViews continue showing events from the removed database rows.
        val refreshes = mutableListOf<Unit>()
        val repository = InMemoryCalendarRepository(listOf(calendar("work", true)))
        val viewModel = viewModel(
            repository = repository,
            widgetUpdater = WidgetRefreshNotifier { refreshes += Unit },
        )
        advanceUntilIdle()

        viewModel.requestClearCache()
        viewModel.confirmDestructiveAction()
        advanceUntilIdle()

        assertTrue(repository.calendars.value.isEmpty())
        assertEquals(1, refreshes.size)
    }

    @Test
    fun logoutCancelsClearsCredentialsSignsOutAndReportsOnlySafeCacheFailure() = runTest(dispatcher) {
        // Catches reordering logout cleanup, retaining authenticated UI after credential deletion, or leaking a cache error.
        val operations = mutableListOf<String>()
        val repository = InMemoryCalendarRepository(listOf(calendar("work", true))).also {
            it.operations = operations
            it.cacheFailure = IllegalStateException("private database location")
        }
        val credentials = InMemoryCredentialsStore(operations)
        val accountManager = RecordingAccountManager(operations, credentials, repository)
        val scheduler = PersistingScheduler(InMemorySettingsStore(), operations)
        val viewModel = viewModel(
            repository = repository,
            scheduler = scheduler,
            accountManager = accountManager,
        )
        advanceUntilIdle()

        viewModel.requestLogout(clearCache = true)
        viewModel.confirmDestructiveAction()
        advanceUntilIdle()

        assertEquals(listOf("cancel", "credentials", "signed-out", "cache"), operations)
        assertEquals(AccountAuthenticationState.SignedOut, accountManager.authenticationState.value)
        assertTrue(viewModel.uiState.value.showCleanupWarning)
        assertFalse(viewModel.uiState.value.toString().contains("private database location"))
    }

    private fun viewModel(
        repository: InMemoryCalendarRepository = InMemoryCalendarRepository(),
        settings: InMemorySettingsStore = InMemorySettingsStore(),
        scheduler: PersistingScheduler = PersistingScheduler(settings),
        accountManager: RecordingAccountManager = RecordingAccountManager(
            mutableListOf(),
            InMemoryCredentialsStore(mutableListOf()),
            repository,
        ),
        widgetUpdater: WidgetRefreshNotifier = WidgetRefreshNotifier { },
        automaticSyncStatusStore: AutomaticSyncStatusStore = InMemoryAutomaticSyncStatusStore(),
    ) = SettingsViewModel(
        repository = repository,
        settingsStore = settings,
        syncScheduler = scheduler,
        accountManager = accountManager,
        clock = FixedClock,
        widgetUpdater = widgetUpdater,
        automaticSyncStatusStore = automaticSyncStatusStore,
    )
}

private object FixedClock : ClockProvider {
    override val now: Instant = Instant.parse("2026-08-15T00:00:00Z")
    override val zoneId: ZoneId = ZoneId.of("UTC")
}

private class InMemorySettingsStore(initial: SyncMode = SyncMode.MANUAL) : SettingsStore {
    override val settings = MutableStateFlow(AppSettings(initial))
    override suspend fun setSyncMode(mode: SyncMode) {
        settings.value = AppSettings(mode)
    }

    override suspend fun setLanguage(language: AppLanguage) {
        settings.value = settings.value.copy(language = language)
    }
}

private class PersistingScheduler(
    private val settingsStore: InMemorySettingsStore,
    private val operations: MutableList<String>? = null,
) : SyncScheduler {
    override val readiness: StateFlow<SyncSchedulerReadiness> =
        MutableStateFlow(SyncSchedulerReadiness.READY)
    override val manualSyncInProgress: StateFlow<Boolean> = MutableStateFlow(false)
    val appliedModes = mutableListOf<SyncMode>()

    override suspend fun applyMode(mode: SyncMode) {
        appliedModes += mode
        settingsStore.setSyncMode(mode)
    }

    override fun enqueueManual(): UUID = UUID.randomUUID()
    override suspend fun cancelAll() {
        operations?.add("cancel")
    }
}

private class InMemoryAutomaticSyncStatusStore(
    initial: AutomaticSyncStatus? = null,
) : AutomaticSyncStatusStore {
    override val status = MutableStateFlow(initial)
    override val authenticationRequired = MutableStateFlow(false)
    override suspend fun recordAuthenticationRequired(required: Boolean) {
        authenticationRequired.value = required
    }

    override suspend fun record(status: AutomaticSyncStatus) {
        this.status.value = status
    }
}

private class InMemoryCredentialsStore(
    private val operations: MutableList<String>,
) : CredentialsStore {
    private var value: Credentials? = Credentials("person@example.test", "secret".toCharArray())

    override suspend fun save(credentials: Credentials) {
        value = Credentials(credentials.appleId, credentials.appSpecificPassword.copyOf())
    }

    override suspend fun load(): Credentials? = value?.let {
        Credentials(it.appleId, it.appSpecificPassword.copyOf())
    }

    override suspend fun clear() {
        operations += "credentials"
        value?.appSpecificPassword?.fill('\u0000')
        value = null
    }
}

private class RecordingAccountManager(
    private val operations: MutableList<String>,
    private val credentials: CredentialsStore,
    private val repository: InMemoryCalendarRepository,
) : AccountManager {
    override val authenticationState = MutableStateFlow<AccountAuthenticationState>(
        AccountAuthenticationState.Authenticated("p***@example.test"),
    )

    override suspend fun markAuthenticated(appleId: String) = Unit

    override suspend fun logout(clearCache: Boolean): Boolean {
        operations += "cancel"
        credentials.clear()
        authenticationState.value = AccountAuthenticationState.SignedOut
        operations += "signed-out"
        return if (clearCache) {
            try {
                repository.clearCalendarCache()
                true
            } catch (_: IllegalStateException) {
                false
            }
        } else {
            true
        }
    }
}

private class InMemoryCalendarRepository(
    initialCalendars: List<CalendarSummary> = emptyList(),
) : CalendarRepository {
    val calendars = MutableStateFlow(initialCalendars)
    val historyRows = mutableListOf<LocalDate>()
    var historyBoundary: CalendarHistoryBoundary? = null
    var operations: MutableList<String>? = null
    var cacheFailure: Throwable? = null

    override fun observeCalendars(): Flow<List<CalendarSummary>> = calendars
    override fun observeAgenda(range: TimeRange, calendarIds: Set<String>): Flow<List<EventInstance>> = flowOf(emptyList())
    override fun observeDay(day: LocalDate, zoneId: ZoneId): Flow<List<EventInstance>> = flowOf(emptyList())
    override fun observeEvent(instanceId: String): Flow<EventDetail?> = flowOf(null)
    override suspend fun getSyncState(calendarId: String): CalendarSyncState = CalendarSyncState()
    override suspend fun replaceCalendarSnapshot(snapshot: CalendarSnapshot) = Unit
    override suspend fun applyCalendarDelta(delta: CalendarDelta) = Unit
    override suspend fun setCalendarVisible(calendarId: String, visible: Boolean) {
        calendars.value = calendars.value.map { if (it.id == calendarId) it.copy(isVisible = visible) else it }
    }
    override suspend fun recordCoverage(calendarId: String, range: TimeRange) = Unit
    override suspend fun clearCalendarHistory(before: CalendarHistoryBoundary) {
        historyBoundary = before
        historyRows.removeAll { it.isBefore(before.localDate) }
    }
    override suspend fun clearCalendarCache() {
        operations?.add("cache")
        cacheFailure?.let { throw it }
        calendars.value = emptyList()
        historyRows.clear()
    }
}

private fun calendar(id: String, visible: Boolean) = CalendarSummary(id, id, 0xff336699.toInt(), visible)

package com.brightnook.nookcalendar.ui.settings

import android.net.Uri
import com.brightnook.nookcalendar.data.SettingsStore
import com.brightnook.nookcalendar.domain.AccountAuthenticationState
import com.brightnook.nookcalendar.domain.AccountManager
import com.brightnook.nookcalendar.domain.AppSettings
import com.brightnook.nookcalendar.domain.CalendarDelta
import com.brightnook.nookcalendar.domain.CalendarHistoryBoundary
import com.brightnook.nookcalendar.domain.CalendarRepository
import com.brightnook.nookcalendar.domain.CalendarSnapshot
import com.brightnook.nookcalendar.domain.CalendarSummary
import com.brightnook.nookcalendar.domain.CalendarSyncState
import com.brightnook.nookcalendar.domain.EventDetail
import com.brightnook.nookcalendar.domain.EventInstance
import com.brightnook.nookcalendar.domain.SyncMode
import com.brightnook.nookcalendar.domain.ThemeBackgroundMode
import com.brightnook.nookcalendar.domain.ThemeSettings
import com.brightnook.nookcalendar.domain.TimeRange
import com.brightnook.nookcalendar.sync.SyncScheduler
import com.brightnook.nookcalendar.sync.SyncSchedulerReadiness
import com.brightnook.nookcalendar.theme.ThemeAssetFailure
import com.brightnook.nookcalendar.theme.ThemeAssetImport
import com.brightnook.nookcalendar.theme.ThemeAssetStore
import com.brightnook.nookcalendar.widget.WidgetRefreshNotifier
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ThemeSettingsViewModelTest {
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
    fun selectingBlackPersistsTheThemeAndRefreshesWidgets() = runTest(dispatcher) {
        // Catches a theme control that changes only transient UI state or leaves widgets stale.
        val settingsStore = ThemeSettingsStore()
        var refreshCount = 0
        val viewModel = viewModel(
            settingsStore = settingsStore,
            widgetUpdater = WidgetRefreshNotifier { refreshCount += 1 },
        )
        advanceUntilIdle()

        viewModel.selectTheme(ThemeBackgroundMode.BLACK)
        advanceUntilIdle()

        assertEquals(ThemeBackgroundMode.BLACK, settingsStore.settings.value.theme.mode)
        assertEquals(ThemeBackgroundMode.BLACK, viewModel.uiState.value.theme.mode)
        assertEquals(1, refreshCount)
    }

    @Test
    fun reselectingCurrentWhiteOrBlackDoesNotPersistOrRefreshWidgets() = runTest(dispatcher) {
        // Catches unchanged theme choices causing redundant DataStore writes and widget refreshes.
        listOf(ThemeBackgroundMode.WHITE, ThemeBackgroundMode.BLACK).forEach { mode ->
            val settingsStore = ThemeSettingsStore(
                AppSettings(theme = ThemeSettings(mode = mode)),
            )
            var refreshCount = 0
            val viewModel = viewModel(
                settingsStore = settingsStore,
                widgetUpdater = WidgetRefreshNotifier { refreshCount += 1 },
            )
            advanceUntilIdle()

            viewModel.selectTheme(mode)
            advanceUntilIdle()

            assertEquals("$mode should not be persisted again", 0, settingsStore.themeWriteCount)
            assertEquals("$mode should not refresh widgets", 0, refreshCount)
        }
    }

    @Test
    fun failedImportPreservesExistingBlackThemeAndDoesNotRefreshWidgets() = runTest(dispatcher) {
        // Catches a failed picker import replacing the current theme or refreshing widgets as if it succeeded.
        val existing = ThemeSettings(
            mode = ThemeBackgroundMode.BLACK,
            assetKey = "existing.gif",
            applyToWidget = true,
        )
        val settingsStore = ThemeSettingsStore(AppSettings(theme = existing))
        val assetStore = FailingThemeAssetStore(ThemeAssetFailure.UNSUPPORTED)
        var refreshCount = 0
        val viewModel = viewModel(
            settingsStore = settingsStore,
            themeAssetStore = assetStore,
            widgetUpdater = WidgetRefreshNotifier { refreshCount += 1 },
        )
        advanceUntilIdle()

        viewModel.importTheme(Uri.parse("content://picker/bad-image"))
        advanceUntilIdle()

        assertEquals(existing, settingsStore.settings.value.theme)
        assertEquals(existing, viewModel.uiState.value.theme)
        assertEquals(ThemeAssetFailure.UNSUPPORTED, viewModel.uiState.value.themeImportError)
        assertEquals(0, refreshCount)
    }

    private fun viewModel(
        settingsStore: ThemeSettingsStore,
        themeAssetStore: ThemeAssetStore = FailingThemeAssetStore(ThemeAssetFailure.IO),
        widgetUpdater: WidgetRefreshNotifier,
    ) = SettingsViewModel(
        repository = NoOpCalendarRepository,
        settingsStore = settingsStore,
        syncScheduler = NoOpSyncScheduler,
        accountManager = NoOpAccountManager,
        widgetUpdater = widgetUpdater,
        themeAssetStore = themeAssetStore,
    )
}

private class ThemeSettingsStore(initial: AppSettings = AppSettings()) : SettingsStore {
    override val settings = MutableStateFlow(initial)
    var themeWriteCount = 0
        private set

    override suspend fun setSyncMode(mode: SyncMode) {
        settings.value = settings.value.copy(syncMode = mode)
    }

    override suspend fun setTheme(settings: ThemeSettings) {
        themeWriteCount += 1
        this.settings.value = this.settings.value.copy(theme = settings)
    }
}

private class FailingThemeAssetStore(private val failure: ThemeAssetFailure) : ThemeAssetStore {
    override suspend fun import(uri: Uri): ThemeAssetImport = ThemeAssetImport.Failure(failure)
    override suspend fun remove(assetKey: String) = Unit
    override fun fileFor(assetKey: String): File? = null
}

private object NoOpSyncScheduler : SyncScheduler {
    override val readiness: StateFlow<SyncSchedulerReadiness> =
        MutableStateFlow(SyncSchedulerReadiness.READY)
    override val manualSyncInProgress: StateFlow<Boolean> = MutableStateFlow(false)

    override suspend fun applyMode(mode: SyncMode) = Unit
    override fun enqueueManual(): UUID = UUID.randomUUID()
    override suspend fun cancelAll() = Unit
}

private object NoOpAccountManager : AccountManager {
    override val authenticationState = MutableStateFlow<AccountAuthenticationState>(
        AccountAuthenticationState.Authenticated("p***@example.test"),
    )

    override suspend fun markAuthenticated(appleId: String) = Unit
    override suspend fun logout(clearCache: Boolean): Boolean = true
}

private object NoOpCalendarRepository : CalendarRepository {
    override fun observeCalendars(): Flow<List<CalendarSummary>> = flowOf(emptyList())
    override fun observeAgenda(range: TimeRange, calendarIds: Set<String>): Flow<List<EventInstance>> = flowOf(emptyList())
    override fun observeDay(day: LocalDate, zoneId: ZoneId): Flow<List<EventInstance>> = flowOf(emptyList())
    override fun observeEvent(instanceId: String): Flow<EventDetail?> = flowOf(null)
    override suspend fun getSyncState(calendarId: String): CalendarSyncState = CalendarSyncState()
    override suspend fun replaceCalendarSnapshot(snapshot: CalendarSnapshot) = Unit
    override suspend fun applyCalendarDelta(delta: CalendarDelta) = Unit
    override suspend fun setCalendarVisible(calendarId: String, visible: Boolean) = Unit
    override suspend fun recordCoverage(calendarId: String, range: TimeRange) = Unit
    override suspend fun clearCalendarHistory(before: CalendarHistoryBoundary) = Unit
    override suspend fun clearCalendarCache() = Unit
}

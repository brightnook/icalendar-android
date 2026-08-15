package com.victor2319.ioscalendar.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.victor2319.ioscalendar.data.SettingsStore
import com.victor2319.ioscalendar.domain.AppSettings
import com.victor2319.ioscalendar.domain.SyncMode
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class WorkManagerSyncSchedulerTest {
    private lateinit var workManager: WorkManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        workManager = WorkManager.getInstance(context)
    }

    @Test
    fun hourlySchedulesConnectedUniquePeriodicWorkAndDailyUpdatesIt() = runTest {
        // Catches a non-network-constrained request, duplicate periodic chains, or keeping the hourly interval.
        val scheduler = scheduler()
        advanceUntilIdle()
        scheduler.applyMode(SyncMode.HOURLY)
        scheduler.applyMode(SyncMode.DAILY)

        val scheduled = workManager
            .getWorkInfosForUniqueWork(WorkManagerSyncScheduler.PERIODIC_SYNC_WORK_NAME)
            .get()

        assertEquals(1, scheduled.size)
        assertEquals(WorkInfo.State.ENQUEUED, scheduled.single().state)
        assertEquals(NetworkType.CONNECTED, scheduled.single().constraints.requiredNetworkType)
        assertEquals(TimeUnit.DAYS.toMillis(1), scheduled.single().periodicityInfo!!.repeatIntervalMillis)
    }

    @Test
    fun manualCancelsPeriodicWorkAndEnqueuesOneTimeRefresh() = runTest {
        // Catches retaining background work after manual selection or blocking an allowed manual refresh.
        val scheduler = scheduler()
        advanceUntilIdle()
        scheduler.applyMode(SyncMode.HOURLY)
        scheduler.applyMode(SyncMode.MANUAL)
        val manualId = scheduler.enqueueManual()

        val periodic = workManager
            .getWorkInfosForUniqueWork(WorkManagerSyncScheduler.PERIODIC_SYNC_WORK_NAME)
            .get()
        val manual = requireNotNull(workManager
            .getWorkInfoById(manualId)
            .get())

        assertTrue(periodic.all { it.state == WorkInfo.State.CANCELLED })
        assertEquals(WorkInfo.State.ENQUEUED, manual.state)
        assertEquals(NetworkType.CONNECTED, manual.constraints.requiredNetworkType)
    }

    @Test
    fun disabledCancelsPeriodicWorkAndRejectsManualRefresh() = runTest {
        // Catches disabled mode leaving background work queued or permitting any new sync request.
        val scheduler = scheduler()
        advanceUntilIdle()
        scheduler.applyMode(SyncMode.DAILY)
        scheduler.applyMode(SyncMode.DISABLED)

        val periodic = workManager
            .getWorkInfosForUniqueWork(WorkManagerSyncScheduler.PERIODIC_SYNC_WORK_NAME)
            .get()

        assertTrue(periodic.all { it.state == WorkInfo.State.CANCELLED })
        assertThrows(SyncDisabledException::class.java) { scheduler.enqueueManual() }
    }

    @Test
    fun recreatedSchedulerRestoresDisabledModeBeforeManualWorkCanBeEnqueued() = runTest {
        // Catches recreating a scheduler with MANUAL in-memory state after the persisted setting was disabled.
        val settings = InMemorySettingsStore()
        val first = scheduler(settings)
        advanceUntilIdle()
        first.applyMode(SyncMode.DISABLED)

        val recreated = scheduler(settings)

        val failure = withTimeout(1_000) {
            while (true) {
                val currentFailure = runCatching { recreated.enqueueManual() }.exceptionOrNull()
                if (currentFailure !is SyncSettingsNotReadyException) return@withTimeout currentFailure
                yield()
            }
        }
        assertTrue(failure is SyncDisabledException)
    }

    @Test
    fun disabledTransitionWaitsForAnHourlyTransitionBeforeCancellingPeriodicWork() = runTest {
        // Catches a delayed hourly enqueue committing after disabled mode has already cancelled periodic work.
        val settings = BlockingSettingsStore()
        val scheduler = scheduler(settings)
        advanceUntilIdle()

        val hourly = launch { scheduler.applyMode(SyncMode.HOURLY) }
        settings.hourlyWriteStarted.await()
        val disabled = async { scheduler.applyMode(SyncMode.DISABLED) }
        settings.allowHourlyWrite.complete(Unit)
        hourly.join()
        disabled.await()

        val periodic = workManager
            .getWorkInfosForUniqueWork(WorkManagerSyncScheduler.PERIODIC_SYNC_WORK_NAME)
            .get()
        assertTrue(periodic.all { it.state == WorkInfo.State.CANCELLED })
    }

    private fun TestScope.scheduler(
        settingsStore: SettingsStore = InMemorySettingsStore(),
    ) = WorkManagerSyncScheduler(workManager, settingsStore, backgroundScope)
}

private open class InMemorySettingsStore(initialMode: SyncMode = SyncMode.MANUAL) : SettingsStore {
    private val state = MutableStateFlow(AppSettings(initialMode))

    override val settings: Flow<AppSettings> = state

    override suspend fun setSyncMode(mode: SyncMode) {
        state.value = AppSettings(mode)
    }
}

private class BlockingSettingsStore : InMemorySettingsStore() {
    val hourlyWriteStarted = CompletableDeferred<Unit>()
    val allowHourlyWrite = CompletableDeferred<Unit>()

    override suspend fun setSyncMode(mode: SyncMode) {
        if (mode == SyncMode.HOURLY) {
            hourlyWriteStarted.complete(Unit)
            allowHourlyWrite.await()
        }
        super.setSyncMode(mode)
    }
}

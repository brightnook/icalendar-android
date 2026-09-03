package com.brightnook.nookcalendar.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.brightnook.nookcalendar.data.SettingsStore
import com.brightnook.nookcalendar.domain.AppSettings
import com.brightnook.nookcalendar.domain.SyncMode
import java.util.concurrent.TimeUnit
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
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
        scheduler.readiness.first { it == SyncSchedulerReadiness.READY }
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
        scheduler.readiness.first { it == SyncSchedulerReadiness.READY }
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
        assertEquals(WorkInfo.State.ENQUEUED, checkNotNull(manual).state)
        assertEquals(NetworkType.CONNECTED, manual.constraints.requiredNetworkType)
    }

    @Test
    fun disabledCancelsPeriodicWorkAndRejectsManualRefresh() = runTest {
        // Catches disabled mode leaving background work queued or permitting any new sync request.
        val scheduler = scheduler()
        scheduler.readiness.first { it == SyncSchedulerReadiness.READY }
        scheduler.applyMode(SyncMode.DAILY)
        scheduler.applyMode(SyncMode.DISABLED)

        val periodic = workManager
            .getWorkInfosForUniqueWork(WorkManagerSyncScheduler.PERIODIC_SYNC_WORK_NAME)
            .get()

        assertTrue(periodic.all { it.state == WorkInfo.State.CANCELLED })
        assertThrows(SyncDisabledException::class.java) { scheduler.enqueueManual() }
    }

    @Test
    fun readyManualModeAllowsManualWorkWithoutReadinessExceptions() = runTest {
        // Catches a public readiness contract that never opens for the normal persisted MANUAL mode.
        val scheduler = scheduler()

        scheduler.readiness.first { it == SyncSchedulerReadiness.READY }

        assertEquals(SyncSchedulerReadiness.READY, scheduler.readiness.value)
        val manualId = scheduler.enqueueManual()
        val manual = workManager
            .getWorkInfoById(manualId)
            .get()
        assertEquals(WorkInfo.State.ENQUEUED, checkNotNull(manual).state)
    }

    @Test
    fun manualRefreshReportsInProgressWhileItsWorkIsQueued() = runTest {
        // Catches a refresh control becoming tappable again before WorkManager has finished the manual sync.
        val scheduler = scheduler()
        scheduler.readiness.first { it == SyncSchedulerReadiness.READY }

        scheduler.enqueueManual()

        assertTrue(scheduler.manualSyncInProgress.first { it })
    }

    @Test
    fun recreatedDisabledSchedulerRejectsManualWorkAfterReadinessOpens() = runTest {
        // Catches treating a persisted DISABLED setting as MANUAL after process recreation.
        val scheduler = scheduler(InMemorySettingsStore(SyncMode.DISABLED))

        scheduler.readiness.first { it == SyncSchedulerReadiness.READY }

        assertEquals(SyncSchedulerReadiness.READY, scheduler.readiness.value)
        assertThrows(SyncDisabledException::class.java) { scheduler.enqueueManual() }
    }

    @Test
    fun disabledTransitionWaitsForAnHourlyTransitionBeforeCancellingPeriodicWork() = runTest {
        // Catches a delayed hourly enqueue committing after disabled mode has already cancelled periodic work.
        val settings = BlockingSettingsStore()
        val scheduler = scheduler(settings)
        scheduler.readiness.first { it == SyncSchedulerReadiness.READY }

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

    @Test
    fun concurrentDisableCannotCompleteBetweenManualModeCheckAndManualEnqueue() = runTest {
        // Catches a mode check outside the transition linearization boundary, which lets disabled finish before a stale manual enqueue.
        val settings = RecordingSettingsStore()
        val manualEnqueuer = BlockingManualWorkEnqueuer(workManager)
        val scheduler = scheduler(settings, manualEnqueuer)
        scheduler.readiness.first { it == SyncSchedulerReadiness.READY }

        val manual = async(Dispatchers.Default) { scheduler.enqueueManual() }
        manualEnqueuer.started.await()
        val disabled = async(Dispatchers.Default) { scheduler.applyMode(SyncMode.DISABLED) }
        settings.disabledPersisted.await()

        assertTrue(!disabled.isCompleted)
        manualEnqueuer.release.countDown()
        val manualId = manual.await()
        advanceUntilIdle()
        disabled.await()

        assertEquals(
            WorkInfo.State.ENQUEUED,
            checkNotNull(workManager.getWorkInfoById(manualId).get()).state,
        )
    }

    private fun TestScope.scheduler(
        settingsStore: SettingsStore = InMemorySettingsStore(),
        manualWorkEnqueuer: ManualWorkEnqueuer? = null,
    ) = if (manualWorkEnqueuer == null) {
        WorkManagerSyncScheduler(workManager, settingsStore, backgroundScope)
    } else {
        WorkManagerSyncScheduler(workManager, settingsStore, backgroundScope, manualWorkEnqueuer)
    }
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

private class RecordingSettingsStore : InMemorySettingsStore() {
    val disabledPersisted = CompletableDeferred<Unit>()

    override suspend fun setSyncMode(mode: SyncMode) {
        super.setSyncMode(mode)
        if (mode == SyncMode.DISABLED) disabledPersisted.complete(Unit)
    }
}

private class BlockingManualWorkEnqueuer(
    private val workManager: WorkManager,
) : ManualWorkEnqueuer {
    val started = CompletableDeferred<Unit>()
    val release = CountDownLatch(1)

    override fun enqueue(request: androidx.work.OneTimeWorkRequest) {
        started.complete(Unit)
        release.await()
        workManager.enqueueUniqueWork(
            WorkManagerSyncScheduler.MANUAL_SYNC_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}

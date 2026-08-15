package com.victor2319.ioscalendar.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.victor2319.ioscalendar.domain.SyncMode
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WorkManagerSyncSchedulerTest {
    private lateinit var workManager: WorkManager
    private lateinit var scheduler: WorkManagerSyncScheduler

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        workManager = WorkManager.getInstance(context)
        scheduler = WorkManagerSyncScheduler(workManager)
    }

    @Test
    fun hourlySchedulesConnectedUniquePeriodicWorkAndDailyUpdatesIt() = runTest {
        // Catches a non-network-constrained request, duplicate periodic chains, or keeping the hourly interval.
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
        scheduler.applyMode(SyncMode.DAILY)
        scheduler.applyMode(SyncMode.DISABLED)

        val periodic = workManager
            .getWorkInfosForUniqueWork(WorkManagerSyncScheduler.PERIODIC_SYNC_WORK_NAME)
            .get()

        assertTrue(periodic.all { it.state == WorkInfo.State.CANCELLED })
        assertThrows(SyncDisabledException::class.java) { scheduler.enqueueManual() }
    }
}

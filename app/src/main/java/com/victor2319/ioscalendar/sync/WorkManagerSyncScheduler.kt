package com.victor2319.ioscalendar.sync

import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.await
import com.victor2319.ioscalendar.domain.SyncMode
import java.util.UUID
import java.util.concurrent.TimeUnit

class WorkManagerSyncScheduler(
    private val workManager: WorkManager,
) : SyncScheduler {
    @Volatile
    private var mode: SyncMode = SyncMode.MANUAL

    override suspend fun applyMode(mode: SyncMode) {
        this.mode = mode
        when (mode) {
            SyncMode.HOURLY -> schedulePeriodic(1, TimeUnit.HOURS)
            SyncMode.DAILY -> schedulePeriodic(24, TimeUnit.HOURS)
            SyncMode.MANUAL,
            SyncMode.DISABLED,
            -> workManager.cancelUniqueWork(PERIODIC_SYNC_WORK_NAME).await()
        }
    }

    override fun enqueueManual(): UUID {
        if (mode == SyncMode.DISABLED) throw SyncDisabledException()
        val request = OneTimeWorkRequestBuilder<CalendarSyncWorker>()
            .setConstraints(connectedNetwork)
            .build()
        workManager.enqueueUniqueWork(MANUAL_SYNC_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        return request.id
    }

    override fun cancelAll() {
        workManager.cancelUniqueWork(PERIODIC_SYNC_WORK_NAME)
        workManager.cancelUniqueWork(MANUAL_SYNC_WORK_NAME)
    }

    private fun schedulePeriodic(interval: Long, unit: TimeUnit) {
        val request = PeriodicWorkRequestBuilder<CalendarSyncWorker>(interval, unit)
            .setConstraints(connectedNetwork)
            .build()
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    companion object {
        const val PERIODIC_SYNC_WORK_NAME = "icloud-calendar-periodic-sync"
        const val MANUAL_SYNC_WORK_NAME = "icloud-calendar-manual-sync"

        val connectedNetwork = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }
}

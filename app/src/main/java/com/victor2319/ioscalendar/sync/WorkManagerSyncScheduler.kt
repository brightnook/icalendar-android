package com.victor2319.ioscalendar.sync

import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.await
import com.victor2319.ioscalendar.data.SettingsStore
import com.victor2319.ioscalendar.domain.SyncMode
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class WorkManagerSyncScheduler(
    private val workManager: WorkManager,
    private val settingsStore: SettingsStore,
    scope: CoroutineScope,
) : SyncScheduler {
    private val mode = AtomicReference<SyncMode?>(null)
    private val transitions = Mutex()

    init {
        scope.launch {
            settingsStore.settings.collect { settings ->
                transitions.withLock {
                    mode.set(settings.syncMode)
                    applyWorkManagerMode(settings.syncMode)
                }
            }
        }
    }

    override suspend fun applyMode(mode: SyncMode) {
        transitions.withLock {
            settingsStore.setSyncMode(mode)
            this.mode.set(mode)
            applyWorkManagerMode(mode)
        }
    }

    override fun enqueueManual(): UUID {
        when (mode.get()) {
            SyncMode.DISABLED -> throw SyncDisabledException()
            null -> throw SyncSettingsNotReadyException()
            else -> Unit
        }
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

    private suspend fun applyWorkManagerMode(mode: SyncMode) {
        when (mode) {
            SyncMode.HOURLY -> schedulePeriodic(1, TimeUnit.HOURS)
            SyncMode.DAILY -> schedulePeriodic(24, TimeUnit.HOURS)
            SyncMode.MANUAL,
            SyncMode.DISABLED,
            -> workManager.cancelUniqueWork(PERIODIC_SYNC_WORK_NAME).await()
        }
    }

    private suspend fun schedulePeriodic(interval: Long, unit: TimeUnit) {
        val request = PeriodicWorkRequestBuilder<CalendarSyncWorker>(interval, unit)
            .setConstraints(connectedNetwork)
            .build()
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        ).await()
    }

    companion object {
        const val PERIODIC_SYNC_WORK_NAME = "icloud-calendar-periodic-sync"
        const val MANUAL_SYNC_WORK_NAME = "icloud-calendar-manual-sync"

        val connectedNetwork = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }
}

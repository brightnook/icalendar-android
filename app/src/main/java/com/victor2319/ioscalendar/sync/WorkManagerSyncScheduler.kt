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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal fun interface ManualWorkEnqueuer {
    fun enqueue(request: androidx.work.OneTimeWorkRequest)
}

private class WorkManagerManualWorkEnqueuer(
    private val workManager: WorkManager,
) : ManualWorkEnqueuer {
    override fun enqueue(request: androidx.work.OneTimeWorkRequest) {
        workManager.enqueueUniqueWork(
            WorkManagerSyncScheduler.MANUAL_SYNC_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}

class WorkManagerSyncScheduler internal constructor(
    private val workManager: WorkManager,
    private val settingsStore: SettingsStore,
    scope: CoroutineScope,
    private val manualWorkEnqueuer: ManualWorkEnqueuer,
) : SyncScheduler {
    constructor(
        workManager: WorkManager,
        settingsStore: SettingsStore,
        scope: CoroutineScope,
    ) : this(
        workManager = workManager,
        settingsStore = settingsStore,
        scope = scope,
        manualWorkEnqueuer = WorkManagerManualWorkEnqueuer(workManager),
    )

    private val workTransitions = Mutex()
    private val modeRequests = Mutex()
    private val state = AtomicReference(
        SchedulerState(
            mode = null,
            readiness = SyncSchedulerReadiness.INITIALIZING,
            activeManualEnqueues = 0,
            manualEnqueuesDrained = null,
        ),
    )
    private val observedState = MutableStateFlow(
        ObservedSyncState(
            mode = null,
            readiness = SyncSchedulerReadiness.INITIALIZING,
        ),
    )
    private val _readiness = MutableStateFlow(SyncSchedulerReadiness.INITIALIZING)
    override val readiness: StateFlow<SyncSchedulerReadiness> = _readiness.asStateFlow()

    init {
        scope.launch {
            settingsStore.settings.collect { settings ->
                workTransitions.withLock {
                    applyWorkManagerMode(settings.syncMode)
                    publishState(settings.syncMode)
                }
            }
        }
    }

    override suspend fun applyMode(mode: SyncMode) {
        modeRequests.withLock {
            settingsStore.setSyncMode(mode)
            observedState.first { state ->
                state.mode == mode && state.readiness == SyncSchedulerReadiness.READY
            }
        }
    }

    override fun enqueueManual(): UUID {
        reserveManualEnqueue()
        return try {
            val request = OneTimeWorkRequestBuilder<CalendarSyncWorker>()
                .setConstraints(connectedNetwork)
                .build()
            manualWorkEnqueuer.enqueue(request)
            request.id
        } finally {
            releaseManualEnqueue()
        }
    }

    override suspend fun cancelAll() {
        workTransitions.withLock {
            workManager.cancelUniqueWork(PERIODIC_SYNC_WORK_NAME).await()
            workManager.cancelUniqueWork(MANUAL_SYNC_WORK_NAME).await()
        }
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

    private suspend fun publishState(mode: SyncMode) {
        while (true) {
            val current = state.get()
            current.manualEnqueuesDrained?.await()
            val next = SchedulerState(
                mode = mode,
                readiness = SyncSchedulerReadiness.READY,
                activeManualEnqueues = 0,
                manualEnqueuesDrained = null,
            )
            if (state.compareAndSet(current, next)) {
                observedState.value = ObservedSyncState(
                    mode = mode,
                    readiness = SyncSchedulerReadiness.READY,
                )
                _readiness.value = SyncSchedulerReadiness.READY
                return
            }
        }
    }

    private fun reserveManualEnqueue() {
        while (true) {
            val current = state.get()
            when (current.mode) {
                SyncMode.DISABLED -> throw SyncDisabledException()
                null -> throw SyncSettingsNotReadyException()
                else -> Unit
            }
            if (current.readiness != SyncSchedulerReadiness.READY) {
                throw SyncSettingsNotReadyException()
            }
            val manualEnqueuesDrained = current.manualEnqueuesDrained ?: CompletableDeferred()
            val next = current.copy(
                activeManualEnqueues = current.activeManualEnqueues + 1,
                manualEnqueuesDrained = manualEnqueuesDrained,
            )
            if (state.compareAndSet(current, next)) return
        }
    }

    private fun releaseManualEnqueue() {
        while (true) {
            val current = state.get()
            check(current.activeManualEnqueues > 0)
            val next = if (current.activeManualEnqueues == 1) {
                current.copy(activeManualEnqueues = 0, manualEnqueuesDrained = null)
            } else {
                current.copy(activeManualEnqueues = current.activeManualEnqueues - 1)
            }
            if (state.compareAndSet(current, next)) {
                if (next.activeManualEnqueues == 0) current.manualEnqueuesDrained?.complete(Unit)
                return
            }
        }
    }

    companion object {
        const val PERIODIC_SYNC_WORK_NAME = "icloud-calendar-periodic-sync"
        const val MANUAL_SYNC_WORK_NAME = "icloud-calendar-manual-sync"

        val connectedNetwork = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }
}

private data class ObservedSyncState(
    val mode: SyncMode?,
    val readiness: SyncSchedulerReadiness,
)

private data class SchedulerState(
    val mode: SyncMode?,
    val readiness: SyncSchedulerReadiness,
    val activeManualEnqueues: Int,
    val manualEnqueuesDrained: CompletableDeferred<Unit>?,
)

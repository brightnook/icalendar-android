package com.brightnook.nookcalendar.sync

import com.brightnook.nookcalendar.domain.SyncMode
import java.util.UUID
import kotlinx.coroutines.flow.StateFlow

interface SyncScheduler {
    val readiness: StateFlow<SyncSchedulerReadiness>
    val manualSyncInProgress: StateFlow<Boolean>

    suspend fun applyMode(mode: SyncMode)
    fun enqueueManual(): UUID
    suspend fun cancelAll()
}

enum class SyncSchedulerReadiness {
    INITIALIZING,
    READY,
}

class SyncDisabledException : IllegalStateException("Calendar sync is disabled")

class SyncSettingsNotReadyException : IllegalStateException("Calendar sync settings are not ready")

package com.victor2319.ioscalendar.sync

import com.victor2319.ioscalendar.domain.SyncMode
import java.util.UUID
import kotlinx.coroutines.flow.StateFlow

interface SyncScheduler {
    val readiness: StateFlow<SyncSchedulerReadiness>

    suspend fun applyMode(mode: SyncMode)
    fun enqueueManual(): UUID
    fun cancelAll()
}

enum class SyncSchedulerReadiness {
    INITIALIZING,
    READY,
}

class SyncDisabledException : IllegalStateException("Calendar sync is disabled")

class SyncSettingsNotReadyException : IllegalStateException("Calendar sync settings are not ready")

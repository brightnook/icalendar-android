package com.victor2319.ioscalendar.sync

import com.victor2319.ioscalendar.domain.SyncMode
import java.util.UUID

interface SyncScheduler {
    suspend fun applyMode(mode: SyncMode)
    fun enqueueManual(): UUID
    fun cancelAll()
}

class SyncDisabledException : IllegalStateException("Calendar sync is disabled")

class SyncSettingsNotReadyException : IllegalStateException("Calendar sync settings are not ready")

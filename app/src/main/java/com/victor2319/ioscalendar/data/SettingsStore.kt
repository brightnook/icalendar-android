package com.victor2319.ioscalendar.data

import com.victor2319.ioscalendar.domain.AppSettings
import com.victor2319.ioscalendar.domain.SyncMode
import kotlinx.coroutines.flow.Flow

interface SettingsStore {
    val settings: Flow<AppSettings>

    suspend fun setSyncMode(mode: SyncMode)
}

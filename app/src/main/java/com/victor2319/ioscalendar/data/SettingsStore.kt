package com.victor2319.ioscalendar.data

import com.victor2319.ioscalendar.domain.AppSettings
import com.victor2319.ioscalendar.domain.AppLanguage
import com.victor2319.ioscalendar.domain.SyncMode
import com.victor2319.ioscalendar.domain.WeekStart
import kotlinx.coroutines.flow.Flow

interface SettingsStore {
    val settings: Flow<AppSettings>

    suspend fun setSyncMode(mode: SyncMode)

    suspend fun setWeekStart(weekStart: WeekStart) {}

    suspend fun setLanguage(language: AppLanguage) {}
}

package com.brightnook.nookcalendar.data

import com.brightnook.nookcalendar.domain.AppSettings
import com.brightnook.nookcalendar.domain.AppLanguage
import com.brightnook.nookcalendar.domain.SyncMode
import com.brightnook.nookcalendar.domain.ThemeSettings
import com.brightnook.nookcalendar.domain.WeekStart
import kotlinx.coroutines.flow.Flow

interface SettingsStore {
    val settings: Flow<AppSettings>

    suspend fun setSyncMode(mode: SyncMode)

    suspend fun setWeekStart(weekStart: WeekStart) {}

    suspend fun setLanguage(language: AppLanguage) {}

    suspend fun setTheme(settings: ThemeSettings) {}
}

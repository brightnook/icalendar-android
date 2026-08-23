package com.victor2319.ioscalendar.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.victor2319.ioscalendar.domain.AppSettings
import com.victor2319.ioscalendar.domain.AppLanguage
import com.victor2319.ioscalendar.domain.SyncMode
import com.victor2319.ioscalendar.domain.WeekStart
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(
    name = "settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)
private val syncModeKey = stringPreferencesKey("sync_mode")
private val weekStartKey = stringPreferencesKey("week_start")
private val languageKey = stringPreferencesKey("language")

class DataStoreSettingsStore(
    private val dataStore: DataStore<androidx.datastore.preferences.core.Preferences>,
) : SettingsStore {
    constructor(context: Context) : this(context.applicationContext.settingsDataStore)

    override val settings: Flow<AppSettings> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            AppSettings(
                syncMode = preferences[syncModeKey].toSyncMode(),
                weekStart = preferences[weekStartKey].toWeekStart(),
                language = preferences[languageKey].toAppLanguage(),
            )
        }

    override suspend fun setSyncMode(mode: SyncMode) {
        dataStore.edit { preferences ->
            preferences[syncModeKey] = mode.name
        }
    }

    override suspend fun setWeekStart(weekStart: WeekStart) {
        dataStore.edit { preferences -> preferences[weekStartKey] = weekStart.name }
    }

    override suspend fun setLanguage(language: AppLanguage) {
        dataStore.edit { preferences -> preferences[languageKey] = language.name }
    }
}

private fun String?.toSyncMode(): SyncMode =
    runCatching { SyncMode.valueOf(this.orEmpty()) }.getOrDefault(SyncMode.MANUAL)

private fun String?.toWeekStart(): WeekStart =
    runCatching { WeekStart.valueOf(this.orEmpty()) }.getOrDefault(WeekStart.SUNDAY)

private fun String?.toAppLanguage(): AppLanguage =
    runCatching { AppLanguage.valueOf(this.orEmpty()) }.getOrDefault(AppLanguage.FOLLOW_SYSTEM)

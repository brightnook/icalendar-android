package com.victor2319.ioscalendar.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.victor2319.ioscalendar.domain.AppSettings
import com.victor2319.ioscalendar.domain.SyncMode
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(
    name = "settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)
private val syncModeKey = stringPreferencesKey("sync_mode")

class DataStoreSettingsStore(
    private val dataStore: DataStore<androidx.datastore.preferences.core.Preferences>,
) : SettingsStore {
    constructor(context: Context) : this(context.applicationContext.settingsDataStore)

    override val settings: Flow<AppSettings> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences -> AppSettings(syncMode = preferences[syncModeKey].toSyncMode()) }

    override suspend fun setSyncMode(mode: SyncMode) {
        dataStore.edit { preferences ->
            preferences[syncModeKey] = mode.name
        }
    }
}

private fun String?.toSyncMode(): SyncMode =
    runCatching { SyncMode.valueOf(this.orEmpty()) }.getOrDefault(SyncMode.MANUAL)

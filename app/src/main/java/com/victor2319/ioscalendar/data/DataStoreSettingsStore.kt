package com.victor2319.ioscalendar.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.victor2319.ioscalendar.domain.AppSettings
import com.victor2319.ioscalendar.domain.SyncMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")
private val syncModeKey = stringPreferencesKey("sync_mode")

class DataStoreSettingsStore(context: Context) : SettingsStore {
    private val dataStore = context.applicationContext.settingsDataStore

    override val settings: Flow<AppSettings> = dataStore.data.map { preferences ->
        AppSettings(syncMode = preferences[syncModeKey].toSyncMode())
    }

    override suspend fun setSyncMode(mode: SyncMode) {
        dataStore.edit { preferences ->
            preferences[syncModeKey] = mode.name
        }
    }
}

private fun String?.toSyncMode(): SyncMode =
    runCatching { SyncMode.valueOf(this.orEmpty()) }.getOrDefault(SyncMode.MANUAL)

package com.victor2319.ioscalendar.data

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.victor2319.ioscalendar.domain.SyncMode
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class DataStoreSettingsStoreTest {
    private val fixtures = mutableListOf<DataStoreFixture>()

    @After
    fun tearDown() {
        fixtures.forEach { it.close() }
    }

    @Test
    fun absentAndMalformedSyncModesDefaultToManual() = runTest {
        // Catches exposing a missing or unknown persisted mode as an invalid scheduling state.
        val fixture = fixture()
        val store = DataStoreSettingsStore(fixture.dataStore)

        assertEquals(SyncMode.MANUAL, store.settings.first().syncMode)
        fixture.dataStore.edit { it[stringPreferencesKey("sync_mode")] = "removed-mode" }
        assertEquals(SyncMode.MANUAL, store.settings.first().syncMode)
    }

    @Test
    fun corruptPreferencesAreReplacedWithDefaultSettings() = runTest {
        // Catches a corrupted on-disk preferences file terminating the observable settings stream.
        val fixture = fixture(corrupt = true)

        assertEquals(SyncMode.MANUAL, DataStoreSettingsStore(fixture.dataStore).settings.first().syncMode)
    }

    @Test
    fun recoverableReadIOExceptionEmitsDefaultSettings() = runTest {
        // Catches a transient preferences read failure escaping and permanently stopping settings observation.
        val store = DataStoreSettingsStore(FailingReadDataStore(IOException("temporary read failure")))

        assertEquals(SyncMode.MANUAL, store.settings.first().syncMode)
    }

    private fun fixture(corrupt: Boolean = false): DataStoreFixture {
        val file = File(System.getProperty("java.io.tmpdir"), "settings-${UUID.randomUUID()}.preferences_pb")
        if (corrupt) file.writeText("not a preferences protobuf")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        return DataStoreFixture(
            file = file,
            scope = scope,
            dataStore = PreferenceDataStoreFactory.create(
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                scope = scope,
                produceFile = { file },
            ),
        ).also(fixtures::add)
    }
}

private data class DataStoreFixture(
    val file: File,
    val scope: CoroutineScope,
    val dataStore: DataStore<Preferences>,
) {
    fun close() {
        scope.cancel()
        file.delete()
    }
}

private class FailingReadDataStore(
    private val failure: IOException,
) : DataStore<Preferences> {
    override val data: Flow<Preferences> = flow { throw failure }

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = throw failure
}

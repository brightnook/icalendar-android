package com.brightnook.nookcalendar.data

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.brightnook.nookcalendar.domain.SyncMode
import com.brightnook.nookcalendar.domain.AppLanguage
import com.brightnook.nookcalendar.domain.WeekStart
import com.brightnook.nookcalendar.sync.AutomaticSyncOutcome
import com.brightnook.nookcalendar.sync.AutomaticSyncStatus
import java.io.File
import java.io.IOException
import java.time.Instant
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
    fun weekStartDefaultsToSundayAndPersistsMondaySelection() = runTest {
        // Catches a fresh install inheriting the locale's first weekday or losing a user's Monday choice.
        val fixture = fixture()
        val store = DataStoreSettingsStore(fixture.dataStore)

        assertEquals(WeekStart.SUNDAY, store.settings.first().weekStart)
        store.setWeekStart(WeekStart.MONDAY)
        assertEquals(WeekStart.MONDAY, DataStoreSettingsStore(fixture.dataStore).settings.first().weekStart)
    }

    @Test
    fun languageDefaultsToFollowSystemAndPersistsItsExtensionPoint() = runTest {
        // Catches an app language preference being absent from stored settings, blocking future language choices.
        val fixture = fixture()
        val store = DataStoreSettingsStore(fixture.dataStore)

        assertEquals(AppLanguage.FOLLOW_SYSTEM, store.settings.first().language)
        store.setLanguage(AppLanguage.FOLLOW_SYSTEM)
        assertEquals(AppLanguage.FOLLOW_SYSTEM, DataStoreSettingsStore(fixture.dataStore).settings.first().language)
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

    @Test
    fun automaticSyncStatusSurvivesStoreRecreation() = runTest {
        // Catches the settings screen losing the last background-sync result after the app process is recreated.
        val fixture = fixture()
        val expected = AutomaticSyncStatus(
            completedAt = Instant.parse("2026-09-10T08:00:00Z"),
            outcome = AutomaticSyncOutcome.COMPLETED,
        )

        DataStoreAutomaticSyncStatusStore(fixture.dataStore).record(expected)

        assertEquals(expected, DataStoreAutomaticSyncStatusStore(fixture.dataStore).status.first())
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

    @Test
    fun currentAuthenticationFailurePersistsSeparatelyFromAutomaticSyncHistory() = runTest {
        // FileStorage uses File.renameTo, which cannot replace existing files on Windows.
        // Exercise multiple updates in memory; verify the disk write separately below.
        val dataStore = object : DataStore<Preferences> {
            private val state = kotlinx.coroutines.flow.MutableStateFlow(emptyPreferences())
            override val data: Flow<Preferences> = state
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
                return transform(state.value).also { state.value = it }
            }
        }
        val store = DataStoreAutomaticSyncStatusStore(dataStore)
        val historical = AutomaticSyncStatus(Instant.parse("2026-09-10T08:00:00Z"),
            AutomaticSyncOutcome.AUTHENTICATION_REQUIRED)
        store.record(historical)
        assertEquals(false, store.authenticationRequired.first())
        store.recordAuthenticationRequired(true)
        assertEquals(true, DataStoreAutomaticSyncStatusStore(dataStore).authenticationRequired.first())
        store.recordAuthenticationRequired(false)
        assertEquals(false, store.authenticationRequired.first())
        assertEquals(historical, store.status.first())
    }

    @Test
    fun currentAuthenticationFailureIsWrittenToDisk() = runTest {
        val fixture = fixture()
        DataStoreAutomaticSyncStatusStore(fixture.dataStore).recordAuthenticationRequired(true)
        assertEquals(true, DataStoreAutomaticSyncStatusStore(fixture.dataStore).authenticationRequired.first())
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

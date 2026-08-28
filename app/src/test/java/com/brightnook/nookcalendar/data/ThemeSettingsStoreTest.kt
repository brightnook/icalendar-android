package com.brightnook.nookcalendar.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.brightnook.nookcalendar.domain.ThemeBackgroundMode
import com.brightnook.nookcalendar.domain.ThemeSettings
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ThemeSettingsStoreTest {
    private val fixtures = mutableListOf<ThemeSettingsDataStoreFixture>()

    @After
    fun tearDown() {
        fixtures.forEach { it.close() }
    }

    @Test
    fun defaultThemeIsWhiteAndDoesNotApplyToWidget() = runTest {
        // Catches a fresh install exposing a non-white background or changing the widget without consent.
        val store = DataStoreSettingsStore(fixture().dataStore)

        val theme = store.settings.first().theme

        assertEquals(ThemeBackgroundMode.WHITE, theme.mode)
        assertEquals(null, theme.assetKey)
        assertFalse(theme.applyToWidget)
    }

    @Test
    fun imageThemeRoundTripsAssetKeyAndWidgetApplication() = runTest {
        // Catches persistence dropping a selected image key or the user's widget choice.
        val fixture = fixture()
        val store = DataStoreSettingsStore(fixture.dataStore)

        store.setTheme(
            ThemeSettings(
                mode = ThemeBackgroundMode.IMAGE,
                assetKey = "theme/background.gif",
                applyToWidget = true,
            ),
        )

        assertEquals(
            ThemeSettings(
                mode = ThemeBackgroundMode.IMAGE,
                assetKey = "theme/background.gif",
                applyToWidget = true,
            ),
            DataStoreSettingsStore(fixture.dataStore).settings.first().theme,
        )
    }

    @Test
    fun invalidPersistedModeFallsBackToWhite() = runTest {
        // Catches a renamed or corrupt persisted enum value crashing settings collection.
        val fixture = fixture()
        fixture.dataStore.edit { preferences ->
            preferences[stringPreferencesKey("theme_mode")] = "SEPIA"
        }

        assertEquals(
            ThemeBackgroundMode.WHITE,
            DataStoreSettingsStore(fixture.dataStore).settings.first().theme.mode,
        )
    }

    @Test
    fun clearingAssetKeyRemovesItFromPersistence() = runTest {
        // Catches setTheme leaving a stale image key when the replacement has no asset.
        val dataStore = InMemoryThemePreferencesDataStore(
            preferencesOf(
                stringPreferencesKey("theme_mode") to ThemeBackgroundMode.IMAGE.name,
                stringPreferencesKey("theme_asset_key") to "old-background.gif",
            ),
        )
        val store = DataStoreSettingsStore(dataStore)

        store.setTheme(ThemeSettings(mode = ThemeBackgroundMode.BLACK))

        assertEquals(null, store.settings.first().theme.assetKey)
    }

    private fun fixture(): ThemeSettingsDataStoreFixture {
        val file = File(System.getProperty("java.io.tmpdir"), "theme-settings-${UUID.randomUUID()}.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        return ThemeSettingsDataStoreFixture(
            file = file,
            scope = scope,
            dataStore = PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { file },
            ),
        ).also(fixtures::add)
    }
}

private class InMemoryThemePreferencesDataStore(initial: Preferences) : DataStore<Preferences> {
    private val values = MutableStateFlow(initial)

    override val data: Flow<Preferences> = values

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
        transform(values.value).also { values.value = it }
}

private data class ThemeSettingsDataStoreFixture(
    val file: File,
    val scope: CoroutineScope,
    val dataStore: DataStore<Preferences>,
) {
    fun close() {
        scope.cancel()
        file.delete()
    }
}

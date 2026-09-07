package com.brightnook.nookcalendar.widget

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.brightnook.nookcalendar.data.CalendarDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomWidgetPreferencesTest {
    private lateinit var database: CalendarDatabase
    private lateinit var preferences: RoomWidgetPreferences

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), CalendarDatabase::class.java,
        ).allowMainThreadQueries().build()
        preferences = RoomWidgetPreferences(database)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun newWidgetsReceiveSequentialOrdinalsAndUpdatingOrDeletingAnotherDoesNotRenumberThem() = runTest {
        preferences.save(WidgetConfig(44, setOf("work"), WidgetRange.THIRTY_DAYS))
        preferences.save(WidgetConfig(108, emptySet(), WidgetRange.SEVEN_DAYS, followVisibleCalendars = true))
        assertEquals(mapOf(44 to 1, 108 to 2), storedOrdinals())

        preferences.save(WidgetConfig(44, setOf("family"), WidgetRange.THREE_DAYS))
        assertEquals(mapOf(44 to 1, 108 to 2), storedOrdinals())
        preferences.delete(44)
        preferences.save(WidgetConfig(900, emptySet(), WidgetRange.TODAY))
        assertEquals(mapOf(108 to 2, 900 to 3), storedOrdinals())
        assertEquals(WidgetRange.SEVEN_DAYS, preferences.configFor(108)?.range)
        assertEquals(true, preferences.configFor(108)?.followVisibleCalendars)
    }

    @Test
    fun managementItemsUsePersistedOrdinalsButKeepInternalIdsForConfiguration() = runTest {
        preferences.save(WidgetConfig(44, emptySet(), WidgetRange.THIRTY_DAYS))
        preferences.save(WidgetConfig(900, emptySet(), WidgetRange.THREE_DAYS))
        preferences.delete(44)

        assertEquals(
            listOf(WidgetManagementItem(900, WidgetRange.THREE_DAYS, displayOrdinal = 2)),
            loadWidgetManagementItems(intArrayOf(900), preferences),
        )
    }

    @Test
    fun unconfiguredActiveWidgetGetsAPersistedNameAndKeepsFollowVisibleDefaults() = runTest {
        val first = loadWidgetManagementItems(intArrayOf(44, 900), preferences)
        val reordered = loadWidgetManagementItems(intArrayOf(900, 44), preferences)
        assertEquals(listOf(1, 2), first.map { it.displayOrdinal })
        assertEquals(listOf(2, 1), reordered.map { it.displayOrdinal })
        assertEquals(true, preferences.configFor(44)?.followVisibleCalendars)
        assertEquals(emptySet<String>(), preferences.configFor(44)?.calendarIds)
    }

    private fun storedOrdinals(): Map<Int, Int> =
        database.openHelper.writableDatabase.query("SELECT * FROM widget_configuration").use { cursor ->
            // Missing persistence is a regression: UI positions cannot be used as stable labels.
            assertTrue("Widget display ordinals must be persisted", "displayOrdinal" in cursor.columnNames)
            buildMap {
                while (cursor.moveToNext()) {
                    put(cursor.getInt(cursor.getColumnIndexOrThrow("appWidgetId")), cursor.getInt(cursor.getColumnIndexOrThrow("displayOrdinal")))
                }
            }
        }
}

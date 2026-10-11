package com.brightnook.nookcalendar.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetOrdinalMigrationTest {
    @Test
    fun versionSixWidgetsReceiveSequentialLabelsWithoutLosingConfiguration() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "widget-ordinal-migration-test"
        context.deleteDatabase(name)
        val path = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(path, null).use { legacy ->
            val schema = JSONObject(context.assets.open("com.brightnook.nookcalendar.data.CalendarDatabase/6.json")
                .bufferedReader().use { it.readText() }).getJSONObject("database")
            schema.getJSONArray("entities").let { entities ->
                repeat(entities.length()) { index ->
                    val entity = entities.getJSONObject(index)
                    legacy.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                    entity.optJSONArray("indices")?.let { indices ->
                        repeat(indices.length()) { position ->
                            legacy.execSQL(indices.getJSONObject(position).getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                        }
                    }
                }
            }
            schema.getJSONArray("setupQueries").let { queries -> repeat(queries.length()) { legacy.execSQL(queries.getString(it)) } }
            // Insert out of order to prove deterministic assignment, not row insertion order.
            legacy.execSQL("INSERT INTO widget_configuration VALUES (900, 30, 1), (44, 3, 0), (108, 7, 0)")
            legacy.execSQL("INSERT INTO widget_calendar VALUES (44, 'work'), (44, 'family'), (108, 'personal')")
            legacy.version = 6
        }
        val database = Room.databaseBuilder(context, CalendarDatabase::class.java, name)
            .addMigrations(CalendarDatabase.MIGRATION_6_7)
            .addMigrations(CalendarDatabase.MIGRATION_7_8)
            .allowMainThreadQueries().build()
        try {
            database.openHelper.writableDatabase.query(
                "SELECT appWidgetId, rangeDays, followVisibleCalendars, displayOrdinal FROM widget_configuration ORDER BY appWidgetId",
            ).use { cursor ->
                val actual = buildList {
                    while (cursor.moveToNext()) add(List(4) { cursor.getInt(it) })
                }
                assertEquals(listOf(listOf(44, 3, 0, 1), listOf(108, 7, 0, 2), listOf(900, 30, 1, 3)), actual)
            }
            database.openHelper.writableDatabase.query(
                "SELECT appWidgetId, calendarId FROM widget_calendar ORDER BY appWidgetId, calendarId",
            ).use { cursor ->
                val actual = buildList {
                    while (cursor.moveToNext()) add(cursor.getInt(0) to cursor.getString(1))
                }
                assertEquals(listOf(44 to "family", 44 to "work", 108 to "personal"), actual)
            }
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }
}

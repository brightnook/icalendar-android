package com.victor2319.ioscalendar.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CalendarDatabaseMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun migrationOneToTwoPreservesLegacyRowsButInvalidatesTheirSyncTokenAndCoverage() {
        // Catches legacy coverage suppressing the bounded snapshot needed to assign resource ownership.
        context.deleteDatabase(DATABASE_NAME)
        val path = context.getDatabasePath(DATABASE_NAME).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(path, null).apply {
            val schema = JSONObject(
                context.assets.open("com.victor2319.ioscalendar.data.CalendarDatabase/1.json")
                    .bufferedReader()
                    .use { it.readText() },
            ).getJSONObject("database")
            val entities = schema.getJSONArray("entities")
            repeat(entities.length()) { index ->
                val entity = entities.getJSONObject(index)
                execSQL(
                    entity.getString("createSql")
                        .replace("\${TABLE_NAME}", entity.getString("tableName")),
                )
                val indices = entity.optJSONArray("indices")
                if (indices != null) repeat(indices.length()) { indexPosition ->
                    execSQL(
                        indices.getJSONObject(indexPosition).getString("createSql")
                            .replace("\${TABLE_NAME}", entity.getString("tableName")),
                    )
                }
            }
            val setupQueries = schema.getJSONArray("setupQueries")
            repeat(setupQueries.length()) { index -> execSQL(setupQueries.getString(index)) }
            execSQL("INSERT INTO calendars VALUES ('work', 'Work', -1, 1)")
            execSQL(
                "INSERT INTO event_masters VALUES " +
                    "('event', 'work', 'uid', '', 'Title', NULL, NULL, NULL, 0, NULL, 'CONFIRMED')",
            )
            execSQL("INSERT INTO sync_state VALUES ('work', 'legacy-token')")
            execSQL("INSERT INTO sync_coverage VALUES ('work', 1000, 2000, 'Z')")
            version = 1
            close()
        }

        val database = Room.databaseBuilder(context, CalendarDatabase::class.java, DATABASE_NAME)
            .addMigrations(CalendarDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
        val migrated = database.openHelper.writableDatabase
        migrated.query("SELECT title, remoteResourceHref FROM event_masters WHERE id = 'event'").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals("Title", cursor.getString(0))
            assertEquals(true, cursor.isNull(1))
        }
        migrated.query("SELECT syncToken FROM sync_state WHERE calendarId = 'work'").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(true, cursor.isNull(0))
        }
        migrated.query(
            "SELECT startEpochMillis, endEpochMillis, zoneId FROM sync_coverage WHERE calendarId = 'work'",
        ).use { cursor ->
            assertEquals(false, cursor.moveToFirst())
        }
        migrated.query("PRAGMA index_list('event_masters')").use { cursor ->
            val name = cursor.getColumnIndexOrThrow("name")
            val indices = buildSet {
                while (cursor.moveToNext()) add(cursor.getString(name))
            }
            assertEquals(true, "index_event_masters_calendarId_remoteResourceHref" in indices)
        }
        database.close()
        context.deleteDatabase(DATABASE_NAME)
    }

    private companion object {
        const val DATABASE_NAME = "calendar-migration-test"
    }
}

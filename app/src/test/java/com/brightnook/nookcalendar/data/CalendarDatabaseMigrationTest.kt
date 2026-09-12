package com.brightnook.nookcalendar.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
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
            .addMigrations(CalendarDatabase.MIGRATION_2_3)
            .addMigrations(CalendarDatabase.MIGRATION_3_4)
            .addMigrations(CalendarDatabase.MIGRATION_4_5)
            .addMigrations(CalendarDatabase.MIGRATION_5_6)
            .addMigrations(CalendarDatabase.MIGRATION_6_7)
            .addMigrations(CalendarDatabase.MIGRATION_7_8)
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

    @Test
    fun migrationThreeToFourRetainsWidgetSelectionsWhenCalendarCacheIsCleared() {
        // Catches migrating widget_calendar with its calendar foreign key, which deletes saved widget selections on a cache reset.
        context.deleteDatabase(DATABASE_NAME)
        val path = context.getDatabasePath(DATABASE_NAME).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(path, null).apply {
            val schema = JSONObject(
                context.assets.open("com.victor2319.ioscalendar.data.CalendarDatabase/3.json")
                    .bufferedReader()
                    .use { it.readText() },
            ).getJSONObject("database")
            val entities = schema.getJSONArray("entities")
            repeat(entities.length()) { index ->
                val entity = entities.getJSONObject(index)
                execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                entity.optJSONArray("indices")?.let { indices ->
                    repeat(indices.length()) { indexPosition ->
                        execSQL(indices.getJSONObject(indexPosition).getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                    }
                }
            }
            schema.getJSONArray("setupQueries").let { setupQueries ->
                repeat(setupQueries.length()) { index -> execSQL(setupQueries.getString(index)) }
            }
            execSQL("INSERT INTO calendars VALUES ('work', 'Work', -1, 1)")
            execSQL("INSERT INTO widget_configuration VALUES (42, 30)")
            execSQL("INSERT INTO widget_calendar VALUES (42, 'work')")
            version = 3
            close()
        }

        val database = Room.databaseBuilder(context, CalendarDatabase::class.java, DATABASE_NAME)
            .addMigrations(CalendarDatabase.MIGRATION_3_4)
            .addMigrations(CalendarDatabase.MIGRATION_4_5)
            .addMigrations(CalendarDatabase.MIGRATION_5_6)
            .addMigrations(CalendarDatabase.MIGRATION_6_7)
            .addMigrations(CalendarDatabase.MIGRATION_7_8)
            .allowMainThreadQueries()
            .build()
        val migrated = database.openHelper.writableDatabase
        migrated.execSQL("DELETE FROM calendars")
        migrated.query("SELECT calendarId FROM widget_calendar WHERE appWidgetId = 42").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals("work", cursor.getString(0))
        }
        database.close()
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun migrationFiveToSixAddsEmptyReminderDefinitionsWithoutDeletingExistingEvents() {
        context.deleteDatabase(DATABASE_NAME)
        val path = context.getDatabasePath(DATABASE_NAME).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(path, null).apply {
            val schema = JSONObject(
                context.assets.open("com.brightnook.nookcalendar.data.CalendarDatabase/5.json")
                    .bufferedReader().use { it.readText() },
            ).getJSONObject("database")
            schema.getJSONArray("entities").let { entities ->
                repeat(entities.length()) { index ->
                    val entity = entities.getJSONObject(index)
                    execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                    entity.optJSONArray("indices")?.let { indices ->
                        repeat(indices.length()) { position ->
                            execSQL(indices.getJSONObject(position).getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                        }
                    }
                }
            }
            schema.getJSONArray("setupQueries").let { queries -> repeat(queries.length()) { execSQL(queries.getString(it)) } }
            execSQL("INSERT INTO calendars VALUES ('work', 'Work', -1, 1)")
            execSQL("INSERT INTO event_masters VALUES ('event', 'work', 'uid', '', 'Title', NULL, NULL, NULL, 0, NULL, 'CONFIRMED', NULL)")
            version = 5
            close()
        }

        val database = Room.databaseBuilder(context, CalendarDatabase::class.java, DATABASE_NAME)
            .addMigrations(CalendarDatabase.MIGRATION_5_6)
            .addMigrations(CalendarDatabase.MIGRATION_6_7)
            .addMigrations(CalendarDatabase.MIGRATION_7_8)
            .allowMainThreadQueries()
            .build()
        database.openHelper.writableDatabase.query("SELECT reminders FROM event_masters WHERE id = 'event'").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals("", cursor.getString(0))
        }
        database.close()
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun migrationFiveToSixKeepsTheReminderColumnFromAnEarlierTestBuild() {
        // Catches a test build that added reminders before its schema version was incremented.
        context.deleteDatabase(DATABASE_NAME)
        val path = context.getDatabasePath(DATABASE_NAME).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(path, null).apply {
            val schema = JSONObject(
                context.assets.open("com.brightnook.nookcalendar.data.CalendarDatabase/5.json")
                    .bufferedReader().use { it.readText() },
            ).getJSONObject("database")
            schema.getJSONArray("entities").let { entities ->
                repeat(entities.length()) { index ->
                    val entity = entities.getJSONObject(index)
                    execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                    entity.optJSONArray("indices")?.let { indices ->
                        repeat(indices.length()) { position ->
                            execSQL(indices.getJSONObject(position).getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                        }
                    }
                }
            }
            schema.getJSONArray("setupQueries").let { queries -> repeat(queries.length()) { execSQL(queries.getString(it)) } }
            execSQL("ALTER TABLE event_masters ADD COLUMN reminders TEXT NOT NULL DEFAULT ''")
            version = 5
            close()
        }

        val database = Room.databaseBuilder(context, CalendarDatabase::class.java, DATABASE_NAME)
            .addMigrations(CalendarDatabase.MIGRATION_5_6)
            .addMigrations(CalendarDatabase.MIGRATION_6_7)
            .addMigrations(CalendarDatabase.MIGRATION_7_8)
            .allowMainThreadQueries()
            .build()

        database.openHelper.writableDatabase.query("SELECT reminders FROM event_masters").use { cursor ->
            assertEquals("reminders", cursor.columnNames.single())
        }
        database.close()
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun migrationSixToSevenAssignsReadableOrdinalsAndPreservesAllWidgetFields() {
        context.deleteDatabase(DATABASE_NAME)
        val path = context.getDatabasePath(DATABASE_NAME).apply { parentFile?.mkdirs() }
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
            legacy.execSQL("INSERT INTO widget_configuration VALUES (900, 30, 1), (44, 3, 0), (108, 7, 0)")
            legacy.execSQL("INSERT INTO widget_calendar VALUES (44, 'work'), (44, 'family'), (108, 'personal')")
            legacy.version = 6
        }
        val database = Room.databaseBuilder(context, CalendarDatabase::class.java, DATABASE_NAME)
            .addMigrations(CalendarDatabase.MIGRATION_6_7)
            .addMigrations(CalendarDatabase.MIGRATION_7_8)
            .allowMainThreadQueries().build()
        try {
            database.openHelper.writableDatabase.query(
                "SELECT appWidgetId, rangeDays, followVisibleCalendars, displayOrdinal FROM widget_configuration ORDER BY appWidgetId",
            ).use { cursor ->
                val actual = buildList { while (cursor.moveToNext()) add(List(4) { cursor.getInt(it) }) }
                assertEquals(listOf(listOf(44, 3, 0, 1), listOf(108, 7, 0, 2), listOf(900, 30, 1, 3)), actual)
            }
            database.openHelper.writableDatabase.query("SELECT appWidgetId, calendarId FROM widget_calendar ORDER BY appWidgetId, calendarId").use { cursor ->
                val actual = buildList { while (cursor.moveToNext()) add(cursor.getInt(0) to cursor.getString(1)) }
                assertEquals(listOf(44 to "family", 44 to "work", 108 to "personal"), actual)
            }
        } finally {
            database.close()
            context.deleteDatabase(DATABASE_NAME)
        }
    }

    @Test
    fun migrationSevenToEightPreservesCalendarAndCreatesDormantWriteMetadata() {
        context.deleteDatabase(DATABASE_NAME)
        val path = context.getDatabasePath(DATABASE_NAME).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(path, null).use { legacy ->
            val schema = JSONObject(
                context.assets.open("com.brightnook.nookcalendar.data.CalendarDatabase/7.json")
                    .bufferedReader().use { it.readText() },
            ).getJSONObject("database")
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
            schema.getJSONArray("setupQueries").let { queries ->
                repeat(queries.length()) { legacy.execSQL(queries.getString(it)) }
            }
            legacy.execSQL("INSERT INTO calendars VALUES ('personal', 'Personal', 1234, 1)")
            legacy.version = 7
        }

        val database = Room.databaseBuilder(context, CalendarDatabase::class.java, DATABASE_NAME)
            .addMigrations(CalendarDatabase.MIGRATION_7_8)
            .allowMainThreadQueries()
            .build()
        try {
            database.openHelper.writableDatabase.query(
                "SELECT id, name, colorArgb, isVisible, isWritable FROM calendars",
            ).use { cursor ->
                assertEquals(true, cursor.moveToFirst())
                assertEquals("personal", cursor.getString(0))
                assertEquals("Personal", cursor.getString(1))
                assertEquals(1234, cursor.getInt(2))
                assertEquals(1, cursor.getInt(3))
                assertEquals(0, cursor.getInt(4))
            }
            database.openHelper.writableDatabase.query(
                "SELECT calendarId, href, etag, calendarData, updatedAtEpochMillis FROM calendar_resources",
            ).use { cursor ->
                assertEquals(false, cursor.moveToFirst())
            }
        } finally {
            database.close()
            context.deleteDatabase(DATABASE_NAME)
        }
    }

    private companion object {
        const val DATABASE_NAME = "calendar-migration-test"
    }
}

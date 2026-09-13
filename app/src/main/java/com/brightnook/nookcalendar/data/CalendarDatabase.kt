package com.brightnook.nookcalendar.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

@Database(
    entities = [
        CalendarEntity::class,
        EventEntity::class,
        EventInstanceEntity::class,
        SyncStateEntity::class,
        CoverageEntity::class,
        WidgetConfigEntity::class,
        WidgetCalendarCrossRef::class,
        CalendarResourceEntity::class,
    ],
    version = 8,
    exportSchema = true,
)
abstract class CalendarDatabase : RoomDatabase() {
    abstract fun calendarDao(): CalendarDao
    abstract fun eventDao(): EventDao
    abstract fun syncStateDao(): SyncStateDao
    abstract fun widgetConfigDao(): WidgetConfigDao
    abstract fun calendarResourceDao(): CalendarResourceDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE event_masters ADD COLUMN remoteResourceHref TEXT")
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_event_masters_calendarId_remoteResourceHref " +
                        "ON event_masters(calendarId, remoteResourceHref)",
                )
                connection.execSQL(
                    "UPDATE sync_state SET syncToken = NULL WHERE calendarId IN " +
                        "(SELECT DISTINCT calendarId FROM event_masters WHERE remoteResourceHref IS NULL)",
                )
                connection.execSQL(
                    "DELETE FROM sync_coverage WHERE calendarId IN " +
                        "(SELECT DISTINCT calendarId FROM event_masters WHERE remoteResourceHref IS NULL)",
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "CREATE TABLE IF NOT EXISTS widget_configuration (appWidgetId INTEGER NOT NULL, rangeDays INTEGER NOT NULL, PRIMARY KEY(appWidgetId))",
                )
                connection.execSQL(
                    "CREATE TABLE IF NOT EXISTS widget_calendar (appWidgetId INTEGER NOT NULL, calendarId TEXT NOT NULL, PRIMARY KEY(appWidgetId, calendarId), FOREIGN KEY(appWidgetId) REFERENCES widget_configuration(appWidgetId) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(calendarId) REFERENCES calendars(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                connection.execSQL("CREATE INDEX IF NOT EXISTS index_widget_calendar_calendarId ON widget_calendar(calendarId)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "CREATE TABLE IF NOT EXISTS widget_calendar_new (appWidgetId INTEGER NOT NULL, calendarId TEXT NOT NULL, PRIMARY KEY(appWidgetId, calendarId), FOREIGN KEY(appWidgetId) REFERENCES widget_configuration(appWidgetId) ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                connection.execSQL(
                    "INSERT INTO widget_calendar_new (appWidgetId, calendarId) SELECT appWidgetId, calendarId FROM widget_calendar",
                )
                connection.execSQL("DROP TABLE widget_calendar")
                connection.execSQL("ALTER TABLE widget_calendar_new RENAME TO widget_calendar")
                connection.execSQL("CREATE INDEX IF NOT EXISTS index_widget_calendar_calendarId ON widget_calendar(calendarId)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "ALTER TABLE widget_configuration ADD COLUMN followVisibleCalendars INTEGER NOT NULL DEFAULT 0",
                )
                connection.execSQL(
                    "UPDATE widget_configuration SET followVisibleCalendars = 1 " +
                        "WHERE NOT EXISTS (SELECT 1 FROM widget_calendar " +
                        "WHERE widget_calendar.appWidgetId = widget_configuration.appWidgetId)",
                )
            }
        }

        /**
         * Adds reminder definitions to the released version-5 schema.
         *
         * Version 5 is already installed on user devices without this column, so this must be
         * a new schema version rather than reusing migration 4 to 5.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(connection: SQLiteConnection) {
                if (!connection.hasColumn("event_masters", "reminders")) {
                    connection.execSQL("ALTER TABLE event_masters ADD COLUMN reminders TEXT NOT NULL DEFAULT ''")
                }
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE widget_configuration ADD COLUMN displayOrdinal INTEGER NOT NULL DEFAULT 0")
                // Rank the existing internal IDs without ever exposing them as display names.
                // The correlated count works on the SQLite versions supported by Android 9.
                connection.execSQL(
                    "UPDATE widget_configuration SET displayOrdinal = " +
                        "(SELECT COUNT(*) FROM widget_configuration AS previous " +
                        "WHERE previous.appWidgetId <= widget_configuration.appWidgetId)",
                )
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "ALTER TABLE calendars ADD COLUMN isWritable INTEGER NOT NULL DEFAULT 0",
                )
                connection.execSQL(
                    "CREATE TABLE IF NOT EXISTS calendar_resources (" +
                        "calendarId TEXT NOT NULL, href TEXT NOT NULL, etag TEXT, " +
                        "calendarData TEXT NOT NULL, updatedAtEpochMillis INTEGER NOT NULL, " +
                        "PRIMARY KEY(calendarId, href), " +
                        "FOREIGN KEY(calendarId) REFERENCES calendars(id) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_calendar_resources_href ON calendar_resources(href)",
                )
            }
        }

        private fun SQLiteConnection.hasColumn(table: String, column: String): Boolean =
            prepare("PRAGMA table_info($table)").use { statement ->
                while (statement.step()) {
                    if (statement.getText(1) == column) return true
                }
                false
            }
    }
}

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
    ],
    version = 4,
    exportSchema = true,
)
abstract class CalendarDatabase : RoomDatabase() {
    abstract fun calendarDao(): CalendarDao
    abstract fun eventDao(): EventDao
    abstract fun syncStateDao(): SyncStateDao
    abstract fun widgetConfigDao(): WidgetConfigDao

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
    }
}

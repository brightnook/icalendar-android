package com.victor2319.ioscalendar.data

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
    version = 2,
    exportSchema = true,
)
abstract class CalendarDatabase : RoomDatabase() {
    abstract fun calendarDao(): CalendarDao
    abstract fun eventDao(): EventDao
    abstract fun syncStateDao(): SyncStateDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE event_masters ADD COLUMN remoteResourceHref TEXT")
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_event_masters_calendarId_remoteResourceHref " +
                        "ON event_masters(calendarId, remoteResourceHref)",
                )
            }
        }
    }
}

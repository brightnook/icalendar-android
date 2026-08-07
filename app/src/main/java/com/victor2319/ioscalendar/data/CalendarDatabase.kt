package com.victor2319.ioscalendar.data

import androidx.room.Database
import androidx.room.RoomDatabase

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
    version = 1,
    exportSchema = true,
)
abstract class CalendarDatabase : RoomDatabase() {
    abstract fun calendarDao(): CalendarDao
    abstract fun eventDao(): EventDao
    abstract fun syncStateDao(): SyncStateDao
}

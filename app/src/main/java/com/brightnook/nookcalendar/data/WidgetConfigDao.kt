package com.brightnook.nookcalendar.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

@Dao
interface WidgetConfigDao {
    @Query("SELECT * FROM widget_configuration WHERE appWidgetId = :appWidgetId")
    suspend fun findConfig(appWidgetId: Int): WidgetConfigEntity?

    @Query("SELECT calendarId FROM widget_calendar WHERE appWidgetId = :appWidgetId")
    suspend fun calendarIds(appWidgetId: Int): List<String>

    @Upsert
    suspend fun upsertConfig(config: WidgetConfigEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCalendarIds(crossRefs: List<WidgetCalendarCrossRef>)

    @Query("DELETE FROM widget_calendar WHERE appWidgetId = :appWidgetId")
    suspend fun deleteCalendarIds(appWidgetId: Int)

    @Query("DELETE FROM widget_configuration WHERE appWidgetId = :appWidgetId")
    suspend fun deleteConfig(appWidgetId: Int)

    @Transaction
    suspend fun replaceConfig(config: WidgetConfigEntity, calendarIds: Set<String>) {
        upsertConfig(config)
        deleteCalendarIds(config.appWidgetId)
        if (calendarIds.isNotEmpty()) {
            insertCalendarIds(calendarIds.map { WidgetCalendarCrossRef(config.appWidgetId, it) })
        }
    }
}

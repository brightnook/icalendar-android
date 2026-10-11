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

    @Query("SELECT COALESCE(MAX(displayOrdinal), 0) FROM widget_configuration")
    suspend fun highestDisplayOrdinal(): Int

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
        // Allocate inside the same transaction as the write so simultaneous saves cannot
        // assign the same ordinal. Edits always retain the original persisted ordinal.
        val existingOrdinal = findConfig(config.appWidgetId)?.displayOrdinal?.takeIf { it > 0 }
        upsertConfig(config.copy(displayOrdinal = existingOrdinal ?: (highestDisplayOrdinal() + 1)))
        deleteCalendarIds(config.appWidgetId)
        if (calendarIds.isNotEmpty()) {
            insertCalendarIds(calendarIds.map { WidgetCalendarCrossRef(config.appWidgetId, it) })
        }
    }
}

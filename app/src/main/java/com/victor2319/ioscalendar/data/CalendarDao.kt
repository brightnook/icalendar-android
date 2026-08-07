package com.victor2319.ioscalendar.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarDao {
    @Query("SELECT * FROM calendars ORDER BY name COLLATE NOCASE, id")
    fun observeAll(): Flow<List<CalendarEntity>>

    @Query("SELECT * FROM calendars WHERE id = :calendarId")
    suspend fun find(calendarId: String): CalendarEntity?

    @Upsert
    suspend fun upsert(calendar: CalendarEntity)

    @Query("UPDATE calendars SET isVisible = :visible WHERE id = :calendarId")
    suspend fun setVisible(calendarId: String, visible: Boolean)

    @Query("DELETE FROM calendars")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM calendars")
    suspend fun count(): Int
}

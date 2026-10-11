package com.brightnook.nookcalendar.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface CalendarResourceDao {
    @Query("SELECT * FROM calendar_resources WHERE calendarId = :calendarId AND href = :href")
    suspend fun find(calendarId: String, href: String): CalendarResourceEntity?

    @Upsert
    suspend fun upsertAll(resources: List<CalendarResourceEntity>)

    @Query("DELETE FROM calendar_resources WHERE calendarId = :calendarId")
    suspend fun deleteForCalendar(calendarId: String)

    @Query("DELETE FROM calendar_resources WHERE calendarId = :calendarId AND href IN (:hrefs)")
    suspend fun delete(calendarId: String, hrefs: Collection<String>)
}

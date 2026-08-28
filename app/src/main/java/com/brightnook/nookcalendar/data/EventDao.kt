package com.brightnook.nookcalendar.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Insert
    suspend fun insertEvent(event: EventEntity)

    @Update
    suspend fun updateEvent(event: EventEntity)

    @Upsert
    suspend fun upsertInstances(instances: List<EventInstanceEntity>)

    @Query("SELECT * FROM event_instances WHERE calendarId = :calendarId")
    suspend fun instancesForCalendar(calendarId: String): List<EventInstanceEntity>

    @Query("DELETE FROM event_instances WHERE calendarId = :calendarId AND instanceId IN (:instanceIds)")
    suspend fun deleteInstances(calendarId: String, instanceIds: Collection<String>)

    @Query("DELETE FROM event_masters WHERE calendarId = :calendarId AND uid IN (:uids)")
    suspend fun deleteEventsByUid(calendarId: String, uids: Collection<String>)

    @Query("DELETE FROM event_masters WHERE calendarId = :calendarId AND remoteResourceHref IN (:resourceHrefs)")
    suspend fun deleteEventsByResourceHref(calendarId: String, resourceHrefs: Collection<String>)

    @Query("SELECT * FROM event_masters WHERE id = :eventId")
    suspend fun findEvent(eventId: String): EventEntity?

    @Query("SELECT * FROM event_instances WHERE instanceId = :instanceId")
    suspend fun findInstance(instanceId: String): EventInstanceEntity?

    @Query("SELECT COUNT(*) FROM event_masters")
    suspend fun eventCount(): Int

    @Query("SELECT COUNT(*) FROM event_instances")
    suspend fun instanceCount(): Int

    @Query("DELETE FROM event_instances WHERE (isAllDay = 0 AND endEpochMillis <= :cutoffEpochMillis) OR (isAllDay = 1 AND endDateExclusive <= :cutoffDate)")
    suspend fun deleteHistoryBefore(cutoffEpochMillis: Long, cutoffDate: String)

    @Query("DELETE FROM event_masters WHERE NOT EXISTS (SELECT 1 FROM event_instances WHERE event_instances.eventId = event_masters.id AND event_instances.calendarId = event_masters.calendarId)")
    suspend fun deleteOrphanedEvents()

    @Query(
        """
        SELECT i.instanceId, i.eventId, i.calendarId, e.title,
               c.colorArgb AS calendarColorArgb, i.isAllDay,
               i.startEpochMillis, i.endEpochMillis, i.startDate, i.endDateExclusive,
               e.location
        FROM event_instances i
        JOIN event_masters e ON e.id = i.eventId AND e.calendarId = i.calendarId
        JOIN calendars c ON c.id = i.calendarId
        WHERE c.isVisible = 1
          AND i.calendarId IN (:calendarIds)
          AND (
            (i.isAllDay = 0 AND i.startEpochMillis < :endEpochMillis AND i.endEpochMillis > :startEpochMillis)
            OR
            (i.isAllDay = 1 AND i.startDate < :endDateExclusive AND i.endDateExclusive > :startDate)
          )
        ORDER BY i.isAllDay DESC,
                 CASE WHEN i.isAllDay = 1 THEN i.startDate END ASC,
                 CASE WHEN i.isAllDay = 0 THEN i.startEpochMillis END ASC,
                 e.title COLLATE NOCASE,
                 i.instanceId
        """,
    )
    fun observeAgenda(
        calendarIds: Set<String>,
        startEpochMillis: Long,
        endEpochMillis: Long,
        startDate: String,
        endDateExclusive: String,
    ): Flow<List<EventInstanceRow>>

    @Query(
        """
        SELECT i.instanceId, i.eventId, i.calendarId, e.title,
               c.colorArgb AS calendarColorArgb, i.isAllDay,
               i.startEpochMillis, i.endEpochMillis, i.startDate, i.endDateExclusive,
               e.location
        FROM event_instances i
        JOIN event_masters e ON e.id = i.eventId AND e.calendarId = i.calendarId
        JOIN calendars c ON c.id = i.calendarId
        WHERE c.isVisible = 1
          AND (
            (i.isAllDay = 0 AND i.startEpochMillis < :endEpochMillis AND i.endEpochMillis > :startEpochMillis)
            OR
            (i.isAllDay = 1 AND i.startDate < :endDateExclusive AND i.endDateExclusive > :startDate)
          )
        ORDER BY i.isAllDay DESC,
                 CASE WHEN i.isAllDay = 1 THEN i.startDate END ASC,
                 CASE WHEN i.isAllDay = 0 THEN i.startEpochMillis END ASC,
                 e.title COLLATE NOCASE,
                 i.instanceId
        """,
    )
    fun observeVisibleRange(
        startEpochMillis: Long,
        endEpochMillis: Long,
        startDate: String,
        endDateExclusive: String,
    ): Flow<List<EventInstanceRow>>

    @Query(
        """
        SELECT i.instanceId, i.eventId, i.calendarId,
               c.name AS calendarName, c.colorArgb AS calendarColorArgb,
               e.title, e.location, e.notes, i.isAllDay,
               i.startEpochMillis, i.endEpochMillis, i.startDate, i.endDateExclusive,
               e.originalTimeZoneId, e.recurrenceRule
        FROM event_instances i
        JOIN event_masters e ON e.id = i.eventId AND e.calendarId = i.calendarId
        JOIN calendars c ON c.id = i.calendarId
        WHERE i.instanceId = :instanceId
        """,
    )
    fun observeEvent(instanceId: String): Flow<EventDetailRow?>
}

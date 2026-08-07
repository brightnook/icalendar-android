package com.victor2319.ioscalendar.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface SyncStateDao {
    @Upsert
    suspend fun upsert(state: SyncStateEntity)

    @Query("SELECT * FROM sync_state WHERE calendarId = :calendarId")
    suspend fun find(calendarId: String): SyncStateEntity?

    @Query("SELECT * FROM sync_coverage WHERE calendarId = :calendarId ORDER BY zoneId, startEpochMillis")
    suspend fun coverageForCalendar(calendarId: String): List<CoverageEntity>

    @Query("DELETE FROM sync_coverage WHERE calendarId = :calendarId AND zoneId = :zoneId")
    suspend fun deleteCoverageForZone(calendarId: String, zoneId: String)

    @Upsert
    suspend fun upsertCoverage(coverage: List<CoverageEntity>)

    @Query("SELECT COUNT(*) FROM sync_state")
    suspend fun syncStateCount(): Int

    @Query("SELECT COUNT(*) FROM sync_coverage")
    suspend fun coverageCount(): Int
}

package com.brightnook.nookcalendar.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow

interface CalendarRepository {
    fun observeCalendars(): Flow<List<CalendarSummary>>
    fun observeAgenda(range: TimeRange, calendarIds: Set<String>): Flow<List<EventInstance>>
    fun observeDay(day: LocalDate, zoneId: ZoneId): Flow<List<EventInstance>>
    fun observeEvent(instanceId: String): Flow<EventDetail?>
    suspend fun upcomingReminders(after: Instant): List<ScheduledReminder> = emptyList()
    suspend fun findEventWriteSource(instanceId: String): EventWriteSource? = null
    suspend fun getSyncState(calendarId: String): CalendarSyncState
    suspend fun replaceCalendarSnapshot(snapshot: CalendarSnapshot)
    suspend fun applyCalendarDelta(delta: CalendarDelta)
    suspend fun setCalendarVisible(calendarId: String, visible: Boolean)
    suspend fun recordCoverage(calendarId: String, range: TimeRange)
    suspend fun clearCalendarHistory(before: CalendarHistoryBoundary)
    suspend fun clearCalendarCache()
}

data class CalendarHistoryBoundary(
    val instant: Instant,
    val localDate: LocalDate,
)

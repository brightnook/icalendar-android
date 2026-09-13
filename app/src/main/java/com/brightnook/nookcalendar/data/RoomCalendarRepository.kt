package com.brightnook.nookcalendar.data

import androidx.room.withTransaction
import com.brightnook.nookcalendar.domain.CalendarDelta
import com.brightnook.nookcalendar.domain.CalendarEvent
import com.brightnook.nookcalendar.domain.CalendarRepository
import com.brightnook.nookcalendar.domain.CalendarHistoryBoundary
import com.brightnook.nookcalendar.domain.CalendarSnapshot
import com.brightnook.nookcalendar.domain.CalendarSummary
import com.brightnook.nookcalendar.domain.CalendarSyncState
import com.brightnook.nookcalendar.domain.EventDetail
import com.brightnook.nookcalendar.domain.EventInstance
import com.brightnook.nookcalendar.domain.TimeRange
import com.brightnook.nookcalendar.domain.ScheduledReminder
import com.brightnook.nookcalendar.domain.EventReminder
import com.brightnook.nookcalendar.domain.EventWriteSource
import java.time.LocalDate
import java.time.ZoneId
import java.time.Instant
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class RoomCalendarRepository(private val database: CalendarDatabase) : CalendarRepository {
    private val calendarDao = database.calendarDao()
    private val eventDao = database.eventDao()
    private val syncStateDao = database.syncStateDao()
    private val calendarResourceDao = database.calendarResourceDao()

    override fun observeCalendars(): Flow<List<CalendarSummary>> =
        calendarDao.observeAll().map { calendars -> calendars.map(CalendarEntity::toDomain) }

    override fun observeAgenda(
        range: TimeRange,
        calendarIds: Set<String>,
    ): Flow<List<EventInstance>> {
        if (calendarIds.isEmpty()) return flowOf(emptyList())
        val dates = range.localDateBounds()
        return eventDao.observeAgenda(
            calendarIds = calendarIds,
            startEpochMillis = range.start.toEpochMilli(),
            endEpochMillis = range.endExclusive.toEpochMilli(),
            startDate = dates.first.toString(),
            endDateExclusive = dates.second.toString(),
        ).map { rows -> rows.map(EventInstanceRow::toDomain) }
    }

    override fun observeDay(day: LocalDate, zoneId: ZoneId): Flow<List<EventInstance>> {
        val start = day.atStartOfDay(zoneId).toInstant()
        val end = day.plusDays(1).atStartOfDay(zoneId).toInstant()
        return eventDao.observeVisibleRange(
            startEpochMillis = start.toEpochMilli(),
            endEpochMillis = end.toEpochMilli(),
            startDate = day.toString(),
            endDateExclusive = day.plusDays(1).toString(),
        ).map { rows -> rows.map(EventInstanceRow::toDomain) }
    }

    override fun observeEvent(instanceId: String): Flow<EventDetail?> =
        eventDao.observeEvent(instanceId).map { it?.toDomain() }

    override suspend fun findEventWriteSource(instanceId: String): EventWriteSource? {
        val instance = eventDao.findInstance(instanceId) ?: return null
        val event = eventDao.findEvent(instance.eventId) ?: return null
        val calendar = calendarDao.find(instance.calendarId) ?: return null
        val href = event.remoteResourceHref ?: return null
        val resource = calendarResourceDao.find(instance.calendarId, href) ?: return null
        return EventWriteSource(
            calendar = calendar.toDomain(),
            uid = event.uid,
            resource = resource.toDomain(),
            isRecurring = event.recurrenceKey.isNotEmpty() || !event.recurrenceRule.isNullOrBlank(),
        )
    }

    override suspend fun upcomingReminders(after: Instant): List<ScheduledReminder> =
        eventDao.reminderSources().flatMap { source ->
            val start = if (source.isAllDay) {
                LocalDate.parse(requireNotNull(source.startDate)).atStartOfDay(ZoneId.systemDefault()).toInstant()
            } else {
                Instant.ofEpochMilli(requireNotNull(source.startEpochMillis))
            }
            val end = if (source.isAllDay) {
                LocalDate.parse(requireNotNull(source.endDateExclusive)).atStartOfDay(ZoneId.systemDefault()).toInstant()
            } else {
                Instant.ofEpochMilli(requireNotNull(source.endEpochMillis))
            }
            source.reminders.decodeReminders().mapIndexedNotNull { index, reminder ->
                val triggerAt = when (reminder) {
                    is EventReminder.Absolute -> reminder.instant
                    is EventReminder.RelativeToStart -> start.plus(reminder.offset)
                    is EventReminder.RelativeToEnd -> end.plus(reminder.offset)
                }
                if (!triggerAt.isAfter(after)) return@mapIndexedNotNull null
                ScheduledReminder(
                    reminderId = stableReminderId(source.instanceId, index),
                    instanceId = source.instanceId,
                    date = start.atZone(ZoneId.systemDefault()).toLocalDate(),
                    triggerAt = triggerAt,
                    title = source.title,
                    location = source.location,
                )
            }
        }

    override suspend fun getSyncState(calendarId: String): CalendarSyncState = CalendarSyncState(
        syncToken = syncStateDao.find(calendarId)?.syncToken,
        coverage = syncStateDao.coverageForCalendar(calendarId).map { coverage ->
            TimeRange(
                start = Instant.ofEpochMilli(coverage.startEpochMillis),
                endExclusive = Instant.ofEpochMilli(coverage.endEpochMillis),
                zoneId = ZoneId.of(coverage.zoneId),
            )
        },
    )

    override suspend fun replaceCalendarSnapshot(snapshot: CalendarSnapshot) {
        database.withTransaction {
            validatePayload(snapshot.calendar.id, snapshot.events, snapshot.instances)
            val existing = calendarDao.find(snapshot.calendar.id)
            calendarDao.upsert(
                snapshot.calendar.copy(
                    isVisible = existing?.isVisible ?: snapshot.calendar.isVisible,
                ).toEntity(),
            )
            calendarResourceDao.deleteForCalendar(snapshot.calendar.id)
            if (snapshot.resources.isNotEmpty()) {
                require(snapshot.resources.all { it.calendarId == snapshot.calendar.id })
                calendarResourceDao.upsertAll(snapshot.resources.map { it.toEntity() })
            }
            val overlappingIds = eventDao.instancesForCalendar(snapshot.calendar.id)
                .filter { it.overlaps(snapshot.coverage) }
                .map(EventInstanceEntity::instanceId)
            if (overlappingIds.isNotEmpty()) {
                eventDao.deleteInstances(snapshot.calendar.id, overlappingIds)
            }
            upsertEvents(snapshot.events)
            if (snapshot.instances.isNotEmpty()) {
                eventDao.upsertInstances(snapshot.instances.map { it.toEntity() })
            }
            syncStateDao.upsert(SyncStateEntity(snapshot.calendar.id, snapshot.syncToken))
            mergeCoverage(snapshot.calendar.id, snapshot.coverage)
        }
    }

    override suspend fun applyCalendarDelta(delta: CalendarDelta) {
        database.withTransaction {
            validatePayload(delta.calendarId, delta.upsertEvents, delta.upsertInstances)
            val removedResourceHrefs = delta.deletedResourceHrefs + delta.replacedResourceHrefs
            if (removedResourceHrefs.isNotEmpty()) {
                eventDao.deleteEventsByResourceHref(delta.calendarId, removedResourceHrefs)
                calendarResourceDao.delete(delta.calendarId, removedResourceHrefs)
            }
            upsertEvents(delta.upsertEvents)
            if (delta.upsertInstances.isNotEmpty()) {
                eventDao.upsertInstances(delta.upsertInstances.map { it.toEntity() })
            }
            if (delta.deletedEventUids.isNotEmpty()) {
                eventDao.deleteEventsByUid(delta.calendarId, delta.deletedEventUids)
            }
            if (delta.deletedInstanceIds.isNotEmpty()) {
                eventDao.deleteInstances(delta.calendarId, delta.deletedInstanceIds)
            }
            if (delta.upsertResources.isNotEmpty()) {
                require(delta.upsertResources.all { it.calendarId == delta.calendarId })
                calendarResourceDao.upsertAll(delta.upsertResources.map { it.toEntity() })
            }
            syncStateDao.upsert(SyncStateEntity(delta.calendarId, delta.syncToken))
        }
    }

    override suspend fun setCalendarVisible(calendarId: String, visible: Boolean) {
        calendarDao.setVisible(calendarId, visible)
    }

    override suspend fun recordCoverage(calendarId: String, range: TimeRange) {
        database.withTransaction { mergeCoverage(calendarId, range) }
    }

    override suspend fun clearCalendarHistory(before: CalendarHistoryBoundary) {
        database.withTransaction {
            eventDao.deleteHistoryBefore(before.instant.toEpochMilli(), before.localDate.toString())
            eventDao.deleteOrphanedEvents()
            syncStateDao.deleteCoverageEndingBefore(before.instant.toEpochMilli())
            syncStateDao.trimCoverageBefore(before.instant.toEpochMilli())
        }
    }

    override suspend fun clearCalendarCache() {
        database.withTransaction { calendarDao.deleteAll() }
    }

    private suspend fun validatePayload(
        calendarId: String,
        events: List<CalendarEvent>,
        instances: List<EventInstance>,
    ) {
        require(events.all { it.calendarId == calendarId }) {
            "Every event must belong to calendar $calendarId"
        }
        require(instances.all { it.calendarId == calendarId }) {
            "Every instance must belong to calendar $calendarId"
        }

        val payloadEvents = events.associateBy(CalendarEvent::id)
        for (event in events) {
            val storedEvent = eventDao.findEvent(event.id)
            require(storedEvent == null || storedEvent.calendarId == calendarId) {
                "Event ${event.id} is owned by another calendar"
            }
        }
        for (instance in instances) {
            val storedInstance = eventDao.findInstance(instance.instanceId)
            require(storedInstance == null || storedInstance.calendarId == calendarId) {
                "Instance ${instance.instanceId} is owned by another calendar"
            }
            val ownerCalendarId = payloadEvents[instance.eventId]?.calendarId
                ?: eventDao.findEvent(instance.eventId)?.calendarId
            require(ownerCalendarId == null || ownerCalendarId == calendarId) {
                "Instance ${instance.instanceId} references an event owned by another calendar"
            }
        }
    }

    private suspend fun upsertEvents(events: List<CalendarEvent>) {
        for (event in events) {
            val entity = event.toEntity()
            if (eventDao.findEvent(event.id) == null) {
                eventDao.insertEvent(entity)
            } else {
                eventDao.updateEvent(entity)
            }
        }
    }

    private suspend fun mergeCoverage(calendarId: String, range: TimeRange) {
        val zoneId = range.zoneId.id
        val ranges = syncStateDao.coverageForCalendar(calendarId)
            .filter { it.zoneId == zoneId }
            .plus(range.toCoverageEntity(calendarId))
            .sortedBy(CoverageEntity::startEpochMillis)
        val merged = ranges.fold(mutableListOf<CoverageEntity>()) { result, next ->
            val current = result.lastOrNull()
            if (current != null && next.startEpochMillis <= current.endEpochMillis) {
                result[result.lastIndex] = current.copy(
                    endEpochMillis = maxOf(current.endEpochMillis, next.endEpochMillis),
                )
            } else {
                result += next
            }
            result
        }
        syncStateDao.deleteCoverageForZone(calendarId, zoneId)
        syncStateDao.upsertCoverage(merged)
    }
}

private fun stableReminderId(instanceId: String, index: Int): String =
    MessageDigest.getInstance("SHA-256")
        .digest("reminder:$instanceId:$index".toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }

private fun TimeRange.localDateBounds(): Pair<LocalDate, LocalDate> {
    val startDate = start.atZone(zoneId).toLocalDate()
    val endDateExclusive = endExclusive.minusNanos(1).atZone(zoneId).toLocalDate().plusDays(1)
    return startDate to endDateExclusive
}

private fun EventInstanceEntity.overlaps(range: TimeRange): Boolean = if (isAllDay) {
    val bounds = range.localDateBounds()
    LocalDate.parse(startDate).isBefore(bounds.second) && LocalDate.parse(endDateExclusive).isAfter(bounds.first)
} else {
    requireNotNull(startEpochMillis) < range.endExclusive.toEpochMilli() &&
        requireNotNull(endEpochMillis) > range.start.toEpochMilli()
}

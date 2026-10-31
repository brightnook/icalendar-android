package com.brightnook.nookcalendar.data

import androidx.room.withTransaction
import com.brightnook.nookcalendar.auth.CredentialsStore
import com.brightnook.nookcalendar.caldav.IcsEventWriter
import com.brightnook.nookcalendar.domain.*
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.first

/** Saves are database-only. A queued item is immutable until explicitly resolved/reconciled. */
class QueuedEventEditingRepository(
    private val database: CalendarDatabase,
    private val credentials: CredentialsStore,
    private val repository: CalendarRepository,
    private val online: EventEditingRepository,
    private val schedule: () -> Unit,
) : EventEditingRepository {
    override fun observeWritableCalendars() = online.observeWritableCalendars()
    override fun observeEvent(instanceId: String) = repository.observeEvent(instanceId)

    override suspend fun create(draft: EventDraft): EventEditingResult = enqueue(null, draft)
    override suspend fun update(instanceId: String, draft: EventDraft): EventEditingResult = enqueue(instanceId, draft)

    private suspend fun enqueue(instanceId: String?, draft: EventDraft): EventEditingResult {
        val account = credentials.outboxAccountKey() ?: return EventEditingResult.AuthenticationRequired
        val result = database.withTransaction {
            if (instanceId != null && database.eventOutboxDao().find(instanceId) != null) return@withTransaction EventEditingResult.Conflict
            val destination = database.calendarDao().find(draft.calendarId)
            if (destination?.isWritable != true) return@withTransaction EventEditingResult.ReadOnly
            val source = instanceId?.let { repository.findEventWriteSource(it) }
            if (instanceId != null && source == null) return@withTransaction EventEditingResult.NotFound
            if (source?.isRecurring == true) return@withTransaction EventEditingResult.RecurringUnsupported
            if (source != null && !source.calendar.isWritable) return@withTransaction EventEditingResult.ReadOnly
            if (source != null && !strongEtag(source.resource.etag)) return@withTransaction EventEditingResult.Conflict
            val uid = source?.uid ?: UUID.randomUUID().toString()
            val data = try { IcsEventWriter().write(draft, uid, Instant.now()) } catch (_: IllegalArgumentException) { return@withTransaction EventEditingResult.Invalid }
            database.eventOutboxDao().insert(EventOutboxEntity(instanceId ?: "local:$uid", account, uid,
                source?.calendar?.id ?: draft.calendarId, source?.resource?.href, source?.resource?.etag, encodeDraft(draft), data))
            EventEditingResult.Success
        }
        if (result == EventEditingResult.Success) runCatching { schedule() } // persisted even if scheduler unavailable
        return result
    }

    override suspend fun delete(instanceId: String): EventEditingResult {
        if (database.eventOutboxDao().find(instanceId) != null) return EventEditingResult.Conflict
        return online.delete(instanceId)
    }

    override suspend fun resolveConflict(instanceId: String, keepLocal: Boolean): EventEditingResult {
        val account = credentials.outboxAccountKey() ?: return EventEditingResult.AuthenticationRequired
        val result = database.withTransaction {
            val row = database.eventOutboxDao().find(instanceId) ?: return@withTransaction EventEditingResult.NotFound
            if (row.accountKey != account || row.state != "CONFLICT") return@withTransaction EventEditingResult.Invalid
            if (!keepLocal) {
                // Keep remote overlay until a successful read refresh confirms it, so a stale cache cannot reappear.
                if (row.remoteMissing) {
                    database.eventOutboxDao().update(row.copy(phase = "REMOTE_DELETED", state = "PENDING"))
                    return@withTransaction EventEditingResult.Success
                }
                val remote = row.remoteJson ?: return@withTransaction EventEditingResult.Invalid
                val remoteUid = row.remoteUid ?: return@withTransaction EventEditingResult.Invalid
                val remoteCalendar = row.remoteCalendarId ?: return@withTransaction EventEditingResult.Invalid
                database.eventOutboxDao().update(row.copy(draftJson = remote, calendarData = row.remoteData!!, uid = remoteUid,
                    originalCalendarId = row.originalCalendarId ?: row.sourceCalendarId,
                    originalHref = row.originalHref ?: row.sourceHref,
                    sourceCalendarId = remoteCalendar, sourceHref = row.remoteHref,
                    phase = "ACKNOWLEDGED", state = "PENDING", resultHref = row.remoteHref, resultEtag = row.remoteEtag,
                    remoteJson = null))
            } else {
                if (row.remoteMissing) {
                    // Never recreate a source after uncertain MOVE: the target might already contain it.
                    if (row.phase !in setOf("READY", "PUT_UNKNOWN", "ACKNOWLEDGED")) return@withTransaction EventEditingResult.Conflict
                    database.eventOutboxDao().update(row.copy(phase = "RECREATE_READY", state = "PENDING", remoteMissing = false))
                    return@withTransaction EventEditingResult.Success
                }
                // A random UID/path collision belongs to another event; never overwrite it as a retry.
                if (row.remoteJson == null) return@withTransaction EventEditingResult.Conflict
                if (row.remoteUid != row.uid) return@withTransaction EventEditingResult.Conflict
                if (!strongEtag(row.remoteEtag)) return@withTransaction EventEditingResult.Conflict
                val remoteCalendar = row.remoteCalendarId ?: return@withTransaction EventEditingResult.Invalid
                val remoteHref = row.remoteHref ?: return@withTransaction EventEditingResult.Invalid
                database.eventOutboxDao().update(row.copy(baseEtag = row.remoteEtag, phase = "READY", state = "PENDING",
                    originalCalendarId = row.originalCalendarId ?: row.sourceCalendarId,
                    originalHref = row.originalHref ?: row.sourceHref,
                    sourceCalendarId = remoteCalendar, sourceHref = remoteHref,
                    remoteJson = null, remoteData = null, remoteEtag = null))
            }
            EventEditingResult.Success
        }
        if (result == EventEditingResult.Success) runCatching { schedule() }
        return result
    }
}

internal fun strongEtag(etag: String?): Boolean = etag != null && etag.startsWith('"') && etag.endsWith('"') && etag.length > 2

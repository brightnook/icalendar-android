package com.brightnook.nookcalendar.data

import com.brightnook.nookcalendar.auth.CredentialsStore
import androidx.room.withTransaction
import com.brightnook.nookcalendar.caldav.*
import com.brightnook.nookcalendar.domain.*
import java.net.URI
import java.time.ZoneOffset
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Persist-before-write journal. FAILED items can only be retried by an explicit manual sync. */
class EventOutboxProcessor(
    private val database: CalendarDatabase,
    private val credentials: CredentialsStore,
    private val gateway: CalDavGateway,
    private val parser: IcsParser,
) {
    private val mutex = Mutex()
    private val dao get() = database.eventOutboxDao()

    suspend fun process(manual: Boolean = false) = mutex.withLock {
        val account = credentials.outboxAccountKey() ?: return@withLock
        val rows = dao.all().filter { it.accountKey == account }
        for (stored in rows) {
            if (stored.state == "CONFLICT") continue
            if (!manual && stored.phase in setOf("ACKNOWLEDGED", "REMOTE_DELETED", "DELETE_CONFIRMED")) continue
            // An interrupted attempt is ambiguous; do not silently retry writes in a background loop.
            if (stored.state == "SYNCING" && !manual) { dao.update(stored.copy(state = "FAILED")); continue }
            if (stored.state == "FAILED" && !manual) continue
            if (credentials.outboxAccountKey() != account) break
            dao.update(stored.copy(state = "SYNCING"))
            try { upload(stored.copy(state = "SYNCING"), account) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                dao.find(stored.instanceId)?.takeIf { it.state != "CONFLICT" }
                    ?.let { dao.update(it.copy(state = "FAILED")) }
            }
        }
    }

    private suspend fun upload(initial: EventOutboxEntity, account: String) {
        val auth = credentials.load() ?: error("Signed out")
        val principal = try { gateway.authenticate(auth) } finally { auth.appSpecificPassword.fill('\u0000') }
        val calendars = gateway.discoverCalendars(principal)
        val draft = decodeDraft(initial.draftJson)
        var row = initial
        suspend fun stillAccount() { check(credentials.outboxAccountKey() == account) { "Account changed" } }
        suspend fun save(next: EventOutboxEntity) { dao.update(next); row = next }
        suspend fun conflict(remote: RemoteResource?, calendarId: String = row.sourceCalendarId) {
            val remoteDraft = remote?.let { runCatching { resourceDraft(it.calendarData, calendarId, draft) }.getOrNull() }
            val remoteUid = remote?.let { runCatching { parser.parse(it.calendarData, draftRange(draft), ZoneOffset.UTC).events.single().uid }.getOrNull() }
            save(row.copy(state = "CONFLICT", remoteJson = remoteDraft?.let(::encodeDraft),
                remoteData = remote?.calendarData, remoteEtag = remote?.etag, remoteHref = remote?.href,
                remoteUid = remoteUid, remoteCalendarId = calendarId, remoteMissing = remote == null))
        }

        stillAccount()
        if (row.phase in setOf("ACKNOWLEDGED", "REMOTE_DELETED", "DELETE_CONFIRMED")) {
            val acknowledged = row.phase == "ACKNOWLEDGED"
            val actualCalendarId = if (acknowledged) draft.calendarId else row.sourceCalendarId
            val actualHref = (if (acknowledged) row.resultHref else row.sourceHref)
                ?: error("Acknowledged location unavailable")
            // Manual reconciliation is read-only and still works if the collection became read-only
            // or the original collection disappeared after a successful MOVE.
            val actualCalendar = calendars.firstOrNull { stableCalendarId(it.href) == actualCalendarId }
                ?: error("Acknowledged calendar unavailable")
            val remote = gateway.getResource(actualCalendar, actualHref)
            if ((acknowledged && remote != null && matches(remote.calendarData, row)) || (!acknowledged && remote == null)) {
                save(row.copy(state = "PENDING", resultEtag = if (acknowledged) remote?.etag else row.resultEtag))
            } else {
                save(row.copy(sourceCalendarId = actualCalendarId, sourceHref = actualHref,
                    originalCalendarId = row.originalCalendarId ?: row.sourceCalendarId,
                    originalHref = row.originalHref ?: row.sourceHref))
                conflict(remote, actualCalendarId)
            }
            return
        }
        val source = calendars.firstOrNull { stableCalendarId(it.href) == row.sourceCalendarId && it.isWritable }
            ?: error("Source unavailable")
        if (row.phase in setOf("DELETE_READY", "DELETE_UNKNOWN")) {
            val deleteHref = row.sourceHref ?: error("Delete source unavailable")
            val current = gateway.getResource(source, deleteHref)
            if (current == null) {
                save(row.copy(phase = "DELETE_CONFIRMED", state = "PENDING"))
                return
            }
            if (!strongEtag(current.etag) || current.etag != row.baseEtag) {
                conflict(current)
                return
            }
            stillAccount()
            save(row.copy(phase = "DELETE_UNKNOWN"))
            try {
                gateway.deleteResource(source, deleteHref, current.etag!!)
            } catch (_: CalDavFailure.Conflict) {
                val latest = gateway.getResource(source, deleteHref)
                if (latest == null) save(row.copy(phase = "DELETE_CONFIRMED", state = "PENDING")) else conflict(latest)
                return
            }
            val remaining = gateway.getResource(source, deleteHref)
            if (remaining == null) save(row.copy(phase = "DELETE_CONFIRMED", state = "PENDING")) else conflict(remaining)
            return
        }
        val destination = calendars.firstOrNull { stableCalendarId(it.href) == draft.calendarId && it.isWritable }
            ?: error("Destination unavailable")
        val href = row.sourceHref ?: URI(source.href).resolve("${row.uid}.ics").toString()
        if (row.phase in setOf("RECREATE_READY", "RECREATE_UNKNOWN")) {
            val destinationHref = URI(destination.href).resolve("${row.uid}.ics").toString()
            var remote = gateway.getResource(destination, destinationHref)
            if (remote != null && !matches(remote.calendarData, row)) { conflict(remote, draft.calendarId); return }
            if (remote == null) {
                stillAccount(); save(row.copy(phase = "RECREATE_UNKNOWN"))
                try { gateway.createResource(destination, "${row.uid}.ics", row.calendarData) }
                catch (_: CalDavFailure.Conflict) { conflict(gateway.getResource(destination, destinationHref), draft.calendarId); return }
                remote = gateway.getResource(destination, destinationHref) ?: error("Recreated write not visible")
            }
            if (!matches(remote.calendarData, row)) { conflict(remote, draft.calendarId); return }
            save(row.copy(phase = "ACKNOWLEDGED", state = "PENDING", resultHref = remote.href, resultEtag = remote.etag))
            return
        }
        if (row.phase == "MOVE_UNKNOWN") {
            val destinationHref = URI(destination.href).resolve(URI(href).path.substringAfterLast('/')).toString()
            val target = gateway.getResource(destination, destinationHref)
            val original = gateway.getResource(source, href)
            if (target != null && original == null && matches(target.calendarData, row)) {
                save(row.copy(phase = "ACKNOWLEDGED", state = "PENDING", resultHref = target.href, resultEtag = target.etag))
                return
            }
            if (target != null || original == null || !matches(original.calendarData, row) || !strongEtag(original.etag)) {
                if (original == null && target != null) conflict(target, draft.calendarId) else conflict(original)
                return
            }
            // Proven source still exists, destination absent: a conditional MOVE can safely be repeated.
            save(row.copy(phase = "PUT_ACKNOWLEDGED", resultHref = original.href, resultEtag = original.etag))
        }

        if (row.phase == "READY" || row.phase == "PUT_UNKNOWN") {
            val remote = gateway.getResource(source, href)
            if (remote != null && matches(remote.calendarData, row)) {
                save(row.copy(phase = "PUT_ACKNOWLEDGED", resultHref = remote.href, resultEtag = remote.etag))
            } else {
                if ((row.sourceHref == null && remote != null) ||
                    (row.sourceHref != null && (remote == null || !strongEtag(remote.etag) || remote.etag != row.baseEtag))) {
                    conflict(remote); return
                }
                stillAccount()
                save(row.copy(phase = "PUT_UNKNOWN"))
                val result = try {
                    if (row.sourceHref == null) gateway.createResource(source, "${row.uid}.ics", row.calendarData)
                    else gateway.updateResource(source, RemoteResource(href, row.baseEtag, row.calendarData))
                } catch (_: CalDavFailure.Conflict) {
                    conflict(gateway.getResource(source, href)); return
                }
                // Server may normalize ICS. Read back and verify instead of treating nullable ETag as safe.
                val confirmed = gateway.getResource(source, result.href) ?: error("Write not visible")
                if (!matches(confirmed.calendarData, row)) { conflict(confirmed); return }
                save(row.copy(phase = "PUT_ACKNOWLEDGED", resultHref = confirmed.href, resultEtag = confirmed.etag))
            }
        }

        if (row.sourceCalendarId != draft.calendarId) {
            stillAccount()
            val resource = gateway.getResource(source, row.resultHref ?: href) ?: error("Source unavailable")
            if (!matches(resource.calendarData, row) || !strongEtag(resource.etag) || resource.etag != row.resultEtag) {
                conflict(resource); return
            }
            save(row.copy(phase = "MOVE_UNKNOWN"))
            val moved = try { gateway.moveResource(source, resource, destination) }
                catch (_: CalDavFailure.Conflict) { conflict(gateway.getResource(source, resource.href)); return }
            val confirmed = gateway.getResource(destination, moved.href) ?: error("Move not visible")
            if (!matches(confirmed.calendarData, row)) { conflict(confirmed, draft.calendarId); return }
            save(row.copy(phase = "ACKNOWLEDGED", state = "PENDING", resultHref = confirmed.href, resultEtag = confirmed.etag))
        } else save(row.copy(phase = "ACKNOWLEDGED", state = "PENDING"))
    }

    /** Only retire a draft when the canonical cached resource proves the acknowledged write is represented. */
    suspend fun reconcile() = mutex.withLock {
        val account = credentials.outboxAccountKey() ?: return@withLock
        dao.all().filter { it.accountKey == account && it.state == "PENDING" && it.phase in setOf("ACKNOWLEDGED", "REMOTE_DELETED", "DELETE_CONFIRMED") }.forEach { row ->
            if (row.phase in setOf("REMOTE_DELETED", "DELETE_CONFIRMED")) {
                if (row.sourceHref == null || database.calendarResourceDao().find(row.sourceCalendarId, row.sourceHref) == null) {
                    database.withTransaction {
                        row.sourceHref?.let { database.eventDao().deleteEventsByResourceHref(row.sourceCalendarId, setOf(it)) }
                        if (row.originalCalendarId != null && row.originalHref != null)
                            database.eventDao().deleteEventsByResourceHref(row.originalCalendarId, setOf(row.originalHref))
                        dao.delete(row.instanceId)
                    }
                }
                return@forEach
            }
            val draft = decodeDraft(row.draftJson)
            val cached = row.resultHref?.let { database.calendarResourceDao().find(draft.calendarId, it) }
            val staleSource = (row.sourceCalendarId != draft.calendarId || row.sourceHref != row.resultHref) && row.sourceHref != null &&
                database.calendarResourceDao().find(row.sourceCalendarId, row.sourceHref) != null
            val staleOriginal = row.originalHref != null && row.originalCalendarId != null &&
                (row.originalCalendarId != draft.calendarId || row.originalHref != row.resultHref) &&
                database.calendarResourceDao().find(row.originalCalendarId, row.originalHref) != null
            if (cached != null && !staleSource && !staleOriginal && matches(cached.calendarData, row)) {
                val canonical = parser.parse(cached.calendarData, draftRange(draft), ZoneOffset.UTC)
                val color = database.calendarDao().find(draft.calendarId)?.colorArgb ?: 0
                database.withTransaction {
                    // Snapshot resource metadata is collection-wide, instances are range-scoped. Purge only
                    // proven superseded source resources before removing the protective overlay.
                    if (row.sourceHref != null && (row.sourceCalendarId != draft.calendarId || row.sourceHref != row.resultHref))
                        database.eventDao().deleteEventsByResourceHref(row.sourceCalendarId, setOf(row.sourceHref))
                    if (row.originalCalendarId != null && row.originalHref != null &&
                        (row.originalCalendarId != draft.calendarId || row.originalHref != row.resultHref))
                        database.eventDao().deleteEventsByResourceHref(row.originalCalendarId, setOf(row.originalHref))
                    // Rebuild the acknowledged nonrecurring resource, including instances outside the
                    // latest snapshot window (e.g. an occurrence moved to another month).
                    database.eventDao().deleteEventsByResourceHref(draft.calendarId, setOf(cached.href))
                    canonical.events.forEach { database.eventDao().insertEvent(it.toDomain(draft.calendarId, cached.href).toEntity()) }
                    database.eventDao().upsertInstances(canonical.instances.map { it.toDomain(draft.calendarId, color).toEntity() })
                    dao.delete(row.instanceId)
                }
            }
        }
    }

    private fun matches(data: String, row: EventOutboxEntity): Boolean = runCatching {
        val desired = decodeDraft(row.draftJson)
        resourceDraft(data, desired.calendarId, desired)?.let { actual ->
            actual.copy(timeZoneId = null) == desired.copy(title = desired.title.trim(),
                location = desired.location?.takeIf(String::isNotBlank), notes = desired.notes?.takeIf(String::isNotBlank), timeZoneId = null)
        } == true && parser.parse(data, draftRange(desired), ZoneOffset.UTC).events.single().uid == row.uid
    }.getOrDefault(false)

    private fun resourceDraft(data: String, calendarId: String, context: EventDraft): EventDraft? {
        // Read the date of the remote occurrence itself: another client can move it outside our draft range.
        // Only a bounded four-day window is expanded, even for untrusted/recurring input.
        val unfolded = data.replace(Regex("\\r?\\n[ \\t]"), "")
        val date = Regex("(?m)^DTSTART(?:;[^:\\r\\n]*)?:(\\d{8})").find(unfolded)
            ?.groupValues?.get(1)?.let { LocalDate.parse(it, DateTimeFormatter.BASIC_ISO_DATE) }
        val range = date?.let { TimeRange(it.minusDays(2).atStartOfDay(ZoneOffset.UTC).toInstant(),
            it.plusDays(2).atStartOfDay(ZoneOffset.UTC).toInstant()) } ?: draftRange(context)
        val parsed = parser.parse(data, range, ZoneOffset.UTC)
        val event = parsed.events.singleOrNull() ?: return null
        val item = parsed.instances.singleOrNull() ?: return null
        if (event.recurrenceRule != null || event.recurrenceId != null) return null
        return EventDraft(calendarId, event.title, event.location, event.notes, item.isAllDay, item.startInstant,
            item.endInstant, item.startDate, item.endDateExclusive, event.originalTimeZoneId, event.reminders)
    }

    private fun draftRange(draft: EventDraft): TimeRange {
        val start = draft.startInstant ?: draft.startDate!!.atStartOfDay(ZoneOffset.UTC).toInstant()
        return TimeRange(start.minusSeconds(86400), start.plusSeconds(86400 * 2))
    }
}

package com.brightnook.nookcalendar.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.brightnook.nookcalendar.auth.*
import com.brightnook.nookcalendar.caldav.*
import com.brightnook.nookcalendar.domain.*
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EventOutboxProcessorTest {
    private lateinit var db: CalendarDatabase
    private val remote = RemoteCalendar("remote", "https://example.test/work/", "Work", null, null, "session", true)
    private val target = remote.copy(id = "target", href = "https://example.test/home/")
    private val calendarId = stableCalendarId(remote.href)
    private val targetId = stableCalendarId(target.href)
    private val draft = EventDraft(calendarId, "Offline", isAllDay = false,
        startInstant = Instant.parse("2026-10-31T10:00:00Z"), endInstant = Instant.parse("2026-10-31T11:00:00Z"))
    private val store = object : CredentialsStore {
        var account: String? = "one@example.test"
        override suspend fun load() = account?.let { Credentials(it, "pass".toCharArray()) }
        override suspend fun save(credentials: Credentials) { account = credentials.appleId }
        override suspend fun clear() { account = null }
    }
    private lateinit var repository: RoomCalendarRepository
    private val gateway = Gateway()
    private lateinit var queue: QueuedEventEditingRepository
    private lateinit var processor: EventOutboxProcessor

    @Before fun setup() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), CalendarDatabase::class.java)
            .allowMainThreadQueries().build()
        db.calendarDao().upsert(CalendarEntity(calendarId, "Work", 1, true, true))
        db.calendarDao().upsert(CalendarEntity(targetId, "Home", 2, true, true))
        repository = RoomCalendarRepository(db) { store.outboxAccountKey() }
        queue = QueuedEventEditingRepository(db, store, repository, object : EventEditingRepository {
            override fun observeWritableCalendars() = flowOf(emptyList<CalendarSummary>())
            override fun observeEvent(instanceId: String) = repository.observeEvent(instanceId)
            override suspend fun create(draft: EventDraft) = error("Unexpected online create")
            override suspend fun update(instanceId: String, draft: EventDraft) = error("Unexpected online update")
            override suspend fun delete(instanceId: String) = EventEditingResult.NetworkFailure
        }) {}
        processor = EventOutboxProcessor(db, store, gateway, Ical4jIcsParser())
    }
    @After fun close() { db.close() }

    @Test fun saveIsDurableWithoutNetworkAndBusyDraftCannotBeReplaced() = runTest {
        assertEquals(EventEditingResult.Success, queue.create(draft))
        val row = db.eventOutboxDao().all().single()
        assertEquals(0, gateway.writes)
        assertEquals(draft, decodeDraft(row.draftJson))
        assertEquals(PendingEventSyncState.PENDING, repository.observeEvent(row.instanceId).first()!!.pendingSyncState)
        assertEquals(EventEditingResult.Conflict, queue.update(row.instanceId, draft.copy(title = "New")))
        assertEquals(EventEditingResult.Conflict, queue.delete(row.instanceId))
        assertEquals(draft, decodeDraft(db.eventOutboxDao().all().single().draftJson))
    }

    @Test fun failedUploadWaitsForManualRetry() = runTest {
        queue.create(draft); gateway.failCreate = true
        processor.process()
        assertEquals("FAILED", db.eventOutboxDao().all().single().state)
        processor.process(); assertEquals(1, gateway.writes)
        gateway.failCreate = false; processor.process(manual = true)
        assertEquals(2, gateway.writes)
        assertEquals("ACKNOWLEDGED", db.eventOutboxDao().all().single().phase)
    }

    @Test fun lostSuccessfulPutIsReadBackWithoutDuplicateWrite() = runTest {
        queue.create(draft); gateway.loseResponse = true
        processor.process(); assertEquals("FAILED", db.eventOutboxDao().all().single().state)
        gateway.loseResponse = false; processor.process(manual = true)
        assertEquals(1, gateway.writes)
        assertEquals("ACKNOWLEDGED", db.eventOutboxDao().all().single().phase)
    }

    @Test fun interruptedAttemptRequiresManualReadbackAndSurvivesCacheClear() = runTest {
        queue.create(draft)
        val row = db.eventOutboxDao().all().single()
        db.eventOutboxDao().update(row.copy(state = "SYNCING", phase = "PUT_UNKNOWN"))
        repository.clearCalendarCache(); processor.process()
        assertEquals("FAILED", db.eventOutboxDao().all().single().state)
        assertEquals(0, gateway.writes)
        assertEquals(draft.title, repository.observeEvent(row.instanceId).first()!!.title)
    }

    @Test fun anotherAccountNeverUploadsOrSeesPendingDraft() = runTest {
        queue.create(draft)
        val row = db.eventOutboxDao().all().single()
        store.account = "two@example.test"
        processor.process(manual = true)
        assertEquals(0, gateway.writes)
        assertNull(repository.observeEvent(row.instanceId).first())
        assertEquals(1, db.eventOutboxDao().all().size)
    }

    @Test fun remoteSnapshotCannotErasePendingDraftAndReminder() = runTest {
        queue.create(draft.copy(reminders = listOf(EventReminder.RelativeToStart(java.time.Duration.ofMinutes(-5)))))
        repository.replaceCalendarSnapshot(CalendarSnapshot(CalendarSummary(calendarId, "Work", 1, true, true),
            emptyList(), emptyList(), null, range()))
        val agenda = repository.observeAgenda(range(), setOf(calendarId)).first()
        assertEquals(listOf("Offline"), agenda.map { it.title })
        assertEquals(1, repository.upcomingReminders(Instant.parse("2026-10-31T00:00:00Z")).size)
    }

    @Test fun acknowledgementOverlayRetiresOnlyAfterCanonicalSnapshot() = runTest {
        queue.create(draft); processor.process(); processor.reconcile()
        assertEquals(1, db.eventOutboxDao().all().size)
        val resource = gateway.resources.values.single()
        snapshot(resource)
        processor.reconcile()
        assertTrue(db.eventOutboxDao().all().isEmpty())
        assertEquals(1, repository.observeAgenda(range(), setOf(calendarId)).first().size)
    }

    @Test fun changedEtagPreservesLocalAndRemoteDraftOutsideOriginalRange() = runTest {
        val old = IcsEventWriter().write(draft, "existing", Instant.now())
        val href = remote.href + "existing.ics"
        snapshot(RemoteResource(href, "\"old\"", old))
        val id = repository.observeAgenda(range(), setOf(calendarId)).first().single().instanceId
        assertEquals(EventEditingResult.Success, queue.update(id, draft.copy(title = "Local edit")))
        val changed = draft.copy(title = "Remote edit", startInstant = draft.startInstant!!.plusSeconds(86400 * 40),
            endInstant = draft.endInstant!!.plusSeconds(86400 * 40))
        gateway.resources[href] = RemoteResource(href, "\"changed\"", IcsEventWriter().write(changed, "existing", Instant.now()))
        processor.process()
        val detail = repository.observeEvent(id).first()!!
        assertEquals(PendingEventSyncState.CONFLICT, detail.pendingSyncState)
        assertEquals("Local edit", detail.title)
        assertEquals("Remote edit", detail.remoteConflict!!.title)
        assertEquals(0, gateway.writes)
        assertEquals(EventEditingResult.Success, queue.resolveConflict(id, true))
        processor.process()
        assertEquals(1, gateway.writes)
    }

    @Test fun weakEtagCannotBeQueuedForOverwrite() = runTest {
        val href = remote.href + "existing.ics"
        snapshot(RemoteResource(href, "W/\"old\"", IcsEventWriter().write(draft, "existing", Instant.now())))
        val id = repository.observeAgenda(range(), setOf(calendarId)).first().single().instanceId
        assertEquals(EventEditingResult.Conflict, queue.update(id, draft))
        assertTrue(db.eventOutboxDao().all().isEmpty())
    }

    @Test fun successfulMoveWithLostResponseIsNeverReplayedOrCopied() = runTest {
        val href = remote.href + "existing.ics"
        val original = RemoteResource(href, "\"old\"", IcsEventWriter().write(draft, "existing", Instant.now()))
        snapshot(original); gateway.resources[href] = original
        val id = repository.observeAgenda(range(), setOf(calendarId)).first().single().instanceId
        queue.update(id, draft.copy(calendarId = targetId, title = "Moved"))
        gateway.loseMoveResponse = true; processor.process()
        assertEquals("MOVE_UNKNOWN", db.eventOutboxDao().all().single().phase)
        gateway.loseMoveResponse = false; processor.process(manual = true)
        assertEquals(1, gateway.moves)
        assertEquals(1, gateway.writes)
        assertEquals("ACKNOWLEDGED", db.eventOutboxDao().all().single().phase)
        val moved = gateway.resources.values.single()
        val parsed = Ical4jIcsParser().parse(moved.calendarData, range(), java.time.ZoneOffset.UTC)
        repository.replaceCalendarSnapshot(CalendarSnapshot(CalendarSummary(targetId, "Home", 2, true, true),
            parsed.events.map { it.toDomain(targetId, moved.href) }, parsed.instances.map { it.toDomain(targetId, 2) },
            null, range(), listOf(CalendarResource(targetId, moved.href, moved.etag, moved.calendarData, 0))))
        processor.reconcile(); assertEquals(1, db.eventOutboxDao().all().size) // source snapshot still stale
        repository.replaceCalendarSnapshot(CalendarSnapshot(CalendarSummary(calendarId, "Work", 1, true, true),
            emptyList(), emptyList(), null, range()))
        processor.reconcile(); assertTrue(db.eventOutboxDao().all().isEmpty())
    }

    @Test fun unavailableDestinationDoesNotWriteAndDraftRemainsFailed() = runTest {
        queue.create(draft)
        gateway.discovered = emptyList()
        processor.process()
        assertEquals(0, gateway.writes)
        assertEquals("FAILED", db.eventOutboxDao().all().single().state)
    }

    @Test fun destinationCollisionCannotOverwriteOrDeleteEitherResource() = runTest {
        val href = remote.href + "existing.ics"
        val original = RemoteResource(href, "\"old\"", IcsEventWriter().write(draft, "existing", Instant.now()))
        snapshot(original); gateway.resources[href] = original
        val occupant = original.copy(href = target.href + "existing.ics", etag = "\"occupied\"")
        gateway.resources[occupant.href] = occupant
        val id = repository.observeAgenda(range(), setOf(calendarId)).first().single().instanceId
        queue.update(id, draft.copy(calendarId = targetId, title = "Moved"))
        processor.process()
        assertEquals(occupant, gateway.resources[occupant.href])
        assertNotNull(gateway.resources[href])
        assertEquals(2, gateway.resources.size)
        assertEquals("FAILED", db.eventOutboxDao().all().single().state)
        processor.process(manual = true)
        assertEquals("CONFLICT", db.eventOutboxDao().all().single().state)
        assertEquals(1, gateway.moves)
    }

    @Test fun acceptingRemoteDeletionHidesStaleCacheUntilRefreshConfirmsAbsence() = runTest {
        val href = remote.href + "existing.ics"
        snapshot(RemoteResource(href, "\"old\"", IcsEventWriter().write(draft, "existing", Instant.now())))
        val id = repository.observeAgenda(range(), setOf(calendarId)).first().single().instanceId
        queue.update(id, draft.copy(title = "Local")); processor.process()
        assertTrue(db.eventOutboxDao().all().single().remoteMissing)
        assertEquals(EventEditingResult.Success, queue.resolveConflict(id, false))
        assertTrue(repository.observeAgenda(range(), setOf(calendarId)).first().isEmpty())
        processor.reconcile(); assertEquals(1, db.eventOutboxDao().all().size)
        repository.replaceCalendarSnapshot(CalendarSnapshot(CalendarSummary(calendarId, "Work", 1, true, true),
            emptyList(), emptyList(), null, range()))
        processor.reconcile(); assertTrue(db.eventOutboxDao().all().isEmpty())
    }

    @Test fun keepingLocalAfterRemoteDeletionExplicitlyRecreatesWithSameUid() = runTest {
        val href = remote.href + "existing.ics"
        snapshot(RemoteResource(href, "\"old\"", IcsEventWriter().write(draft, "existing", Instant.now())))
        val id = repository.observeAgenda(range(), setOf(calendarId)).first().single().instanceId
        queue.update(id, draft.copy(title = "Local")); processor.process()
        assertEquals(EventEditingResult.Success, queue.resolveConflict(id, true))
        processor.process()
        assertEquals(1, gateway.writes)
        val result = gateway.resources.values.single()
        assertEquals("existing", Ical4jIcsParser().parse(result.calendarData, range(), java.time.ZoneOffset.UTC).events.single().uid)
        assertEquals("ACKNOWLEDGED", db.eventOutboxDao().all().single().phase)
    }

    @Test fun unknownMoveWithChangedTargetResolvesAgainstTargetNotMissingSource() = runTest {
        val href = remote.href + "existing.ics"
        val original = RemoteResource(href, "\"old\"", IcsEventWriter().write(draft, "existing", Instant.now()))
        snapshot(original); gateway.resources[href] = original
        val id = repository.observeAgenda(range(), setOf(calendarId)).first().single().instanceId
        queue.update(id, draft.copy(calendarId = targetId, title = "Local"))
        gateway.loseMoveResponse = true; processor.process()
        val movedHref = target.href + "existing.ics"
        gateway.resources[movedHref] = RemoteResource(movedHref, "\"other-client\"",
            IcsEventWriter().write(draft.copy(calendarId = targetId, title = "Other client"), "existing", Instant.now()))
        processor.process(manual = true)
        val conflict = db.eventOutboxDao().all().single()
        assertFalse(conflict.remoteMissing); assertEquals(movedHref, conflict.remoteHref)
        assertEquals(EventEditingResult.Success, queue.resolveConflict(id, true))
        assertEquals(targetId, db.eventOutboxDao().all().single().sourceCalendarId)
        gateway.loseMoveResponse = false; processor.process()
        assertEquals(1, gateway.moves); assertEquals(2, gateway.writes)
        assertFalse(gateway.resources.containsKey(href))
        assertEquals("ACKNOWLEDGED", db.eventOutboxDao().all().single().phase)
    }

    @Test fun unrelatedCalendarCopyWithSameUidRetainsAgendaAndReminder() = runTest {
        val withReminder = draft.copy(reminders = listOf(EventReminder.RelativeToStart(java.time.Duration.ofMinutes(-5))))
        val data = IcsEventWriter().write(withReminder, "shared-uid", Instant.now())
        val original = RemoteResource(remote.href + "a.ics", "\"old\"", data)
        snapshot(original)
        val parsed = Ical4jIcsParser().parse(data, range(), java.time.ZoneOffset.UTC)
        repository.replaceCalendarSnapshot(CalendarSnapshot(CalendarSummary(targetId, "Home", 2, true, true),
            parsed.events.map { it.toDomain(targetId, target.href + "copy.ics") },
            parsed.instances.map { it.toDomain(targetId, 2) }, null, range(),
            listOf(CalendarResource(targetId, target.href + "copy.ics", "\"copy\"", data, 0))))
        val id = repository.observeAgenda(range(), setOf(calendarId)).first().single().instanceId
        queue.update(id, withReminder.copy(title = "Local"))
        val agenda = repository.observeAgenda(range(), setOf(calendarId, targetId)).first()
        assertEquals(2, agenda.size)
        assertEquals(1, agenda.count { it.calendarId == targetId && it.title == "Offline" })
        assertEquals(2, repository.upcomingReminders(Instant.parse("2026-10-31T00:00:00Z")).size)
    }

    @Test fun acceptingRemoteUidCollisionAdoptsActualHrefAndUidWithoutOverwriting() = runTest {
        queue.create(draft)
        val pending = db.eventOutboxDao().all().single()
        val href = remote.href + pending.uid + ".ics"
        val other = RemoteResource(href, "\"other\"", IcsEventWriter().write(draft.copy(title = "Other event"), "other-uid", Instant.now()))
        gateway.resources[href] = other
        processor.process()
        assertEquals("CONFLICT", db.eventOutboxDao().all().single().state)
        assertEquals(EventEditingResult.Conflict, queue.resolveConflict(pending.instanceId, true))
        assertEquals(EventEditingResult.Success, queue.resolveConflict(pending.instanceId, false))
        assertEquals("other-uid", db.eventOutboxDao().all().single().uid)
        snapshot(other); processor.reconcile()
        assertTrue(db.eventOutboxDao().all().isEmpty()); assertEquals(0, gateway.writes)
    }

    @Test fun changedReadbackAfterSuccessfulMoveUsesActualTargetForKeepLocal() = runTest {
        val href = remote.href + "existing.ics"
        val original = RemoteResource(href, "\"old\"", IcsEventWriter().write(draft, "existing", Instant.now()))
        snapshot(original); gateway.resources[href] = original
        val id = repository.observeAgenda(range(), setOf(calendarId)).first().single().instanceId
        queue.update(id, draft.copy(calendarId = targetId, title = "Local"))
        gateway.changeAfterMove = true; processor.process()
        assertEquals(targetId, db.eventOutboxDao().all().single().remoteCalendarId)
        assertEquals(EventEditingResult.Success, queue.resolveConflict(id, true))
        gateway.changeAfterMove = false; processor.process()
        assertEquals(1, gateway.moves); assertEquals(2, gateway.writes)
        assertEquals("ACKNOWLEDGED", db.eventOutboxDao().all().single().phase)
    }

    @Test fun reconciliationPurgesOldOccurrenceOutsideLatestSnapshotWindow() = runTest {
        val href = remote.href + "existing.ics"
        val original = RemoteResource(href, "\"old\"", IcsEventWriter().write(draft, "existing", Instant.now()))
        snapshot(original); gateway.resources[href] = original
        val id = repository.observeAgenda(range(), setOf(calendarId)).first().single().instanceId
        val changed = draft.copy(startInstant = draft.startInstant!!.plusSeconds(40 * 86400),
            endInstant = draft.endInstant!!.plusSeconds(40 * 86400))
        queue.update(id, changed); processor.process()
        val result = gateway.resources[href]!!
        val newRange = TimeRange(changed.startInstant!!.minusSeconds(86400), changed.endInstant!!.plusSeconds(86400))
        val parsed = Ical4jIcsParser().parse(result.calendarData, newRange, java.time.ZoneOffset.UTC)
        repository.replaceCalendarSnapshot(CalendarSnapshot(CalendarSummary(calendarId, "Work", 1, true, true),
            parsed.events.map { it.toDomain(calendarId, href) }, parsed.instances.map { it.toDomain(calendarId, 1) },
            null, newRange, listOf(CalendarResource(calendarId, href, result.etag, result.calendarData, 0))))
        assertEquals(2, db.eventDao().instanceCount())
        processor.reconcile()
        assertEquals(1, db.eventDao().instanceCount())
        assertTrue(repository.observeAgenda(range(), setOf(calendarId)).first().isEmpty())
    }

    @Test fun manualSyncTurnsAcknowledgedButRemotelyEditedWriteIntoFreshConflictWithoutWriting() = runTest {
        queue.create(draft); processor.process()
        val acknowledged = db.eventOutboxDao().all().single()
        val saved = gateway.resources.values.single()
        snapshot(saved) // A matching old cache must not retire a newly detected conflict.
        val remoteEdit = saved.copy(etag = "\"new-client\"",
            calendarData = IcsEventWriter().write(draft.copy(title = "Remote edit"), acknowledged.uid, Instant.now()))
        gateway.resources[saved.href] = remoteEdit
        processor.process() // Background synchronization does not retry or alter acknowledged drafts.
        assertEquals("ACKNOWLEDGED", db.eventOutboxDao().all().single().phase)
        processor.process(manual = true)
        val conflict = db.eventOutboxDao().all().single()
        assertEquals("CONFLICT", conflict.state)
        assertEquals(saved.href, conflict.remoteHref)
        assertEquals("Remote edit", decodeDraft(conflict.remoteJson!!).title)
        assertEquals(1, gateway.writes)
        processor.reconcile(); assertEquals(1, db.eventOutboxDao().all().size)
        assertEquals(EventEditingResult.Success, queue.resolveConflict(acknowledged.instanceId, false))
        snapshot(remoteEdit); processor.reconcile()
        assertTrue(db.eventOutboxDao().all().isEmpty())
    }

    @Test fun manualSyncRestoresVisibleConflictWhenDeletedTombstoneWasRecreatedRemotely() = runTest {
        val href = remote.href + "existing.ics"
        snapshot(RemoteResource(href, "\"old\"", IcsEventWriter().write(draft, "existing", Instant.now())))
        val id = repository.observeAgenda(range(), setOf(calendarId)).first().single().instanceId
        queue.update(id, draft.copy(title = "Local edit")); processor.process()
        assertEquals(EventEditingResult.Success, queue.resolveConflict(id, false))
        assertNull(repository.observeEvent(id).first())
        gateway.resources[href] = RemoteResource(href, "\"recreated\"",
            IcsEventWriter().write(draft.copy(title = "Recreated remote"), "existing", Instant.now()))
        processor.process(manual = true)
        val detail = repository.observeEvent(id).first()!!
        assertEquals(PendingEventSyncState.CONFLICT, detail.pendingSyncState)
        assertEquals("Local edit", detail.title)
        assertEquals("Recreated remote", detail.remoteConflict!!.title)
        assertFalse(db.eventOutboxDao().all().single().remoteMissing)
        assertEquals(0, gateway.writes)
        processor.reconcile(); assertEquals(1, db.eventOutboxDao().all().size)
        assertEquals(EventEditingResult.Success, queue.resolveConflict(id, false))
    }

    @Test fun acknowledgedRemote404RequiresExplicitChoiceAndRecreatesOnlyAfterKeepLocal() = runTest {
        queue.create(draft); processor.process()
        val acknowledged = db.eventOutboxDao().all().single()
        val saved = gateway.resources.values.single()
        snapshot(saved); gateway.resources.clear()
        processor.process(manual = true)
        val conflict = db.eventOutboxDao().all().single()
        assertEquals("CONFLICT", conflict.state); assertTrue(conflict.remoteMissing)
        assertEquals(saved.href, conflict.sourceHref)
        assertEquals(1, gateway.writes)
        processor.reconcile(); assertEquals(1, db.eventOutboxDao().all().size)
        assertEquals(EventEditingResult.Success, queue.resolveConflict(acknowledged.instanceId, true))
        processor.process()
        assertEquals(2, gateway.writes)
        assertEquals(acknowledged.uid, db.eventOutboxDao().all().single().uid)
        assertEquals("ACKNOWLEDGED", db.eventOutboxDao().all().single().phase)
    }

    @Test fun manualAcknowledgedMoveReadbackUsesDestinationEvenWhenOriginalCollectionDisappears() = runTest {
        val href = remote.href + "existing.ics"
        val original = RemoteResource(href, "\"old\"", IcsEventWriter().write(draft, "existing", Instant.now()))
        snapshot(original); gateway.resources[href] = original
        val id = repository.observeAgenda(range(), setOf(calendarId)).first().single().instanceId
        queue.update(id, draft.copy(calendarId = targetId, title = "Local")); processor.process()
        val moved = gateway.resources.values.single()
        gateway.discovered = listOf(target.copy(isWritable = false))
        gateway.resources[moved.href] = moved.copy(etag = "\"changed\"",
            calendarData = moved.calendarData.replace("SUMMARY:Local", "SUMMARY:Remote"))
        processor.process(manual = true)
        val conflict = db.eventOutboxDao().all().single()
        assertEquals("CONFLICT", conflict.state)
        assertEquals(targetId, conflict.sourceCalendarId)
        assertEquals(moved.href, conflict.remoteHref)
        assertEquals(1, gateway.writes); assertEquals(1, gateway.moves)
    }

    private fun range() = TimeRange(Instant.parse("2026-10-30T00:00:00Z"), Instant.parse("2026-11-02T00:00:00Z"))
    private suspend fun snapshot(resource: RemoteResource) {
        val parsed = Ical4jIcsParser().parse(resource.calendarData, range(), java.time.ZoneOffset.UTC)
        repository.replaceCalendarSnapshot(CalendarSnapshot(CalendarSummary(calendarId, "Work", 1, true, true),
            parsed.events.map { it.toDomain(calendarId, resource.href) }, parsed.instances.map { it.toDomain(calendarId, 1) },
            null, range(), listOf(CalendarResource(calendarId, resource.href, resource.etag, resource.calendarData, 0))))
    }

    private inner class Gateway : CalDavGateway {
        val resources = mutableMapOf<String, RemoteResource>()
        var discovered = listOf(remote, target)
        var writes = 0; var moves = 0; var failCreate = false; var loseResponse = false; var loseMoveResponse = false
        var changeAfterMove = false
        override suspend fun authenticate(credentials: Credentials) = Principal("/principal/", "session")
        override suspend fun discoverCalendars(principal: Principal) = discovered
        override suspend fun fetchRange(calendar: RemoteCalendar, range: TimeRange) = error("unused")
        override suspend fun fetchDelta(calendar: RemoteCalendar, syncToken: String) = error("unused")
        override suspend fun getResource(calendar: RemoteCalendar, href: String) = resources[href]
        override suspend fun createResource(calendar: RemoteCalendar, href: String, calendarData: String): RemoteResource {
            writes++; if (failCreate) throw CalDavFailure.NetworkUnavailable()
            val result = RemoteResource(calendar.href + href, "\"saved\"", calendarData)
            check(resources.putIfAbsent(result.href, result) == null)
            if (loseResponse) throw CalDavFailure.NetworkUnavailable()
            return result
        }
        override suspend fun updateResource(calendar: RemoteCalendar, resource: RemoteResource): RemoteResource {
            writes++; check(resources[resource.href]?.etag == resource.etag)
            return resource.copy(etag = "\"updated\"").also { resources[it.href] = it }
        }
        override suspend fun moveResource(calendar: RemoteCalendar, resource: RemoteResource, destination: RemoteCalendar): RemoteResource {
            moves++; val href = destination.href + resource.href.substringAfterLast('/')
            check(href !in resources); check(resources[resource.href]?.etag == resource.etag)
            resources.remove(resource.href)
            val moved = resource.copy(href = href); resources[href] = moved
            if (changeAfterMove) resources[href] = moved.copy(etag = "\"changed-after-move\"",
                calendarData = moved.calendarData.replace("SUMMARY:Local", "SUMMARY:Other client"))
            if (loseMoveResponse) throw CalDavFailure.NetworkUnavailable()
            return moved
        }
    }
}

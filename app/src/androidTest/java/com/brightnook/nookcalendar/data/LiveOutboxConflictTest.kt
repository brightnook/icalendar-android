package com.brightnook.nookcalendar.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.brightnook.nookcalendar.CalendarApplication
import com.brightnook.nookcalendar.caldav.*
import com.brightnook.nookcalendar.domain.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real iCloud ETags, isolated local cache. No worker and no existing user event is mutated. */
@RunWith(AndroidJUnit4::class)
class LiveOutboxConflictTest {
    @Test fun bothConflictDecisionsPreserveTheExplicitlyChosenVersion() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveTestAccount") == "true")
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CalendarApplication
        val credentials = requireNotNull(app.container.credentialsStore.load())
        val verifier = ICloudCalDavGateway()
        val principal = try { verifier.authenticate(credentials) } finally { credentials.appSpecificPassword.fill('\u0000') }
        val source = verifier.discoverCalendars(principal).first { it.isWritable }
        val calendarId = stableCalendarId(source.href)
        val uid = "nookcalendar-conflict-${UUID.randomUUID()}"
        val href = source.href + "$uid.ics"
        val marker = "NookCalendar CONFLICT TEST $uid"
        val notes = "Disposable conflict verification $uid"
        val zone = ZoneId.systemDefault()
        val day = LocalDate.now(zone).plusDays(2)
        val start = day.atTime(14, 0).atZone(zone).toInstant()
        val range = TimeRange(day.atStartOfDay(zone).toInstant(), day.plusDays(1).atStartOfDay(zone).toInstant(), zone)
        val original = EventDraft(calendarId, marker, notes = notes, isAllDay = false,
            startInstant = start, endInstant = start.plusSeconds(3600), timeZoneId = zone.id)
        val writer = IcsEventWriter()
        val parser = Ical4jIcsParser()
        val database = Room.inMemoryDatabaseBuilder(app, CalendarDatabase::class.java).build()
        val repository = RoomCalendarRepository(database) { app.container.credentialsStore.outboxAccountKey() }
        val online = object : EventEditingRepository {
            override fun observeWritableCalendars() = flowOf(emptyList<CalendarSummary>())
            override fun observeEvent(instanceId: String) = repository.observeEvent(instanceId)
            override suspend fun create(draft: EventDraft): EventEditingResult = error("Unexpected online path")
            override suspend fun update(instanceId: String, draft: EventDraft): EventEditingResult = error("Unexpected online path")
            override suspend fun delete(instanceId: String) = EventEditingResult.Invalid
        }
        val queue = QueuedEventEditingRepository(database, app.container.credentialsStore, repository, online) {}
        val processor = EventOutboxProcessor(database, app.container.credentialsStore, ICloudCalDavGateway(), parser)
        var writeAttempted = false
        fun owned(data: String) {
            val event = parser.parse(data, range, zone).events.single()
            assertEquals(uid, event.uid)
            assertTrue(event.title.startsWith(marker))
            assertEquals(notes, event.notes)
        }
        suspend fun cacheRemote(): RemoteResource {
            val resource = requireNotNull(verifier.getResource(source, href))
            owned(resource.calendarData)
            val parsed = parser.parse(resource.calendarData, range, zone)
            repository.replaceCalendarSnapshot(CalendarSnapshot(CalendarSummary(calendarId, "Test", 0, true, true),
                parsed.events.map { it.toDomain(calendarId, href) }, parsed.instances.map { it.toDomain(calendarId, 0) },
                null, range, listOf(CalendarResource(calendarId, href, resource.etag, resource.calendarData, 0))))
            return resource
        }
        try {
            writeAttempted = true
            verifier.createResource(source, "$uid.ics", writer.write(original, uid, Instant.now()))
            var resource = cacheRemote()
            val instanceId = repository.observeAgenda(range, setOf(calendarId)).first().single().instanceId
            val local = original.copy(title = "$marker local choice")
            assertEquals(EventEditingResult.Success, queue.update(instanceId, local))
            val remote = original.copy(title = "$marker remote change")
            verifier.updateResource(source, resource.copy(calendarData = writer.write(remote, uid, Instant.now())))
            processor.process()
            var detail = requireNotNull(repository.observeEvent(instanceId).first())
            assertEquals(PendingEventSyncState.CONFLICT, detail.pendingSyncState)
            assertEquals(local.title, detail.title)
            assertEquals(remote.title, detail.remoteConflict?.title)
            assertEquals(remote.title, parser.parse(requireNotNull(verifier.getResource(source, href)).calendarData, range, zone).events.single().title)
            assertEquals(EventEditingResult.Success, queue.resolveConflict(instanceId, true))
            processor.process()
            resource = cacheRemote()
            processor.reconcile()
            assertEquals(local.title, parser.parse(resource.calendarData, range, zone).events.single().title)
            assertTrue(database.eventOutboxDao().all().isEmpty())
            println("LIVE_KEEP_LOCAL_CONFLICT_CHOICE_VERIFIED")

            val localAgain = original.copy(title = "$marker discard local choice")
            val remoteAgain = original.copy(title = "$marker keep remote choice")
            assertEquals(EventEditingResult.Success, queue.update(instanceId, localAgain))
            verifier.updateResource(source, resource.copy(calendarData = writer.write(remoteAgain, uid, Instant.now())))
            processor.process()
            detail = requireNotNull(repository.observeEvent(instanceId).first())
            assertEquals(PendingEventSyncState.CONFLICT, detail.pendingSyncState)
            assertEquals(EventEditingResult.Success, queue.resolveConflict(instanceId, false))
            resource = cacheRemote()
            processor.reconcile()
            assertEquals(remoteAgain.title, parser.parse(resource.calendarData, range, zone).events.single().title)
            assertTrue(database.eventOutboxDao().all().isEmpty())
            assertEquals(remoteAgain.title, repository.observeEvent(instanceId).first()!!.title)
            println("LIVE_KEEP_REMOTE_CONFLICT_CHOICE_VERIFIED")
        } finally {
            withContext(NonCancellable) {
                // No background scheduler exists for this isolated queue; it cannot recreate a cleaned-up resource.
                if (writeAttempted) verifier.getResource(source, href)?.let { remaining ->
                    owned(remaining.calendarData)
                    verifier.deleteResource(source, href, requireNotNull(remaining.etag))
                    assertNull(verifier.getResource(source, href))
                    println("LIVE_CONFLICT_TEST_EVENT_DELETE_VERIFIED")
                }
                database.close()
            }
        }
    }
}

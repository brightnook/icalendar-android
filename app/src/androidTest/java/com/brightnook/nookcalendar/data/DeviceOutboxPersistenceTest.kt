package com.brightnook.nookcalendar.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.brightnook.nookcalendar.auth.Credentials
import com.brightnook.nookcalendar.auth.CredentialsStore
import com.brightnook.nookcalendar.caldav.*
import com.brightnook.nookcalendar.domain.*
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Isolated real-device SQLite + simulated transport; never authenticates to iCloud. */
@RunWith(AndroidJUnit4::class)
class DeviceOutboxPersistenceTest {
    @Test fun offlineSaveSurvivesReopenAndRetriesOnlyManuallyWithoutDuplicates() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "disposable-outbox-${UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, CalendarDatabase::class.java, name).build()
        var database = open()
        val remote = RemoteCalendar("work", "https://example.test/work/", "Work", null, null, "session", true)
        val calendarId = stableCalendarId(remote.href)
        val credentials = object : CredentialsStore {
            override suspend fun load() = Credentials("disposable@example.test", "fake".toCharArray())
            override suspend fun save(credentials: Credentials) = Unit
            override suspend fun clear() = Unit
        }
        val start = Instant.parse("2026-10-31T10:00:00Z")
        val draft = EventDraft(calendarId, "Disposable device offline test", isAllDay = false,
            startInstant = start, endInstant = start.plusSeconds(3600))
        val gateway = object : CalDavGateway {
            var fail = true
            var loseReply = false
            var writes = 0
            val resources = mutableMapOf<String, RemoteResource>()
            override suspend fun authenticate(credentials: Credentials) = Principal("/principal", "session")
            override suspend fun discoverCalendars(principal: Principal) = listOf(remote)
            override suspend fun fetchRange(calendar: RemoteCalendar, range: TimeRange) = RemoteSnapshot(resources.values.toList(), null)
            override suspend fun fetchDelta(calendar: RemoteCalendar, syncToken: String) = RemoteDelta(emptyList(), emptyList(), null)
            override suspend fun getResource(calendar: RemoteCalendar, href: String) = resources[href]
            override suspend fun createResource(calendar: RemoteCalendar, href: String, calendarData: String): RemoteResource {
                writes++
                if (fail) throw CalDavFailure.NetworkUnavailable()
                val result = RemoteResource(remote.href + href, "\"device-test\"", calendarData)
                check(resources.putIfAbsent(result.href, result) == null)
                if (loseReply) throw CalDavFailure.NetworkUnavailable()
                return result
            }
        }
        try {
            database.calendarDao().upsert(CalendarEntity(calendarId, "Work", 0, true, true))
            var repository = RoomCalendarRepository(database) { credentials.outboxAccountKey() }
            val online = object : EventEditingRepository {
                override fun observeWritableCalendars() = flowOf(emptyList<CalendarSummary>())
                override fun observeEvent(instanceId: String) = repository.observeEvent(instanceId)
                override suspend fun create(draft: EventDraft): EventEditingResult = error("Unexpected online save")
                override suspend fun update(instanceId: String, draft: EventDraft): EventEditingResult = error("Unexpected online save")
                override suspend fun delete(instanceId: String) = EventEditingResult.NetworkFailure
            }
            val queue = QueuedEventEditingRepository(database, credentials, repository, online) {}
            assertEquals(EventEditingResult.Success, queue.create(draft))
            assertEquals(0, gateway.writes)
            val localId = database.eventOutboxDao().all().single().instanceId
            assertEquals(PendingEventSyncState.PENDING, repository.observeEvent(localId).first()!!.pendingSyncState)

            // Close/reopen the real database, independent of the live application's data.
            database.close()
            database = open()
            repository = RoomCalendarRepository(database) { credentials.outboxAccountKey() }
            assertEquals(draft.title, repository.observeEvent(localId).first()!!.title)
            val processor = EventOutboxProcessor(database, credentials, gateway, Ical4jIcsParser())
            processor.process()
            assertEquals("FAILED", database.eventOutboxDao().all().single().state)
            processor.process()
            assertEquals(1, gateway.writes)

            // Request succeeded but reply was lost: a later retry must read back, not create again.
            gateway.fail = false
            gateway.loseReply = true
            processor.process(manual = true)
            assertEquals("FAILED", database.eventOutboxDao().all().single().state)
            gateway.loseReply = false
            processor.process(manual = true)
            assertEquals(2, gateway.writes)
            assertEquals(1, gateway.resources.size)
            assertEquals("ACKNOWLEDGED", database.eventOutboxDao().all().single().phase)

            val resource = gateway.resources.values.single()
            val range = TimeRange(start.minusSeconds(86400), start.plusSeconds(86400))
            val parsed = Ical4jIcsParser().parse(resource.calendarData, range, ZoneOffset.UTC)
            repository.replaceCalendarSnapshot(CalendarSnapshot(CalendarSummary(calendarId, "Work", 0, true, true),
                parsed.events.map { it.toDomain(calendarId, resource.href) },
                parsed.instances.map { it.toDomain(calendarId, 0) }, null, range,
                listOf(CalendarResource(calendarId, resource.href, resource.etag, resource.calendarData, 0))))
            processor.reconcile()
            assertTrue(database.eventOutboxDao().all().isEmpty())
            val events = repository.observeAgenda(range, setOf(calendarId)).first()
            assertEquals(1, events.size)
            assertNull(events.single().pendingSyncState)
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }
}

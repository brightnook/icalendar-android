package com.victor2319.ioscalendar.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.victor2319.ioscalendar.auth.Credentials
import com.victor2319.ioscalendar.auth.CredentialsStore
import com.victor2319.ioscalendar.caldav.CalDavGateway
import com.victor2319.ioscalendar.caldav.IcsParser
import com.victor2319.ioscalendar.caldav.ParsedCalendar
import com.victor2319.ioscalendar.caldav.ParsedEvent
import com.victor2319.ioscalendar.caldav.ParsedInstance
import com.victor2319.ioscalendar.caldav.Principal
import com.victor2319.ioscalendar.caldav.RemoteCalendar
import com.victor2319.ioscalendar.caldav.RemoteDelta
import com.victor2319.ioscalendar.caldav.RemoteResource
import com.victor2319.ioscalendar.caldav.RemoteSnapshot
import com.victor2319.ioscalendar.data.CalendarDatabase
import com.victor2319.ioscalendar.data.RoomCalendarRepository
import com.victor2319.ioscalendar.domain.ClockProvider
import com.victor2319.ioscalendar.domain.EventStatus
import com.victor2319.ioscalendar.domain.TimeRange
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DefaultSyncCoordinatorRoomTest {
    private lateinit var database: CalendarDatabase
    private lateinit var repository: RoomCalendarRepository
    private lateinit var gateway: MutableDeltaGateway
    private lateinit var coordinator: DefaultSyncCoordinator

    private val agendaRange = TimeRange(
        Instant.parse("2026-03-14T00:00:00Z"),
        Instant.parse("2026-03-18T00:00:00Z"),
        TOKYO,
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, CalendarDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomCalendarRepository(database)
        gateway = MutableDeltaGateway()
        coordinator = DefaultSyncCoordinator(
            credentialsStore = StoredCredentials(),
            gateway = gateway,
            parser = DeltaFixtureParser(),
            repository = repository,
            clock = FixedRoomClock,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun movedDeltaResourceReplacesItsPreviousInstanceBeforeAdvancingToken() = runTest {
        // Catches upserting a moved occurrence without removing the old instance from the same resource.
        val calendarId = baselineCalendarId()
        gateway.delta = remoteDelta("moved", "next-token")

        val result = coordinator.sync(SyncRequest.Refresh)

        assertTrue(result is SyncResult.Success)
        val agenda = repository.observeAgenda(agendaRange, setOf(calendarId)).first()
        assertEquals(listOf(Instant.parse("2026-03-16T09:00:00Z")), agenda.map { it.startInstant })
        assertEquals("next-token", repository.getSyncState(calendarId).syncToken)
    }

    @Test
    fun cancelledDeltaResourceRemovesItsPreviousInstancesBeforeAdvancingToken() = runTest {
        // Catches a STATUS:CANCELLED master leaving previously expanded instances visible.
        val calendarId = baselineCalendarId()
        gateway.delta = remoteDelta("cancelled", "cancel-token")

        val result = coordinator.sync(SyncRequest.Refresh)

        assertTrue(result is SyncResult.Success)
        assertTrue(repository.observeAgenda(agendaRange, setOf(calendarId)).first().isEmpty())
        assertEquals("cancel-token", repository.getSyncState(calendarId).syncToken)
    }

    @Test
    fun changedResourceDeletionFailureRollsBackTheOldInstanceAndToken() = runTest {
        // Catches changed-resource deletion or token persistence escaping the repository transaction.
        val calendarId = baselineCalendarId()
        database.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER reject_changed_resource_delete
            BEFORE DELETE ON event_masters
            WHEN OLD.remoteResourceHref = '/calendars/work/a.ics'
            BEGIN
                SELECT RAISE(ABORT, 'fixture delete failure');
            END
            """.trimIndent(),
        )
        gateway.delta = remoteDelta("moved", "must-not-commit")

        val result = coordinator.sync(SyncRequest.Refresh)

        assertTrue(result is SyncResult.PartialSuccess)
        assertEquals(
            listOf(Instant.parse("2026-03-15T09:00:00Z")),
            repository.observeAgenda(agendaRange, setOf(calendarId)).first().map { it.startInstant },
        )
        assertEquals("baseline-token", repository.getSyncState(calendarId).syncToken)
    }

    private suspend fun baselineCalendarId(): String {
        gateway.snapshotContent = "old"
        assertTrue(coordinator.sync(SyncRequest.Initial) is SyncResult.Success)
        return repository.observeCalendars().first().single().id
    }
}

private class StoredCredentials : CredentialsStore {
    override suspend fun save(credentials: Credentials) = Unit
    override suspend fun load() = Credentials("person@example.com", "app-password".toCharArray())
    override suspend fun clear() = Unit
}

private object FixedRoomClock : ClockProvider {
    override val now: Instant = Instant.parse("2026-03-15T08:00:00Z")
    override val zoneId: ZoneId = ZoneId.of("Asia/Tokyo")
}

private class MutableDeltaGateway : CalDavGateway {
    var snapshotContent = "old"
    var delta = remoteDelta("old", "baseline-token")

    override suspend fun authenticate(credentials: Credentials) = Principal("/principal/", "session")

    override suspend fun discoverCalendars(principal: Principal) = listOf(
        RemoteCalendar(
            id = "work",
            href = "/calendars/work/",
            displayName = "Work",
            color = "#3366CCFF",
            syncToken = null,
            sessionId = principal.sessionId,
        ),
    )

    override suspend fun fetchRange(calendar: RemoteCalendar, range: TimeRange) = RemoteSnapshot(
        resources = listOf(RemoteResource(RESOURCE_HREF, "etag-$snapshotContent", snapshotContent)),
        syncToken = "baseline-token",
    )

    override suspend fun fetchDelta(calendar: RemoteCalendar, syncToken: String) = delta
}

private class DeltaFixtureParser : IcsParser {
    override fun parse(ics: String, range: TimeRange, displayZone: ZoneId): ParsedCalendar {
        val event = ParsedEvent(
            id = "event-u",
            uid = "U",
            recurrenceId = null,
            title = "Event U",
            location = null,
            notes = null,
            originalTimeZoneId = "UTC",
            isAllDay = false,
            recurrenceRule = null,
            status = if (ics == "cancelled") EventStatus.CANCELLED else EventStatus.CONFIRMED,
        )
        if (ics == "cancelled") return ParsedCalendar(listOf(event), emptyList())
        val day = if (ics == "moved") "2026-03-16" else "2026-03-15"
        return ParsedCalendar(
            events = listOf(event),
            instances = listOf(
                ParsedInstance(
                    instanceId = "instance-$day",
                    eventId = event.id,
                    uid = event.uid,
                    recurrenceId = null,
                    title = event.title,
                    location = null,
                    isAllDay = false,
                    startInstant = Instant.parse("${day}T09:00:00Z"),
                    endInstant = Instant.parse("${day}T10:00:00Z"),
                    startDate = null,
                    endDateExclusive = null,
                ),
            ),
        )
    }
}

private fun remoteDelta(content: String, token: String) = RemoteDelta(
    upserts = listOf(RemoteResource(RESOURCE_HREF, "etag-$content", content)),
    tombstones = emptyList(),
    syncToken = token,
)

private val TOKYO: ZoneId = ZoneId.of("Asia/Tokyo")
private const val RESOURCE_HREF = "/calendars/work/a.ics"

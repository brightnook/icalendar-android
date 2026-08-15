package com.victor2319.ioscalendar.sync

import android.content.Context
import android.database.sqlite.SQLiteDatabase
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
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MigratedV1SyncCoordinatorTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var database: CalendarDatabase? = null

    @After
    fun tearDown() {
        database?.close()
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun refreshAfterV1MigrationBaselinesMovedResourceBeforeAdvancingToken() = runTest {
        // Catches a migrated nonblank token using a delta that cannot delete its NULL-owned legacy master.
        val fixture = migratedFixture("moved")

        val result = fixture.coordinator.sync(SyncRequest.Refresh)

        assertTrue(result.toString(), result is SyncResult.Success)
        assertEquals(1, fixture.gateway.rangeCalls)
        assertEquals(0, fixture.gateway.deltaCalls)
        assertEquals(
            listOf(Instant.parse("2026-03-16T09:00:00Z")),
            fixture.repository.observeAgenda(AGENDA_RANGE, setOf(CALENDAR_ID)).first().map { it.startInstant },
        )
        assertEquals("snapshot-token", fixture.repository.getSyncState(CALENDAR_ID).syncToken)
    }

    @Test
    fun refreshAfterV1MigrationBaselinesCancelledResourceBeforeAdvancingToken() = runTest {
        // Catches a cancelled migrated resource leaving its legacy instance while publishing a delta token.
        val fixture = migratedFixture("cancelled")

        val result = fixture.coordinator.sync(SyncRequest.Refresh)

        assertTrue(result.toString(), result is SyncResult.Success)
        assertEquals(1, fixture.gateway.rangeCalls)
        assertEquals(0, fixture.gateway.deltaCalls)
        assertTrue(fixture.repository.observeAgenda(AGENDA_RANGE, setOf(CALENDAR_ID)).first().isEmpty())
        assertEquals("snapshot-token", fixture.repository.getSyncState(CALENDAR_ID).syncToken)
    }

    private suspend fun migratedFixture(content: String): MigratedFixture {
        createLegacyV1Database()
        val opened = Room.databaseBuilder(context, CalendarDatabase::class.java, DATABASE_NAME)
            .addMigrations(CalendarDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
        database = opened
        checkNotNull(opened.eventDao().findEvent(LEGACY_EVENT_ID))
        val repository = RoomCalendarRepository(opened)
        val gateway = MigratedGateway(content)
        return MigratedFixture(
            coordinator = DefaultSyncCoordinator(
                credentialsStore = MigratedCredentials,
                gateway = gateway,
                parser = MigratedFixtureParser(),
                repository = repository,
                clock = MigratedClock,
            ),
            repository = repository,
            gateway = gateway,
        )
    }

    private fun createLegacyV1Database() {
        context.deleteDatabase(DATABASE_NAME)
        val path = context.getDatabasePath(DATABASE_NAME).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(path, null).apply {
            val schema = JSONObject(
                context.assets.open("com.victor2319.ioscalendar.data.CalendarDatabase/1.json")
                    .bufferedReader()
                    .use { it.readText() },
            ).getJSONObject("database")
            val entities = schema.getJSONArray("entities")
            repeat(entities.length()) { index ->
                val entity = entities.getJSONObject(index)
                execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                entity.optJSONArray("indices")?.let { indices ->
                    repeat(indices.length()) { position ->
                        execSQL(
                            indices.getJSONObject(position).getString("createSql")
                                .replace("\${TABLE_NAME}", entity.getString("tableName")),
                        )
                    }
                }
            }
            val setupQueries = schema.getJSONArray("setupQueries")
            repeat(setupQueries.length()) { index -> execSQL(setupQueries.getString(index)) }
            execSQL("INSERT INTO calendars VALUES ('$CALENDAR_ID', 'Work', -1, 1)")
            execSQL(
                "INSERT INTO event_masters VALUES " +
                    "('$LEGACY_EVENT_ID', '$CALENDAR_ID', 'U', '', 'Event U', NULL, NULL, 'UTC', 0, NULL, 'CONFIRMED')",
            )
            execSQL(
                "INSERT INTO event_instances VALUES " +
                    "('$LEGACY_INSTANCE_ID', '$LEGACY_EVENT_ID', '$CALENDAR_ID', " +
                    "0, 1773565200000, 1773568800000, NULL, NULL)",
            )
            execSQL("INSERT INTO sync_state VALUES ('$CALENDAR_ID', 'legacy-token')")
            execSQL(
                "INSERT INTO sync_coverage VALUES " +
                    "('$CALENDAR_ID', 1772323200000, 1806537600000, 'Asia/Tokyo')",
            )
            version = 1
            close()
        }
    }

    private data class MigratedFixture(
        val coordinator: DefaultSyncCoordinator,
        val repository: RoomCalendarRepository,
        val gateway: MigratedGateway,
    )

    private companion object {
        const val DATABASE_NAME = "migrated-v1-sync-test"
        const val CALENDAR_ID = "24de227d1ac372e79772ce65452231cd9b5ada993233c41b166443c8504bec1c"
        const val LEGACY_EVENT_ID = "9bd5bc80ff228f4124c4ffccaadc74d178463ad8783cb4dcaac1d3fe1798e3d3"
        const val LEGACY_INSTANCE_ID = "63845b6afa631d0207559763c1c6b86d87e1c2a71c3f86b3254fd1e0cc62935e"
        val AGENDA_RANGE = TimeRange(
            Instant.parse("2026-03-14T00:00:00Z"),
            Instant.parse("2026-03-18T00:00:00Z"),
            ZoneId.of("Asia/Tokyo"),
        )
    }
}

private object MigratedCredentials : CredentialsStore {
    override suspend fun save(credentials: Credentials) = Unit
    override suspend fun load() = Credentials("person@example.com", "app-password".toCharArray())
    override suspend fun clear() = Unit
}

private object MigratedClock : ClockProvider {
    override val now: Instant = Instant.parse("2026-03-15T08:00:00Z")
    override val zoneId: ZoneId = ZoneId.of("Asia/Tokyo")
}

private class MigratedGateway(private val content: String) : CalDavGateway {
    var rangeCalls = 0
    var deltaCalls = 0

    override suspend fun authenticate(credentials: Credentials) = Principal("/principal/", "session")

    override suspend fun discoverCalendars(principal: Principal) = listOf(
        RemoteCalendar("work", "/calendars/work/", "Work", null, null, principal.sessionId),
    )

    override suspend fun fetchRange(calendar: RemoteCalendar, range: TimeRange): RemoteSnapshot {
        rangeCalls++
        return RemoteSnapshot(
            listOf(RemoteResource("/calendars/work/a.ics", "etag-$content", content)),
            "snapshot-token",
        )
    }

    override suspend fun fetchDelta(calendar: RemoteCalendar, syncToken: String): RemoteDelta {
        deltaCalls++
        return RemoteDelta(
            listOf(RemoteResource("/calendars/work/a.ics", "etag-$content", content)),
            emptyList(),
            "delta-token",
        )
    }
}

private class MigratedFixtureParser : IcsParser {
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
        return ParsedCalendar(
            listOf(event),
            listOf(
                ParsedInstance(
                    instanceId = "instance-2026-03-16",
                    eventId = event.id,
                    uid = event.uid,
                    recurrenceId = null,
                    title = event.title,
                    location = null,
                    isAllDay = false,
                    startInstant = Instant.parse("2026-03-16T09:00:00Z"),
                    endInstant = Instant.parse("2026-03-16T10:00:00Z"),
                    startDate = null,
                    endDateExclusive = null,
                ),
            ),
        )
    }
}

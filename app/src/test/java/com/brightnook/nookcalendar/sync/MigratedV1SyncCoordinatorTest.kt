package com.brightnook.nookcalendar.sync

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.brightnook.nookcalendar.auth.Credentials
import com.brightnook.nookcalendar.auth.CredentialsStore
import com.brightnook.nookcalendar.caldav.CalDavGateway
import com.brightnook.nookcalendar.caldav.IcsParser
import com.brightnook.nookcalendar.caldav.ParsedCalendar
import com.brightnook.nookcalendar.caldav.ParsedEvent
import com.brightnook.nookcalendar.caldav.ParsedInstance
import com.brightnook.nookcalendar.caldav.Principal
import com.brightnook.nookcalendar.caldav.RemoteCalendar
import com.brightnook.nookcalendar.caldav.RemoteDelta
import com.brightnook.nookcalendar.caldav.RemoteResource
import com.brightnook.nookcalendar.caldav.RemoteSnapshot
import com.brightnook.nookcalendar.data.CalendarDatabase
import com.brightnook.nookcalendar.data.RoomCalendarRepository
import com.brightnook.nookcalendar.domain.ClockProvider
import com.brightnook.nookcalendar.domain.EventStatus
import com.brightnook.nookcalendar.domain.TimeRange
import java.time.Instant
import java.time.YearMonth
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

    @Test
    fun ensureMonthAfterMigratedRefreshFetchesAndReplacesLegacyDataOutsideTheInitialRange() = runTest {
        // Catches preserved legacy coverage skipping the only snapshot that can assign an href outside Initial.
        val exactMonth = TimeRange(
            Instant.parse("2024-12-31T15:00:00Z"),
            Instant.parse("2025-01-31T15:00:00Z"),
            ZoneId.of("Asia/Tokyo"),
        )
        createLegacyV1Database(
            instanceId = OUTSIDE_LEGACY_INSTANCE_ID,
            instanceStartMillis = 1736931600000,
            instanceEndMillis = 1736935200000,
            coverage = exactMonth,
        )
        val fixture = openMigratedFixture(MigratedGateway("january-replacement", exactMonth))

        val refreshResult = fixture.coordinator.sync(SyncRequest.Refresh)
        val stateAfterRefresh = fixture.repository.getSyncState(CALENDAR_ID)
        val startsAfterRefresh = fixture.repository.observeAgenda(JANUARY_AGENDA_RANGE, setOf(CALENDAR_ID))
            .first().map { it.startInstant }
        val ensureResult = fixture.coordinator.sync(SyncRequest.EnsureMonth(YearMonth.of(2025, 1)))

        assertTrue(refreshResult.toString(), refreshResult is SyncResult.Success)
        assertEquals("initial-token", stateAfterRefresh.syncToken)
        assertTrue(stateAfterRefresh.coverage.none { it == exactMonth })
        assertEquals(listOf(Instant.parse("2025-01-15T09:00:00Z")), startsAfterRefresh)
        assertTrue(ensureResult.toString(), ensureResult is SyncResult.Success)
        assertEquals(
            listOf(
                TimeRange(
                    Instant.parse("2026-02-28T15:00:00Z"),
                    Instant.parse("2027-03-31T15:00:00Z"),
                    ZoneId.of("Asia/Tokyo"),
                ),
                exactMonth,
            ),
            fixture.gateway.requestedRanges,
        )
        assertEquals(
            listOf(Instant.parse("2025-01-16T09:00:00Z")),
            fixture.repository.observeAgenda(JANUARY_AGENDA_RANGE, setOf(CALENDAR_ID)).first()
                .map { it.startInstant },
        )
        val finalState = fixture.repository.getSyncState(CALENDAR_ID)
        assertEquals("month-token", finalState.syncToken)
        assertTrue(exactMonth in finalState.coverage)
    }

    private suspend fun migratedFixture(content: String): MigratedFixture {
        createLegacyV1Database()
        return openMigratedFixture(MigratedGateway(content))
    }

    private suspend fun openMigratedFixture(gateway: MigratedGateway): MigratedFixture {
        val opened = Room.databaseBuilder(context, CalendarDatabase::class.java, DATABASE_NAME)
            .addMigrations(CalendarDatabase.MIGRATION_1_2)
            .addMigrations(CalendarDatabase.MIGRATION_2_3)
            .addMigrations(CalendarDatabase.MIGRATION_3_4)
            .addMigrations(CalendarDatabase.MIGRATION_4_5)
            .addMigrations(CalendarDatabase.MIGRATION_5_6)
            .addMigrations(CalendarDatabase.MIGRATION_6_7)
            .addMigrations(CalendarDatabase.MIGRATION_7_8)
            .allowMainThreadQueries()
            .build()
        database = opened
        checkNotNull(opened.eventDao().findEvent(LEGACY_EVENT_ID))
        val repository = RoomCalendarRepository(opened)
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

    private fun createLegacyV1Database(
        instanceId: String = LEGACY_INSTANCE_ID,
        instanceStartMillis: Long = 1773565200000,
        instanceEndMillis: Long = 1773568800000,
        coverage: TimeRange = TimeRange(
            Instant.parse("2026-03-01T00:00:00Z"),
            Instant.parse("2027-04-01T00:00:00Z"),
            ZoneId.of("Asia/Tokyo"),
        ),
    ) {
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
                    "('$instanceId', '$LEGACY_EVENT_ID', '$CALENDAR_ID', " +
                    "0, $instanceStartMillis, $instanceEndMillis, NULL, NULL)",
            )
            execSQL("INSERT INTO sync_state VALUES ('$CALENDAR_ID', 'legacy-token')")
            execSQL(
                "INSERT INTO sync_coverage VALUES " +
                    "('$CALENDAR_ID', ${coverage.start.toEpochMilli()}, " +
                    "${coverage.endExclusive.toEpochMilli()}, '${coverage.zoneId.id}')",
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
        const val OUTSIDE_LEGACY_INSTANCE_ID =
            "ad89839e44b8a6ac8e6e2736df6ce9bba8d3b19532991c94e871e4244ee7bffb"
        val AGENDA_RANGE = TimeRange(
            Instant.parse("2026-03-14T00:00:00Z"),
            Instant.parse("2026-03-18T00:00:00Z"),
            ZoneId.of("Asia/Tokyo"),
        )
        val JANUARY_AGENDA_RANGE = TimeRange(
            Instant.parse("2025-01-14T00:00:00Z"),
            Instant.parse("2025-01-18T00:00:00Z"),
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

private class MigratedGateway(
    private val content: String,
    private val exactMonth: TimeRange? = null,
) : CalDavGateway {
    var rangeCalls = 0
    var deltaCalls = 0
    val requestedRanges = mutableListOf<TimeRange>()

    override suspend fun authenticate(credentials: Credentials) = Principal("/principal/", "session")

    override suspend fun discoverCalendars(principal: Principal) = listOf(
        RemoteCalendar("work", "/calendars/work/", "Work", null, null, principal.sessionId),
    )

    override suspend fun fetchRange(calendar: RemoteCalendar, range: TimeRange): RemoteSnapshot {
        rangeCalls++
        requestedRanges += range
        val responseContent = when {
            exactMonth == null -> content
            range == exactMonth -> content
            else -> null
        }
        return RemoteSnapshot(
            responseContent?.let {
                listOf(RemoteResource("/calendars/work/a.ics", "etag-$it", it))
            }.orEmpty(),
            when {
                exactMonth == null -> "snapshot-token"
                range == exactMonth -> "month-token"
                else -> "initial-token"
            },
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
        val day = if (ics == "january-replacement") "2025-01-16" else "2026-03-16"
        return ParsedCalendar(
            listOf(event),
            listOf(
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

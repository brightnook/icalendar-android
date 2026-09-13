package com.brightnook.nookcalendar.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.brightnook.nookcalendar.widget.RoomWidgetPreferences
import com.brightnook.nookcalendar.widget.WidgetConfig
import com.brightnook.nookcalendar.widget.WidgetRange
import com.brightnook.nookcalendar.domain.CalendarDelta
import com.brightnook.nookcalendar.domain.CalendarEvent
import com.brightnook.nookcalendar.domain.CalendarHistoryBoundary
import com.brightnook.nookcalendar.domain.CalendarSnapshot
import com.brightnook.nookcalendar.domain.CalendarSummary
import com.brightnook.nookcalendar.domain.CalendarResource
import com.brightnook.nookcalendar.domain.EventInstance
import com.brightnook.nookcalendar.domain.EventStatus
import com.brightnook.nookcalendar.domain.TimeRange
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomCalendarRepositoryTest {
    private lateinit var database: CalendarDatabase
    private lateinit var repository: RoomCalendarRepository

    private val dayRange = TimeRange(
        start = Instant.parse("2026-01-10T00:00:00Z"),
        endExclusive = Instant.parse("2026-01-11T00:00:00Z"),
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, CalendarDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomCalendarRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun agendaOrdersAllDayBeforeTimedEvents() = runTest {
        // Catches a query that sorts solely by timestamp and interleaves all-day events.
        repository.replaceCalendarSnapshot(calendarSnapshotWithMixedEvents())

        val items = repository.observeAgenda(dayRange, setOf("work")).first()

        assertEquals(listOf("all-day", "09:00", "14:00"), items.map { it.title })
    }

    @Test
    fun hiddenCalendarsAreExcludedFromAgendaAndRemainHiddenAfterRefresh() = runTest {
        // Catches visibility being ignored by agenda queries or reset by snapshot refresh.
        repository.replaceCalendarSnapshot(calendarSnapshotWithMixedEvents())
        repository.setCalendarVisible("work", false)
        repository.replaceCalendarSnapshot(
            calendarSnapshotWithMixedEvents().copy(
                calendar = workCalendar.copy(name = "Work renamed", isVisible = true),
            ),
        )

        assertTrue(repository.observeAgenda(dayRange, setOf("work")).first().isEmpty())
        assertFalse(repository.observeCalendars().first().single().isVisible)
        assertEquals("Work renamed", repository.observeCalendars().first().single().name)
    }

    @Test
    fun snapshotPersistsWritableStateAndRawResourcesForSafeEditing() = runTest {
        val resource = CalendarResource(
            calendarId = "work",
            href = "/calendars/owner/work/event.ics",
            etag = "\"etag-1\"",
            calendarData = "BEGIN:VCALENDAR\r\nEND:VCALENDAR\r\n",
            updatedAtEpochMillis = 1_757_721_600_000,
        )
        repository.replaceCalendarSnapshot(
            calendarSnapshotWithMixedEvents().copy(
                calendar = workCalendar.copy(isWritable = true),
                resources = listOf(resource),
            ),
        )

        assertTrue(repository.observeCalendars().first().single().isWritable)
        assertEquals(
            resource,
            database.calendarResourceDao().find("work", "/calendars/owner/work/event.ics")?.toDomain(),
        )
    }

    @Test
    fun emptyCalendarSelectionProducesAnEmptyAgenda() = runTest {
        // Catches an empty IN filter accidentally being treated as all calendars.
        repository.replaceCalendarSnapshot(calendarSnapshotWithMixedEvents())

        assertTrue(repository.observeAgenda(dayRange, emptySet()).first().isEmpty())
    }

    @Test
    fun dayObservationUsesZonedTimedOverlapAndDateOnlyAllDayOverlap() = runTest {
        // Catches UTC-midnight filtering and boundary-inclusive overlap mistakes.
        val tokyo = ZoneId.of("Asia/Tokyo")
        val coverage = TimeRange(
            Instant.parse("2026-01-09T15:00:00Z"),
            Instant.parse("2026-01-10T15:00:00Z"),
            tokyo,
        )
        val events = listOf(
            event("all-day", allDay = true),
            event("crosses-midnight"),
            event("ends-at-start"),
        )
        val instances = listOf(
            allDayInstance("all-day", LocalDate.parse("2026-01-10"), LocalDate.parse("2026-01-11")),
            timedInstance("crosses-midnight", "2026-01-09T14:30:00Z", "2026-01-09T15:30:00Z"),
            timedInstance("ends-at-start", "2026-01-09T14:00:00Z", "2026-01-09T15:00:00Z"),
        )
        repository.replaceCalendarSnapshot(snapshot(events, instances, coverage = coverage))

        val items = repository.observeDay(LocalDate.parse("2026-01-10"), tokyo).first()

        assertEquals(listOf("all-day", "crosses-midnight"), items.map { it.title })
    }

    @Test
    fun eventDetailJoinsCalendarMasterAndInstanceFields() = runTest {
        // Catches detail mapping from denormalized or mismatched calendar/event rows.
        repository.replaceCalendarSnapshot(calendarSnapshotWithMixedEvents())

        val detail = repository.observeEvent("09:00-instance").first()

        assertNotNull(detail)
        assertEquals("Work", detail!!.calendarName)
        assertEquals("09:00", detail.title)
        assertEquals("Room 9", detail.location)
        assertEquals("notes-09:00", detail.notes)
        assertEquals(Instant.parse("2026-01-10T09:00:00Z"), detail.startInstant)
    }

    @Test
    fun boundedSnapshotReplacesOnlyOverlappingInstancesAndMergesCoverage() = runTest {
        // Catches deleting the whole calendar cache during a bounded refresh or duplicating coverage.
        val twoDays = TimeRange(
            Instant.parse("2026-01-10T00:00:00Z"),
            Instant.parse("2026-01-12T00:00:00Z"),
        )
        val oldDayOne = event("old-day-one")
        val keptDayTwo = event("kept-day-two")
        repository.replaceCalendarSnapshot(
            snapshot(
                listOf(oldDayOne, keptDayTwo),
                listOf(
                    timedInstance("old-day-one", "2026-01-10T09:00:00Z", "2026-01-10T10:00:00Z"),
                    timedInstance("kept-day-two", "2026-01-11T09:00:00Z", "2026-01-11T10:00:00Z"),
                ),
                coverage = twoDays,
                syncToken = "first",
            ),
        )
        val replacement = event("replacement")

        repository.replaceCalendarSnapshot(
            snapshot(
                listOf(replacement),
                listOf(timedInstance("replacement", "2026-01-10T12:00:00Z", "2026-01-10T13:00:00Z")),
                coverage = dayRange,
                syncToken = "second",
            ),
        )

        val items = repository.observeAgenda(twoDays, setOf("work")).first()
        assertEquals(listOf("replacement", "kept-day-two"), items.map { it.title })
        assertEquals("second", database.syncStateDao().find("work")!!.syncToken)
        val coverage = database.syncStateDao().coverageForCalendar("work")
        assertEquals(1, coverage.size)
        assertEquals(twoDays.start.toEpochMilli(), coverage.single().startEpochMillis)
        assertEquals(twoDays.endExclusive.toEpochMilli(), coverage.single().endEpochMillis)
        assertNotNull(database.eventDao().findEvent("old-day-one-event"))
    }

    @Test
    fun boundedSnapshotRemovesOnlyOverlappingAllDayInstancesUsingHalfOpenDates() = runTest {
        // Catches skipping all-day replacement or treating end/start boundary equality as overlap.
        val overlapping = event("all-day-overlap", allDay = true)
        val endsAtStart = event("all-day-ends-at-start", allDay = true)
        val startsAtEnd = event("all-day-starts-at-end", allDay = true)
        repository.replaceCalendarSnapshot(
            snapshot(
                events = listOf(overlapping, endsAtStart, startsAtEnd),
                instances = listOf(
                    allDayInstance(
                        "all-day-overlap",
                        LocalDate.parse("2026-01-10"),
                        LocalDate.parse("2026-01-11"),
                    ),
                    allDayInstance(
                        "all-day-ends-at-start",
                        LocalDate.parse("2026-01-09"),
                        LocalDate.parse("2026-01-10"),
                    ),
                    allDayInstance(
                        "all-day-starts-at-end",
                        LocalDate.parse("2026-01-11"),
                        LocalDate.parse("2026-01-12"),
                    ),
                ),
                coverage = TimeRange(
                    Instant.parse("2026-01-09T00:00:00Z"),
                    Instant.parse("2026-01-12T00:00:00Z"),
                ),
            ),
        )
        val tokyoDay = TimeRange(
            Instant.parse("2026-01-09T15:00:00Z"),
            Instant.parse("2026-01-10T15:00:00Z"),
            ZoneId.of("Asia/Tokyo"),
        )

        repository.replaceCalendarSnapshot(
            snapshot(events = emptyList(), instances = emptyList(), coverage = tokyoDay),
        )

        assertNull(database.eventDao().findInstance("all-day-overlap-instance"))
        assertNotNull(database.eventDao().findInstance("all-day-ends-at-start-instance"))
        assertNotNull(database.eventDao().findInstance("all-day-starts-at-end-instance"))
        assertNotNull(database.eventDao().findEvent(overlapping.id))
    }

    @Test
    fun calendarDeltaUpsertsAndDeletesByUidAndInstanceId() = runTest {
        // Catches a partial delta that deletes only masters or ignores explicit instance tombstones.
        repository.replaceCalendarSnapshot(calendarSnapshotWithMixedEvents())
        val replacement = event("replacement")
        repository.applyCalendarDelta(
            CalendarDelta(
                calendarId = "work",
                upsertEvents = listOf(replacement),
                upsertInstances = listOf(
                    timedInstance("replacement", "2026-01-10T16:00:00Z", "2026-01-10T17:00:00Z"),
                ),
                deletedEventUids = setOf("09:00-uid"),
                deletedInstanceIds = setOf("14:00-instance"),
                syncToken = "delta-token",
            ),
        )

        val titles = repository.observeAgenda(dayRange, setOf("work")).first().map { it.title }
        assertEquals(listOf("all-day", "replacement"), titles)
        assertNull(database.eventDao().findEvent("09:00-event"))
        assertNotNull(database.eventDao().findEvent("14:00-event"))
        assertEquals("delta-token", database.syncStateDao().find("work")!!.syncToken)
    }

    @Test
    fun getSyncStateReturnsCommittedTokenAndCoverageAsDomainRanges() = runTest {
        // Catches the coordinator seeing DAO entities, losing zones, or reading an uncommitted token separately.
        val tokyoRange = TimeRange(
            Instant.parse("2026-01-31T15:00:00Z"),
            Instant.parse("2026-02-28T15:00:00Z"),
            ZoneId.of("Asia/Tokyo"),
        )
        repository.replaceCalendarSnapshot(
            snapshot(emptyList(), emptyList(), coverage = tokyoRange, syncToken = "committed"),
        )

        val state = repository.getSyncState("work")

        assertEquals("committed", state.syncToken)
        assertEquals(listOf(tokyoRange), state.coverage)
        assertEquals(null, repository.getSyncState("missing").syncToken)
        assertTrue(repository.getSyncState("missing").coverage.isEmpty())
    }

    @Test
    fun resourceTombstoneDeletesEveryMasterInThatResourceOnlyForTheTargetCalendar() = runTest {
        // Catches UID guessing, deleting only one VEVENT, or deleting another calendar that reuses the href.
        val sharedHref = "https://caldav.icloud.com/resource/shared.ics"
        val first = event("first").copy(remoteResourceHref = sharedHref)
        val override = event("override", uid = first.uid, recurrenceId = "20260110T100000Z")
            .copy(remoteResourceHref = sharedHref)
        val kept = event("kept").copy(remoteResourceHref = "https://caldav.icloud.com/resource/kept.ics")
        repository.replaceCalendarSnapshot(
            snapshot(
                events = listOf(first, override, kept),
                instances = listOf(
                    timedInstance("first", "2026-01-10T08:00:00Z", "2026-01-10T09:00:00Z"),
                    timedInstance(
                        "override",
                        "2026-01-10T10:00:00Z",
                        "2026-01-10T11:00:00Z",
                        eventId = override.id,
                    ),
                    timedInstance("kept", "2026-01-10T12:00:00Z", "2026-01-10T13:00:00Z"),
                ),
                syncToken = "work-before",
            ),
        )
        val personal = event("personal", calendarId = "personal").copy(remoteResourceHref = sharedHref)
        repository.replaceCalendarSnapshot(
            snapshot(
                events = listOf(personal),
                instances = listOf(
                    timedInstance(
                        "personal",
                        "2026-01-10T14:00:00Z",
                        "2026-01-10T15:00:00Z",
                        calendarId = "personal",
                    ),
                ),
                calendar = personalCalendar,
                syncToken = "personal-before",
            ),
        )

        repository.applyCalendarDelta(
            CalendarDelta(
                calendarId = "work",
                upsertEvents = emptyList(),
                upsertInstances = emptyList(),
                deletedEventUids = emptySet(),
                deletedInstanceIds = emptySet(),
                deletedResourceHrefs = setOf(sharedHref),
                syncToken = "work-after",
            ),
        )

        assertNull(database.eventDao().findEvent(first.id))
        assertNull(database.eventDao().findEvent(override.id))
        assertNull(database.eventDao().findInstance("first-instance"))
        assertNull(database.eventDao().findInstance("override-instance"))
        assertNotNull(database.eventDao().findEvent(kept.id))
        assertNotNull(database.eventDao().findEvent(personal.id))
        assertEquals("work-after", database.syncStateDao().find("work")!!.syncToken)
        assertEquals("personal-before", database.syncStateDao().find("personal")!!.syncToken)
    }

    @Test
    fun failedDeltaRollsBackResourceDeletionAndTokenTogether() = runTest {
        // Catches resource tombstones or tokens escaping the repository transaction when a later upsert fails.
        val href = "https://caldav.icloud.com/resource/atomic.ics"
        val existing = event("atomic-existing").copy(remoteResourceHref = href)
        repository.replaceCalendarSnapshot(
            snapshot(
                listOf(existing),
                listOf(
                    timedInstance(
                        "atomic-existing",
                        "2026-01-10T09:00:00Z",
                        "2026-01-10T10:00:00Z",
                    ),
                ),
                syncToken = "before",
            ),
        )
        val orphan = timedInstance(
            "atomic-orphan",
            "2026-01-10T11:00:00Z",
            "2026-01-10T12:00:00Z",
            eventId = "missing-master",
        )

        val failure = runCatching {
            repository.applyCalendarDelta(
                CalendarDelta(
                    calendarId = "work",
                    upsertEvents = emptyList(),
                    upsertInstances = listOf(orphan),
                    deletedEventUids = emptySet(),
                    deletedInstanceIds = emptySet(),
                    deletedResourceHrefs = setOf(href),
                    syncToken = "after",
                ),
            )
        }.exceptionOrNull()

        assertNotNull(failure)
        assertNotNull(database.eventDao().findEvent(existing.id))
        assertNotNull(database.eventDao().findInstance("atomic-existing-instance"))
        assertEquals("before", database.syncStateDao().find("work")!!.syncToken)
    }

    @Test
    fun resourceTombstoneDeletesTheOldResourceBeforeApplyingItsReplacement() = runTest {
        // Catches deleting a freshly upserted replacement by processing the resource tombstone after upserts.
        val href = "https://caldav.icloud.com/resource/rebuilt.ics"
        val old = event("old-resource").copy(remoteResourceHref = href)
        repository.replaceCalendarSnapshot(snapshot(listOf(old), emptyList(), syncToken = "before"))
        val replacement = event("replacement-resource").copy(remoteResourceHref = href)

        repository.applyCalendarDelta(
            CalendarDelta(
                calendarId = "work",
                upsertEvents = listOf(replacement),
                upsertInstances = emptyList(),
                deletedEventUids = emptySet(),
                deletedInstanceIds = emptySet(),
                deletedResourceHrefs = setOf(href),
                syncToken = "after",
            ),
        )

        assertNull(database.eventDao().findEvent(old.id))
        assertNotNull(database.eventDao().findEvent(replacement.id))
        assertEquals("after", database.syncStateDao().find("work")!!.syncToken)
    }

    @Test
    fun snapshotRejectsInstanceOwnedByAnotherCalendarWithoutChangingEitherCalendar() = runTest {
        // Catches a snapshot advancing calendar A while an instance targets a master owned by B.
        repository.replaceCalendarSnapshot(calendarSnapshotWithMixedEvents().copy(syncToken = "work-before"))
        val personalEvent = event("personal-existing", calendarId = "personal")
        repository.replaceCalendarSnapshot(
            snapshot(
                events = listOf(personalEvent),
                instances = listOf(
                    timedInstance(
                        title = "personal-existing",
                        start = "2026-01-10T11:00:00Z",
                        end = "2026-01-10T12:00:00Z",
                        calendarId = "personal",
                    ),
                ),
                calendar = personalCalendar,
                syncToken = "personal-before",
            ),
        )
        val crossOwnedInstance = timedInstance(
            title = "cross-owned",
            start = "2026-01-10T16:00:00Z",
            end = "2026-01-10T17:00:00Z",
            calendarId = "work",
            eventId = personalEvent.id,
        )

        val failure = runCatching {
            repository.replaceCalendarSnapshot(
                snapshot(
                    events = emptyList(),
                    instances = listOf(crossOwnedInstance),
                    calendar = workCalendar.copy(name = "Mutated work"),
                    syncToken = "work-after",
                ),
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertEquals("Work", repository.observeCalendars().first().single { it.id == "work" }.name)
        assertEquals("work-before", database.syncStateDao().find("work")!!.syncToken)
        assertEquals("personal-before", database.syncStateDao().find("personal")!!.syncToken)
        assertEquals(
            listOf("all-day", "09:00", "14:00"),
            repository.observeAgenda(dayRange, setOf("work")).first().map { it.title },
        )
        assertEquals(
            listOf("personal-existing"),
            repository.observeAgenda(dayRange, setOf("personal")).first().map { it.title },
        )
    }

    @Test
    fun deltaRejectsForeignCalendarRowsWithoutChangingEitherCalendar() = runTest {
        // Catches a delta for A silently upserting B while advancing or deleting from A.
        repository.replaceCalendarSnapshot(calendarSnapshotWithMixedEvents().copy(syncToken = "work-before"))
        val personalEvent = event("personal-existing", calendarId = "personal")
        repository.replaceCalendarSnapshot(
            snapshot(
                events = listOf(personalEvent),
                instances = listOf(
                    timedInstance(
                        title = "personal-existing",
                        start = "2026-01-10T11:00:00Z",
                        end = "2026-01-10T12:00:00Z",
                        calendarId = "personal",
                    ),
                ),
                calendar = personalCalendar,
                syncToken = "personal-before",
            ),
        )
        val foreignEvent = event("foreign-delta", calendarId = "personal")

        val failure = runCatching {
            repository.applyCalendarDelta(
                CalendarDelta(
                    calendarId = "work",
                    upsertEvents = listOf(foreignEvent),
                    upsertInstances = listOf(
                        timedInstance(
                            title = "foreign-delta",
                            start = "2026-01-10T16:00:00Z",
                            end = "2026-01-10T17:00:00Z",
                            calendarId = "personal",
                        ),
                    ),
                    deletedEventUids = setOf("09:00-uid"),
                    deletedInstanceIds = setOf("14:00-instance"),
                    syncToken = "work-after",
                ),
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertEquals("work-before", database.syncStateDao().find("work")!!.syncToken)
        assertEquals("personal-before", database.syncStateDao().find("personal")!!.syncToken)
        assertNull(database.eventDao().findEvent(foreignEvent.id))
        assertEquals(
            listOf("all-day", "09:00", "14:00"),
            repository.observeAgenda(dayRange, setOf("work")).first().map { it.title },
        )
        assertEquals(
            listOf("personal-existing"),
            repository.observeAgenda(dayRange, setOf("personal")).first().map { it.title },
        )
    }

    @Test
    fun failedDeltaForeignKeyRollsBackUpsertsAndPreservesPriorState() = runTest {
        // Catches removing the outer transaction when a later instance insert violates a foreign key.
        repository.replaceCalendarSnapshot(calendarSnapshotWithMixedEvents().copy(syncToken = "before-delta"))
        val validUpsert = event("atomic-new")
        val orphanInstance = timedInstance(
            title = "atomic-orphan",
            start = "2026-01-10T16:00:00Z",
            end = "2026-01-10T17:00:00Z",
            eventId = "missing-master",
        )

        val failure = runCatching {
            repository.applyCalendarDelta(
                CalendarDelta(
                    calendarId = "work",
                    upsertEvents = listOf(validUpsert),
                    upsertInstances = listOf(orphanInstance),
                    deletedEventUids = setOf("09:00-uid"),
                    deletedInstanceIds = setOf("14:00-instance"),
                    syncToken = "after-delta",
                ),
            )
        }.exceptionOrNull()

        assertNotNull(failure)
        assertTrue(failure!!.hasCauseNamed("SQLiteConstraintException"))
        assertNull(database.eventDao().findEvent(validUpsert.id))
        assertEquals(
            listOf("all-day", "09:00", "14:00"),
            repository.observeAgenda(dayRange, setOf("work")).first().map { it.title },
        )
        assertNotNull(database.eventDao().findEvent("09:00-event"))
        assertNotNull(database.eventDao().findInstance("14:00-instance"))
        assertEquals("before-delta", database.syncStateDao().find("work")!!.syncToken)
    }

    @Test
    fun coverageRecordsMergeAdjacentRangesButKeepZonesSeparate() = runTest {
        // Catches fragmented adjacent coverage or merging ranges whose date semantics use different zones.
        repository.replaceCalendarSnapshot(calendarSnapshotWithMixedEvents())
        repository.recordCoverage(
            "work",
            TimeRange(dayRange.endExclusive, Instant.parse("2026-01-12T00:00:00Z")),
        )
        repository.recordCoverage(
            "work",
            TimeRange(
                Instant.parse("2026-01-12T00:00:00Z"),
                Instant.parse("2026-01-13T00:00:00Z"),
                ZoneId.of("Asia/Tokyo"),
            ),
        )

        val coverage = database.syncStateDao().coverageForCalendar("work")
        assertEquals(2, coverage.size)
        val utcCoverage = coverage.single { it.zoneId == "Z" }
        assertEquals(dayRange.start.toEpochMilli(), utcCoverage.startEpochMillis)
        assertEquals(
            Instant.parse("2026-01-12T00:00:00Z").toEpochMilli(),
            utcCoverage.endEpochMillis,
        )
        assertEquals(1, coverage.count { it.zoneId == "Asia/Tokyo" })
    }

    @Test
    fun failedSnapshotForeignKeyRollsBackEveryTable() = runTest {
        // Catches transaction boundaries that leave calendar, event, sync, or coverage rows behind.
        val valid = event("valid")
        val orphan = timedInstance("orphan", "2026-01-10T10:00:00Z", "2026-01-10T11:00:00Z")

        val failure = runCatching {
            repository.replaceCalendarSnapshot(snapshot(listOf(valid), listOf(orphan), syncToken = "partial"))
        }.exceptionOrNull()

        assertNotNull("A real foreign-key violation must fail the transaction", failure)
        assertTrue(failure!!.hasCauseNamed("SQLiteConstraintException"))
        assertTrue(repository.observeCalendars().first().isEmpty())
        assertEquals(0, database.calendarDao().count())
        assertEquals(0, database.eventDao().eventCount())
        assertEquals(0, database.eventDao().instanceCount())
        assertEquals(0, database.syncStateDao().syncStateCount())
        assertEquals(0, database.syncStateDao().coverageCount())
    }

    @Test
    fun recurrenceKeyIsUniqueWithinCalendarAndLegalAcrossCalendars() = runTest {
        // Catches removing the composite recurrence index or accidentally making it global.
        val personalShared = event(
            title = "personal-shared",
            calendarId = "personal",
            uid = "shared-uid",
            recurrenceId = "20260110T090000Z",
        )
        repository.replaceCalendarSnapshot(
            snapshot(
                events = listOf(personalShared),
                instances = emptyList(),
                calendar = personalCalendar,
                syncToken = "personal-token",
            ),
        )
        val workShared = event(
            title = "work-shared",
            uid = "shared-uid",
            recurrenceId = "20260110T090000Z",
        )
        repository.replaceCalendarSnapshot(
            snapshot(events = listOf(workShared), instances = emptyList(), syncToken = "work-before"),
        )
        val duplicateWorkKey = event(
            title = "work-duplicate",
            uid = "shared-uid",
            recurrenceId = "20260110T090000Z",
        )

        val failure = runCatching {
            repository.replaceCalendarSnapshot(
                snapshot(
                    events = listOf(workShared.copy(title = "mutated"), duplicateWorkKey),
                    instances = emptyList(),
                    calendar = workCalendar.copy(name = "Mutated work"),
                    syncToken = "work-after",
                ),
            )
        }.exceptionOrNull()

        assertNotNull(failure)
        assertTrue(failure!!.hasCauseNamed("SQLiteConstraintException"))
        assertEquals("Work", repository.observeCalendars().first().single { it.id == "work" }.name)
        assertEquals("work-shared", database.eventDao().findEvent(workShared.id)!!.title)
        assertNull(database.eventDao().findEvent(duplicateWorkKey.id))
        assertEquals("work-before", database.syncStateDao().find("work")!!.syncToken)
        assertEquals("personal-token", database.syncStateDao().find("personal")!!.syncToken)
        assertNotNull(database.eventDao().findEvent(personalShared.id))
        assertEquals(1, database.syncStateDao().coverageForCalendar("work").size)
    }

    @Test
    fun clearCalendarCacheRemovesAllRepositoryOwnedRows() = runTest {
        // Catches cache clearing that leaves sync or coverage metadata behind.
        repository.replaceCalendarSnapshot(calendarSnapshotWithMixedEvents())

        repository.clearCalendarCache()

        assertTrue(repository.observeCalendars().first().isEmpty())
        assertEquals(0, database.eventDao().eventCount())
        assertEquals(0, database.eventDao().instanceCount())
        assertEquals(0, database.syncStateDao().syncStateCount())
        assertEquals(0, database.syncStateDao().coverageCount())
    }

    @Test
    fun clearHistoryRemovesOnlyPastInstancesOfARecurringEventAndKeepsItsMaster() = runTest {
        // Catches purging a recurring master merely because one of its historical instances was removed.
        val recurring = event("weekly", id = "weekly-master").copy(recurrenceRule = "FREQ=WEEKLY")
        repository.replaceCalendarSnapshot(
            snapshot(
                events = listOf(recurring),
                instances = listOf(
                    timedInstance(
                        title = "weekly",
                        start = "2026-01-25T09:00:00Z",
                        end = "2026-01-25T10:00:00Z",
                        eventId = recurring.id,
                        instanceId = "weekly-past",
                    ),
                    timedInstance(
                        title = "weekly",
                        start = "2026-02-01T09:00:00Z",
                        end = "2026-02-01T10:00:00Z",
                        eventId = recurring.id,
                        instanceId = "weekly-current",
                    ),
                ),
                coverage = TimeRange(
                    Instant.parse("2026-01-01T00:00:00Z"),
                    Instant.parse("2026-03-01T00:00:00Z"),
                ),
            ),
        )

        repository.clearCalendarHistory(
            CalendarHistoryBoundary(
                instant = Instant.parse("2026-02-01T00:00:00Z"),
                localDate = LocalDate.parse("2026-02-01"),
            ),
        )

        assertNull(database.eventDao().findInstance("weekly-past"))
        assertNotNull(database.eventDao().findInstance("weekly-current"))
        assertNotNull(database.eventDao().findEvent(recurring.id))
        assertEquals(
            Instant.parse("2026-02-01T00:00:00Z"),
            repository.getSyncState("work").coverage.single().start,
        )
    }

    @Test
    fun domainModelsRejectInvalidRangesAndMixedAllDayStorage() {
        // Catches accepting empty ranges or instances with both date and instant representations.
        assertThrows(IllegalArgumentException::class.java) {
            TimeRange(dayRange.start, dayRange.start)
        }
        assertThrows(IllegalArgumentException::class.java) {
            EventInstance(
                instanceId = "bad",
                eventId = "bad-event",
                calendarId = "work",
                title = "bad",
                calendarColorArgb = 0,
                isAllDay = true,
                startInstant = dayRange.start,
                endInstant = dayRange.endExclusive,
                startDate = LocalDate.parse("2026-01-10"),
                endDateExclusive = LocalDate.parse("2026-01-11"),
                location = null,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            event("blank-resource").copy(remoteResourceHref = "   ")
        }
    }

    @Test
    fun widgetConfigurationRejectsUnsupportedRangeDays() {
        // Catches persisting a widget range outside the product-supported presets.
        assertEquals(7, WidgetConfigEntity(appWidgetId = 1).rangeDays)
        listOf(1, 3, 7, 30).forEach { rangeDays ->
            assertEquals(rangeDays, WidgetConfigEntity(1, rangeDays).rangeDays)
        }
        assertThrows(IllegalArgumentException::class.java) {
            WidgetConfigEntity(appWidgetId = 1, rangeDays = 14)
        }
    }

    @Test
    fun clearingCalendarCachePreservesWidgetCalendarSelections() = runTest {
        // Catches a cache reset cascading through calendar IDs and turning configured widgets into empty selections.
        repository.replaceCalendarSnapshot(calendarSnapshotWithMixedEvents())
        val preferences = RoomWidgetPreferences(database)
        preferences.save(WidgetConfig(appWidgetId = 42, calendarIds = setOf("work"), range = WidgetRange.THIRTY_DAYS))

        repository.clearCalendarCache()

        assertEquals(
            WidgetConfig(appWidgetId = 42, calendarIds = setOf("work"), range = WidgetRange.THIRTY_DAYS, displayOrdinal = 1),
            preferences.configFor(42),
        )
    }

    private fun calendarSnapshotWithMixedEvents(): CalendarSnapshot {
        val events = listOf(event("all-day", allDay = true), event("09:00"), event("14:00"))
        return snapshot(
            events,
            listOf(
                allDayInstance("all-day", LocalDate.parse("2026-01-10"), LocalDate.parse("2026-01-11")),
                timedInstance("09:00", "2026-01-10T09:00:00Z", "2026-01-10T10:00:00Z"),
                timedInstance("14:00", "2026-01-10T14:00:00Z", "2026-01-10T15:00:00Z"),
            ),
        )
    }

    private fun snapshot(
        events: List<CalendarEvent>,
        instances: List<EventInstance>,
        coverage: TimeRange = dayRange,
        syncToken: String? = "sync-token",
        calendar: CalendarSummary = workCalendar,
    ) = CalendarSnapshot(calendar, events, instances, syncToken, coverage)

    private fun event(
        title: String,
        allDay: Boolean = false,
        calendarId: String = "work",
        uid: String = "$title-uid",
        recurrenceId: String? = null,
        id: String = "$title-event",
    ) = CalendarEvent(
        id = id,
        calendarId = calendarId,
        uid = uid,
        recurrenceId = recurrenceId,
        title = title,
        location = if (title == "09:00") "Room 9" else null,
        notes = "notes-$title",
        originalTimeZoneId = if (allDay) null else "UTC",
        isAllDay = allDay,
        recurrenceRule = null,
        status = EventStatus.CONFIRMED,
    )

    private fun timedInstance(
        title: String,
        start: String,
        end: String,
        calendarId: String = "work",
        eventId: String = "$title-event",
        instanceId: String = "$title-instance",
    ) = EventInstance(
        instanceId = instanceId,
        eventId = eventId,
        calendarId = calendarId,
        title = title,
        calendarColorArgb = workCalendar.colorArgb,
        isAllDay = false,
        startInstant = Instant.parse(start),
        endInstant = Instant.parse(end),
        startDate = null,
        endDateExclusive = null,
        location = if (title == "09:00") "Room 9" else null,
    )

    private fun allDayInstance(
        title: String,
        start: LocalDate,
        end: LocalDate,
        calendarId: String = "work",
        eventId: String = "$title-event",
        instanceId: String = "$title-instance",
    ) = EventInstance(
        instanceId = instanceId,
        eventId = eventId,
        calendarId = calendarId,
        title = title,
        calendarColorArgb = workCalendar.colorArgb,
        isAllDay = true,
        startInstant = null,
        endInstant = null,
        startDate = start,
        endDateExclusive = end,
        location = null,
    )

    private fun Throwable.hasCauseNamed(simpleName: String): Boolean =
        generateSequence(this) { it.cause }.any { it::class.java.simpleName == simpleName }

    private companion object {
        val workCalendar = CalendarSummary("work", "Work", 0xFF3366CC.toInt(), true)
        val personalCalendar = CalendarSummary("personal", "Personal", 0xFFAA3366.toInt(), true)
    }
}

package com.brightnook.nookcalendar.data

import com.brightnook.nookcalendar.auth.Credentials
import com.brightnook.nookcalendar.auth.CredentialsStore
import com.brightnook.nookcalendar.caldav.CalDavFailure
import com.brightnook.nookcalendar.caldav.CalDavGateway
import com.brightnook.nookcalendar.caldav.Principal
import com.brightnook.nookcalendar.caldav.RemoteCalendar
import com.brightnook.nookcalendar.caldav.RemoteDelta
import com.brightnook.nookcalendar.caldav.RemoteResource
import com.brightnook.nookcalendar.caldav.RemoteSnapshot
import com.brightnook.nookcalendar.caldav.stableCalendarId
import com.brightnook.nookcalendar.domain.CalendarRepository
import com.brightnook.nookcalendar.domain.CalendarResource
import com.brightnook.nookcalendar.domain.CalendarSummary
import com.brightnook.nookcalendar.domain.EventDraft
import com.brightnook.nookcalendar.domain.EventEditingResult
import com.brightnook.nookcalendar.domain.EventWriteSource
import com.brightnook.nookcalendar.domain.TimeRange
import com.brightnook.nookcalendar.sync.SyncCoordinator
import com.brightnook.nookcalendar.sync.SyncRequest
import com.brightnook.nookcalendar.sync.SyncResult
import com.brightnook.nookcalendar.widget.WidgetRefreshNotifier
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultEventEditingRepositoryTest {
    private val credentials = Credentials("tester@example.com", "password".toCharArray())
    private val remoteCalendar = RemoteCalendar(
        id = "remote", href = "/calendars/test/work/", displayName = "Work",
        color = null, syncToken = null, sessionId = "session", isWritable = true,
    )
    private val localCalendar = CalendarSummary(
        id = stableCalendarId(remoteCalendar.href), name = "Work", colorArgb = 0, isVisible = true, isWritable = true,
    )
    private val draft = EventDraft(
        calendarId = localCalendar.id, title = "Meeting", isAllDay = false,
        startInstant = Instant.parse("2026-09-14T01:00:00Z"),
        endInstant = Instant.parse("2026-09-14T02:00:00Z"),
    )

    @Test
    fun createWritesRemoteBeforeRefreshAndRefreshesWidget() = runTest {
        val actions = mutableListOf<String>()
        val gateway = FakeGateway(remoteCalendar, actions)
        val widgets = FakeWidgets(actions)
        val subject = subject(gateway = gateway, sync = FakeSync(actions), widgets = widgets)

        val result = subject.create(draft)

        assertEquals(EventEditingResult.Success, result)
        assertEquals(listOf("create", "sync", "widgets"), actions)
        assertTrue(gateway.createdHref.endsWith(".ics"))
    }

    @Test
    fun updateConflictDoesNotRefreshLocalState() = runTest {
        val actions = mutableListOf<String>()
        val gateway = FakeGateway(remoteCalendar, actions).apply { updateFailure = CalDavFailure.Conflict() }
        val subject = subject(gateway = gateway, sync = FakeSync(actions), widgets = FakeWidgets(actions))

        val result = subject.update("instance", draft)

        assertEquals(EventEditingResult.Conflict, result)
        assertEquals(listOf("update"), actions)
    }

    @Test
    fun recurringEventIsRejectedBeforeNetworkCall() = runTest {
        val actions = mutableListOf<String>()
        val source = source().copy(isRecurring = true)
        val subject = subject(
            gateway = FakeGateway(remoteCalendar, actions), sync = FakeSync(actions),
            widgets = FakeWidgets(actions), source = source,
        )

        assertEquals(EventEditingResult.RecurringUnsupported, subject.delete("instance"))
        assertTrue(actions.isEmpty())
    }

    @Test
    fun remoteSuccessWithFailedRefreshIsReportedWithoutRetryingWrite() = runTest {
        val actions = mutableListOf<String>()
        val gateway = FakeGateway(remoteCalendar, actions)
        val sync = FakeSync(actions, SyncResult.RetryableFailure(com.brightnook.nookcalendar.sync.SyncFailureCategory.NETWORK))
        val subject = subject(gateway = gateway, sync = sync, widgets = FakeWidgets(actions))

        assertEquals(EventEditingResult.SavedRefreshPending, subject.delete("instance"))
        assertEquals(listOf("delete", "sync"), actions)
        assertFalse(actions.contains("widgets"))
    }

    private fun subject(
        gateway: FakeGateway,
        sync: SyncCoordinator,
        widgets: WidgetRefreshNotifier,
        source: EventWriteSource = source(),
    ) = DefaultEventEditingRepository(
        credentialsStore = FakeCredentialsStore(credentials), gateway = gateway,
        calendarRepository = FakeCalendarRepository(localCalendar, source), syncCoordinator = sync,
        widgetRefreshNotifier = widgets, now = { Instant.parse("2026-09-13T00:00:00Z") }, uid = { "uid-1" },
    )

    private fun source() = EventWriteSource(
        calendar = localCalendar, uid = "uid-existing",
        resource = CalendarResource(localCalendar.id, "/calendars/test/work/existing.ics", "\"etag\"", "raw", 0),
        isRecurring = false,
    )
}

private class FakeCredentialsStore(private val credentials: Credentials?) : CredentialsStore {
    override suspend fun save(credentials: Credentials) = Unit
    override suspend fun load(): Credentials? = credentials
    override suspend fun clear() = Unit
}

private class FakeGateway(private val calendar: RemoteCalendar, private val actions: MutableList<String>) : CalDavGateway {
    var updateFailure: Throwable? = null
    var createdHref = ""
    override suspend fun authenticate(credentials: Credentials) = Principal("/principal", "session")
    override suspend fun discoverCalendars(principal: Principal) = listOf(calendar)
    override suspend fun fetchRange(calendar: RemoteCalendar, range: TimeRange) = RemoteSnapshot(emptyList(), null)
    override suspend fun fetchDelta(calendar: RemoteCalendar, syncToken: String) = RemoteDelta(emptyList(), emptyList(), null)
    override suspend fun createResource(calendar: RemoteCalendar, href: String, calendarData: String): RemoteResource {
        actions += "create"; createdHref = href
        return RemoteResource(href, "\"new\"", calendarData)
    }
    override suspend fun updateResource(calendar: RemoteCalendar, resource: RemoteResource): RemoteResource {
        actions += "update"; updateFailure?.let { throw it }
        return resource.copy(etag = "\"new\"")
    }
    override suspend fun deleteResource(calendar: RemoteCalendar, href: String, etag: String) { actions += "delete" }
}

private class FakeSync(private val actions: MutableList<String>, private val result: SyncResult = SyncResult.Success()) : SyncCoordinator {
    override suspend fun sync(request: SyncRequest): SyncResult { actions += "sync"; return result }
}

private class FakeWidgets(private val actions: MutableList<String>) : WidgetRefreshNotifier {
    override fun refreshAll() { actions += "widgets" }
}

private class FakeCalendarRepository(
    private val calendar: CalendarSummary,
    private val source: EventWriteSource,
) : CalendarRepository {
    override fun observeCalendars(): Flow<List<CalendarSummary>> = flowOf(listOf(calendar))
    override fun observeAgenda(range: TimeRange, calendarIds: Set<String>) = flowOf(emptyList<com.brightnook.nookcalendar.domain.EventInstance>())
    override fun observeDay(day: java.time.LocalDate, zoneId: java.time.ZoneId) = flowOf(emptyList<com.brightnook.nookcalendar.domain.EventInstance>())
    override fun observeEvent(instanceId: String) = flowOf<com.brightnook.nookcalendar.domain.EventDetail?>(null)
    override suspend fun findEventWriteSource(instanceId: String) = source
    override suspend fun getSyncState(calendarId: String) = com.brightnook.nookcalendar.domain.CalendarSyncState()
    override suspend fun replaceCalendarSnapshot(snapshot: com.brightnook.nookcalendar.domain.CalendarSnapshot) = Unit
    override suspend fun applyCalendarDelta(delta: com.brightnook.nookcalendar.domain.CalendarDelta) = Unit
    override suspend fun setCalendarVisible(calendarId: String, visible: Boolean) = Unit
    override suspend fun recordCoverage(calendarId: String, range: TimeRange) = Unit
    override suspend fun clearCalendarHistory(before: com.brightnook.nookcalendar.domain.CalendarHistoryBoundary) = Unit
    override suspend fun clearCalendarCache() = Unit
}

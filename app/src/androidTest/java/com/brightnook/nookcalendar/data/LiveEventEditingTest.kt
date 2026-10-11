package com.brightnook.nookcalendar.data

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.brightnook.nookcalendar.CalendarApplication
import com.brightnook.nookcalendar.caldav.stableCalendarId
import com.brightnook.nookcalendar.caldav.ICloudCalDavGateway
import com.brightnook.nookcalendar.domain.EventDraft
import com.brightnook.nookcalendar.domain.EventEditingResult
import com.brightnook.nookcalendar.domain.EventReminder
import com.brightnook.nookcalendar.domain.TimeRange
import com.brightnook.nookcalendar.sync.SyncRequest
import com.brightnook.nookcalendar.sync.SyncResult
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in only: uses the signed-in TEST account and removes only its own unique event. */
@RunWith(AndroidJUnit4::class)
class LiveEventEditingTest {
    @Test fun refreshSignedInTestAccount() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveTestAccount") == "true")
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CalendarApplication
        assertNotNull("Sign into the test account first", app.container.credentialsStore.load())
        val result = app.container.syncCoordinator.sync(SyncRequest.Refresh)
        assertTrue("Refresh failed", result is SyncResult.Success || result is SyncResult.PartialSuccess)
    }

    @Test fun createUpdateDeleteRoundTrip() = runBlocking {
        assumeTrue("Explicit test-account authorization required",
            InstrumentationRegistry.getArguments().getString("liveTestAccount") == "true")
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CalendarApplication
        val container = app.container
        val credentials = container.credentialsStore.load()
        assertNotNull("Sign into the test account first", credentials)
        // Keep independent read-back authentication separate from app sync/write sessions.
        val gateway = ICloudCalDavGateway()
        val principal = gateway.authenticate(requireNotNull(credentials))
        val remote = gateway.discoverCalendars(principal).firstOrNull { it.isWritable }
        assertNotNull("No writable test calendar", remote)
        val calendar = requireNotNull(remote)
        val zone = ZoneId.systemDefault()
        val day = LocalDate.now(zone).plusDays(1)
        val range = TimeRange(day.atStartOfDay(zone).toInstant(), day.plusDays(1).atStartOfDay(zone).toInstant())
        // Recover only disposable events created by this test if an earlier run was interrupted.
        gateway.fetchRange(calendar, range).resources.filter {
            it.calendarData.contains("UID:nookcalendar-test-") &&
                it.calendarData.contains("SUMMARY:NookCalendar CRUD TEST ") &&
                it.calendarData.contains("DESCRIPTION:Disposable automated test event")
        }.forEach { gateway.deleteResource(calendar, it.href, requireNotNull(it.etag)) }
        val sync = container.syncCoordinator.sync(SyncRequest.Initial)
        assertTrue("Initial synchronization failed", sync is SyncResult.Success || sync is SyncResult.PartialSuccess)
        val uid = "nookcalendar-test-${UUID.randomUUID()}"
        val title = "NookCalendar CRUD TEST $uid"
        val start = day.atTime(14, 0).atZone(zone).toInstant()
        val repository = DefaultEventEditingRepository(
            container.credentialsStore, container.calDavGateway, container.calendarRepository,
            container.syncCoordinator, container.widgetUpdater, uid = { uid },
        )
        val draft = EventDraft(
            calendarId = stableCalendarId(calendar.href), title = title,
            location = "Temporary test location", notes = "Disposable automated test event",
            isAllDay = false, startInstant = start, endInstant = start.plusSeconds(3600),
            timeZoneId = zone.id,
            reminders = listOf(EventReminder.RelativeToStart(Duration.ofMinutes(-15))),
        )
        var writeAttempted = false
        try {
            writeAttempted = true
            assertEquals("Create result", EventEditingResult.Success, repository.create(draft))
            val event = withTimeout(30_000) {
                container.calendarRepository.observeDay(day, zone).first { events -> events.any { it.title == title } }
            }.single { it.title == title }
            val created = gateway.fetchRange(calendar, range).resources.single { it.calendarData.contains("UID:$uid\r\n") }
            assertTrue("Cloud create title missing", created.calendarData.replace("\r\n ", "").contains("SUMMARY:$title"))
            assertNotNull("Cloud ETag missing", created.etag)
            val edited = draft.copy(title = "$title edited", location = "Edited test location",
                endInstant = start.plusSeconds(7200),
                reminders = draft.reminders + EventReminder.RelativeToStart(Duration.ofMinutes(-30)))
            assertEquals("Update result", EventEditingResult.Success, repository.update(event.instanceId, edited))
            val updated = gateway.fetchRange(calendar, range).resources.single { it.calendarData.contains("UID:$uid\r\n") }
            assertTrue("Cloud updated title missing", updated.calendarData.replace("\r\n ", "").contains("SUMMARY:${edited.title}"))
            val local = container.calendarRepository.observeEvent(event.instanceId).first()
            assertNotNull("Updated local event missing", local)
            assertEquals(edited.title, local?.title)
            assertEquals(edited.location, local?.location)
            assertEquals(edited.endInstant, local?.endInstant)
            assertEquals(2, local?.reminders?.size)
            assertEquals("Delete result", EventEditingResult.Success, repository.delete(event.instanceId))
            assertFalse("Test event still in cloud", gateway.fetchRange(calendar, range).resources.any {
                it.calendarData.contains("UID:$uid\r\n")
            })
            assertNull("Test event still local", container.calendarRepository.observeEvent(event.instanceId).first())
        } finally {
            // Even a failed refresh must not leave the disposable cloud event behind.
            if (writeAttempted) {
                val leftovers = gateway.fetchRange(calendar, range).resources.filter {
                    it.calendarData.contains("UID:$uid\r\n")
                }
                leftovers.forEach { resource ->
                    gateway.deleteResource(calendar, resource.href, requireNotNull(resource.etag))
                }
                if (leftovers.isNotEmpty()) container.syncCoordinator.sync(SyncRequest.Refresh)
            }
        }
    }
}

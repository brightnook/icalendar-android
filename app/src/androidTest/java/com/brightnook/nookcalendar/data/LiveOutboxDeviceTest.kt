package com.brightnook.nookcalendar.data

import android.content.Context
import android.net.ConnectivityManager
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.brightnook.nookcalendar.CalendarApplication
import com.brightnook.nookcalendar.caldav.ICloudCalDavGateway
import com.brightnook.nookcalendar.caldav.Ical4jIcsParser
import com.brightnook.nookcalendar.caldav.stableCalendarId
import com.brightnook.nookcalendar.domain.*
import com.brightnook.nookcalendar.sync.SyncRequest
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay
import androidx.work.WorkManager
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit opt-in. Only its own UUID-marked disposable event may be changed/deleted. */
@RunWith(AndroidJUnit4::class)
class LiveOutboxDeviceTest {
    private fun app(): CalendarApplication {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveTestAccount") == "true")
        return InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CalendarApplication
    }
    private fun journal(app: Context) = File(app.filesDir, "disposable-live-outbox-test.json")
    private val notes = "Disposable live outbox device verification; safe to remove after test"

    @Test fun queueOneDisposableEventWhileOffline() = runBlocking {
        val app = app()
        assertNull("Disconnect emulator radios before this phase", app.getSystemService(ConnectivityManager::class.java).activeNetwork)
        val credentials = app.container.credentialsStore.load()
        assertNotNull("Sign into the TEST account first", credentials)
        credentials?.appSpecificPassword?.fill('\u0000')
        assertFalse("Previous disposable test needs reconciliation; refusing to replace its journal", journal(app).exists())
        val calendar = app.container.calendarRepository.observeCalendars().first().firstOrNull { it.isWritable && it.isVisible }
        assertNotNull("First sync the test account and enable a writable calendar", calendar)
        val zone = ZoneId.systemDefault()
        val day = LocalDate.now(zone).plusDays(1)
        val start = day.atTime(13, 0).atZone(zone).toInstant()
        val marker = UUID.randomUUID().toString()
        val title = "NookCalendar OUTBOX TEST $marker"
        val draft = EventDraft(calendar!!.id, title, notes = notes, isAllDay = false,
            startInstant = start, endInstant = start.plusSeconds(3600), timeZoneId = zone.id)
        val started = SystemClock.elapsedRealtime()
        assertEquals(EventEditingResult.Success, app.container.eventEditingRepository.create(draft))
        val elapsed = SystemClock.elapsedRealtime() - started
        assertTrue("Local-only save took ${elapsed}ms", elapsed < 5000)
        val item = withTimeout(15000) { app.container.calendarRepository.observeDay(day, zone).first { rows -> rows.any { it.title == title } } }
            .single { it.title == title }
        assertEquals(PendingEventSyncState.PENDING, item.pendingSyncState)
        assertTrue(item.instanceId.startsWith("local:"))
        journal(app).writeText(JSONObject().apply {
            put("id", item.instanceId); put("uid", item.instanceId.removePrefix("local:"))
            put("title", title); put("day", day.toString()); put("calendarId", calendar.id)
            put("saveMillis", elapsed)
        }.toString())
        // Count/timing only; never print account identifiers or real event contents.
        println("LOCAL_SAVE_MILLIS=$elapsed")
    }

    @Test fun localEventStillExistsAfterProcessRestartOffline() = runBlocking {
        val app = app()
        assertNull(app.getSystemService(ConnectivityManager::class.java).activeNetwork)
        val state = JSONObject(journal(app).readText())
        val detail = app.container.calendarRepository.observeEvent(state.getString("id")).first()
        assertNotNull("Local draft disappeared after process restart", detail)
        assertEquals(state.getString("title"), detail!!.title)
        assertEquals(notes, detail.notes)
        assertEquals(PendingEventSyncState.PENDING, detail.pendingSyncState)
    }

    @Test fun manualSyncWhileOfflineKeepsDraftWithFailureState() = runBlocking {
        val app = app()
        assertNull(app.getSystemService(ConnectivityManager::class.java).activeNetwork)
        val state = JSONObject(journal(app).readText())
        withTimeout(90000) { app.container.syncCoordinator.syncManually(SyncRequest.Refresh) }
        val detail = requireNotNull(app.container.calendarRepository.observeEvent(state.getString("id")).first())
        assertEquals(state.getString("title"), detail.title)
        assertEquals(PendingEventSyncState.FAILED, detail.pendingSyncState)
        println("LIVE_OFFLINE_FAILURE_STATE_VERIFIED")
    }

    @Test fun reconnectDoesNotAutomaticallyRetryFailedDraft() = runBlocking {
        val app = app()
        val state = JSONObject(journal(app).readText())
        withTimeout(180000) {
            // Application startup schedules normal background work; wait until it finishes.
            while (WorkManager.getInstance(app).getWorkInfosForUniqueWork("event-outbox").get()
                    .any { !it.state.isFinished }) delay(250)
        }
        val detail = requireNotNull(app.container.calendarRepository.observeEvent(state.getString("id")).first())
        assertEquals(PendingEventSyncState.FAILED, detail.pendingSyncState)
        println("LIVE_RECONNECT_WAITED_FOR_MANUAL_RETRY_VERIFIED")
    }

    @Test fun submitEditMoveAndDeleteOnlyTheDisposableEvent() = runBlocking {
        val app = app()
        val state = JSONObject(journal(app).readText())
        val uid = state.getString("uid")
        val title = state.getString("title")
        val day = LocalDate.parse(state.getString("day"))
        val zone = ZoneId.systemDefault()
        val credentials = requireNotNull(app.container.credentialsStore.load())
        val verifier = ICloudCalDavGateway()
        val principal = try { verifier.authenticate(credentials) } finally { credentials.appSpecificPassword.fill('\u0000') }
        val calendars = verifier.discoverCalendars(principal).filter { it.isWritable }
        val source = calendars.single { stableCalendarId(it.href) == state.getString("calendarId") }
        val href = source.href + "$uid.ics"
        suspend fun syncAndFind(expectedTitle: String): EventDetail = withTimeout(240000) {
            app.container.syncCoordinator.syncManually(SyncRequest.Refresh)
            val items = app.container.calendarRepository.observeDay(day, zone).first()
            val event = items.single { it.title == expectedTitle }
            assertNull("Submission not reconciled: ${event.pendingSyncState}", event.pendingSyncState)
            requireNotNull(app.container.calendarRepository.observeEvent(event.instanceId).first())
        }
        fun requireOwned(data: String) {
            val range = TimeRange(day.atStartOfDay(zone).toInstant(), day.plusDays(1).atStartOfDay(zone).toInstant(), zone)
            val event = Ical4jIcsParser().parse(data, range, zone).events.single()
            // Parse ICS escaping/folding before comparison; the marker includes an escaped semicolon.
            assertEquals("Refusing non-test UID", uid, event.uid)
            assertTrue("Refusing non-test title", event.title.startsWith(title))
            assertEquals("Refusing non-test notes", notes, event.notes)
        }
        var detail = syncAndFind(title)
        requireOwned(requireNotNull(verifier.getResource(source, href)).calendarData)
        println("LIVE_QUEUED_CREATE_VERIFIED")
        val edited = EventDraft(detail.calendarId, "$title edited", location = "Disposable edited test location",
            notes = notes, isAllDay = false, startInstant = detail.startInstant,
            endInstant = detail.endInstant!!.plusSeconds(1800), timeZoneId = zone.id)
        assertEquals(EventEditingResult.Success, app.container.eventEditingRepository.update(detail.instanceId, edited))
        // Observe the automatic path before issuing any manual synchronization.
        val autoConfirmed = withTimeoutOrNull(90000) {
            app.container.calendarRepository.observeDay(day, zone).first { items ->
                items.any { it.title == edited.title && it.pendingSyncState == null }
            }
        } != null
        detail = syncAndFind(edited.title)
        val editedCloud = requireNotNull(verifier.getResource(source, href))
        requireOwned(editedCloud.calendarData)
        assertTrue(editedCloud.calendarData.contains("Disposable edited test location"))
        println("LIVE_QUEUED_EDIT_VERIFIED")

        val target = calendars.firstOrNull { it.href != source.href }
        if (target != null) {
            assertEquals(EventEditingResult.Success,
                app.container.eventEditingRepository.update(detail.instanceId, edited.copy(calendarId = stableCalendarId(target.href))))
            // Chosen target may be hidden; enable ONLY its local visibility for this test, restoring afterward.
            val prior = app.container.calendarRepository.observeCalendars().first().single { it.id == stableCalendarId(target.href) }
            try {
                if (!prior.isVisible) app.container.calendarRepository.setCalendarVisible(prior.id, true)
                detail = syncAndFind(edited.title)
                assertEquals(stableCalendarId(target.href), detail.calendarId)
                assertNull("Source still exists after MOVE", verifier.getResource(source, href))
                requireOwned(requireNotNull(verifier.getResource(target, target.href + "$uid.ics")).calendarData)
                println("LIVE_QUEUED_MOVE_VERIFIED")
            } finally {
                if (!prior.isVisible) app.container.calendarRepository.setCalendarVisible(prior.id, false)
            }
        } else println("LIVE_MOVE_NOT_TESTED_ONLY_ONE_WRITABLE_CALENDAR")
        // Ownership was independently verified above, and the queued overlay must be gone before delete.
        assertNull(detail.pendingSyncState)
        assertEquals(EventEditingResult.Success, app.container.eventEditingRepository.delete(detail.instanceId))
        // Delete is queued locally; explicitly drain it before checking the server.
        withTimeout(240000) { app.container.syncCoordinator.syncManually(SyncRequest.Refresh) }
        assertNull(verifier.getResource(source, href))
        if (target != null) assertNull(verifier.getResource(target, target.href + "$uid.ics"))
        journal(app).delete()
        println("LIVE_DISPOSABLE_EVENT_DELETE_VERIFIED")
        assertTrue("Automatic edit upload was not reconciled within 90 seconds", autoConfirmed)
    }
}

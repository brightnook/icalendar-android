package com.brightnook.nookcalendar.sync

import com.brightnook.nookcalendar.caldav.NetworkFailureDetail
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.brightnook.nookcalendar.sync.SyncFailureCategory.NETWORK

class SyncDiagnosticsTest {
    @Test
    fun eventForChangedCalendarContainsCountsButNeverCalendarOrEventContent() {
        val event = SyncDiagnosticsEvent.deltaReceived(
            attemptId = "test",
            calendarId = "/calendars/owner/private-calendar/",
            upserts = 2,
            tombstones = 1,
        )

        val rendered = event.toLogLine()

        assertTrue(rendered.contains("upserts=2"))
        assertFalse(rendered.contains("private-calendar"))
    }

    @Test
    fun networkFailureEventIdentifiesTheNetworkDetailWithoutSensitiveText() {
        val rendered = SyncDiagnosticsEvent.failed(
            attemptId = "test",
            calendarId = "/calendars/owner/private-calendar/",
            category = NETWORK,
        ).toLogLine()

        assertTrue(rendered.contains("failure=NETWORK"))
        assertTrue(rendered.contains("network=UNKNOWN"))
        assertFalse(rendered.contains("private-calendar"))
    }

    @Test
    fun networkFailureEventIncludesSafeTimeoutDetail() {
        val rendered = SyncDiagnosticsEvent.failed(
            attemptId = "test",
            calendarId = "/calendars/owner/private-calendar/",
            category = NETWORK,
            networkDetail = NetworkFailureDetail.TIMEOUT,
        ).toLogLine()

        assertTrue(rendered.contains("network=TIMEOUT"))
    }
}

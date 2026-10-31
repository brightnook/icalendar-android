package com.brightnook.nookcalendar.ui.main

import com.brightnook.nookcalendar.R
import com.brightnook.nookcalendar.domain.PendingEventSyncState
import org.junit.Assert.assertEquals
import org.junit.Test

class PendingEventStatusTest {
    @Test fun eachQueueStateHasItsOwnUserVisibleStatus() {
        assertEquals(R.string.event_sync_pending, PendingEventSyncState.PENDING.statusResource())
        assertEquals(R.string.event_sync_sending, PendingEventSyncState.SYNCING.statusResource())
        assertEquals(R.string.event_sync_failed, PendingEventSyncState.FAILED.statusResource())
        assertEquals(R.string.event_sync_conflict, PendingEventSyncState.CONFLICT.statusResource())
    }
}

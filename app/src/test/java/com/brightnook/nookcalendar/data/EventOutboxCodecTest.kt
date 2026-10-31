package com.brightnook.nookcalendar.data

import com.brightnook.nookcalendar.domain.EventDraft
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EventOutboxCodecTest {
    @Test fun draftRoundTripPreservesOfflineEdits() {
        val draft = EventDraft("calendar", "離線 🗓", notes = "line\nnext", isAllDay = false,
            startInstant = Instant.parse("2026-10-31T10:00:00Z"), endInstant = Instant.parse("2026-10-31T11:00:00Z"))
        assertEquals(draft, decodeDraft(encodeDraft(draft)))
    }
}

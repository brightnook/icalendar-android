package com.brightnook.nookcalendar.ui.settings

import java.time.Instant
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class AutomaticSyncTimestampFormatterTest {
    @Test
    fun usesTheActiveLocaleDateAndTimeConventions() {
        // Catches displaying a fixed Chinese/English timestamp after a user changes the app language.
        val instant = Instant.parse("2026-09-10T08:00:00Z")

        assertEquals("Sep 10, 2026, 5:00\u202fPM", formatAutomaticSyncTimestamp(instant, Locale.US))
        assertEquals("2026年9月10日 17:00", formatAutomaticSyncTimestamp(instant, Locale.SIMPLIFIED_CHINESE))
        assertEquals("2026/09/10 17:00", formatAutomaticSyncTimestamp(instant, Locale.JAPAN))
    }
}

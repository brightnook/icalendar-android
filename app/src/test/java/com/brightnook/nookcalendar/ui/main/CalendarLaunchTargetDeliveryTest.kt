package com.brightnook.nookcalendar.ui.main

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarLaunchTargetDeliveryTest {
    @Test
    fun oversizedInstanceIdsNeverEnterOrRestoreSavedHistory() {
        // Catches an external intent or pending target placing an unbounded value in the activity Bundle.
        val date = LocalDate.of(2026, 8, 15)
        val delivery = CalendarLaunchTargetDelivery()

        assertNull(delivery.parseAndAccept("2026-08-15", "x".repeat(129)))
        assertNull(delivery.parseAndAccept("2026-08-15", "界".repeat(86)))
        assertTrue(delivery.savedState().isEmpty())
        assertTrue(
            CalendarLaunchTargetDelivery(setOf(CalendarLaunchTarget(date, "x".repeat(129))))
                .savedState()
                .isEmpty(),
        )
    }

    @Test
    fun acceptedHistoryUsesBoundedLeastRecentlyUsedEvictionAndStableSerialization() {
        // Catches unbounded history while preserving duplicate suppression for recently delivered targets.
        val delivery = CalendarLaunchTargetDelivery()
        repeat(32) { index ->
            assertEquals(
                CalendarLaunchTarget(LocalDate.of(2026, 8, 15), "id-%02d".format(index)),
                delivery.parseAndAccept("2026-08-15", "id-%02d".format(index)),
            )
        }
        assertNull(delivery.parseAndAccept("2026-08-15", "id-00"))
        assertEquals(
            CalendarLaunchTarget(LocalDate.of(2026, 8, 15), "id-32"),
            delivery.parseAndAccept("2026-08-15", "id-32"),
        )

        val saved = delivery.savedState()
        assertEquals(32, saved.size)
        assertEquals(saved, CalendarLaunchTargetDelivery.fromSavedState(saved).savedState())
        assertNull(
            CalendarLaunchTargetDelivery.fromSavedState(saved)
                .parseAndAccept("2026-08-15", "id-00"),
        )
        assertEquals(
            CalendarLaunchTarget(LocalDate.of(2026, 8, 15), "id-01"),
            CalendarLaunchTargetDelivery.fromSavedState(saved)
                .parseAndAccept("2026-08-15", "id-01"),
        )
        assertNull(
            CalendarLaunchTargetDelivery.fromSavedState(saved)
                .parseAndAccept("2026-08-15", "id-32"),
        )
    }

    @Test
    fun malformedBase64AndOversizedRestoredIdsAreIgnored() {
        // Catches malformed UTF-8 or encoded payloads being normalized into attacker-controlled history.
        val restored = CalendarLaunchTargetDelivery.fromSavedState(
            listOf(
                "2026-08-15|1|wA",
                "2026-08-16|1|${"A".repeat(400)}",
                "2026-08-17|1|%%%not-base64%%%",
            ),
        )

        assertTrue(restored.savedState().isEmpty())
        assertEquals(
            CalendarLaunchTarget(LocalDate.of(2026, 8, 15), "safe-id"),
            restored.parseAndAccept("2026-08-15", "safe-id"),
        )
    }
}

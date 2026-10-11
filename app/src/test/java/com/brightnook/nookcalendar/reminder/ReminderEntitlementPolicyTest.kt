package com.brightnook.nookcalendar.reminder

import com.brightnook.nookcalendar.domain.ScheduledReminder
import java.time.Instant
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ReminderEntitlementPolicyTest {
    private val earlyA = reminder("early-a", "instance-a", "2026-09-02T08:45:00Z")
    private val lateA = reminder("late-a", "instance-a", "2026-09-02T08:55:00Z")
    private val earlyB = reminder("early-b", "instance-b", "2026-09-02T08:50:00Z")

    @Test
    fun freeTierKeepsOnlyEarliestTriggerForEachInstance() {
        assertEquals(
            listOf(earlyA, earlyB),
            remindersForEntitlement(listOf(lateA, earlyA, earlyB), premium = false),
        )
    }

    @Test
    fun premiumTierKeepsEveryReminderInTriggerOrder() {
        assertEquals(
            listOf(earlyA, earlyB, lateA),
            remindersForEntitlement(listOf(lateA, earlyA, earlyB), premium = true),
        )
    }

    private fun reminder(id: String, instanceId: String, at: String) = ScheduledReminder(
        reminderId = id,
        instanceId = instanceId,
        date = LocalDate.parse("2026-09-02"),
        triggerAt = Instant.parse(at),
        title = id,
        location = null,
    )
}

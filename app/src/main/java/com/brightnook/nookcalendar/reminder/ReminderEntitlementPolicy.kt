package com.brightnook.nookcalendar.reminder

import com.brightnook.nookcalendar.domain.ScheduledReminder

fun remindersForEntitlement(
    reminders: List<ScheduledReminder>,
    premium: Boolean,
): List<ScheduledReminder> = if (premium) {
    reminders.sortedBy(ScheduledReminder::triggerAt)
} else {
    reminders.groupBy(ScheduledReminder::instanceId)
        .values
        .mapNotNull { instanceReminders -> instanceReminders.minByOrNull(ScheduledReminder::triggerAt) }
        .sortedBy(ScheduledReminder::triggerAt)
}

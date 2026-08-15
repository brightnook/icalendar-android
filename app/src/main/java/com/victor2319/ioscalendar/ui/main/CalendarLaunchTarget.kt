package com.victor2319.ioscalendar.ui.main

import java.time.LocalDate
import java.time.format.DateTimeParseException

data class CalendarLaunchTarget(
    val date: LocalDate,
    val instanceId: String? = null,
)

class CalendarLaunchTargetDelivery {
    private var lastAccepted: CalendarLaunchTarget? = null

    fun parseAndAccept(date: String?, instanceId: String?): CalendarLaunchTarget? {
        val parsedDate = try {
            date?.takeIf(String::isNotBlank)?.let(LocalDate::parse)
        } catch (_: DateTimeParseException) {
            null
        } ?: return null
        if (instanceId != null && instanceId.isBlank()) return null
        val target = CalendarLaunchTarget(parsedDate, instanceId)
        if (target == lastAccepted) return null
        lastAccepted = target
        return target
    }

    fun markConsumed(target: CalendarLaunchTarget) {
        if (lastAccepted == target) lastAccepted = null
    }

    companion object {
        const val EXTRA_DATE = "com.victor2319.ioscalendar.extra.DATE"
        const val EXTRA_INSTANCE_ID = "com.victor2319.ioscalendar.extra.INSTANCE_ID"
    }
}

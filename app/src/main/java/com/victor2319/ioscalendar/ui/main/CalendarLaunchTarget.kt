package com.victor2319.ioscalendar.ui.main

import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.Base64

data class CalendarLaunchTarget(
    val date: LocalDate,
    val instanceId: String? = null,
)

class CalendarLaunchTargetDelivery(
    previouslyAccepted: Set<CalendarLaunchTarget> = emptySet(),
) {
    private val acceptedTargets = previouslyAccepted.toMutableSet()

    fun parseAndAccept(date: String?, instanceId: String?): CalendarLaunchTarget? {
        val parsedDate = try {
            date?.takeIf(String::isNotBlank)?.let(LocalDate::parse)
        } catch (_: DateTimeParseException) {
            null
        } ?: return null
        if (instanceId != null && instanceId.isBlank()) return null
        val target = CalendarLaunchTarget(parsedDate, instanceId)
        if (!acceptedTargets.add(target)) return null
        return target
    }

    fun markConsumed(@Suppress("UNUSED_PARAMETER") target: CalendarLaunchTarget) = Unit

    fun acceptedTargets(): Set<CalendarLaunchTarget> = acceptedTargets.toSet()

    fun savedState(): List<String> = acceptedTargets
        .sortedWith(compareBy<CalendarLaunchTarget> { it.date }.thenBy { it.instanceId.orEmpty() })
        .map { target ->
            if (target.instanceId == null) {
                "${target.date}|0"
            } else {
                val encodedId = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(target.instanceId.toByteArray(Charsets.UTF_8))
                "${target.date}|1|$encodedId"
            }
        }

    companion object {
        const val EXTRA_DATE = "com.victor2319.ioscalendar.extra.DATE"
        const val EXTRA_INSTANCE_ID = "com.victor2319.ioscalendar.extra.INSTANCE_ID"

        fun fromSavedState(state: List<String>): CalendarLaunchTargetDelivery {
            val restored = state.mapNotNull(::decodeTarget).toSet()
            return CalendarLaunchTargetDelivery(restored)
        }

        private fun decodeTarget(value: String): CalendarLaunchTarget? = try {
            val parts = value.split('|', limit = 3)
            val date = LocalDate.parse(parts.getOrNull(0) ?: return null)
            when (parts.getOrNull(1)) {
                "0" -> CalendarLaunchTarget(date)
                "1" -> {
                    val encoded = parts.getOrNull(2) ?: return null
                    val instanceId = Base64.getUrlDecoder().decode(encoded).toString(Charsets.UTF_8)
                    instanceId.takeIf(String::isNotBlank)?.let { CalendarLaunchTarget(date, it) }
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }
}

package com.brightnook.nookcalendar.ui.main

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.Base64
import java.util.LinkedHashMap

data class CalendarLaunchTarget(
    val date: LocalDate,
    val instanceId: String? = null,
)

/**
 * Suppresses duplicate launch targets within the 32 most recently delivered targets.
 * Duplicate delivery refreshes recency; a target may be delivered again after LRU eviction.
 */
class CalendarLaunchTargetDelivery(
    previouslyAccepted: Iterable<CalendarLaunchTarget> = emptyList(),
) {
    private val acceptedTargets = LinkedHashMap<CalendarLaunchTarget, Unit>(
        ACCEPTED_HISTORY_CAPACITY + 1,
        0.75f,
        true,
    )

    init {
        previouslyAccepted.forEach { target ->
            if (isValidInstanceId(target.instanceId)) remember(target)
        }
    }

    fun parseAndAccept(date: String?, instanceId: String?): CalendarLaunchTarget? {
        val parsedDate = try {
            date?.takeIf(String::isNotBlank)?.let(LocalDate::parse)
        } catch (_: DateTimeParseException) {
            null
        } ?: return null
        if (!isValidInstanceId(instanceId)) return null
        val target = CalendarLaunchTarget(parsedDate, instanceId)
        if (acceptedTargets[target] != null) return null
        remember(target)
        return target
    }

    fun markConsumed(@Suppress("UNUSED_PARAMETER") target: CalendarLaunchTarget) = Unit

    fun acceptedTargets(): Set<CalendarLaunchTarget> = acceptedTargets.keys.toSet()

    fun savedState(): List<String> = acceptedTargets.keys
        .map { target ->
            if (target.instanceId == null) {
                "${target.date}|0"
            } else {
                val encodedId = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(target.instanceId.toByteArray(Charsets.UTF_8))
                "${target.date}|1|$encodedId"
            }
        }

    private fun remember(target: CalendarLaunchTarget) {
        acceptedTargets[target] = Unit
        if (acceptedTargets.size > ACCEPTED_HISTORY_CAPACITY) {
            acceptedTargets.remove(acceptedTargets.entries.iterator().next().key)
        }
    }

    companion object {
        const val EXTRA_DATE = "com.brightnook.nookcalendar.extra.DATE"
        const val EXTRA_INSTANCE_ID = "com.brightnook.nookcalendar.extra.INSTANCE_ID"
        private const val MAX_INSTANCE_ID_CHARS = 128
        private const val MAX_INSTANCE_ID_BYTES = 256
        private const val MAX_ENCODED_INSTANCE_ID_CHARS = 342
        private const val MAX_SAVED_ENTRY_CHARS = 361
        private const val ACCEPTED_HISTORY_CAPACITY = 32

        fun fromSavedState(state: List<String>): CalendarLaunchTargetDelivery {
            val restored = state.takeLast(ACCEPTED_HISTORY_CAPACITY).mapNotNull(::decodeTarget)
            return CalendarLaunchTargetDelivery(restored)
        }

        private fun decodeTarget(value: String): CalendarLaunchTarget? = try {
            if (value.length > MAX_SAVED_ENTRY_CHARS) return null
            val parts = value.split('|', limit = 3)
            val date = LocalDate.parse(parts.getOrNull(0) ?: return null)
            when (parts.getOrNull(1)) {
                "0" -> if (parts.size == 2) CalendarLaunchTarget(date) else null
                "1" -> {
                    if (parts.size != 3) return null
                    val encoded = parts[2].takeIf {
                        it.isNotEmpty() && it.length <= MAX_ENCODED_INSTANCE_ID_CHARS
                    } ?: return null
                    val bytes = Base64.getUrlDecoder().decode(encoded)
                    if (bytes.size > MAX_INSTANCE_ID_BYTES) return null
                    val instanceId = Charsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes))
                        .toString()
                    val canonical = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
                    if (canonical != encoded || !isValidInstanceId(instanceId)) return null
                    CalendarLaunchTarget(date, instanceId)
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }

        private fun isValidInstanceId(instanceId: String?): Boolean {
            if (instanceId == null) return true
            if (instanceId.isBlank() || instanceId.length > MAX_INSTANCE_ID_CHARS) return false
            return instanceId.toByteArray(Charsets.UTF_8).size <= MAX_INSTANCE_ID_BYTES
        }
    }
}

package com.brightnook.nookcalendar.ui.main

import com.brightnook.nookcalendar.domain.EventInstance
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

internal fun monthDayMarkerColors(
    events: List<EventInstance>,
    visibleCalendarIds: Set<String>,
    month: YearMonth,
    zoneId: ZoneId,
): Map<LocalDate, List<Int>> {
    val firstDay = month.atDay(1)
    val lastDay = month.atEndOfMonth()
    val colorsByDay = linkedMapOf<LocalDate, MutableList<Int>>()
    events.asSequence()
        .filter { it.calendarId in visibleCalendarIds }
        .sortedBy { it.calendarId }
        .forEach { event ->
            val (start, endInclusive) = event.localDateSpan(zoneId)
            var day = maxOf(start, firstDay)
            val finalDay = minOf(endInclusive, lastDay)
            while (!day.isAfter(finalDay)) {
                val colors = colorsByDay.getOrPut(day) { mutableListOf() }
                if (event.calendarColorArgb !in colors && colors.size < MAX_MARKER_COLORS) {
                    colors += event.calendarColorArgb
                }
                day = day.plusDays(1)
            }
        }
    return colorsByDay
}

private fun EventInstance.localDateSpan(zoneId: ZoneId): Pair<LocalDate, LocalDate> =
    if (isAllDay) {
        requireNotNull(startDate) to requireNotNull(endDateExclusive).minusDays(1)
    } else {
        requireNotNull(startInstant).atZone(zoneId).toLocalDate() to
            requireNotNull(endInstant).minusNanos(1).atZone(zoneId).toLocalDate()
    }

internal const val MAX_MARKER_COLORS = 3

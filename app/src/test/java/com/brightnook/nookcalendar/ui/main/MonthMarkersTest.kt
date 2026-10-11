package com.brightnook.nookcalendar.ui.main

import com.brightnook.nookcalendar.domain.EventInstance
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class MonthMarkersTest {
    private val month = YearMonth.of(2026, 8)
    private val zone = ZoneId.of("Asia/Tokyo")

    @Test
    fun visibleCalendarColorsAreDistinctCappedAndCoverEveryLocalDayOfMultiDayEvents() {
        val markers = monthDayMarkerColors(
            events = listOf(
                allDay("long", "one", 0xff112233.toInt(), LocalDate.of(2026, 8, 14), LocalDate.of(2026, 8, 17)),
                allDay("same-color", "two", 0xff112233.toInt(), LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 16)),
                allDay("third", "three", 0xff445566.toInt(), LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 16)),
                allDay("fourth", "four", 0xff778899.toInt(), LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 16)),
                allDay("adjacent", "one", 0xff112233.toInt(), LocalDate.of(2026, 7, 31), LocalDate.of(2026, 8, 1)),
                allDay("hidden", "hidden", 0xffaa0000.toInt(), LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 16)),
            ),
            visibleCalendarIds = setOf("one", "two", "three", "four"),
            month = month,
            zoneId = zone,
        )

        assertEquals(listOf(0xff112233.toInt()), markers[LocalDate.of(2026, 8, 14)])
        assertEquals(
            setOf(0xff112233.toInt(), 0xff445566.toInt(), 0xff778899.toInt()),
            markers[LocalDate.of(2026, 8, 15)]?.toSet(),
        )
        assertEquals(listOf(0xff112233.toInt()), markers[LocalDate.of(2026, 8, 16)])
        assertEquals(null, markers[LocalDate.of(2026, 8, 13)])
        assertEquals(listOf(0xff112233.toInt()), markers[LocalDate.of(2026, 7, 31)])
    }

    private fun allDay(id: String, calendarId: String, color: Int, start: LocalDate, endExclusive: LocalDate) =
        EventInstance(id, "event-$id", calendarId, id, color, true, null, null, start, endExclusive, null)
}

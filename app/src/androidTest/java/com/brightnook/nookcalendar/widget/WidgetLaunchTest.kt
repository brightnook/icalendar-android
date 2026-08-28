package com.brightnook.nookcalendar.widget

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.brightnook.nookcalendar.domain.EventInstance
import com.brightnook.nookcalendar.ui.main.CalendarLaunchTargetDelivery
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetLaunchTest {
    @Test
    fun widgetRowFillInIntentPreservesRenderedInstanceAndLocalDate() {
        // Catches a home-screen event click losing its instance ID before MainActivity receives it.
        val row = AgendaWidgetFactory.agendaRows(
            listOf(
                EventInstance(
                    instanceId = "instance-42",
                    eventId = "event-42",
                    calendarId = "work",
                    title = "Planning",
                    calendarColorArgb = 0xff336699.toInt(),
                    isAllDay = false,
                    startInstant = Instant.parse("2026-08-16T01:30:00Z"),
                    endInstant = Instant.parse("2026-08-16T02:30:00Z"),
                    startDate = null,
                    endDateExclusive = null,
                    location = null,
                ),
            ),
            ZoneId.of("Asia/Tokyo"),
        ).single()

        val intent = WidgetIntentFactory.eventIntent(row)

        assertEquals("2026-08-16", intent.getStringExtra(CalendarLaunchTargetDelivery.EXTRA_DATE))
        assertEquals("instance-42", intent.getStringExtra(CalendarLaunchTargetDelivery.EXTRA_INSTANCE_ID))
    }
}

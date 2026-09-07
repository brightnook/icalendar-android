package com.brightnook.nookcalendar.widget

import android.app.PendingIntent
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.brightnook.nookcalendar.domain.EventInstance
import com.brightnook.nookcalendar.ui.main.CalendarLaunchTargetDelivery
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AgendaWidgetFactoryTest {
    @Test
    fun allDayItemsComeBeforeTimedItemsWithinTheirDateGroup() {
        // Catches time-only ordering that places a timed item before an all-day item on the same date.
        val rows = AgendaWidgetFactory.agendaRows(
            events = listOf(timed("late", "2026-08-15T14:00:00Z"), allDay("day"), timed("early", "2026-08-15T09:00:00Z")),
            zoneId = ZoneId.of("UTC"),
        )

        assertEquals(listOf("day", "early", "late"), rows.map { it.event.title })
        assertEquals(listOf(true, false, false), rows.map { it.startsDateGroup })
    }

    @Test
    fun rowIntentCarriesItsExactDateAndInstanceId() {
        // Catches a widget row opening today or a different event instead of its rendered occurrence.
        val row = AgendaWidgetFactory.agendaRows(
            events = listOf(timed("planning", "2026-08-16T01:30:00Z", instanceId = "event-42")),
            zoneId = ZoneId.of("Asia/Tokyo"),
        ).single()

        val intent = WidgetIntentFactory.eventIntent(row)

        assertEquals("2026-08-16", intent.getStringExtra(CalendarLaunchTargetDelivery.EXTRA_DATE))
        assertEquals("event-42", intent.getStringExtra(CalendarLaunchTargetDelivery.EXTRA_INSTANCE_ID))
    }

    @Test
    fun collectionTemplateRemainsMutableForRowFillInExtras() {
        // Catches an immutable PendingIntent silently discarding a row's date and instance ID on Android 12+.
        val context = ApplicationProvider.getApplicationContext<Context>()

        val template = WidgetIntentFactory.eventPendingIntent(context, 101)

        assertFalse(template.isImmutable)
    }

    @Test
    fun followingVisibleCalendarsUsesCurrentVisibleCalendarIdsAfterSync() {
        // A newly added widget stores no explicit calendar IDs while it follows the main calendar.
        // It must show events from calendars that arrive in a later sync.
        val selected = selectedWidgetCalendarIds(
            config = WidgetConfig(
                appWidgetId = 44,
                calendarIds = emptySet(),
                range = WidgetRange.THIRTY_DAYS,
                followVisibleCalendars = true,
            ),
            visibleIds = linkedSetOf("personal", "work"),
        )

        assertEquals(linkedSetOf("personal", "work"), selected)
    }

    private fun allDay(title: String) = EventInstance(
        instanceId = "$title-id", eventId = "$title-event", calendarId = "work", title = title,
        calendarColorArgb = 0xff336699.toInt(), isAllDay = true, startInstant = null, endInstant = null,
        startDate = LocalDate.of(2026, 8, 15), endDateExclusive = LocalDate.of(2026, 8, 16), location = null,
    )

    private fun timed(title: String, start: String, instanceId: String = "$title-id") = EventInstance(
        instanceId = instanceId, eventId = "$title-event", calendarId = "work", title = title,
        calendarColorArgb = 0xff336699.toInt(), isAllDay = false, startInstant = Instant.parse(start),
        endInstant = Instant.parse(start).plusSeconds(3_600), startDate = null, endDateExclusive = null, location = "Room 7",
    )
}

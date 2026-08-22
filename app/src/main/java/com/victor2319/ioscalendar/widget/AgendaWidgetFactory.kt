package com.victor2319.ioscalendar.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.victor2319.ioscalendar.CalendarApplication
import com.victor2319.ioscalendar.R
import com.victor2319.ioscalendar.domain.EventInstance
import com.victor2319.ioscalendar.domain.TimeRange
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

data class AgendaWidgetRow(
    val date: LocalDate,
    val event: EventInstance,
    val startsDateGroup: Boolean,
)

class AgendaWidgetFactory(
    private val context: Context,
    private val appWidgetId: Int,
    private val widgetWidthDp: Int,
) : RemoteViewsService.RemoteViewsFactory {
    private var rows: List<AgendaWidgetRow> = emptyList()
    private var calendarNames: Map<String, String> = emptyMap()

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        val app = context.applicationContext as CalendarApplication
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val preferences = app.container.widgetPreferences
        val config = runBlocking { preferences.configFor(appWidgetId) }
        val calendars = runBlocking { app.container.calendarRepository.observeCalendars().first() }
        val visibleIds = calendars.filter { it.isVisible }.mapTo(linkedSetOf()) { it.id }
        val selectedIds = (config?.calendarIds ?: visibleIds).intersect(visibleIds)
        val range = config?.range ?: WidgetRange.SEVEN_DAYS
        val timeRange = TimeRange(
            today.atStartOfDay(zone).toInstant(),
            today.plusDays(range.days.toLong()).atStartOfDay(zone).toInstant(),
            zone,
        )
        calendarNames = calendars.associate { it.id to it.name }
        rows = agendaRows(
            runBlocking { app.container.calendarRepository.observeAgenda(timeRange, selectedIds).first() },
            zone,
        )
    }

    override fun onDestroy() {
        rows = emptyList()
    }

    override fun getCount(): Int = rows.size
    override fun getViewTypeCount(): Int = 1
    override fun getViewAt(position: Int): RemoteViews {
        val row = rows[position]
        return RemoteViews(context.packageName, R.layout.agenda_widget_item).apply {
            setTextViewText(R.id.widget_item_date, if (row.startsDateGroup) formatDate(row.date) else "")
            setTextViewText(R.id.widget_item_time, if (row.event.isAllDay) context.getString(R.string.calendar_all_day) else formatTime(row.event.startInstant!!))
            setTextViewText(R.id.widget_item_title, row.event.title)
            setTextViewText(R.id.widget_item_calendar, calendarNames[row.event.calendarId].orEmpty())
            setTextColor(R.id.widget_item_color, row.event.calendarColorArgb)
            setTextViewText(R.id.widget_item_location, row.event.location.orEmpty())
            val showLocation = widgetWidthDp >= 300 && !row.event.location.isNullOrBlank()
            setViewVisibility(R.id.widget_item_location, if (showLocation) android.view.View.VISIBLE else android.view.View.GONE)
            setOnClickFillInIntent(R.id.widget_item_root, WidgetIntentFactory.eventIntent(row))
        }
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getItemId(position: Int): Long = rows[position].event.instanceId.hashCode().toLong()
    override fun hasStableIds(): Boolean = true

    companion object {
        fun agendaRows(events: List<EventInstance>, zoneId: ZoneId): List<AgendaWidgetRow> {
            val ordered = events.map { event -> eventDate(event, zoneId) to event }
                .sortedWith(
                    compareBy<Pair<LocalDate, EventInstance>> { it.first }
                        .thenByDescending { it.second.isAllDay }
                        .thenBy { it.second.startInstant ?: Instant.EPOCH }
                        .thenBy { it.second.title.lowercase() }
                        .thenBy { it.second.instanceId },
                )
            var priorDate: LocalDate? = null
            return ordered.map { (date, event) ->
                AgendaWidgetRow(date, event, startsDateGroup = date != priorDate).also { priorDate = date }
            }
        }

        private fun eventDate(event: EventInstance, zoneId: ZoneId): LocalDate =
            if (event.isAllDay) requireNotNull(event.startDate) else requireNotNull(event.startInstant).atZone(zoneId).toLocalDate()
    }

    private fun formatDate(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
    private fun formatTime(instant: Instant): String = instant.atZone(ZoneId.systemDefault()).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
}

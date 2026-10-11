package com.brightnook.nookcalendar.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import android.util.TypedValue
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.brightnook.nookcalendar.CalendarApplication
import com.brightnook.nookcalendar.R
import com.brightnook.nookcalendar.domain.EventInstance
import com.brightnook.nookcalendar.domain.TimeRange
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

data class AgendaWidgetRow(
    val date: LocalDate,
    val event: EventInstance,
    val startsDateGroup: Boolean,
)

internal fun selectedWidgetCalendarIds(
    config: WidgetConfig?,
    visibleIds: Set<String>,
): Set<String> = when {
    config == null || config.followVisibleCalendars -> visibleIds
    else -> config.calendarIds.intersect(visibleIds)
}

internal class AgendaWidgetFactory(
    private val context: Context,
    private val appWidgetId: Int,
    private val widgetWidthDp: Int,
    private val widgetThemeMode: WidgetThemeMode = WidgetThemeMode.LIGHT,
) : RemoteViewsService.RemoteViewsFactory {
    private var rows: List<AgendaWidgetRow> = emptyList()
    private var calendarNames: Map<String, String> = emptyMap()
    private var localizedContext: Context = context

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        val app = context.applicationContext as CalendarApplication
        val settings = runBlocking { app.container.settingsStore.settings.first() }
        localizedContext = WidgetLocaleContext.resolve(context, settings.language)
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val preferences = app.container.widgetPreferences
        val config = runBlocking { preferences.configFor(appWidgetId) }
        val calendars = runBlocking { app.container.calendarRepository.observeCalendars().first() }
        val visibleIds = calendars.filter { it.isVisible }.mapTo(linkedSetOf()) { it.id }
        val selectedIds = selectedWidgetCalendarIds(config, visibleIds)
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
        val presentation = widgetPresentationForWidth(widgetWidthDp)
        val rowPresentation = widgetRowPresentation(
            title = row.event.title,
            timeText = if (row.event.isAllDay) localizedContext.getString(R.string.calendar_all_day) else formatTime(row.event.startInstant!!),
            calendarName = calendarNames[row.event.calendarId].orEmpty(),
        )
        val theme = widgetThemePresentationForMode(widgetThemeMode)
        return RemoteViews(context.packageName, R.layout.agenda_widget_item).apply {
            setTextViewText(R.id.widget_item_date, if (row.startsDateGroup) formatDate(row.date) else "")
            setViewVisibility(R.id.widget_item_date, if (row.startsDateGroup) android.view.View.VISIBLE else android.view.View.GONE)
            setTextViewText(R.id.widget_item_title, rowPresentation.title)
            setTextViewText(R.id.widget_item_calendar, rowPresentation.metadata)
            setTextColor(R.id.widget_item_color, row.event.calendarColorArgb)
            setTextColor(R.id.widget_item_date, theme.dateGroupTextColor)
            setTextColor(R.id.widget_item_title, theme.primaryTextColor)
            setTextColor(R.id.widget_item_calendar, theme.secondaryTextColor)
            setTextColor(R.id.widget_item_location, theme.secondaryTextColor)
            setInt(
                R.id.widget_item_date,
                "setBackgroundResource",
                if (widgetThemeMode == WidgetThemeMode.LIGHT) {
                    R.drawable.widget_date_group_background
                } else {
                    R.drawable.widget_date_group_background_dark
                },
            )
            setInt(
                R.id.widget_item_title,
                "setBackgroundResource",
                if (widgetThemeMode == WidgetThemeMode.LIGHT) {
                    R.drawable.widget_event_title_background
                } else {
                    R.drawable.widget_event_title_background_dark
                },
            )
            setTextViewTextSize(R.id.widget_item_date, TypedValue.COMPLEX_UNIT_SP, presentation.secondaryTextSizeSp)
            setTextViewTextSize(R.id.widget_item_title, TypedValue.COMPLEX_UNIT_SP, presentation.eventTitleTextSizeSp)
            setTextViewTextSize(R.id.widget_item_calendar, TypedValue.COMPLEX_UNIT_SP, presentation.secondaryTextSizeSp)
            setTextViewTextSize(R.id.widget_item_location, TypedValue.COMPLEX_UNIT_SP, presentation.secondaryTextSizeSp)
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

    private fun formatDate(date: LocalDate): String = WidgetDateTimeFormatter.formatDate(
        date = date,
        zoneId = ZoneId.systemDefault(),
        locale = localizedContext.resources.configuration.locales[0],
    )

    private fun formatTime(instant: Instant): String = WidgetDateTimeFormatter.formatTime(
        instant = instant,
        zoneId = ZoneId.systemDefault(),
        locale = localizedContext.resources.configuration.locales[0],
        is24Hour = DateFormat.is24HourFormat(context),
    )
}

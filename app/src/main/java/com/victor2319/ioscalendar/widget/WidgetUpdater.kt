package com.victor2319.ioscalendar.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.TypedValue
import android.widget.RemoteViews
import com.victor2319.ioscalendar.CalendarApplication
import com.victor2319.ioscalendar.R
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

fun interface WidgetRefreshNotifier {
    fun refreshAll()
}

class WidgetUpdater(private val context: Context) : WidgetRefreshNotifier {
    override fun refreshAll() {
        val manager = AppWidgetManager.getInstance(context)
        val provider = ComponentName(context, AgendaWidgetProvider::class.java)
        val ids = manager.getAppWidgetIds(provider)
        if (ids.isEmpty()) return
        ids.forEach { appWidgetId -> manager.updateAppWidget(appWidgetId, views(manager, appWidgetId)) }
        manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_events)
    }

    fun refresh(appWidgetIds: IntArray) {
        if (appWidgetIds.isEmpty()) return
        val manager = AppWidgetManager.getInstance(context)
        appWidgetIds.forEach { appWidgetId -> manager.updateAppWidget(appWidgetId, views(manager, appWidgetId)) }
        manager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_events)
    }

    private fun views(manager: AppWidgetManager, appWidgetId: Int): RemoteViews = RemoteViews(context.packageName, R.layout.agenda_widget).apply {
        val widthDp = manager.getAppWidgetOptions(appWidgetId)
            .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        val presentation = widgetPresentationForWidth(widthDp)
        setTextViewTextSize(R.id.widget_title, TypedValue.COMPLEX_UNIT_SP, presentation.titleTextSizeSp)
        val app = context.applicationContext as CalendarApplication
        val settings = runBlocking { app.container.settingsStore.settings.first() }
        val localizedContext = WidgetLocaleContext.resolve(context, settings.language)
        setTextViewText(R.id.widget_title, localizedContext.getString(R.string.widget_title))
        setTextViewText(R.id.widget_empty, localizedContext.getString(R.string.widget_empty))
        setContentDescription(R.id.widget_refresh, localizedContext.getString(R.string.widget_refresh))
        setTextViewText(R.id.widget_header_date, WidgetDateTimeFormatter.formatHeaderDate(
            LocalDate.now(),
            localizedContext.resources.configuration.locales[0],
        ))
        setTextViewTextSize(R.id.widget_header_date, TypedValue.COMPLEX_UNIT_SP, if (presentation.isCompact) 10f else 12f)
        val serviceIntent = Intent(context, AgendaWidgetService::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            .putExtra(EXTRA_WIDGET_WIDTH_DP, widthDp)
            .setData(Uri.parse("ioscalendar-widget://agenda/$appWidgetId/$widthDp"))
        setRemoteAdapter(R.id.widget_events, serviceIntent)
        setEmptyView(R.id.widget_events, R.id.widget_empty)
        setPendingIntentTemplate(R.id.widget_events, WidgetIntentFactory.eventPendingIntent(context, appWidgetId))
        setOnClickPendingIntent(R.id.widget_title, WidgetIntentFactory.todayPendingIntent(context, appWidgetId))
        setOnClickPendingIntent(R.id.widget_refresh, WidgetIntentFactory.refreshPendingIntent(context, appWidgetId))
    }

    companion object {
        const val EXTRA_WIDGET_WIDTH_DP = "com.victor2319.ioscalendar.widget.WIDTH_DP"
    }
}

package com.victor2319.ioscalendar.widget

import android.content.Context
import android.content.Intent
import android.app.PendingIntent
import com.victor2319.ioscalendar.MainActivity
import com.victor2319.ioscalendar.ui.main.CalendarLaunchTargetDelivery
import java.time.LocalDate

object WidgetIntentFactory {
    const val ACTION_REFRESH = "com.victor2319.ioscalendar.widget.REFRESH"
    const val ACTION_OPEN_TODAY = "com.victor2319.ioscalendar.widget.OPEN_TODAY"

    fun eventIntent(row: AgendaWidgetRow): Intent = Intent().apply {
        putExtra(CalendarLaunchTargetDelivery.EXTRA_DATE, row.date.toString())
        putExtra(CalendarLaunchTargetDelivery.EXTRA_INSTANCE_ID, row.event.instanceId)
    }

    fun eventPendingIntent(context: Context, appWidgetId: Int): PendingIntent = PendingIntent.getActivity(
        context,
        appWidgetId,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )

    fun todayPendingIntent(context: Context, appWidgetId: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        appWidgetId + 10_000,
        Intent(context, AgendaWidgetProvider::class.java).setAction(ACTION_OPEN_TODAY),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun refreshPendingIntent(context: Context, appWidgetId: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        appWidgetId + 20_000,
        Intent(context, AgendaWidgetProvider::class.java).setAction(ACTION_REFRESH),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun configurationIntent(context: Context, appWidgetId: Int): Intent = Intent(context, WidgetConfigActivity::class.java)
        .putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)

    fun settingsIntent(context: Context): Intent = Intent(context, MainActivity::class.java)
        .putExtra(EXTRA_OPEN_SETTINGS, true)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    const val EXTRA_OPEN_SETTINGS = "com.victor2319.ioscalendar.widget.OPEN_SETTINGS"
}

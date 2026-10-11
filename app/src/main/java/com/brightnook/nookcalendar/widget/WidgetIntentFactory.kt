package com.brightnook.nookcalendar.widget

import android.content.Context
import android.content.Intent
import android.app.PendingIntent
import com.brightnook.nookcalendar.MainActivity
import com.brightnook.nookcalendar.ui.main.CalendarLaunchTargetDelivery
import java.time.LocalDate

object WidgetIntentFactory {
    const val ACTION_REFRESH = "com.brightnook.nookcalendar.widget.REFRESH"
    const val ACTION_OPEN_TODAY = "com.brightnook.nookcalendar.widget.OPEN_TODAY"
    const val ACTION_CREATE_EVENT = "com.brightnook.nookcalendar.widget.CREATE_EVENT"
    const val EXTRA_CREATE_EVENT = "com.brightnook.nookcalendar.widget.extra.CREATE_EVENT"

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
        Intent(context, WidgetSyncRequestReceiver::class.java).setAction(ACTION_REFRESH),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun createEventIntent(context: Context): Intent = Intent(context, MainActivity::class.java)
        .setAction(ACTION_CREATE_EVENT)
        .putExtra(EXTRA_CREATE_EVENT, true)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    fun createEventPendingIntent(context: Context, appWidgetId: Int): PendingIntent = PendingIntent.getActivity(
        context,
        appWidgetId + 30_000,
        createEventIntent(context),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun configurationIntent(context: Context, appWidgetId: Int): Intent = Intent(context, WidgetConfigActivity::class.java)
        .putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)

    fun settingsIntent(context: Context): Intent = Intent(context, MainActivity::class.java)
        .putExtra(EXTRA_OPEN_SETTINGS, true)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    const val EXTRA_OPEN_SETTINGS = "com.brightnook.nookcalendar.widget.OPEN_SETTINGS"
}

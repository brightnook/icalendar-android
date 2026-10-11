package com.brightnook.nookcalendar.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import com.brightnook.nookcalendar.CalendarApplication
import com.brightnook.nookcalendar.ui.main.CalendarLaunchTargetDelivery
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AgendaWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        WidgetUpdater(context.applicationContext).refresh(appWidgetIds)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val preferences = (context.applicationContext as CalendarApplication).container.widgetPreferences
                for (appWidgetId in appWidgetIds) preferences.delete(appWidgetId)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle,
    ) {
        WidgetUpdater(context.applicationContext).refresh(intArrayOf(appWidgetId))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action in TIME_REFRESH_ACTIONS) {
            WidgetUpdater(context.applicationContext).refreshAll()
            return
        }
        if (intent.action != WidgetIntentFactory.ACTION_OPEN_TODAY) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                context.startActivity(
                    Intent(context, com.brightnook.nookcalendar.MainActivity::class.java)
                        .putExtra(CalendarLaunchTargetDelivery.EXTRA_DATE, LocalDate.now().toString())
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            } catch (_: Throwable) {
                // Opening the app from a widget is best effort.
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val TIME_REFRESH_ACTIONS = setOf(Intent.ACTION_DATE_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)
    }
}

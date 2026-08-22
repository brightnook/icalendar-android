package com.victor2319.ioscalendar.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import com.victor2319.ioscalendar.CalendarApplication
import com.victor2319.ioscalendar.domain.SyncMode
import com.victor2319.ioscalendar.ui.main.CalendarLaunchTargetDelivery
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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
        if (intent.action !in setOf(WidgetIntentFactory.ACTION_REFRESH, WidgetIntentFactory.ACTION_OPEN_TODAY)) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val application = context.applicationContext as CalendarApplication
                when (intent.action) {
                    WidgetIntentFactory.ACTION_OPEN_TODAY -> context.startActivity(
                        Intent(context, com.victor2319.ioscalendar.MainActivity::class.java)
                            .putExtra(CalendarLaunchTargetDelivery.EXTRA_DATE, LocalDate.now().toString())
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                    WidgetIntentFactory.ACTION_REFRESH -> {
                        if (application.container.settingsStore.settings.first().syncMode == SyncMode.DISABLED) {
                            context.startActivity(WidgetIntentFactory.settingsIntent(context))
                        } else {
                            application.container.syncScheduler.enqueueManual()
                        }
                    }
                }
            } catch (_: Throwable) {
                // A widget refresh is best effort; the in-app state remains the source of any error message.
            } finally {
                pending.finish()
            }
        }
    }
}

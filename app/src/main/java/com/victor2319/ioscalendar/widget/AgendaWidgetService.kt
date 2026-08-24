package com.victor2319.ioscalendar.widget

import android.content.Intent
import android.widget.RemoteViewsService

class AgendaWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = AgendaWidgetFactory(
        context = applicationContext,
        appWidgetId = intent.getIntExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID),
        widgetWidthDp = intent.getIntExtra(WidgetUpdater.EXTRA_WIDGET_WIDTH_DP, 0),
        widgetThemeMode = runCatching {
            WidgetThemeMode.valueOf(intent.getStringExtra(WidgetUpdater.EXTRA_WIDGET_THEME_MODE).orEmpty())
        }.getOrDefault(WidgetThemeMode.LIGHT),
    )
}

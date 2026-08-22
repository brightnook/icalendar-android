package com.victor2319.ioscalendar.widget

import com.victor2319.ioscalendar.data.CalendarDatabase
import com.victor2319.ioscalendar.data.WidgetConfigEntity

class RoomWidgetPreferences(database: CalendarDatabase) : WidgetPreferences {
    private val dao = database.widgetConfigDao()

    override suspend fun configFor(appWidgetId: Int): WidgetConfig? {
        val config = dao.findConfig(appWidgetId) ?: return null
        return WidgetConfig(
            appWidgetId = config.appWidgetId,
            calendarIds = dao.calendarIds(appWidgetId).toSet(),
            range = WidgetRange.entries.single { it.days == config.rangeDays },
        )
    }

    override suspend fun save(config: WidgetConfig) {
        dao.replaceConfig(
            WidgetConfigEntity(config.appWidgetId, config.range.days),
            config.calendarIds,
        )
    }

    override suspend fun delete(appWidgetId: Int) {
        dao.deleteConfig(appWidgetId)
    }
}

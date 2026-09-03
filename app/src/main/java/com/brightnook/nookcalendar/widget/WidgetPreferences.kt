package com.brightnook.nookcalendar.widget

enum class WidgetRange(val days: Int) {
    TODAY(1),
    THREE_DAYS(3),
    SEVEN_DAYS(7),
    THIRTY_DAYS(30),
}

data class WidgetConfig(
    val appWidgetId: Int,
    val calendarIds: Set<String>,
    val range: WidgetRange,
    val followVisibleCalendars: Boolean = false,
)

interface WidgetPreferences {
    suspend fun configFor(appWidgetId: Int): WidgetConfig?
    suspend fun save(config: WidgetConfig)
    suspend fun delete(appWidgetId: Int)
}

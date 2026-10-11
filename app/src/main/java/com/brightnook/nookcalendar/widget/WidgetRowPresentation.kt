package com.brightnook.nookcalendar.widget

data class WidgetRowPresentation(
    val title: String,
    val metadata: String,
)

data class WidgetRowVisualPolicy(
    val dateGroupHasRoundedEnds: Boolean,
    val titleTopPaddingDp: Int,
    val titleBackgroundResource: String,
)

internal fun widgetRowVisualPolicy(): WidgetRowVisualPolicy = WidgetRowVisualPolicy(
    dateGroupHasRoundedEnds = false,
    titleTopPaddingDp = 1,
    titleBackgroundResource = "widget_event_title_background",
)

internal fun widgetRowPresentation(
    title: String,
    timeText: String,
    calendarName: String,
): WidgetRowPresentation = WidgetRowPresentation(
    title = title,
    metadata = listOf(timeText, calendarName).filter { it.isNotBlank() }.joinToString(" · "),
)

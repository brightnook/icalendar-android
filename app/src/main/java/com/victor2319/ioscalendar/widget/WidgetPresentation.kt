package com.victor2319.ioscalendar.widget

internal data class WidgetPresentation(
    val isCompact: Boolean,
    val titleTextSizeSp: Float,
    val eventTitleTextSizeSp: Float,
    val secondaryTextSizeSp: Float,
)

internal fun widgetPresentationForWidth(widthDp: Int): WidgetPresentation =
    if (widthDp in 1 until 180) {
        WidgetPresentation(true, titleTextSizeSp = 14f, eventTitleTextSizeSp = 12f, secondaryTextSizeSp = 10f)
    } else {
        WidgetPresentation(false, titleTextSizeSp = 18f, eventTitleTextSizeSp = 14f, secondaryTextSizeSp = 12f)
    }

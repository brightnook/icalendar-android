package com.victor2319.ioscalendar.widget

internal data class WidgetPresentation(
    val isCompact: Boolean,
    val showHeaderDate: Boolean,
    val titleTextSizeSp: Float,
    val eventTitleTextSizeSp: Float,
    val secondaryTextSizeSp: Float,
)

internal fun widgetPresentationForWidth(widthDp: Int): WidgetPresentation = when {
    widthDp in 1 until 160 -> {
        WidgetPresentation(
            isCompact = true,
            showHeaderDate = true,
            titleTextSizeSp = 14f,
            eventTitleTextSizeSp = 12f,
            secondaryTextSizeSp = 10f,
        )
    }
    widthDp in 160 until 240 -> {
        WidgetPresentation(
            isCompact = true,
            showHeaderDate = true,
            titleTextSizeSp = 14f,
            eventTitleTextSizeSp = 12f,
            secondaryTextSizeSp = 10f,
        )
    }
    else -> {
        WidgetPresentation(
            isCompact = false,
            showHeaderDate = true,
            titleTextSizeSp = 18f,
            eventTitleTextSizeSp = 14f,
            secondaryTextSizeSp = 12f,
        )
    }
}

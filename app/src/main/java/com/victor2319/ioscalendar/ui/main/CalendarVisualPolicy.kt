package com.victor2319.ioscalendar.ui.main

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

internal data class CalendarDayCellLayout(
    val dateBoxSize: Dp,
    val markerRowHeight: Dp,
    val dateCornerRadius: Dp,
)

internal fun fixedCalendarDayCellLayout() = CalendarDayCellLayout(
    dateBoxSize = 32.dp,
    markerRowHeight = 6.dp,
    dateCornerRadius = 8.dp,
)

internal enum class CalendarDayHighlight {
    NONE,
    FILLED_PRIMARY,
}

internal fun calendarDayHighlight(isToday: Boolean, isSelected: Boolean) =
    if (isToday || isSelected) CalendarDayHighlight.FILLED_PRIMARY else CalendarDayHighlight.NONE

internal data class YearCalendarLayout(
    val horizontalContentPadding: Dp,
    val monthGap: Dp,
    val miniDateBoxSize: Dp,
    val miniDateCornerRadius: Dp,
    val miniMarkerRowHeight: Dp,
    val miniDayTouchTarget: Dp,
)

internal fun fixedYearCalendarLayout() = YearCalendarLayout(
    horizontalContentPadding = 16.dp,
    monthGap = 12.dp,
    miniDateBoxSize = 16.dp,
    miniDateCornerRadius = 4.dp,
    miniMarkerRowHeight = 4.dp,
    miniDayTouchTarget = 48.dp,
)

internal fun yearDateContentDescription(date: LocalDate, locale: Locale): String =
    date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale))

internal fun primaryControlPressFeedbackIsRippleFree() = true

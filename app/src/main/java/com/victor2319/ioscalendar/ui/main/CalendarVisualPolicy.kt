package com.victor2319.ioscalendar.ui.main

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.victor2319.ioscalendar.domain.WeekStart
import java.time.LocalDate
import java.time.YearMonth
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
    val miniDayRowHeight: Dp,
)

internal fun fixedYearCalendarLayout() = YearCalendarLayout(
    horizontalContentPadding = 16.dp,
    monthGap = 12.dp,
    miniDateBoxSize = 16.dp,
    miniDateCornerRadius = 4.dp,
    miniMarkerRowHeight = 4.dp,
    miniDayRowHeight = 48.dp,
)

/**
 * Resolves a tap inside a six-week mini-month grid. The compact year view exposes the whole
 * mini-month as the touch target, then maps its coordinate to the visible date cell.
 */
internal fun miniMonthDateAt(
    month: YearMonth,
    weekStart: WeekStart,
    gridSize: IntSize,
    tap: Offset,
): LocalDate? {
    if (gridSize.width <= 0 || gridSize.height <= 0 ||
        tap.x < 0f || tap.x >= gridSize.width.toFloat() ||
        tap.y < 0f || tap.y >= gridSize.height.toFloat()
    ) return null

    val column = (tap.x * 7 / gridSize.width).toInt()
    val row = (tap.y * 6 / gridSize.height).toInt()
    val date = calendarGridStart(month, weekStart).plusDays((row * 7L) + column)
    return date.takeIf { it.month == month.month }
}

internal fun yearDateContentDescription(date: LocalDate, locale: Locale): String =
    date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale))

internal fun primaryControlPressFeedbackIsRippleFree() = true

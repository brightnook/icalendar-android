package com.brightnook.nookcalendar.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import com.brightnook.nookcalendar.ui.PrimaryControlRippleScope
import com.brightnook.nookcalendar.domain.WeekStart

const val YEAR_CALENDAR_TAG = "year-calendar"

@Composable
fun YearCalendar(
    year: Int,
    selectedDate: LocalDate,
    markerColors: Map<LocalDate, List<Int>>,
    onSelectDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    weekStart: WeekStart = WeekStart.SUNDAY,
    contentColor: Color = Color.Unspecified,
    secondaryContentColor: Color = Color.Unspecified,
    saturdayColor: Color = Color.Unspecified,
    sundayColor: Color = Color.Unspecified,
) {
    val layout = fixedYearCalendarLayout()
    val resolvedContentColor = contentColor.yearColorOrDefault(MaterialTheme.colorScheme.onSurface)
    val resolvedSecondaryContentColor = secondaryContentColor.yearColorOrDefault(MaterialTheme.colorScheme.onSurfaceVariant)
    val resolvedSaturdayColor = saturdayColor.yearColorOrDefault(MaterialTheme.colorScheme.tertiary)
    val resolvedSundayColor = sundayColor.yearColorOrDefault(MaterialTheme.colorScheme.tertiary)
    PrimaryControlRippleScope {
    Column(
        modifier.padding(horizontal = layout.horizontalContentPadding).verticalScroll(rememberScrollState()).testTag(YEAR_CALENDAR_TAG),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            year.toString(),
            color = resolvedContentColor,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.testTag("year-title"),
        )
        (1..12).chunked(3).forEach { months ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(layout.monthGap)) {
                months.forEach { number ->
                    MiniMonth(
                        month = YearMonth.of(year, number),
                        selectedDate = selectedDate,
                        markerColors = markerColors,
                        onSelectDate = onSelectDate,
                        weekStart = weekStart,
                        contentColor = resolvedContentColor,
                        secondaryContentColor = resolvedSecondaryContentColor,
                        saturdayColor = resolvedSaturdayColor,
                        sundayColor = resolvedSundayColor,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun MiniMonth(
    month: YearMonth,
    selectedDate: LocalDate,
    markerColors: Map<LocalDate, List<Int>>,
    onSelectDate: (LocalDate) -> Unit,
    weekStart: WeekStart,
    contentColor: Color,
    secondaryContentColor: Color,
    saturdayColor: Color,
    sundayColor: Color,
    modifier: Modifier,
) {
    val layout = fixedYearCalendarLayout()
    val today = LocalDate.now()
    val locale = currentLocale()
    val weekDays = calendarWeekdays(weekStart)
    val headers = calendarWeekdayHeaders(
        weekStart = weekStart,
        locale = locale,
        presentation = CalendarWeekdayHeaderPresentation.YEAR,
    )
    val start = calendarGridStart(month, weekStart)
    var gridSize by androidx.compose.runtime.remember { mutableStateOf(IntSize.Zero) }
    val dateActions = (1..month.lengthOfMonth()).map { day ->
        val date = month.atDay(day)
        CustomAccessibilityAction(yearDateContentDescription(date, locale)) {
            onSelectDate(date)
            true
        }
    }
    Column(modifier.testTag("year-month-$month"), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            yearMonthTitle(month, locale),
            color = contentColor,
            style = MaterialTheme.typography.labelMedium,
        )
        Row(Modifier.fillMaxWidth()) {
            weekDays.zip(headers).forEach { (day, header) ->
                Text(
                    header,
                    modifier = Modifier.weight(1f).testTag("year-weekday-$month-$day"),
                    color = calendarWeekdayTextColor(
                        day,
                        secondaryContentColor,
                        saturdayColor,
                        sundayColor,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .onSizeChanged { gridSize = it }
                .pointerInput(month, weekStart, gridSize, onSelectDate) {
                    detectTapGestures { tap ->
                        miniMonthDateAt(month, weekStart, gridSize, tap)?.let(onSelectDate)
                    }
                }
                .clearAndSetSemantics {
                    contentDescription = yearMonthTitle(month, locale)
                    customActions = dateActions
                }
                .testTag("year-month-grid-$month"),
        ) {
            Column {
                repeat(6) { row ->
                    Row(Modifier.fillMaxWidth()) {
                        repeat(7) { col ->
                            val date = start.plusDays((row * 7L) + col)
                            if (date.month == month.month) {
                                val highlight = calendarDayHighlight(date == today, date == selectedDate)
                                Box(
                                    modifier = Modifier.weight(1f).height(layout.miniDayRowHeight),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(
                                            modifier = Modifier.size(layout.miniDateBoxSize)
                                                .clip(RoundedCornerShape(layout.miniDateCornerRadius))
                                                .then(
                                                    when (highlight) {
                                                        CalendarDayHighlight.FILLED_PRIMARY -> Modifier.background(MaterialTheme.colorScheme.primary)
                                                        CalendarDayHighlight.OUTLINE_PRIMARY -> Modifier.border(
                                                            width = 1.dp,
                                                            color = MaterialTheme.colorScheme.primary,
                                                            shape = RoundedCornerShape(layout.miniDateCornerRadius),
                                                        )
                                                        CalendarDayHighlight.NONE -> Modifier
                                                    },
                                                )
                                                .testTag("year-date-background-$date"),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                date.dayOfMonth.toString(),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (highlight == CalendarDayHighlight.FILLED_PRIMARY) {
                                                    MaterialTheme.colorScheme.onPrimary
                                                } else {
                                                    calendarWeekdayTextColor(
                                                        date.dayOfWeek,
                                                        contentColor,
                                                        saturdayColor,
                                                        sundayColor,
                                                    )
                                                },
                                            )
                                        }
                                        Box(
                                            Modifier.height(layout.miniMarkerRowHeight),
                                            contentAlignment = Alignment.TopCenter,
                                        ) {
                                            markerColors[date]?.firstOrNull()?.let {
                                                Box(
                                                    Modifier.padding(top = 1.dp).size(3.dp).clip(CircleShape)
                                                        .background(Color(it)).testTag("year-marker-$date"),
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                Spacer(Modifier.weight(1f).height(layout.miniDayRowHeight))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun Color.yearColorOrDefault(default: Color): Color = if (this == Color.Unspecified) default else this

internal fun yearMonthTitle(month: YearMonth, locale: java.util.Locale): String =
    month.atDay(1).format(DateTimeFormatter.ofPattern("MMM", locale))

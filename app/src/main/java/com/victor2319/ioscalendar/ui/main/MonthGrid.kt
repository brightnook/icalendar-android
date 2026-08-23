package com.victor2319.ioscalendar.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.victor2319.ioscalendar.R
import com.victor2319.ioscalendar.domain.WeekStart
import com.victor2319.ioscalendar.ui.PrimaryControlRippleScope
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

const val MONTH_GRID_TAG = "month-grid"

internal fun monthGridDateColor(
    date: LocalDate,
    month: YearMonth,
    selected: Boolean,
    onPrimary: Color,
    weekday: Color,
    outsideMonth: Color,
    saturday: Color,
    sunday: Color,
): Color = when {
    selected -> onPrimary
    date.dayOfWeek == DayOfWeek.SATURDAY -> saturday
    date.dayOfWeek == DayOfWeek.SUNDAY -> sunday
    date.month == month.month -> weekday
    else -> outsideMonth
}

internal fun calendarWeekdayTextColor(
    dayOfWeek: DayOfWeek,
    weekday: Color,
    saturday: Color,
    sunday: Color,
): Color = when (dayOfWeek) {
    DayOfWeek.SATURDAY -> saturday
    DayOfWeek.SUNDAY -> sunday
    else -> weekday
}

@Composable
fun MonthGrid(
    month: YearMonth,
    selectedDate: LocalDate,
    dayMarkerColors: Map<LocalDate, List<Int>> = emptyMap(),
    onSelectDate: (LocalDate) -> Unit,
    onShowMonth: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
    weekStart: WeekStart = WeekStart.SUNDAY,
    contentColor: Color = Color.Unspecified,
    secondaryContentColor: Color = Color.Unspecified,
    saturdayColor: Color = Color.Unspecified,
    sundayColor: Color = Color.Unspecified,
) {
    val locale = currentLocale()
    val weekDays = calendarWeekdays(weekStart)
    val weekDayHeaders = calendarWeekdayHeaders(weekStart, locale)
    val gridStart = calendarGridStart(month, weekStart)
    val previousMonthDescription = stringResource(R.string.calendar_previous_month)
    val nextMonthDescription = stringResource(R.string.calendar_next_month)
    val swipeThresholdPx = with(LocalDensity.current) { 64.dp.toPx() }
    val resolvedContentColor = contentColor.orDefault(MaterialTheme.colorScheme.onSurface)
    val resolvedSecondaryContentColor = secondaryContentColor.orDefault(MaterialTheme.colorScheme.onSurfaceVariant)
    val resolvedSaturdayColor = saturdayColor.orDefault(MaterialTheme.colorScheme.tertiary)
    val resolvedSundayColor = sundayColor.orDefault(MaterialTheme.colorScheme.tertiary)
    val dayCellLayout = fixedCalendarDayCellLayout()
    val today = LocalDate.now()

    PrimaryControlRippleScope {
    Column(
        modifier
            .fillMaxSize()
            .testTag(MONTH_GRID_TAG)
            .semantics {
                contentDescription = month.atDay(1).format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
                customActions = listOf(
                    CustomAccessibilityAction(previousMonthDescription) {
                        onShowMonth(month.minusMonths(1))
                        true
                    },
                    CustomAccessibilityAction(nextMonthDescription) {
                        onShowMonth(month.plusMonths(1))
                        true
                    },
                )
            }
            .pointerInput(month, swipeThresholdPx) {
                var totalDrag = 0f
                detectVerticalDragGestures(
                    onVerticalDrag = { change, dragAmount ->
                        totalDrag += dragAmount
                        change.consume()
                    },
                    onDragEnd = {
                        when {
                            totalDrag <= -swipeThresholdPx -> onShowMonth(month.plusMonths(1))
                            totalDrag >= swipeThresholdPx -> onShowMonth(month.minusMonths(1))
                        }
                        totalDrag = 0f
                    },
                    onDragCancel = { totalDrag = 0f },
                )
            },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(
                onClick = { onShowMonth(month.minusMonths(1)) },
                modifier = Modifier.semantics { contentDescription = previousMonthDescription },
            ) {
                Text("‹", color = resolvedContentColor, style = MaterialTheme.typography.headlineSmall)
            }
            Text(
                month.atDay(1).format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)),
                color = resolvedContentColor,
                style = MaterialTheme.typography.titleLarge,
            )
            IconButton(
                onClick = { onShowMonth(month.plusMonths(1)) },
                modifier = Modifier.semantics { contentDescription = nextMonthDescription },
            ) {
                Text("›", color = resolvedContentColor, style = MaterialTheme.typography.headlineSmall)
            }
        }
        Row(Modifier.fillMaxWidth()) {
            weekDays.zip(weekDayHeaders).forEach { (dayOfWeek, header) ->
                Text(
                    header,
                    modifier = Modifier.weight(1f).padding(vertical = 2.dp).testTag("calendar-weekday-$dayOfWeek"),
                    color = calendarWeekdayTextColor(
                        dayOfWeek,
                        resolvedSecondaryContentColor,
                        resolvedSaturdayColor,
                        resolvedSundayColor,
                    ),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        repeat(6) { week ->
            Row(Modifier.fillMaxWidth().weight(1f)) {
                repeat(7) { dayIndex ->
                    val date = gridStart.plusDays((week * 7L) + dayIndex)
                    val selected = date == selectedDate
                    val highlight = calendarDayHighlight(date == today, selected)
                    TextButton(
                        onClick = { onSelectDate(date) },
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .testTag("calendar-day-$date")
                            .semantics {
                                contentDescription = date.format(
                                    DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.FULL)
                                        .withLocale(locale),
                                )
                            },
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(dayCellLayout.dateBoxSize)
                                    .clip(RoundedCornerShape(dayCellLayout.dateCornerRadius))
                                    .then(
                                        when (highlight) {
                                            CalendarDayHighlight.FILLED_PRIMARY -> Modifier.background(MaterialTheme.colorScheme.primary)
                                            CalendarDayHighlight.OUTLINE_PRIMARY -> Modifier.border(
                                                width = 1.5.dp,
                                                color = MaterialTheme.colorScheme.primary,
                                                shape = RoundedCornerShape(dayCellLayout.dateCornerRadius),
                                            )
                                            CalendarDayHighlight.NONE -> Modifier
                                        },
                                    )
                                    .testTag("calendar-date-background-$date"),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    date.dayOfMonth.toString(),
                                    color = monthGridDateColor(
                                        date = date,
                                        month = month,
                                        selected = highlight == CalendarDayHighlight.FILLED_PRIMARY,
                                        onPrimary = MaterialTheme.colorScheme.onPrimary,
                                        weekday = resolvedContentColor,
                                        outsideMonth = resolvedSecondaryContentColor,
                                        saturday = resolvedSaturdayColor,
                                        sunday = resolvedSundayColor,
                                    ),
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                            val colors = dayMarkerColors[date].orEmpty().take(MAX_MARKER_COLORS)
                            Box(
                                modifier = Modifier.height(dayCellLayout.markerRowHeight),
                                contentAlignment = Alignment.TopCenter,
                            ) {
                                if (colors.isNotEmpty()) Row(
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    modifier = Modifier.padding(top = 1.dp),
                                ) {
                                    colors.forEachIndexed { index, color ->
                                        Spacer(
                                            Modifier
                                                .size(5.dp)
                                                .clip(CircleShape)
                                                .background(Color(color))
                                                .testTag("calendar-marker-$date-$index"),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    }
}

private fun Color.orDefault(default: Color): Color = if (this == Color.Unspecified) default else this

@Composable
internal fun currentLocale(): Locale {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    return configuration.locales[0]
}

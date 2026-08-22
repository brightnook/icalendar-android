package com.victor2319.ioscalendar.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale

const val MONTH_GRID_TAG = "month-grid"

@Composable
fun MonthGrid(
    month: YearMonth,
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    onShowMonth: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = currentLocale()
    val firstDayOfWeek = WeekFields.of(locale).firstDayOfWeek
    val weekDays = generateSequence(firstDayOfWeek) { it.plus(1) }.take(7).toList()
    val firstOfMonth = month.atDay(1)
    val offset = (firstOfMonth.dayOfWeek.value - firstDayOfWeek.value + 7) % 7
    val gridStart = firstOfMonth.minusDays(offset.toLong())
    val previousMonthDescription = stringResource(R.string.calendar_previous_month)
    val nextMonthDescription = stringResource(R.string.calendar_next_month)
    val swipeThresholdPx = with(LocalDensity.current) { 64.dp.toPx() }

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
                Text("‹", style = MaterialTheme.typography.headlineSmall)
            }
            Text(
                month.atDay(1).format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)),
                style = MaterialTheme.typography.titleLarge,
            )
            IconButton(
                onClick = { onShowMonth(month.plusMonths(1)) },
                modifier = Modifier.semantics { contentDescription = nextMonthDescription },
            ) {
                Text("›", style = MaterialTheme.typography.headlineSmall)
            }
        }
        Row(Modifier.fillMaxWidth()) {
            weekDays.forEach { dayOfWeek ->
                Text(
                    dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, locale),
                    modifier = Modifier.weight(1f).padding(vertical = 2.dp),
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
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text(
                                date.dayOfMonth.toString(),
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .then(
                                        if (selected) Modifier.background(MaterialTheme.colorScheme.primary)
                                        else Modifier,
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                color = when {
                                    selected -> MaterialTheme.colorScheme.onPrimary
                                    date.month == month.month -> MaterialTheme.colorScheme.onSurface
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun currentLocale(): Locale {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    return configuration.locales[0]
}

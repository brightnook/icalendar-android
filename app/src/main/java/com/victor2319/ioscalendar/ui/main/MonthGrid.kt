package com.victor2319.ioscalendar.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
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

    Column(modifier.fillMaxSize().testTag(MONTH_GRID_TAG)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = { onShowMonth(month.minusMonths(1)) }) {
                Text("‹", style = MaterialTheme.typography.headlineSmall)
            }
            Text(
                month.atDay(1).format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)),
                style = MaterialTheme.typography.titleLarge,
            )
            IconButton(onClick = { onShowMonth(month.plusMonths(1)) }) {
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
                        modifier = Modifier.weight(1f).fillMaxSize(),
                    ) {
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
                        )
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

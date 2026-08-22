package com.victor2319.ioscalendar.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import com.victor2319.ioscalendar.ui.PrimaryControlRippleScope

const val YEAR_CALENDAR_TAG = "year-calendar"

@Composable
fun YearCalendar(year: Int, selectedDate: LocalDate, markerColors: Map<LocalDate, List<Int>>, onSelectDate: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    val layout = fixedYearCalendarLayout()
    PrimaryControlRippleScope {
    Column(
        modifier.padding(horizontal = layout.horizontalContentPadding).verticalScroll(rememberScrollState()).testTag(YEAR_CALENDAR_TAG),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(year.toString(), style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("year-title"))
        (1..12).chunked(3).forEach { months ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(layout.monthGap)) {
                months.forEach { number -> MiniMonth(YearMonth.of(year, number), selectedDate, markerColors, onSelectDate, Modifier.weight(1f)) }
            }
        }
    }
    }
}

@Composable
private fun MiniMonth(month: YearMonth, selectedDate: LocalDate, markerColors: Map<LocalDate, List<Int>>, onSelectDate: (LocalDate) -> Unit, modifier: Modifier) {
    val layout = fixedYearCalendarLayout()
    val today = LocalDate.now()
    val locale = currentLocale()
    val start = month.atDay(1).minusDays((month.atDay(1).dayOfWeek.value % 7).toLong())
    Column(modifier.testTag("year-month-$month"), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(month.atDay(1).format(DateTimeFormatter.ofPattern("MMM")), style = MaterialTheme.typography.labelMedium)
        repeat(6) { row -> Row(Modifier.fillMaxWidth()) { repeat(7) { col ->
            val date = start.plusDays((row * 7L) + col)
            if (date.month == month.month) {
                val highlight = calendarDayHighlight(date == today, date == selectedDate)
                TextButton(onClick = { onSelectDate(date) }, contentPadding = PaddingValues(0.dp), modifier = Modifier.weight(1f).heightIn(min = layout.miniDayTouchTarget).testTag("year-day-$date").semantics { contentDescription = yearDateContentDescription(date, locale) }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier.size(layout.miniDateBoxSize).clip(RoundedCornerShape(layout.miniDateCornerRadius)).then(
                            if (highlight == CalendarDayHighlight.FILLED_PRIMARY) Modifier.background(MaterialTheme.colorScheme.primary) else Modifier,
                        ).testTag("year-date-background-$date"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.labelSmall, color = if (highlight == CalendarDayHighlight.FILLED_PRIMARY) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                    }
                    Box(Modifier.height(layout.miniMarkerRowHeight), contentAlignment = Alignment.TopCenter) {
                        markerColors[date]?.firstOrNull()?.let { Box(Modifier.padding(top = 1.dp).size(3.dp).clip(CircleShape).background(Color(it))) }
                    }
                }
                }
            } else Spacer(Modifier.weight(1f).height(layout.miniDayTouchTarget))
        } } }
    }
}

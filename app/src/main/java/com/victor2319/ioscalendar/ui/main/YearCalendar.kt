package com.victor2319.ioscalendar.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

const val YEAR_CALENDAR_TAG = "year-calendar"

@Composable
fun YearCalendar(year: Int, selectedDate: LocalDate, markerColors: Map<LocalDate, List<Int>>, onSelectDate: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.verticalScroll(rememberScrollState()).testTag(YEAR_CALENDAR_TAG), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(year.toString(), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp))
        (1..12).chunked(3).forEach { months ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                months.forEach { number -> MiniMonth(YearMonth.of(year, number), selectedDate, markerColors, onSelectDate, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun MiniMonth(month: YearMonth, selectedDate: LocalDate, markerColors: Map<LocalDate, List<Int>>, onSelectDate: (LocalDate) -> Unit, modifier: Modifier) {
    val start = month.atDay(1).minusDays((month.atDay(1).dayOfWeek.value % 7).toLong())
    Column(modifier.testTag("year-month-$month"), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(month.atDay(1).format(DateTimeFormatter.ofPattern("MMM")), style = MaterialTheme.typography.labelMedium)
        repeat(6) { row -> Row(Modifier.fillMaxWidth()) { repeat(7) { col ->
            val date = start.plusDays((row * 7L) + col)
            if (date.month == month.month) TextButton(onClick = { onSelectDate(date) }, contentPadding = PaddingValues(0.dp), modifier = Modifier.weight(1f).height(24.dp).testTag("year-day-$date")) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.labelSmall, color = if (date == selectedDate) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    markerColors[date]?.firstOrNull()?.let { Box(Modifier.size(3.dp).clip(CircleShape).background(Color(it))) }
                }
            } else Spacer(Modifier.weight(1f).height(24.dp))
        } } }
    }
}

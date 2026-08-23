package com.victor2319.ioscalendar.ui.main

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.victor2319.ioscalendar.R
import com.victor2319.ioscalendar.domain.CalendarSummary
import com.victor2319.ioscalendar.domain.EventInstance
import java.time.ZoneId
import java.time.format.DateTimeFormatter

const val AGENDA_LIST_TAG = "agenda-list"

@Composable
fun AgendaList(
    agenda: List<EventInstance>,
    calendars: List<CalendarSummary>,
    zoneId: ZoneId,
    listState: LazyListState,
    onOpenEvent: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentColor: Color = Color.Unspecified,
    secondaryContentColor: Color = Color.Unspecified,
) {
    val resolvedContentColor = contentColor.agendaColorOrDefault(MaterialTheme.colorScheme.onSurface)
    val resolvedSecondaryContentColor = secondaryContentColor.agendaColorOrDefault(MaterialTheme.colorScheme.onSurfaceVariant)
    val sorted = agenda.sortedWith(
        compareByDescending<EventInstance> { it.isAllDay }
            .thenBy { it.startInstant }
            .thenBy { it.title.lowercase() }
            .thenBy { it.instanceId },
    )
    if (sorted.isEmpty()) {
        Box(modifier.testTag(AGENDA_LIST_TAG).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.calendar_no_events), color = resolvedSecondaryContentColor)
        }
        return
    }
    LazyColumn(modifier.testTag(AGENDA_LIST_TAG), state = listState) {
        items(sorted, key = EventInstance::instanceId) { event ->
            AgendaItem(
                event = event,
                calendarName = calendars.firstOrNull { it.id == event.calendarId }?.name,
                zoneId = zoneId,
                onClick = { onOpenEvent(event.instanceId) },
                contentColor = resolvedContentColor,
                secondaryContentColor = resolvedSecondaryContentColor,
            )
        }
    }
}

@Composable
private fun AgendaItem(
    event: EventInstance,
    calendarName: String?,
    zoneId: ZoneId,
    onClick: () -> Unit,
    contentColor: Color,
    secondaryContentColor: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("agenda-item-${event.instanceId}")
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.padding(top = 5.dp).size(10.dp).background(Color(event.calendarColorArgb), CircleShape),
        )
        Column(Modifier.weight(1f)) {
            Text(event.title, color = contentColor, style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                calendarName?.let { Text(it, color = contentColor, style = MaterialTheme.typography.labelMedium) }
                Text(eventTime(event, zoneId), color = contentColor, style = MaterialTheme.typography.bodyMedium)
            }
            event.location?.takeIf(String::isNotBlank)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = secondaryContentColor)
            }
        }
    }
}

private fun Color.agendaColorOrDefault(default: Color): Color = if (this == Color.Unspecified) default else this

@Composable
internal fun eventTime(event: EventInstance, zoneId: ZoneId): String {
    if (event.isAllDay) return stringResource(R.string.calendar_all_day)
    val context = LocalContext.current
    val locale = currentLocale()
    val formatter = DateTimeFormatter.ofPattern(
        if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a",
        locale,
    )
    return event.startInstant?.atZone(zoneId)?.format(formatter).orEmpty()
}

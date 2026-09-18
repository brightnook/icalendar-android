package com.brightnook.nookcalendar.ui.main

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.brightnook.nookcalendar.R
import com.brightnook.nookcalendar.domain.CalendarSummary
import com.brightnook.nookcalendar.domain.EventInstance
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
    onDeleteEvent: (String) -> Unit = {},
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
            SwipeAgendaItem(
                event = event,
                calendarName = calendars.firstOrNull { it.id == event.calendarId }?.name,
                zoneId = zoneId,
                onClick = { onOpenEvent(event.instanceId) },
                onDelete = { onDeleteEvent(event.instanceId) },
                contentColor = resolvedContentColor,
                secondaryContentColor = resolvedSecondaryContentColor,
            )
        }
    }
}

@Composable
private fun SwipeAgendaItem(
    event: EventInstance,
    calendarName: String?,
    zoneId: ZoneId,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    contentColor: Color,
    secondaryContentColor: Color,
) {
    var revealed by rememberSaveable { mutableStateOf(false) }
    var direction by rememberSaveable { mutableStateOf(-1) }
    val threshold = with(LocalDensity.current) { 48.dp.toPx() }
    val actionWidth = 80.dp
    val offset by animateDpAsState(
        if (revealed) actionWidth * direction else 0.dp,
        label = "agenda-delete-reveal",
    )
    Box(
        Modifier.fillMaxWidth().clipToBounds().pointerInput(revealed, direction, threshold) {
            var drag = 0f
            detectHorizontalDragGestures(
                onDragStart = { drag = 0f },
                onHorizontalDrag = { change, amount -> change.consume(); drag += amount },
                onDragEnd = {
                    if (kotlin.math.abs(drag) >= threshold) {
                        val swipeDirection = if (drag > 0f) 1 else -1
                        if (revealed && swipeDirection != direction) revealed = false
                        else { direction = swipeDirection; revealed = true }
                    }
                },
                onDragCancel = { drag = 0f },
            )
        },
    ) {
        AgendaItem(event, calendarName, zoneId,
            onClick = { revealed = false; onClick() },
            contentColor = contentColor, secondaryContentColor = secondaryContentColor,
            modifier = Modifier.offset(x = offset))
        if (revealed) {
            Box(Modifier.matchParentSize(),
                contentAlignment = if (direction > 0) Alignment.CenterStart else Alignment.CenterEnd) {
                Column(
                    Modifier.width(actionWidth).fillMaxHeight()
                        .background(MaterialTheme.colorScheme.error)
                        .clickable { revealed = false; onDelete() }
                        .testTag("agenda-delete-${event.instanceId}")
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_agenda_delete), contentDescription = null,
                        tint = MaterialTheme.colorScheme.onError)
                    Text(stringResource(R.string.event_editor_delete),
                        color = MaterialTheme.colorScheme.onError,
                        style = MaterialTheme.typography.labelMedium)
                }
            }
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
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
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

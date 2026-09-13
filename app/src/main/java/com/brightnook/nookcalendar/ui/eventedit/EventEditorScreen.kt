package com.brightnook.nookcalendar.ui.eventedit

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.brightnook.nookcalendar.R
import com.brightnook.nookcalendar.domain.EventDraft
import com.brightnook.nookcalendar.domain.EventReminder
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun EventEditorScreen(
    state: EventEditorState,
    zoneId: ZoneId,
    isEditing: Boolean,
    onUpdateDraft: (EventDraft) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    showDeleteConfirmationInitially: Boolean = false,
) {
    var calendarMenuOpen by remember { mutableStateOf(false) }
    var reminderMenuOpen by remember { mutableStateOf(false) }
    var customReminderOpen by remember { mutableStateOf(false) }
    var customMinutes by remember { mutableStateOf("") }
    var deleteConfirmationOpen by remember(showDeleteConfirmationInitially) {
        mutableStateOf(showDeleteConfirmationInitially)
    }
    val draft = state.draft

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.event_editor_cancel)) }
                Text(
                    stringResource(if (isEditing) R.string.event_editor_edit_title else R.string.event_editor_new_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                TextButton(
                    onClick = onSave,
                    enabled = !state.isSaving && !state.isDeleting,
                    modifier = Modifier.testTag("event-editor-save"),
                ) { Text(stringResource(if (state.isSaving) R.string.event_editor_saving else R.string.event_editor_save)) }
            }
            Column(
                Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = draft.title,
                    onValueChange = { onUpdateDraft(draft.copy(title = it)) },
                    label = { Text(stringResource(R.string.event_editor_title_field)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("event-editor-title"),
                )
                OutlinedTextField(
                    value = draft.location.orEmpty(),
                    onValueChange = { onUpdateDraft(draft.copy(location = it)) },
                    label = { Text(stringResource(R.string.event_editor_location)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("event-editor-location"),
                )
                Row(
                    Modifier.fillMaxWidth().testTag("event-editor-all-day"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.event_editor_all_day))
                    Switch(checked = draft.isAllDay, onCheckedChange = {
                        onUpdateDraft(draft.changeAllDay(it, zoneId))
                    })
                }
                DateTimeRow(
                    label = stringResource(R.string.event_editor_start),
                    draft = draft,
                    start = true,
                    zoneId = zoneId,
                    onUpdateDraft = onUpdateDraft,
                    modifier = Modifier.testTag("event-editor-start"),
                )
                DateTimeRow(
                    label = stringResource(R.string.event_editor_end),
                    draft = draft,
                    start = false,
                    zoneId = zoneId,
                    onUpdateDraft = onUpdateDraft,
                    modifier = Modifier.testTag("event-editor-end"),
                )
                Column(Modifier.fillMaxWidth().testTag("event-editor-calendar")) {
                    Text(stringResource(R.string.event_editor_calendar), style = MaterialTheme.typography.labelLarge)
                    TextButton(onClick = { calendarMenuOpen = true }) {
                        Text(state.calendars.firstOrNull { it.id == draft.calendarId }?.name
                            ?: stringResource(R.string.event_editor_choose_calendar))
                    }
                    DropdownMenu(expanded = calendarMenuOpen, onDismissRequest = { calendarMenuOpen = false }) {
                        state.calendars.forEach { calendar ->
                            DropdownMenuItem(text = { Text(calendar.name) }, onClick = {
                                onUpdateDraft(draft.copy(calendarId = calendar.id))
                                calendarMenuOpen = false
                            })
                        }
                    }
                }
                Column(Modifier.fillMaxWidth().testTag("event-editor-reminders")) {
                    Text(stringResource(R.string.event_editor_reminders), style = MaterialTheme.typography.labelLarge)
                    draft.reminders.forEachIndexed { index, reminder ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(reminderLabel(reminder))
                            TextButton(onClick = {
                                onUpdateDraft(draft.copy(reminders = draft.reminders.filterIndexed { i, _ -> i != index }))
                            }) { Text(stringResource(R.string.event_editor_remove)) }
                        }
                    }
                    TextButton(onClick = { reminderMenuOpen = true }) { Text(stringResource(R.string.event_editor_add_reminder)) }
                    DropdownMenu(expanded = reminderMenuOpen, onDismissRequest = { reminderMenuOpen = false }) {
                        reminderPresets().forEach { reminder ->
                            DropdownMenuItem(text = { Text(reminderLabel(reminder)) }, onClick = {
                                if (reminder !in draft.reminders) onUpdateDraft(draft.copy(reminders = draft.reminders + reminder))
                                reminderMenuOpen = false
                            })
                        }
                        DropdownMenuItem(text = { Text(stringResource(R.string.event_editor_reminder_custom)) }, onClick = {
                            reminderMenuOpen = false
                            customMinutes = ""
                            customReminderOpen = true
                        })
                    }
                }
                OutlinedTextField(
                    value = draft.notes.orEmpty(),
                    onValueChange = { onUpdateDraft(draft.copy(notes = it)) },
                    label = { Text(stringResource(R.string.event_editor_notes)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth().testTag("event-editor-notes"),
                )
                state.error?.let { Text(stringResource(it.messageResource()), color = MaterialTheme.colorScheme.error) }
                if (isEditing) {
                    Button(
                        onClick = { deleteConfirmationOpen = true },
                        enabled = !state.isSaving && !state.isDeleting,
                        modifier = Modifier.fillMaxWidth().testTag("event-editor-delete"),
                    ) { Text(stringResource(R.string.event_editor_delete)) }
                }
            }
        }
    }
    if (deleteConfirmationOpen) {
        AlertDialog(
            onDismissRequest = { deleteConfirmationOpen = false },
            title = { Text(stringResource(R.string.event_editor_delete_title)) },
            text = { Text(stringResource(R.string.event_editor_delete_message)) },
            confirmButton = { TextButton(onClick = { deleteConfirmationOpen = false; onDelete() }) { Text(stringResource(R.string.event_editor_delete)) } },
            dismissButton = { TextButton(onClick = { deleteConfirmationOpen = false }) { Text(stringResource(R.string.event_editor_cancel)) } },
        )
    }
    if (customReminderOpen) {
        val customReminder = customReminderFromMinutes(customMinutes)
        AlertDialog(
            onDismissRequest = { customReminderOpen = false },
            title = { Text(stringResource(R.string.event_editor_custom_reminder_title)) },
            text = {
                OutlinedTextField(
                    value = customMinutes,
                    onValueChange = { value -> customMinutes = value.filter(Char::isDigit).take(6) },
                    label = { Text(stringResource(R.string.event_editor_custom_reminder_minutes)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        customReminder?.let { reminder ->
                            if (reminder !in draft.reminders) onUpdateDraft(draft.copy(reminders = draft.reminders + reminder))
                        }
                        customReminderOpen = false
                    },
                    enabled = customReminder != null,
                ) { Text(stringResource(R.string.event_editor_add)) }
            },
            dismissButton = { TextButton(onClick = { customReminderOpen = false }) { Text(stringResource(R.string.event_editor_cancel)) } },
        )
    }
}

@Composable
private fun DateTimeRow(
    label: String,
    draft: EventDraft,
    start: Boolean,
    zoneId: ZoneId,
    onUpdateDraft: (EventDraft) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val date = if (draft.isAllDay) {
        if (start) draft.startDate else draft.endDateExclusive
    } else {
        (if (start) draft.startInstant else draft.endInstant)?.atZone(zoneId)?.toLocalDate()
    } ?: LocalDate.now(zoneId)
    val time = (if (start) draft.startInstant else draft.endInstant)?.atZone(zoneId)?.toLocalTime() ?: LocalTime.NOON
    val dateText = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    val timeText = time.format(DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"))
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label)
        Row {
            TextButton(onClick = {
                DatePickerDialog(context, { _, year, month, day ->
                    onUpdateDraft(draft.withDate(start, LocalDate.of(year, month + 1, day), zoneId))
                }, date.year, date.monthValue - 1, date.dayOfMonth).show()
            }) { Text(dateText) }
            if (!draft.isAllDay) TextButton(onClick = {
                TimePickerDialog(context, { _, hour, minute ->
                    onUpdateDraft(draft.withTime(start, LocalTime.of(hour, minute), zoneId))
                }, time.hour, time.minute, DateFormat.is24HourFormat(context)).show()
            }) { Text(timeText) }
        }
    }
}

private fun EventDraft.changeAllDay(allDay: Boolean, zoneId: ZoneId): EventDraft {
    if (allDay == isAllDay) return this
    return if (allDay) {
        val start = startInstant?.atZone(zoneId)?.toLocalDate() ?: LocalDate.now(zoneId)
        val end = endInstant?.atZone(zoneId)?.toLocalDate()?.let { if (it.isAfter(start)) it else start.plusDays(1) } ?: start.plusDays(1)
        copy(isAllDay = true, startInstant = null, endInstant = null, startDate = start, endDateExclusive = end)
    } else {
        val start = (startDate ?: LocalDate.now(zoneId)).atTime(9, 0).atZone(zoneId).toInstant()
        val endDate = endDateExclusive?.takeIf { it.isAfter(startDate ?: it.minusDays(1)) } ?: (startDate ?: LocalDate.now(zoneId)).plusDays(1)
        val end = endDate.atTime(9, 0).atZone(zoneId).toInstant()
        copy(isAllDay = false, startInstant = start, endInstant = end, startDate = null, endDateExclusive = null)
    }
}

private fun EventDraft.withDate(start: Boolean, date: LocalDate, zoneId: ZoneId): EventDraft = if (isAllDay) {
    if (start) copy(startDate = date) else copy(endDateExclusive = date)
} else {
    val current = (if (start) startInstant else endInstant)?.atZone(zoneId)?.toLocalTime() ?: LocalTime.NOON
    val instant = LocalDateTime.of(date, current).atZone(zoneId).toInstant()
    if (start) copy(startInstant = instant) else copy(endInstant = instant)
}

private fun EventDraft.withTime(start: Boolean, time: LocalTime, zoneId: ZoneId): EventDraft {
    val date = (if (start) startInstant else endInstant)?.atZone(zoneId)?.toLocalDate() ?: LocalDate.now(zoneId)
    val instant = LocalDateTime.of(date, time).atZone(zoneId).toInstant()
    return if (start) copy(startInstant = instant) else copy(endInstant = instant)
}

private fun reminderPresets() = listOf(0L, 5L, 15L, 30L, 60L, 120L, 1440L, 2880L)
    .map { EventReminder.RelativeToStart(Duration.ofMinutes(-it)) }

internal fun customReminderFromMinutes(value: String): EventReminder.RelativeToStart? =
    value.toLongOrNull()?.takeIf { it in 0..525_600 }?.let {
        EventReminder.RelativeToStart(Duration.ofMinutes(-it))
    }

@Composable
private fun reminderLabel(reminder: EventReminder): String = when (reminder) {
    is EventReminder.RelativeToStart -> {
        val minutes = -reminder.offset.toMinutes()
        when (minutes) {
            0L -> stringResource(R.string.event_editor_reminder_at_start)
            60L -> stringResource(R.string.event_editor_reminder_one_hour)
            120L -> stringResource(R.string.event_editor_reminder_two_hours)
            1440L -> stringResource(R.string.event_editor_reminder_one_day)
            2880L -> stringResource(R.string.event_editor_reminder_two_days)
            else -> stringResource(R.string.event_editor_reminder_minutes, minutes)
        }
    }
    is EventReminder.RelativeToEnd -> stringResource(R.string.event_editor_reminder_relative_end)
    is EventReminder.Absolute -> stringResource(R.string.event_editor_reminder_custom)
}

private fun EventEditorError.messageResource(): Int = when (this) {
    EventEditorError.TitleRequired -> R.string.event_editor_error_title
    EventEditorError.InvalidTimeRange -> R.string.event_editor_error_time
    EventEditorError.PremiumRequired -> R.string.event_editor_error_premium
    EventEditorError.SignInRequired -> R.string.event_editor_error_login
    EventEditorError.NotFound -> R.string.event_editor_error_not_found
    EventEditorError.ReadOnly -> R.string.event_editor_error_read_only
    EventEditorError.RecurringUnsupported -> R.string.event_editor_error_recurring
    EventEditorError.Conflict -> R.string.event_editor_error_conflict
    EventEditorError.Network -> R.string.event_editor_error_network
    EventEditorError.Invalid -> R.string.event_editor_error_invalid
    EventEditorError.SavedRefreshPending -> R.string.event_editor_saved_refresh_pending
}

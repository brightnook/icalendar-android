package com.brightnook.nookcalendar.ui.main

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.brightnook.nookcalendar.R
import com.brightnook.nookcalendar.domain.EventDetail
import com.brightnook.nookcalendar.domain.EventReminder
import java.net.URI
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

sealed interface ExternalAction {
    data class Web(val url: String) : ExternalAction
    data class Telephone(val number: String) : ExternalAction
    data class Geo(val coordinate: String) : ExternalAction
}

const val EVENT_DETAIL_CONTENT_TAG = "event-detail-content"

enum class RecurrenceFrequency { DAILY, WEEKLY, MONTHLY, YEARLY }

data class RecurrenceDescription(
    val frequency: RecurrenceFrequency,
    val interval: Int,
    val days: List<RecurrenceDay>,
)

data class RecurrenceDay(
    val dayOfWeek: DayOfWeek,
    val ordinal: Int?,
)

fun parseRecurrenceRule(rule: String?): RecurrenceDescription? {
    val parts = rule?.split(';') ?: return null
    val frequency = parts.firstOrNull { it.startsWith("FREQ=", ignoreCase = true) }
        ?.substringAfter('=')
        ?.uppercase()
        ?.let { runCatching { RecurrenceFrequency.valueOf(it) }.getOrNull() }
        ?: return null
    val intervalValue = parts.firstOrNull { it.startsWith("INTERVAL=", ignoreCase = true) }
        ?.substringAfter('=')
    val interval = if (intervalValue == null) 1 else intervalValue.toIntOrNull()?.takeIf { it >= 1 }
        ?: return null
    val encodedDays = parts.firstOrNull { it.startsWith("BYDAY=", ignoreCase = true) }
        ?.substringAfter('=')
        ?.split(',')
        .orEmpty()
    val days = encodedDays.map { recurrenceDay(it) ?: return null }
    if (frequency in setOf(RecurrenceFrequency.DAILY, RecurrenceFrequency.WEEKLY) &&
        days.any { it.ordinal != null }
    ) return null
    return RecurrenceDescription(frequency, interval, days)
}

fun parseExternalAction(value: String?): ExternalAction? {
    val candidate = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
    return when {
        candidate.startsWith("http://", ignoreCase = true) ||
            candidate.startsWith("https://", ignoreCase = true) -> parseWeb(candidate)
        candidate.startsWith("tel:", ignoreCase = true) -> parseTelephone(candidate.substring(4))
        candidate.startsWith("geo:", ignoreCase = true) -> parseGeo(candidate.substring(4))
        else -> null
    }
}

private fun parseWeb(candidate: String): ExternalAction.Web? = try {
    val uri = URI(candidate)
    if (
        uri.scheme?.lowercase() !in setOf("http", "https") ||
        uri.host.isNullOrBlank() ||
        uri.userInfo != null ||
        uri.fragment?.contains('\n') == true
    ) null else ExternalAction.Web(candidate)
} catch (_: Exception) {
    null
}

private fun parseTelephone(number: String): ExternalAction.Telephone? {
    if (!number.matches(Regex("[+0-9(). -]{3,32}")) || number.none(Char::isDigit)) return null
    return ExternalAction.Telephone(number)
}

private fun parseGeo(coordinate: String): ExternalAction.Geo? {
    val parts = coordinate.split(',')
    if (parts.size != 2) return null
    val latitude = parts[0].trim().toDoubleOrNull() ?: return null
    val longitude = parts[1].trim().toDoubleOrNull() ?: return null
    if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
    return ExternalAction.Geo(coordinate)
}

@Composable
fun EventDetailContent(
    detail: EventDetail,
    zoneId: ZoneId,
    onClose: () -> Unit,
    onOpenExternalAction: (ExternalAction) -> Unit,
    contentColor: Color,
    secondaryContentColor: Color,
    surfaceColor: Color,
    actionContentColor: Color,
    premium: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.9f)
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .testTag(EVENT_DETAIL_CONTENT_TAG),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(detail.title, style = MaterialTheme.typography.headlineSmall, color = contentColor)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(10.dp).background(Color(detail.calendarColorArgb), CircleShape))
            Text(detail.calendarName, style = MaterialTheme.typography.titleMedium, color = contentColor)
        }
        DetailRow(stringResource(R.string.calendar_detail_time), detailRange(detail, zoneId), contentColor, secondaryContentColor)
        DetailRow(
            stringResource(R.string.calendar_detail_all_day),
            stringResource(if (detail.isAllDay) R.string.calendar_yes else R.string.calendar_no),
            contentColor,
            secondaryContentColor,
        )
        val allReminderTimes = reminderTimes(detail, zoneId)
        val displayedReminderTimes = if (premium) allReminderTimes else allReminderTimes.take(1)
        val reminderFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(currentLocale())
        DetailRow(
            stringResource(R.string.calendar_detail_reminders),
            if (displayedReminderTimes.isEmpty()) {
                stringResource(R.string.calendar_detail_no_reminder)
            } else {
                displayedReminderTimes.joinToString("\n") { reminderTime ->
                    reminderTime.format(reminderFormatter)
                }
            },
            contentColor,
            secondaryContentColor,
        )
        if (!premium && allReminderTimes.size > displayedReminderTimes.size) {
            Text(
                stringResource(R.string.calendar_detail_reminder_upgrade_hint),
                style = MaterialTheme.typography.bodySmall,
                color = secondaryContentColor,
            )
        }
        detail.location?.takeIf(String::isNotBlank)?.let { location ->
            DetailRow(stringResource(R.string.calendar_detail_location), location, contentColor, secondaryContentColor)
            parseExternalAction(location)?.let { action ->
                TextButton(
                    onClick = { onOpenExternalAction(action) },
                    colors = ButtonDefaults.textButtonColors(contentColor = actionContentColor),
                ) {
                    Text(stringResource(R.string.calendar_open_external))
                }
            }
        }
        detail.notes?.takeIf(String::isNotBlank)?.let { notes ->
            DetailRow(stringResource(R.string.calendar_detail_notes), notes, contentColor, secondaryContentColor)
            parseExternalAction(notes)?.let { action ->
                TextButton(
                    onClick = { onOpenExternalAction(action) },
                    colors = ButtonDefaults.textButtonColors(contentColor = actionContentColor),
                ) {
                    Text(stringResource(R.string.calendar_open_external))
                }
            }
        }
        detail.recurrenceRule?.takeIf(String::isNotBlank)?.let { rule ->
            DetailRow(
                stringResource(R.string.calendar_detail_recurrence),
                humanRecurrence(rule),
                contentColor,
                secondaryContentColor,
            )
        }
        Button(
            onClick = onClose,
            modifier = Modifier.align(Alignment.End),
            colors = ButtonDefaults.buttonColors(
                containerColor = actionContentColor,
                contentColor = surfaceColor,
            ),
        ) {
            Text(stringResource(R.string.calendar_close_detail))
        }
    }
}

internal fun reminderTimes(detail: EventDetail, zoneId: ZoneId): List<java.time.ZonedDateTime> {
    val start = if (detail.isAllDay) {
        detail.startDate?.atStartOfDay(zoneId)
    } else {
        detail.startInstant?.atZone(zoneId)
    } ?: return emptyList()
    val end = if (detail.isAllDay) {
        detail.endDateExclusive?.atStartOfDay(zoneId)
    } else {
        detail.endInstant?.atZone(zoneId)
    } ?: return emptyList()
    return detail.reminders.mapNotNull { reminder ->
        when (reminder) {
            is EventReminder.Absolute -> reminder.instant.atZone(zoneId)
            is EventReminder.RelativeToStart -> start.plus(reminder.offset)
            is EventReminder.RelativeToEnd -> end.plus(reminder.offset)
        }
    }.sorted()
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    contentColor: Color,
    secondaryContentColor: Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = secondaryContentColor)
        Text(value, style = MaterialTheme.typography.bodyLarge, color = contentColor)
    }
}

@Composable
private fun detailRange(detail: EventDetail, zoneId: ZoneId): String {
    val locale = currentLocale()
    val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    if (detail.isAllDay) {
        val start = detail.startDate?.format(dateFormatter).orEmpty()
        val inclusiveEnd = detail.endDateExclusive?.minusDays(1)?.format(dateFormatter).orEmpty()
        return if (start == inclusiveEnd) start else "$start – $inclusiveEnd"
    }
    val timeFormatter = DateTimeFormatter.ofPattern(
        if (DateFormat.is24HourFormat(LocalContext.current)) "HH:mm" else "h:mm a",
        locale,
    )
    val start = detail.startInstant?.atZone(zoneId) ?: return ""
    val end = detail.endInstant?.atZone(zoneId) ?: return ""
    val startText = "${start.format(dateFormatter)} ${start.format(timeFormatter)}"
    val endText = if (start.toLocalDate() == end.toLocalDate()) {
        end.format(timeFormatter)
    } else {
        "${end.format(dateFormatter)} ${end.format(timeFormatter)}"
    }
    return "$startText – $endText"
}

@Composable
private fun humanRecurrence(rule: String): String {
    val locale = currentLocale()
    val recurrence = parseRecurrenceRule(rule) ?: return stringResource(R.string.calendar_recurrence_repeating)
    val frequencyText = when (recurrence.frequency to recurrence.interval) {
        RecurrenceFrequency.DAILY to 1 -> stringResource(R.string.calendar_recurrence_daily)
        RecurrenceFrequency.WEEKLY to 1 -> stringResource(R.string.calendar_recurrence_weekly)
        RecurrenceFrequency.MONTHLY to 1 -> stringResource(R.string.calendar_recurrence_monthly)
        RecurrenceFrequency.YEARLY to 1 -> stringResource(R.string.calendar_recurrence_yearly)
        else -> when (recurrence.frequency) {
            RecurrenceFrequency.DAILY -> pluralStringResource(
                R.plurals.calendar_recurrence_every_n_days,
                recurrence.interval,
                recurrence.interval,
            )
            RecurrenceFrequency.WEEKLY -> pluralStringResource(
                R.plurals.calendar_recurrence_every_n_weeks,
                recurrence.interval,
                recurrence.interval,
            )
            RecurrenceFrequency.MONTHLY -> pluralStringResource(
                R.plurals.calendar_recurrence_every_n_months,
                recurrence.interval,
                recurrence.interval,
            )
            RecurrenceFrequency.YEARLY -> pluralStringResource(
                R.plurals.calendar_recurrence_every_n_years,
                recurrence.interval,
                recurrence.interval,
            )
        }
    }
    val dayTexts = mutableListOf<String>()
    for (day in recurrence.days) {
        dayTexts += recurrenceDayText(day, locale)
    }
    val byDay = dayTexts.joinToString(", ").takeIf(String::isNotBlank)
    return if (byDay == null) frequencyText else stringResource(R.string.calendar_recurrence_on, frequencyText, byDay)
}

@Composable
private fun recurrenceDayText(day: RecurrenceDay, locale: java.util.Locale): String {
    val weekday = day.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, locale)
    val ordinal = day.ordinal ?: return weekday
    return when {
        ordinal == 1 -> stringResource(R.string.calendar_recurrence_first_weekday, weekday)
        ordinal == -1 -> stringResource(R.string.calendar_recurrence_last_weekday, weekday)
        ordinal > 0 -> pluralStringResource(
            R.plurals.calendar_recurrence_nth_weekday,
            ordinal,
            ordinal,
            weekday,
        )
        else -> pluralStringResource(
            R.plurals.calendar_recurrence_nth_weekday_from_end,
            -ordinal,
            -ordinal,
            weekday,
        )
    }
}

private fun recurrenceDay(value: String): RecurrenceDay? {
    val match = Regex("^([+-]?\\d{1,2})?(MO|TU|WE|TH|FR|SA|SU)$", RegexOption.IGNORE_CASE)
        .matchEntire(value.trim()) ?: return null
    val ordinal = match.groupValues[1].takeIf(String::isNotEmpty)?.toIntOrNull()
    if (ordinal == 0 || (ordinal != null && ordinal !in -53..53)) return null
    val dayOfWeek = when (match.groupValues[2].uppercase()) {
        "MO" -> DayOfWeek.MONDAY
        "TU" -> DayOfWeek.TUESDAY
        "WE" -> DayOfWeek.WEDNESDAY
        "TH" -> DayOfWeek.THURSDAY
        "FR" -> DayOfWeek.FRIDAY
        "SA" -> DayOfWeek.SATURDAY
        "SU" -> DayOfWeek.SUNDAY
        else -> null
    } ?: return null
    return RecurrenceDay(dayOfWeek, ordinal)
}

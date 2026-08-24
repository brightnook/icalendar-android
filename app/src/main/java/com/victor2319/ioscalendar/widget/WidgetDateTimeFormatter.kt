package com.victor2319.ioscalendar.widget

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

internal object WidgetDateTimeFormatter {
    fun formatDate(date: LocalDate, zoneId: ZoneId, locale: Locale): String =
        date.atStartOfDay(zoneId).format(
            if (locale.language.equals(Locale.JAPANESE.language, ignoreCase = true)) {
                DateTimeFormatter.ofPattern("yyyy年M月d日", locale)
            } else {
                DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
            },
        )

    fun formatTime(instant: Instant, zoneId: ZoneId, locale: Locale, is24Hour: Boolean): String =
        instant.atZone(zoneId).format(DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm a", locale))

    fun formatHeaderDate(date: LocalDate, locale: Locale): String =
        date.format(
            DateTimeFormatter.ofPattern(
                if (locale.language.equals("zh", ignoreCase = true)) "M月d日" else "MMM d",
                locale,
            ),
        )
}

package com.brightnook.nookcalendar.ui.settings

import java.text.DateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale

internal fun formatAutomaticSyncTimestamp(instant: Instant, locale: Locale): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale)
        .format(Date.from(instant))

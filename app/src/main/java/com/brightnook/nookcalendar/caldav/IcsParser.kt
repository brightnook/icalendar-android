package com.brightnook.nookcalendar.caldav

import com.brightnook.nookcalendar.domain.TimeRange
import java.time.ZoneId

interface IcsParser {
    fun parse(ics: String, range: TimeRange, displayZone: ZoneId): ParsedCalendar
}

data class ParsedCalendar(
    val events: List<ParsedEvent>,
    val instances: List<ParsedInstance>,
)

class IcsParseException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

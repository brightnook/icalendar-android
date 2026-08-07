package com.victor2319.ioscalendar.caldav

import com.victor2319.ioscalendar.domain.TimeRange
import java.time.ZoneId

interface IcsParser {
    fun parse(ics: String, range: TimeRange, displayZone: ZoneId): ParsedCalendar
}

data class ParsedCalendar(
    val events: List<ParsedEvent>,
    val instances: List<ParsedInstance>,
)

class IcsParseException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

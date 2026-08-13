package com.victor2319.ioscalendar.caldav

import com.victor2319.ioscalendar.auth.Credentials
import com.victor2319.ioscalendar.domain.TimeRange

interface CalDavGateway {
    suspend fun authenticate(credentials: Credentials): Principal

    suspend fun discoverCalendars(principal: Principal): List<RemoteCalendar>

    /** Fetches a half-open UTC range no longer than 400 days with four-digit UTC years. */
    suspend fun fetchRange(calendar: RemoteCalendar, range: TimeRange): RemoteSnapshot

    suspend fun fetchDelta(calendar: RemoteCalendar, syncToken: String): RemoteDelta
}

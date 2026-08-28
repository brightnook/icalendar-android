package com.brightnook.nookcalendar.caldav

import com.brightnook.nookcalendar.auth.Credentials
import com.brightnook.nookcalendar.domain.TimeRange

interface CalDavGateway {
    suspend fun authenticate(credentials: Credentials): Principal

    suspend fun discoverCalendars(principal: Principal): List<RemoteCalendar>

    /** Fetches a half-open UTC range no longer than 400 days with four-digit UTC years. */
    suspend fun fetchRange(calendar: RemoteCalendar, range: TimeRange): RemoteSnapshot

    suspend fun fetchDelta(calendar: RemoteCalendar, syncToken: String): RemoteDelta
}

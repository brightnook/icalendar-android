package com.brightnook.nookcalendar.caldav

import com.brightnook.nookcalendar.auth.Credentials
import com.brightnook.nookcalendar.domain.TimeRange

interface CalDavGateway {
    suspend fun authenticate(credentials: Credentials): Principal

    suspend fun discoverCalendars(principal: Principal): List<RemoteCalendar>

    /** Fetches a half-open UTC range no longer than 400 days with four-digit UTC years. */
    suspend fun fetchRange(calendar: RemoteCalendar, range: TimeRange): RemoteSnapshot

    suspend fun fetchDelta(calendar: RemoteCalendar, syncToken: String): RemoteDelta

    suspend fun createResource(calendar: RemoteCalendar, href: String, calendarData: String): RemoteResource =
        throw UnsupportedOperationException("Calendar resource creation is not supported")

    suspend fun updateResource(calendar: RemoteCalendar, resource: RemoteResource): RemoteResource =
        throw UnsupportedOperationException("Calendar resource updates are not supported")

    suspend fun deleteResource(calendar: RemoteCalendar, href: String, etag: String): Unit =
        throw UnsupportedOperationException("Calendar resource deletion is not supported")
}

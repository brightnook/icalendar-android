package com.victor2319.ioscalendar.caldav

data class Principal(
    val href: String,
    val sessionId: String,
)

data class RemoteCalendar(
    val id: String,
    val href: String,
    val displayName: String,
    val color: String?,
    val syncToken: String?,
    val sessionId: String,
)

data class RemoteResource(
    val href: String,
    val etag: String?,
    val calendarData: String,
) {
    override fun toString(): String =
        "RemoteResource(href=<redacted>, etag=<redacted>, calendarData=<redacted>)"
}

data class RemoteSnapshot(
    val resources: List<RemoteResource>,
    val syncToken: String?,
)

data class RemoteTombstone(val href: String)

data class RemoteDelta(
    val upserts: List<RemoteResource>,
    val tombstones: List<RemoteTombstone>,
    val syncToken: String?,
)

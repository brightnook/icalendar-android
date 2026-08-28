package com.brightnook.nookcalendar.domain

import java.time.Instant
import java.time.ZoneId

interface ClockProvider {
    val now: Instant
    val zoneId: ZoneId
}

object SystemClockProvider : ClockProvider {
    override val now: Instant get() = Instant.now()
    override val zoneId: ZoneId get() = ZoneId.systemDefault()
}

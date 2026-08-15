package com.victor2319.ioscalendar.domain

data class AppSettings(
    val syncMode: SyncMode = SyncMode.MANUAL,
)

enum class SyncMode {
    MANUAL,
    HOURLY,
    DAILY,
    DISABLED,
}

package com.victor2319.ioscalendar.domain

data class AppSettings(
    val syncMode: SyncMode = SyncMode.MANUAL,
    val weekStart: WeekStart = WeekStart.SUNDAY,
    val language: AppLanguage = AppLanguage.FOLLOW_SYSTEM,
)

enum class SyncMode {
    MANUAL,
    HOURLY,
    DAILY,
    DISABLED,
}

enum class WeekStart {
    SUNDAY,
    MONDAY,
}

/**
 * Kept as a mode rather than a Boolean so future manual language choices can be added without
 * changing the persisted settings shape.
 */
enum class AppLanguage {
    FOLLOW_SYSTEM,
}

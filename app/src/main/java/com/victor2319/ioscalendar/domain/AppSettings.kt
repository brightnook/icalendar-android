package com.victor2319.ioscalendar.domain

data class AppSettings(
    val syncMode: SyncMode = SyncMode.MANUAL,
    val weekStart: WeekStart = WeekStart.SUNDAY,
    val language: AppLanguage = AppLanguage.FOLLOW_SYSTEM,
    val theme: ThemeSettings = ThemeSettings(),
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

enum class AppLanguage {
    FOLLOW_SYSTEM,
    CHINESE,
    ENGLISH,
    JAPANESE,
}

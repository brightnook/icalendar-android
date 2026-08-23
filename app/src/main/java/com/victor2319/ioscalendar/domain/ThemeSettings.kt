package com.victor2319.ioscalendar.domain

enum class ThemeBackgroundMode {
    WHITE,
    BLACK,
    IMAGE,
}

data class ThemeSettings(
    val mode: ThemeBackgroundMode = ThemeBackgroundMode.WHITE,
    val assetKey: String? = null,
    val applyToWidget: Boolean = false,
)

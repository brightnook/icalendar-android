package com.brightnook.nookcalendar.domain

enum class ThemeBackgroundMode {
    WHITE,
    BLACK,
    IMAGE,
}

data class ThemeSettings(
    val mode: ThemeBackgroundMode = ThemeBackgroundMode.WHITE,
    val assetKey: String? = null,
    val applyToWidget: Boolean = false,
    val wallpaperFocusX: Float = 0f,
    val wallpaperFocusY: Float = 0f,
    val wallpaperScale: Float = 1f,
)

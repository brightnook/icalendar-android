package com.brightnook.nookcalendar.widget

import com.brightnook.nookcalendar.domain.ThemeBackgroundMode
import com.brightnook.nookcalendar.domain.ThemeSettings
import java.io.File

internal enum class WidgetThemeMode {
    LIGHT,
    BLACK,
    IMAGE,
}

internal data class WidgetThemePresentation(
    val mode: WidgetThemeMode,
    val backgroundColor: Int,
    val contentOverlayColor: Int,
    val primaryTextColor: Int,
    val secondaryTextColor: Int,
    val dateGroupBackgroundColor: Int,
    val dateGroupTextColor: Int,
    val eventTitleBackgroundColor: Int,
    val imageSource: File?,
)

internal fun widgetThemePresentation(
    theme: ThemeSettings,
    imageSource: File?,
): WidgetThemePresentation {
    if (!theme.applyToWidget) return lightWidgetThemePresentation()

    return when (theme.mode) {
        ThemeBackgroundMode.WHITE -> lightWidgetThemePresentation()
        ThemeBackgroundMode.BLACK -> darkWidgetThemePresentation(WidgetThemeMode.BLACK, imageSource = null)
        ThemeBackgroundMode.IMAGE -> {
            if (theme.assetKey.isNullOrBlank() || imageSource == null) {
                lightWidgetThemePresentation()
            } else {
                darkWidgetThemePresentation(WidgetThemeMode.IMAGE, imageSource)
            }
        }
    }
}

internal fun renderableWidgetTheme(
    presentation: WidgetThemePresentation,
    imagePreviewAvailable: Boolean,
): WidgetThemePresentation = if (
    presentation.mode == WidgetThemeMode.IMAGE && !imagePreviewAvailable
) {
    lightWidgetThemePresentation()
} else {
    presentation
}

internal fun widgetThemePresentationForMode(mode: WidgetThemeMode): WidgetThemePresentation = when (mode) {
    WidgetThemeMode.LIGHT -> lightWidgetThemePresentation()
    WidgetThemeMode.BLACK -> darkWidgetThemePresentation(WidgetThemeMode.BLACK, imageSource = null)
    WidgetThemeMode.IMAGE -> darkWidgetThemePresentation(WidgetThemeMode.IMAGE, imageSource = null)
}

private fun lightWidgetThemePresentation() = WidgetThemePresentation(
    mode = WidgetThemeMode.LIGHT,
    backgroundColor = 0xFFFFFFFF.toInt(),
    contentOverlayColor = 0xFFFFFFFF.toInt(),
    primaryTextColor = 0xFF1A1C1E.toInt(),
    secondaryTextColor = 0xFF5F6368.toInt(),
    dateGroupBackgroundColor = 0xFFEAF6EE.toInt(),
    dateGroupTextColor = 0xFF20633F.toInt(),
    eventTitleBackgroundColor = 0xFFF4FAF6.toInt(),
    imageSource = null,
)

private fun darkWidgetThemePresentation(mode: WidgetThemeMode, imageSource: File?) = WidgetThemePresentation(
    mode = mode,
    backgroundColor = 0xFF121212.toInt(),
    contentOverlayColor = if (mode == WidgetThemeMode.IMAGE) 0xA6000000.toInt() else 0xE6121212.toInt(),
    primaryTextColor = 0xFFFFFFFF.toInt(),
    secondaryTextColor = 0xFFE4E7EC.toInt(),
    dateGroupBackgroundColor = 0xB32A3A32.toInt(),
    dateGroupTextColor = 0xFFA8E6BF.toInt(),
    eventTitleBackgroundColor = 0x991F2933.toInt(),
    imageSource = imageSource,
)

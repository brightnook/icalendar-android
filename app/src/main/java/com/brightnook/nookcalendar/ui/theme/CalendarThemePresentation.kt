package com.brightnook.nookcalendar.ui.theme

import androidx.compose.ui.graphics.Color
import com.brightnook.nookcalendar.domain.ThemeBackgroundMode
import com.brightnook.nookcalendar.domain.ThemeSettings
import java.io.File

data class CalendarThemePresentation(
    val resolvedMode: ThemeBackgroundMode,
    val contentColor: Color,
    val secondaryContentColor: Color,
    val backgroundColor: Color,
    val panelColor: Color,
    val saturdayColor: Color,
    val sundayColor: Color,
    val errorColor: Color,
    val eventDetailContainerColor: Color,
    val eventDetailContentColor: Color,
    val eventDetailSecondaryContentColor: Color,
    val bottomActionContainerColor: Color,
    val useOriginalActionArtwork: Boolean,
    val wallpaperPath: String?,
)

fun calendarThemePresentation(
    theme: ThemeSettings,
    assetFile: File?,
): CalendarThemePresentation {
    val resolvedMode = when (theme.mode) {
        ThemeBackgroundMode.IMAGE -> if (assetFile?.isFile == true) {
            ThemeBackgroundMode.IMAGE
        } else {
            ThemeBackgroundMode.WHITE
        }
        else -> theme.mode
    }

    return when (resolvedMode) {
        ThemeBackgroundMode.WHITE -> CalendarThemePresentation(
            resolvedMode = resolvedMode,
            contentColor = LIGHT_CONTENT,
            secondaryContentColor = LIGHT_SECONDARY_CONTENT,
            backgroundColor = Color.White,
            panelColor = Color.Transparent,
            saturdayColor = LIGHT_SATURDAY,
            sundayColor = LIGHT_SUNDAY,
            errorColor = LIGHT_ERROR,
            eventDetailContainerColor = Color.White,
            eventDetailContentColor = LIGHT_CONTENT,
            eventDetailSecondaryContentColor = LIGHT_SECONDARY_CONTENT,
            bottomActionContainerColor = Color.Transparent,
            useOriginalActionArtwork = true,
            wallpaperPath = null,
        )
        ThemeBackgroundMode.BLACK -> CalendarThemePresentation(
            resolvedMode = resolvedMode,
            contentColor = Color.White,
            secondaryContentColor = DARK_SECONDARY_CONTENT,
            backgroundColor = DARK_BACKGROUND,
            panelColor = DARK_PANEL,
            saturdayColor = DARK_SATURDAY,
            sundayColor = DARK_SUNDAY,
            errorColor = DARK_ERROR,
            eventDetailContainerColor = DARK_DETAIL_SURFACE,
            eventDetailContentColor = Color.White,
            eventDetailSecondaryContentColor = DARK_SECONDARY_CONTENT,
            bottomActionContainerColor = BLACK_ACTION_SURFACE,
            useOriginalActionArtwork = false,
            wallpaperPath = null,
        )
        ThemeBackgroundMode.IMAGE -> CalendarThemePresentation(
            resolvedMode = resolvedMode,
            contentColor = Color.White,
            secondaryContentColor = IMAGE_SECONDARY_CONTENT,
            backgroundColor = DARK_BACKGROUND,
            panelColor = IMAGE_PANEL,
            saturdayColor = IMAGE_SATURDAY,
            sundayColor = IMAGE_SUNDAY,
            errorColor = IMAGE_ERROR,
            eventDetailContainerColor = DARK_DETAIL_SURFACE,
            eventDetailContentColor = Color.White,
            eventDetailSecondaryContentColor = DARK_SECONDARY_CONTENT,
            bottomActionContainerColor = IMAGE_ACTION_SURFACE,
            useOriginalActionArtwork = false,
            wallpaperPath = assetFile!!.absolutePath,
        )
    }
}

private val LIGHT_CONTENT = Color(0xFF1D1B20)
private val LIGHT_SECONDARY_CONTENT = Color(0xFF49454F)
private val DARK_SECONDARY_CONTENT = Color(0xFFD0D5DD)
private val IMAGE_SECONDARY_CONTENT = Color(0xFFE4E7EC)
private val DARK_BACKGROUND = Color(0xFF080A0D)
private val DARK_PANEL = Color(0xD9111318)
private val IMAGE_PANEL = Color(0xA5101216)
private val DARK_DETAIL_SURFACE = Color(0xFF111318)
private val BLACK_ACTION_SURFACE = Color(0xD8111318)
private val IMAGE_ACTION_SURFACE = Color(0xB31A1F2A)
private val LIGHT_SATURDAY = Color(0xFF0057B8)
private val LIGHT_SUNDAY = Color(0xFFC62828)
private val DARK_SATURDAY = Color(0xFF64B5F6)
private val DARK_SUNDAY = Color(0xFFFF8A80)
private val IMAGE_SATURDAY = Color(0xFFD0EBFF)
private val IMAGE_SUNDAY = Color(0xFFFFE0DD)
private val LIGHT_ERROR = Color(0xFFB3261E)
private val DARK_ERROR = Color(0xFFFFB4AB)
private val IMAGE_ERROR = Color(0xFFFFE5E2)

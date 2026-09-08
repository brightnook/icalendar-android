package com.brightnook.nookcalendar.ui.settings

import com.brightnook.nookcalendar.R
import com.brightnook.nookcalendar.domain.ThemeBackgroundMode
import com.brightnook.nookcalendar.domain.ThemeSettings
import com.brightnook.nookcalendar.ui.theme.calendarThemePresentation
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsActionArtworkTest {

    @Test
    fun closeControlUsesTransparentArtworkOnDarkAndImageThemes() {
        val dark = calendarThemePresentation(ThemeSettings(ThemeBackgroundMode.BLACK), null)
        val image = calendarThemePresentation(ThemeSettings(ThemeBackgroundMode.IMAGE, "wallpaper.png"), fakeImageFile())

        assertEquals(R.drawable.ic_action_close_transparent, settingsCloseArtwork(dark))
        assertEquals(R.drawable.ic_action_close_transparent, settingsCloseArtwork(image))
    }

    @Test
    fun closeControlKeepsOriginalArtworkOnWhiteTheme() {
        val white = calendarThemePresentation(ThemeSettings(ThemeBackgroundMode.WHITE), null)

        assertEquals(R.drawable.ic_action_close, settingsCloseArtwork(white))
    }

    private fun fakeImageFile() = kotlin.io.path.createTempFile("settings-close", ".png").toFile().also {
        it.writeBytes(byteArrayOf())
        it.deleteOnExit()
    }
}

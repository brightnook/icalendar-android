package com.brightnook.nookcalendar.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import com.brightnook.nookcalendar.domain.SyncMode
import com.brightnook.nookcalendar.domain.ThemeBackgroundMode
import com.brightnook.nookcalendar.domain.ThemeSettings
import com.brightnook.nookcalendar.ui.theme.CalendarThemePresentation
import com.brightnook.nookcalendar.ui.theme.calendarThemePresentation
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsPresentationPolicyTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun syncPickerOffersEveryPersistedFrequency() {
        assertEquals(SyncMode.entries, syncModePickerOptions())
    }

    @Test
    fun blackThemeSettingsDoesNotRenderWhiteSectionSurface() {
        val presentation = calendarThemePresentation(
            theme = ThemeSettings(mode = ThemeBackgroundMode.BLACK),
            assetFile = null,
        )
        val settings = settingsSurfacePresentation(presentation)

        renderSettings(presentation)

        composeRule.onNodeWithTag("settings-section-account").assertIsDisplayed()
        assertEquals(1f, settings.sectionColor.alpha)
        assertTrue(settings.sectionColor.luminance() < 0.1f)
        assertTrue(settings.containerColor.luminance() < 0.1f)
    }

    @Test
    fun imageThemeKeepsSettingsControlsReadableOnDarkTranslucentCards() {
        val asset = Files.createTempFile("settings-theme", ".png").toFile()
        try {
            val presentation = calendarThemePresentation(
                theme = ThemeSettings(ThemeBackgroundMode.IMAGE, asset.name),
                assetFile = asset,
            )
            val settings = settingsSurfacePresentation(presentation)

            renderSettings(presentation)

            composeRule.onNodeWithTag("settings-section-theme").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithTag("theme-white").assertIsDisplayed()
            composeRule.onNodeWithTag("theme-black").assertIsDisplayed()
            assertTrue(settings.sectionColor.alpha in 0.55f..0.85f)
            assertTrue(settings.sectionColor.luminance() < 0.1f)
            val cardOverBrightWallpaper = settings.sectionColor.compositeOver(Color.White)
            assertTrue(contrastRatio(settings.contentColor, cardOverBrightWallpaper) >= 4.5f)
            assertEquals(asset.absolutePath, settings.backgroundWallpaperPath)
        } finally {
            asset.delete()
        }
    }

    private fun renderSettings(presentation: CalendarThemePresentation) {
        composeRule.setContent {
            MaterialTheme {
                SettingsScreen(
                    state = SettingsUiState(accountLoading = false),
                    themePresentation = presentation,
                    onClose = {},
                    onSelectSyncMode = {},
                    onSetCalendarVisible = { _, _ -> },
                    onClearHistory = {},
                    onClearCache = {},
                    onLogout = {},
                    onDismissConfirmation = {},
                    onConfirmDestructiveAction = {},
                )
            }
        }
    }

    private fun contrastRatio(first: Color, second: Color): Float {
        val lighter = maxOf(first.luminance(), second.luminance())
        val darker = minOf(first.luminance(), second.luminance())
        return (lighter + 0.05f) / (darker + 0.05f)
    }
}

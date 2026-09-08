package com.brightnook.nookcalendar.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
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
import com.brightnook.nookcalendar.ui.theme.CALENDAR_WALLPAPER_TAG
import com.brightnook.nookcalendar.ui.theme.calendarThemePresentation
import java.awt.image.BufferedImage
import java.nio.file.Files
import javax.imageio.ImageIO
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

        renderSettings(presentation)

        val rootColor = composeRule.onNodeWithTag(SETTINGS_ROOT_SURFACE_TAG)
            .fetchSemanticsNode().config[SettingsContainerColorSemantics]
        val sectionColor = composeRule.onNodeWithTag("settings-section-account")
            .assertIsDisplayed()
            .fetchSemanticsNode().config[SettingsSectionColorSemantics]

        assertEquals(presentation.settingsContainerColor, rootColor)
        assertEquals(presentation.settingsSectionColor, sectionColor)
        assertEquals(1f, sectionColor.alpha)
        assertTrue(sectionColor.luminance() < 0.1f)
        assertTrue(rootColor.luminance() < 0.1f)
    }

    @Test
    fun imageThemeRecomposesSettingsWithReadableControlsOnDarkTranslucentCards() {
        val asset = createValidThemeImage()
        try {
            val blackPresentation = calendarThemePresentation(
                theme = ThemeSettings(ThemeBackgroundMode.BLACK),
                assetFile = null,
            )
            val imagePresentation = calendarThemePresentation(
                theme = ThemeSettings(ThemeBackgroundMode.IMAGE, asset.name),
                assetFile = asset,
            )
            val currentPresentation = mutableStateOf(blackPresentation)

            composeRule.setContent {
                MaterialTheme {
                    SettingsScreen(
                        state = SettingsUiState(accountLoading = false),
                        themePresentation = currentPresentation.value,
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
            val blackSectionColor = composeRule.onNodeWithTag("settings-section-account")
                .fetchSemanticsNode().config[SettingsSectionColorSemantics]

            composeRule.runOnIdle { currentPresentation.value = imagePresentation }

            composeRule.onNodeWithTag("settings-section-theme").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithTag("theme-white").assertIsDisplayed()
            composeRule.onNodeWithTag("theme-black").assertIsDisplayed()
            composeRule.onNodeWithTag(CALENDAR_WALLPAPER_TAG).assertIsDisplayed()

            val imageSectionConfig = composeRule.onNodeWithTag("settings-section-theme")
                .fetchSemanticsNode().config
            val imageSectionColor = imageSectionConfig[SettingsSectionColorSemantics]
            val imageContentColor = imageSectionConfig[SettingsContentColorSemantics]
            val actionColor = composeRule.onNodeWithTag("theme-black")
                .fetchSemanticsNode().config[SettingsActionColorSemantics]

            assertEquals(blackPresentation.settingsSectionColor, blackSectionColor)
            assertEquals(imagePresentation.settingsSectionColor, imageSectionColor)
            assertEquals(imagePresentation.contentColor, imageContentColor)
            assertEquals(imagePresentation.actionContentColor, actionColor)
            assertTrue(imageSectionColor.alpha in 0.55f..0.85f)
            val cardOverBrightWallpaper = imageSectionColor.compositeOver(Color.White)
            assertTrue(contrastRatio(imageContentColor, cardOverBrightWallpaper) >= 4.5f)
        } finally {
            asset.delete()
        }
    }

    @Test
    fun imageThemeProtectsTheSettingsHeaderOverBrightWallpaper() {
        val asset = createValidThemeImage()
        try {
            val presentation = calendarThemePresentation(
                theme = ThemeSettings(ThemeBackgroundMode.IMAGE, asset.name),
                assetFile = asset,
            )

            renderSettings(presentation)

            val headerColor = composeRule.onNodeWithTag("settings-header")
                .assertIsDisplayed()
                .fetchSemanticsNode().config[SettingsSectionColorSemantics]
            val headerOverBrightWallpaper = headerColor.compositeOver(Color.White)

            assertEquals(presentation.settingsSectionColor, headerColor)
            assertTrue(contrastRatio(presentation.contentColor, headerOverBrightWallpaper) >= 4.5f)
            assertTrue(contrastRatio(presentation.actionContentColor, headerOverBrightWallpaper) >= 4.5f)
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

    private fun createValidThemeImage() = Files.createTempFile("settings-theme", ".png").toFile().also { file ->
        val image = BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until image.height) {
            for (x in 0 until image.width) image.setRGB(x, y, 0xFFFFFFFF.toInt())
        }
        check(ImageIO.write(image, "png", file))
    }
}

package com.brightnook.nookcalendar.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.brightnook.nookcalendar.domain.ThemeBackgroundMode
import com.brightnook.nookcalendar.domain.ThemeSettings
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarThemePresentationTest {
    @Test
    fun blackThemeUsesLightContentOverDarkTranslucentPanels() {
        val presentation = calendarThemePresentation(
            theme = ThemeSettings(mode = ThemeBackgroundMode.BLACK),
            assetFile = null,
        )

        assertEquals(ThemeBackgroundMode.BLACK, presentation.resolvedMode)
        assertEquals(Color.White, presentation.contentColor)
        assertTrue(presentation.panelColor.alpha in 0f..0.99f)
        assertTrue(presentation.panelColor.luminance() < 0.1f)
        assertNull(presentation.wallpaperPath)
    }

    @Test
    fun existingImageUsesItsAbsolutePathAndImageContrastColors() {
        val asset = Files.createTempFile("calendar-theme", ".gif").toFile()
        try {
            val presentation = calendarThemePresentation(
                theme = ThemeSettings(ThemeBackgroundMode.IMAGE, asset.name),
                assetFile = asset,
            )

            assertEquals(ThemeBackgroundMode.IMAGE, presentation.resolvedMode)
            assertEquals(asset.absolutePath, presentation.wallpaperPath)
            assertEquals(Color.White, presentation.contentColor)
            assertTrue(presentation.panelColor.alpha in 0f..0.99f)
        } finally {
            asset.delete()
        }
    }

    @Test
    fun missingImageFallsBackToWhitePresentation() {
        val presentation = calendarThemePresentation(
            theme = ThemeSettings(ThemeBackgroundMode.IMAGE, "missing.gif"),
            assetFile = null,
        )

        assertEquals(ThemeBackgroundMode.WHITE, presentation.resolvedMode)
        assertNull(presentation.wallpaperPath)
    }

    @Test
    fun weekendColorsRemainDistinctAndReadableInEveryResolvedTheme() {
        ThemeBackgroundMode.entries.forEach { mode ->
            val asset = if (mode == ThemeBackgroundMode.IMAGE) {
                Files.createTempFile("calendar-theme", ".png").toFile()
            } else {
                null
            }
            try {
                val presentation = calendarThemePresentation(ThemeSettings(mode), asset)
                val background = when (presentation.resolvedMode) {
                    ThemeBackgroundMode.WHITE -> Color.White
                    ThemeBackgroundMode.BLACK -> compositeOver(
                        presentation.panelColor,
                        presentation.backgroundColor,
                    )
                    ThemeBackgroundMode.IMAGE -> compositeOver(presentation.panelColor, Color.White)
                }

                assertNotEquals(presentation.contentColor, presentation.saturdayColor)
                assertNotEquals(presentation.contentColor, presentation.sundayColor)
                assertNotEquals(presentation.saturdayColor, presentation.sundayColor)
                assertTrue(contrastRatio(presentation.saturdayColor, background) >= 4.5f)
                assertTrue(contrastRatio(presentation.sundayColor, background) >= 4.5f)
            } finally {
                asset?.delete()
            }
        }
    }

    @Test
    fun darkAndImageThemesUseBrighterWeekendColorsThanWhiteTheme() {
        val white = calendarThemePresentation(ThemeSettings(ThemeBackgroundMode.WHITE), null)
        val black = calendarThemePresentation(ThemeSettings(ThemeBackgroundMode.BLACK), null)

        assertTrue(black.saturdayColor.luminance() > white.saturdayColor.luminance())
        assertTrue(black.sundayColor.luminance() > white.sundayColor.luminance())
    }

    @Test
    fun whiteThemePreservesTheLightMaterialErrorColor() {
        val presentation = calendarThemePresentation(ThemeSettings(ThemeBackgroundMode.WHITE), null)

        assertEquals(Color(0xFFB3261E), presentation.errorColor)
    }

    @Test
    fun blackAndImageThemesUseReadableErrorTextOverTheirPanels() {
        val asset = Files.createTempFile("calendar-theme", ".png").toFile()
        try {
            listOf(
                calendarThemePresentation(ThemeSettings(ThemeBackgroundMode.BLACK), null),
                calendarThemePresentation(ThemeSettings(ThemeBackgroundMode.IMAGE, asset.name), asset),
            ).forEach { presentation ->
                val background = if (presentation.resolvedMode == ThemeBackgroundMode.IMAGE) {
                    compositeOver(presentation.panelColor, Color.White)
                } else {
                    compositeOver(presentation.panelColor, presentation.backgroundColor)
                }

                assertTrue(contrastRatio(presentation.errorColor, background) >= 4.5f)
            }
        } finally {
            asset.delete()
        }
    }

    @Test
    fun darkAndImageThemesKeepEventDetailsOnAHighContrastDarkSurface() {
        val asset = Files.createTempFile("calendar-theme", ".png").toFile()
        try {
            val white = calendarThemePresentation(ThemeSettings(ThemeBackgroundMode.WHITE), null)
            val darkPresentations = listOf(
                calendarThemePresentation(ThemeSettings(ThemeBackgroundMode.BLACK), null),
                calendarThemePresentation(ThemeSettings(ThemeBackgroundMode.IMAGE, asset.name), asset),
            )

            assertTrue(white.eventDetailContainerColor.luminance() > 0.9f)
            darkPresentations.forEach { presentation ->
                assertTrue(presentation.eventDetailContainerColor.luminance() < 0.1f)
                assertTrue(
                    contrastRatio(
                        presentation.eventDetailContentColor,
                        presentation.eventDetailContainerColor,
                    ) >= 7f,
                )
                assertTrue(
                    contrastRatio(
                        presentation.eventDetailSecondaryContentColor,
                        presentation.eventDetailContainerColor,
                    ) >= 4.5f,
                )
            }
        } finally {
            asset.delete()
        }
    }

    @Test
    fun imageCalendarPanelLetsWallpaperShowThroughWithoutLosingPrimaryOrSecondaryTextContrast() {
        val asset = Files.createTempFile("calendar-theme", ".png").toFile()
        try {
            val presentation = calendarThemePresentation(
                ThemeSettings(ThemeBackgroundMode.IMAGE, asset.name),
                asset,
            )
            val panelOverBrightWallpaper = compositeOver(presentation.panelColor, Color.White)

            assertTrue(presentation.panelColor.alpha in 0.55f..0.65f)
            assertTrue(contrastRatio(presentation.contentColor, panelOverBrightWallpaper) >= 4.5f)
            assertTrue(contrastRatio(presentation.secondaryContentColor, panelOverBrightWallpaper) >= 4.5f)
        } finally {
            asset.delete()
        }
    }

    @Test
    fun bottomActionsUseThemeSpecificSurfacesAndDropPaleArtworkBackdropsInDarkModes() {
        val asset = Files.createTempFile("calendar-theme", ".png").toFile()
        try {
            val white = calendarThemePresentation(ThemeSettings(ThemeBackgroundMode.WHITE), null)
            val black = calendarThemePresentation(ThemeSettings(ThemeBackgroundMode.BLACK), null)
            val image = calendarThemePresentation(
                ThemeSettings(ThemeBackgroundMode.IMAGE, asset.name),
                asset,
            )

            assertEquals(Color.Transparent, white.bottomActionContainerColor)
            assertTrue(white.useOriginalActionArtwork)
            listOf(black, image).forEach { presentation ->
                assertTrue(presentation.bottomActionContainerColor.alpha in 0.65f..0.85f)
                assertTrue(presentation.bottomActionContainerColor.luminance() < 0.1f)
                assertTrue(!presentation.useOriginalActionArtwork)
            }
            assertTrue(image.bottomActionContainerColor.alpha < black.bottomActionContainerColor.alpha)
        } finally {
            asset.delete()
        }
    }

    private fun contrastRatio(first: Color, second: Color): Float {
        val lighter = maxOf(first.luminance(), second.luminance())
        val darker = minOf(first.luminance(), second.luminance())
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    private fun compositeOver(foreground: Color, background: Color): Color {
        val foregroundAlpha = foreground.alpha
        return Color(
            red = foreground.red * foregroundAlpha + background.red * (1f - foregroundAlpha),
            green = foreground.green * foregroundAlpha + background.green * (1f - foregroundAlpha),
            blue = foreground.blue * foregroundAlpha + background.blue * (1f - foregroundAlpha),
        )
    }
}

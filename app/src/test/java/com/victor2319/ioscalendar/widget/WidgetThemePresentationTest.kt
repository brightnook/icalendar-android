package com.victor2319.ioscalendar.widget

import com.victor2319.ioscalendar.domain.ThemeBackgroundMode
import com.victor2319.ioscalendar.domain.ThemeSettings
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetThemePresentationTest {
    @Test
    fun widgetThemeOptOutAlwaysPreservesTheExistingLightAppearance() {
        val presentation = widgetThemePresentation(
            theme = ThemeSettings(
                mode = ThemeBackgroundMode.BLACK,
                assetKey = "imported.gif",
                applyToWidget = false,
            ),
            imageSource = File("ignored.gif"),
        )

        assertEquals(WidgetThemeMode.LIGHT, presentation.mode)
        assertEquals(0xFFFFFFFF.toInt(), presentation.backgroundColor)
        assertEquals(0xFF1A1C1E.toInt(), presentation.primaryTextColor)
        assertEquals(0xFF5F6368.toInt(), presentation.secondaryTextColor)
        assertNull(presentation.imageSource)
    }

    @Test
    fun optedInBlackThemeUsesAReadableDarkTranslucentContentOverlay() {
        val presentation = widgetThemePresentation(
            theme = ThemeSettings(mode = ThemeBackgroundMode.BLACK, applyToWidget = true),
            imageSource = null,
        )

        assertEquals(WidgetThemeMode.BLACK, presentation.mode)
        assertEquals(0xFF121212.toInt(), presentation.backgroundColor)
        assertTrue(presentation.contentOverlayColor.alpha() in 0xD0..0xF0)
        assertTrue(contrastRatio(presentation.primaryTextColor, presentation.backgroundColor) >= 7.0)
        assertTrue(contrastRatio(presentation.secondaryTextColor, presentation.backgroundColor) >= 4.5)
        assertNull(presentation.imageSource)
    }

    @Test
    fun optedInImageThemeUsesItsStaticPreviewSourceBehindAReadableOverlay() {
        val source = File("imported-animated.webp")
        val presentation = widgetThemePresentation(
            theme = ThemeSettings(
                mode = ThemeBackgroundMode.IMAGE,
                assetKey = source.name,
                applyToWidget = true,
            ),
            imageSource = source,
        )

        assertEquals(WidgetThemeMode.IMAGE, presentation.mode)
        assertSame(source, presentation.imageSource)
        assertTrue(presentation.contentOverlayColor.alpha() in 0x90..0xC0)
        val overlayOverWhite = compositeOver(
            foreground = presentation.contentOverlayColor,
            background = 0xFFFFFFFF.toInt(),
        )
        assertTrue(contrastRatio(presentation.primaryTextColor, overlayOverWhite) >= 4.5)
        assertTrue(contrastRatio(presentation.secondaryTextColor, overlayOverWhite) >= 4.5)
    }

    @Test
    fun optedInImageThemeWithoutAnImportedAssetFallsBackToLight() {
        val presentation = widgetThemePresentation(
            theme = ThemeSettings(
                mode = ThemeBackgroundMode.IMAGE,
                assetKey = "missing.png",
                applyToWidget = true,
            ),
            imageSource = null,
        )

        assertEquals(WidgetThemeMode.LIGHT, presentation.mode)
        assertEquals(0xFFFFFFFF.toInt(), presentation.backgroundColor)
        assertNull(presentation.imageSource)
    }

    @Test
    fun imageThemeFallsBackToLightWhenItsStaticPreviewCannotBeRendered() {
        val selected = widgetThemePresentation(
            theme = ThemeSettings(
                mode = ThemeBackgroundMode.IMAGE,
                assetKey = "unreadable.gif",
                applyToWidget = true,
            ),
            imageSource = File("unreadable.gif"),
        )

        val presentation = renderableWidgetTheme(selected, imagePreviewAvailable = false)

        assertEquals(WidgetThemeMode.LIGHT, presentation.mode)
        assertEquals(0xFFFFFFFF.toInt(), presentation.backgroundColor)
        assertNull(presentation.imageSource)
    }

    @Test
    fun imageThemeRemainsSelectedWhenItsStaticPreviewIsAvailable() {
        val selected = widgetThemePresentation(
            theme = ThemeSettings(
                mode = ThemeBackgroundMode.IMAGE,
                assetKey = "preview.png",
                applyToWidget = true,
            ),
            imageSource = File("preview.png"),
        )

        val presentation = renderableWidgetTheme(selected, imagePreviewAvailable = true)

        assertSame(selected, presentation)
    }
}

private fun Int.alpha(): Int = ushr(24) and 0xFF

private fun compositeOver(foreground: Int, background: Int): Int {
    val alpha = foreground.alpha() / 255.0
    fun channel(shift: Int): Int {
        val foregroundChannel = foreground ushr shift and 0xFF
        val backgroundChannel = background ushr shift and 0xFF
        return (foregroundChannel * alpha + backgroundChannel * (1.0 - alpha)).toInt()
    }
    return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
}

private fun contrastRatio(first: Int, second: Int): Double {
    val lighter = maxOf(relativeLuminance(first), relativeLuminance(second))
    val darker = minOf(relativeLuminance(first), relativeLuminance(second))
    return (lighter + 0.05) / (darker + 0.05)
}

private fun relativeLuminance(color: Int): Double {
    fun channel(shift: Int): Double {
        val value = (color ushr shift and 0xFF) / 255.0
        return if (value <= 0.04045) value / 12.92 else Math.pow((value + 0.055) / 1.055, 2.4)
    }
    return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
}

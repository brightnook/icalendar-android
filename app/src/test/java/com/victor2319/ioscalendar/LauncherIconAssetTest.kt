package com.victor2319.ioscalendar

import java.io.File
import javax.imageio.ImageIO
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LauncherIconAssetTest {
    @Test
    fun foregroundArtworkStaysInsideTheAdaptiveIconSafeZone() {
        val bitmap = ImageIO.read(
            File("src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.png"),
        )
        val safeZoneStart = 84
        val safeZoneEnd = 348
        var minX = bitmap.width
        var minY = bitmap.height
        var maxX = -1
        var maxY = -1

        assertEquals(432, bitmap.width)
        assertEquals(432, bitmap.height)
        assertTrue(bitmap.colorModel.hasAlpha(), "Foreground must preserve a transparent outer area")

        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                if ((bitmap.getRGB(x, y) ushr 24) != 0) {
                    minX = minOf(minX, x)
                    minY = minOf(minY, y)
                    maxX = maxOf(maxX, x)
                    maxY = maxOf(maxY, y)
                }
            }
        }

        assertTrue(minX >= safeZoneStart, "Artwork begins at $minX, outside the safe zone")
        assertTrue(minY >= safeZoneStart, "Artwork begins at $minY, outside the safe zone")
        assertTrue(maxX < safeZoneEnd, "Artwork ends at $maxX, outside the safe zone")
        assertTrue(maxY < safeZoneEnd, "Artwork ends at $maxY, outside the safe zone")

        val redCalendarArtwork = bitmap.getRGB(100, 120)
        val alpha = redCalendarArtwork ushr 24 and 0xff
        val red = redCalendarArtwork ushr 16 and 0xff
        val green = redCalendarArtwork ushr 8 and 0xff
        val blue = redCalendarArtwork and 0xff
        assertTrue(alpha > 0, "New calendar artwork must occupy the adaptive-icon safe zone")
        assertTrue(red > green * 1.2 && red > blue * 1.2, "New foreground must retain the supplied red calendar art")
    }
}

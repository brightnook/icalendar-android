package com.brightnook.nookcalendar.theme

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WidgetThemePreviewTest {
    private lateinit var directory: File

    @Before
    fun setUp() {
        directory = File(System.getProperty("java.io.tmpdir"), "widget-preview-${UUID.randomUUID()}")
        assertTrue(directory.mkdirs())
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun previewIsPngAndDoesNotExceedBounds() {
        // Catches preview generation retaining source dimensions or writing a non-PNG payload.
        val source = createPng(File(directory, "source.png"), 800, 400)
        val destination = File(directory, "preview.bin")

        assertTrue(createWidgetThemePreview(source, destination, 200, 200))

        assertArrayEquals(
            byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A),
            destination.readBytes().copyOfRange(0, 8),
        )
        val preview = BitmapFactory.decodeFile(destination.path)
        assertEquals(200, preview.width)
        assertEquals(100, preview.height)
        preview.recycle()
    }

    @Test
    fun previewFailureDoesNotReplaceExistingDestination() {
        // Catches decode or write failure truncating the widget's last usable preview.
        val invalidSource = File(directory, "invalid.gif").apply { writeText("not an image") }
        val destination = File(directory, "preview.png").apply { writeBytes(byteArrayOf(7, 8, 9)) }

        assertFalse(createWidgetThemePreview(invalidSource, destination, 200, 200))

        assertArrayEquals(byteArrayOf(7, 8, 9), destination.readBytes())
    }

    @Test
    fun animatedGifPreviewUsesFirstFrame() {
        // Catches preview extraction advancing to a later GIF frame instead of frame zero.
        val source = AnimatedImageFixtures.writeAnimatedGif(File(directory, "animated.gif"))
        val destination = File(directory, "gif-preview.png")

        assertTrue(createWidgetThemePreview(source, destination, 20, 20))

        assertFirstPixelIsRed(destination)
    }

    @Test
    fun publishFailureDoesNotReplaceExistingDestination() {
        // Catches an atomic publish failure truncating or replacing the widget's last usable preview.
        val source = createPng(File(directory, "publish-source.png"), 20, 10)
        val original = byteArrayOf(11, 12, 13, 14)
        val destination = File(directory, "existing-preview.png").apply { writeBytes(original) }

        assertFalse(
            createWidgetThemePreview(source, destination, 20, 20) { _, _ -> false },
        )

        assertArrayEquals(original, destination.readBytes())
    }

    private fun createPng(file: File, width: Int, height: Int): File {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0xFF884422.toInt())
        try {
            FileOutputStream(file).use { output ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
        } finally {
            bitmap.recycle()
        }
        return file
    }

    private fun assertFirstPixelIsRed(file: File) {
        val color = firstPixel(file)
        run {
            assertTrue("red=${Color.red(color)}", Color.red(color) >= 200)
            assertTrue("green=${Color.green(color)}", Color.green(color) <= 30)
            assertTrue("blue=${Color.blue(color)}", Color.blue(color) <= 30)
        }
    }

    private fun firstPixel(file: File): Int {
        val bitmap = BitmapFactory.decodeFile(file.path)
        return try {
            bitmap.getPixel(0, 0)
        } finally {
            bitmap.recycle()
        }
    }
}

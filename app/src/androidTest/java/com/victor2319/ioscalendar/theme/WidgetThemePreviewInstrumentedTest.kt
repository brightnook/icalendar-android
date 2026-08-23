package com.victor2319.ioscalendar.theme

import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.util.Base64
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetThemePreviewInstrumentedTest {
    private lateinit var testDirectory: File

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        testDirectory = File(context.cacheDir, "widget-theme-preview-test-${System.nanoTime()}")
        assertTrue(testDirectory.mkdirs())
    }

    @After
    fun tearDown() {
        testDirectory.deleteRecursively()
    }

    @Test
    fun animatedGifPreviewUsesFirstFrame() {
        val source = File(testDirectory, "animated.gif").apply {
            writeBytes(animatedGif())
        }
        val destination = File(testDirectory, "preview.png")

        assertTrue(createWidgetThemePreview(source, destination, maxWidthPx = 1, maxHeightPx = 1))

        val preview = BitmapFactory.decodeFile(destination.absolutePath)
        assertEquals(Color.RED, preview.getPixel(0, 0))
        preview.recycle()
    }

    @Test
    fun animatedWebpPreviewUsesFirstFrame() {
        val source = File(testDirectory, "animated.webp").apply {
            writeBytes(animatedWebp())
        }
        val destination = File(testDirectory, "preview.png")
        val expectedFirstPixel = decodeFirstPixel(FIRST_WEBP_FRAME)
        val secondPixel = decodeFirstPixel(SECOND_WEBP_FRAME)
        assertNotEquals("Fixture frames must be visually distinct", expectedFirstPixel, secondPixel)

        assertTrue(createWidgetThemePreview(source, destination, maxWidthPx = 100, maxHeightPx = 100))

        val preview = BitmapFactory.decodeFile(destination.absolutePath)
        assertEquals(expectedFirstPixel, preview.getPixel(0, 0))
        preview.recycle()
    }

    private fun decodeFirstPixel(bytes: ByteArray): Int {
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, _, _ ->
            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE)
        }
        return bitmap.getPixel(0, 0).also { bitmap.recycle() }
    }

    private fun animatedGif(): ByteArray = byteArrayOf(
        0x47, 0x49, 0x46, 0x38, 0x39, 0x61,
        0x01, 0x00, 0x01, 0x00, 0x80.toByte(), 0x00, 0x00,
        0xFF.toByte(), 0x00, 0x00, 0x00, 0x00, 0xFF.toByte(),
        0x21, 0xFF.toByte(), 0x0B, 0x4E, 0x45, 0x54, 0x53, 0x43, 0x41, 0x50, 0x45, 0x32, 0x2E, 0x30,
        0x03, 0x01, 0x00, 0x00, 0x00,
        0x21, 0xF9.toByte(), 0x04, 0x00, 0x0A, 0x00, 0x00, 0x00,
        0x2C, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
        0x02, 0x02, 0x44, 0x01, 0x00,
        0x21, 0xF9.toByte(), 0x04, 0x00, 0x0A, 0x00, 0x00, 0x00,
        0x2C, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
        0x02, 0x02, 0x4C, 0x01, 0x00,
        0x3B,
    )

    private fun animatedWebp(): ByteArray {
        val firstFrame = extractImageChunks(FIRST_WEBP_FRAME)
        val secondFrame = extractImageChunks(SECOND_WEBP_FRAME)
        val chunks = ByteArrayOutputStream().apply {
            writeChunk(
                "VP8X",
                byteArrayOf(
                    0x02, 0x00, 0x00, 0x00,
                    0x63, 0x00, 0x00,
                    0x63, 0x00, 0x00,
                ),
            )
            writeChunk("ANIM", byteArrayOf(0x00, 0x00, 0x00, 0xFF.toByte(), 0x00, 0x00))
            writeChunk("ANMF", animatedFramePayload(firstFrame, width = 100, height = 100))
            writeChunk("ANMF", animatedFramePayload(secondFrame, width = 1, height = 1))
        }.toByteArray()

        return ByteArrayOutputStream().apply {
            writeAscii("RIFF")
            writeLittleEndian32(chunks.size + 4)
            writeAscii("WEBP")
            write(chunks)
        }.toByteArray()
    }

    private fun extractImageChunks(webp: ByteArray): ByteArray {
        check(webp.copyOfRange(0, 4).toString(Charsets.US_ASCII) == "RIFF")
        check(webp.copyOfRange(8, 12).toString(Charsets.US_ASCII) == "WEBP")
        val output = ByteArrayOutputStream()
        var offset = 12
        while (offset + 8 <= webp.size) {
            val fourCc = webp.copyOfRange(offset, offset + 4).toString(Charsets.US_ASCII)
            val size = webp.readLittleEndian32(offset + 4)
            val chunkSize = 8 + size + (size and 1)
            check(offset + chunkSize <= webp.size)
            if (fourCc == "ALPH" || fourCc == "VP8 " || fourCc == "VP8L") {
                output.write(webp, offset, chunkSize)
            }
            offset += chunkSize
        }
        return output.toByteArray().also { check(it.isNotEmpty()) }
    }

    private fun animatedFramePayload(imageChunks: ByteArray, width: Int, height: Int): ByteArray =
        ByteArrayOutputStream().apply {
            writeLittleEndian24(0)
            writeLittleEndian24(0)
            writeLittleEndian24(width - 1)
            writeLittleEndian24(height - 1)
            writeLittleEndian24(100)
            write(0x02)
            write(imageChunks)
        }.toByteArray()

    private fun ByteArrayOutputStream.writeChunk(fourCc: String, data: ByteArray) {
        writeAscii(fourCc)
        writeLittleEndian32(data.size)
        write(data)
        if (data.size and 1 != 0) write(0)
    }

    private fun ByteArrayOutputStream.writeAscii(value: String) {
        write(value.toByteArray(Charsets.US_ASCII))
    }

    private fun ByteArrayOutputStream.writeLittleEndian24(value: Int) {
        write(value and 0xFF)
        write(value ushr 8 and 0xFF)
        write(value ushr 16 and 0xFF)
    }

    private fun ByteArrayOutputStream.writeLittleEndian32(value: Int) {
        write(value and 0xFF)
        write(value ushr 8 and 0xFF)
        write(value ushr 16 and 0xFF)
        write(value ushr 24 and 0xFF)
    }

    private fun ByteArray.readLittleEndian32(offset: Int): Int =
        (this[offset].toInt() and 0xFF) or
            (this[offset + 1].toInt() and 0xFF shl 8) or
            (this[offset + 2].toInt() and 0xFF shl 16) or
            (this[offset + 3].toInt() and 0xFF shl 24)

    private companion object {
        val FIRST_WEBP_FRAME: ByteArray = Base64.getDecoder().decode(
            "UklGRhQAAABXRUJQVlA4TAgAAAAvY8AYEIiICA==",
        )
        val SECOND_WEBP_FRAME: ByteArray = Base64.getDecoder().decode(
            "UklGRlYAAABXRUJQVlA4IDoAAADwAgCdASoBAAEAAEcIhYWIhYSIAgICdaoD+AP6Ag1NGAD+/vNYf/5gZt2KO//mBv/80F4SW6//zLwASUNNVAgAAAB0ZXN0MXgxAA==",
        )
    }
}

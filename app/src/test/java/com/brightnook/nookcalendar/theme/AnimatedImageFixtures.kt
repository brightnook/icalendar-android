package com.brightnook.nookcalendar.theme

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64

internal object AnimatedImageFixtures {
    fun writeAnimatedGif(file: File): File = file.apply {
        writeBytes(
            bytes(
                0x47, 0x49, 0x46, 0x38, 0x39, 0x61,
                0x01, 0x00, 0x01, 0x00, 0x80, 0x00, 0x00,
                0xFF, 0x00, 0x00, 0x00, 0x00, 0xFF,
                0x21, 0xFF, 0x0B, 0x4E, 0x45, 0x54, 0x53, 0x43, 0x41, 0x50, 0x45, 0x32, 0x2E, 0x30,
                0x03, 0x01, 0x00, 0x00, 0x00,
                0x21, 0xF9, 0x04, 0x00, 0x0A, 0x00, 0x00, 0x00,
                0x2C, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
                0x02, 0x02, 0x44, 0x01, 0x00,
                0x21, 0xF9, 0x04, 0x00, 0x0A, 0x00, 0x00, 0x00,
                0x2C, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
                0x02, 0x02, 0x4C, 0x01, 0x00,
                0x3B,
            ),
        )
    }

    fun writeAnimatedWebp(file: File): File {
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

        val webp = ByteArrayOutputStream().apply {
            writeAscii("RIFF")
            writeLittleEndian32(chunks.size + 4)
            writeAscii("WEBP")
            write(chunks)
        }.toByteArray()
        return file.apply { writeBytes(webp) }
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

    private fun bytes(vararg values: Int): ByteArray = values.map(Int::toByte).toByteArray()

    private val FIRST_WEBP_FRAME = Base64.getDecoder().decode(
        "UklGRhQAAABXRUJQVlA4TAgAAAAvY8AYEIiICA==",
    )
    private val SECOND_WEBP_FRAME = Base64.getDecoder().decode(
        "UklGRlYAAABXRUJQVlA4IDoAAADwAgCdASoBAAEAAEcIhYWIhYSIAgICdaoD+AP6Ag1NGAD+/vNYf/5gZt2KO//mBv/80F4SW6//zLwASUNNVAgAAAB0ZXN0MXgxAA==",
    )
}

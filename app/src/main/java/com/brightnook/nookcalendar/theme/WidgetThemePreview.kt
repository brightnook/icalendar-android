package com.brightnook.nookcalendar.theme

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.ByteBuffer
import java.util.UUID
import kotlin.math.roundToInt

fun createWidgetThemePreview(
    source: File,
    destination: File,
    maxWidthPx: Int,
    maxHeightPx: Int,
): Boolean = createWidgetThemePreview(
    source = source,
    destination = destination,
    maxWidthPx = maxWidthPx,
    maxHeightPx = maxHeightPx,
    publishPreview = ::publishPreviewAtomically,
)

internal fun createWidgetThemePreview(
    source: File,
    destination: File,
    maxWidthPx: Int,
    maxHeightPx: Int,
    publishPreview: (temporary: File, destination: File) -> Boolean,
): Boolean {
    if (!source.isFile || maxWidthPx <= 0 || maxHeightPx <= 0) return false

    val bitmap = try {
        val bytes = source.readBytes()
        if (!hasSupportedImageSignature(bytes)) return false
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
            val sourceWidth = info.size.width
            val sourceHeight = info.size.height
            if (sourceWidth <= 0 || sourceHeight <= 0) {
                throw IllegalArgumentException("Image has invalid dimensions")
            }
            val scale = minOf(
                1f,
                maxWidthPx.toFloat() / sourceWidth.toFloat(),
                maxHeightPx.toFloat() / sourceHeight.toFloat(),
            )
            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE)
            if (scale < 1f) {
                decoder.setTargetSize(
                    (sourceWidth * scale).roundToInt().coerceAtLeast(1),
                    (sourceHeight * scale).roundToInt().coerceAtLeast(1),
                )
            }
        }
    } catch (_: IOException) {
        return false
    } catch (_: IllegalArgumentException) {
        return false
    } catch (_: RuntimeException) {
        return false
    }

    val absoluteDestination = destination.absoluteFile
    val parent = absoluteDestination.parentFile ?: run {
        bitmap.recycle()
        return false
    }
    if (!parent.isDirectory && !parent.mkdirs()) {
        bitmap.recycle()
        return false
    }
    val temporary = File(parent, ".${absoluteDestination.name}.${UUID.randomUUID()}.tmp")

    return try {
        val wrotePng = try {
            FileOutputStream(temporary).use { output ->
                val compressed = bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                if (compressed) output.fd.sync()
                compressed
            }
        } catch (_: IOException) {
            false
        } catch (_: RuntimeException) {
            false
        }
        if (!wrotePng) return false

        publishPreview(temporary, absoluteDestination)
    } finally {
        bitmap.recycle()
        temporary.delete()
    }
}

private fun publishPreviewAtomically(temporary: File, destination: File): Boolean = try {
    Files.move(
        temporary.toPath(),
        destination.toPath(),
        StandardCopyOption.ATOMIC_MOVE,
        StandardCopyOption.REPLACE_EXISTING,
    )
    true
} catch (_: IOException) {
    false
} catch (_: RuntimeException) {
    false
}

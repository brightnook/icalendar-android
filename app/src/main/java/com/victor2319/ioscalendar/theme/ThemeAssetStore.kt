package com.victor2319.ioscalendar.theme

import android.content.Context
import android.graphics.ImageDecoder
import android.net.Uri
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.ByteBuffer
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface ThemeAssetStore {
    suspend fun import(uri: Uri): ThemeAssetImport

    suspend fun remove(assetKey: String)

    fun fileFor(assetKey: String): File?
}

sealed interface ThemeAssetImport {
    data class Success(val assetKey: String) : ThemeAssetImport

    data class Failure(val reason: ThemeAssetFailure) : ThemeAssetImport
}

enum class ThemeAssetFailure {
    UNSUPPORTED,
    TOO_LARGE,
    IO,
}

class PrivateThemeAssetStore(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val readValidationBytes: (File) -> ByteArray = File::readBytes,
) : ThemeAssetStore {
    private val applicationContext = context.applicationContext
    private val filesDirectory = applicationContext.filesDir
    private val contentResolver = applicationContext.contentResolver

    override suspend fun import(uri: Uri): ThemeAssetImport = withContext(ioDispatcher) {
        val mimeType = runCatching { contentResolver.getType(uri) }.getOrNull()
        if (mimeType != null && !mimeType.startsWith("image/", ignoreCase = true)) {
            return@withContext ThemeAssetImport.Failure(ThemeAssetFailure.UNSUPPORTED)
        }

        val directory = privateAssetDirectory(create = true)
            ?: return@withContext ThemeAssetImport.Failure(ThemeAssetFailure.IO)
        val temporary = File(directory, ".import-${UUID.randomUUID()}.tmp")

        try {
            when (copyIntoTemporary(uri, temporary)) {
                CopyResult.TOO_LARGE -> return@withContext ThemeAssetImport.Failure(ThemeAssetFailure.TOO_LARGE)
                CopyResult.IO_FAILURE -> return@withContext ThemeAssetImport.Failure(ThemeAssetFailure.IO)
                CopyResult.SUCCESS -> Unit
            }

            when (validateImage(temporary)) {
                ValidationResult.IO_FAILURE -> return@withContext ThemeAssetImport.Failure(ThemeAssetFailure.IO)
                ValidationResult.UNSUPPORTED -> return@withContext ThemeAssetImport.Failure(ThemeAssetFailure.UNSUPPORTED)
                ValidationResult.SUPPORTED -> Unit
            }

            val assetKey = randomAssetKey(uri, mimeType)
            val destination = File(directory, assetKey)
            try {
                Files.move(
                    temporary.toPath(),
                    destination.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: IOException) {
                return@withContext ThemeAssetImport.Failure(ThemeAssetFailure.IO)
            } catch (_: RuntimeException) {
                return@withContext ThemeAssetImport.Failure(ThemeAssetFailure.IO)
            }

            ThemeAssetImport.Success(assetKey)
        } finally {
            temporary.delete()
        }
    }

    override suspend fun remove(assetKey: String) {
        withContext(ioDispatcher) {
            resolvePrivateAsset(assetKey, requireExisting = true)?.delete()
        }
    }

    override fun fileFor(assetKey: String): File? =
        resolvePrivateAsset(assetKey, requireExisting = true)

    private fun copyIntoTemporary(uri: Uri, temporary: File): CopyResult = try {
        val input = contentResolver.openInputStream(uri) ?: return CopyResult.IO_FAILURE
        input.use { source ->
            FileOutputStream(temporary).use { destination ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var totalBytes = 0L
                while (true) {
                    val count = source.read(buffer)
                    if (count < 0) break
                    totalBytes += count
                    if (totalBytes > MAX_ASSET_BYTES) return CopyResult.TOO_LARGE
                    destination.write(buffer, 0, count)
                }
                destination.fd.sync()
            }
        }
        CopyResult.SUCCESS
    } catch (_: IOException) {
        CopyResult.IO_FAILURE
    } catch (_: SecurityException) {
        CopyResult.IO_FAILURE
    } catch (_: RuntimeException) {
        CopyResult.IO_FAILURE
    }

    private fun validateImage(file: File): ValidationResult {
        val bytes = try {
            readValidationBytes(file)
        } catch (_: IOException) {
            return ValidationResult.IO_FAILURE
        } catch (_: SecurityException) {
            return ValidationResult.IO_FAILURE
        }
        if (!hasSupportedImageSignature(bytes)) return ValidationResult.UNSUPPORTED

        return try {
            ImageDecoder.decodeDrawable(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
                val largestDimension = maxOf(info.size.width, info.size.height)
                if (largestDimension > MAX_VALIDATION_DIMENSION_PX) {
                    val sampleSize = (
                        (largestDimension.toLong() + MAX_VALIDATION_DIMENSION_PX - 1L) /
                            MAX_VALIDATION_DIMENSION_PX
                        ).toInt()
                    decoder.setTargetSampleSize(sampleSize)
                }
            }
            ValidationResult.SUPPORTED
        } catch (_: IOException) {
            ValidationResult.UNSUPPORTED
        } catch (_: IllegalArgumentException) {
            ValidationResult.UNSUPPORTED
        } catch (_: RuntimeException) {
            ValidationResult.UNSUPPORTED
        }
    }

    private fun randomAssetKey(uri: Uri, mimeType: String?): String {
        val mimeExtension = mimeType
            ?.let(MimeTypeMap.getSingleton()::getExtensionFromMimeType)
            ?.toSafeExtension()
        val uriExtension = MimeTypeMap.getFileExtensionFromUrl(uri.toString()).toSafeExtension()
        val extension = mimeExtension ?: uriExtension ?: DEFAULT_EXTENSION
        return "${UUID.randomUUID()}.$extension"
    }

    private fun String?.toSafeExtension(): String? = this
        ?.lowercase()
        ?.takeIf { extension -> extension.length in 1..8 && extension.all(Char::isLetterOrDigit) }

    private fun resolvePrivateAsset(assetKey: String, requireExisting: Boolean): File? {
        if (assetKey.isBlank() || File(assetKey).isAbsolute) return null
        val directory = privateAssetDirectory(create = false) ?: return null
        val candidate = runCatching { File(directory, assetKey).canonicalFile }.getOrNull() ?: return null
        val directoryPrefix = directory.path + File.separator
        if (!candidate.path.startsWith(directoryPrefix)) return null
        if (requireExisting && !candidate.isFile) return null
        return candidate
    }

    private fun privateAssetDirectory(create: Boolean): File? {
        val canonicalFilesDirectory = runCatching { filesDirectory.canonicalFile }.getOrNull() ?: return null
        val directory = File(canonicalFilesDirectory, DIRECTORY_NAME)
        if (create && !directory.isDirectory && !directory.mkdirs()) return null
        if (!directory.isDirectory) return null
        val canonicalDirectory = runCatching { directory.canonicalFile }.getOrNull() ?: return null
        if (canonicalDirectory != directory.absoluteFile) return null
        return canonicalDirectory
    }

    private enum class CopyResult {
        SUCCESS,
        TOO_LARGE,
        IO_FAILURE,
    }

    private enum class ValidationResult {
        SUPPORTED,
        UNSUPPORTED,
        IO_FAILURE,
    }

    private companion object {
        const val DIRECTORY_NAME = "theme-assets"
        const val DEFAULT_EXTENSION = "img"
        const val MAX_ASSET_BYTES = 20L * 1024L * 1024L
        const val MAX_VALIDATION_DIMENSION_PX = 4_096
    }
}

internal fun hasSupportedImageSignature(bytes: ByteArray): Boolean =
    bytes.startsWith(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) ||
        bytes.startsWith(0xFF, 0xD8, 0xFF) ||
        bytes.startsWithAscii("GIF87a") ||
        bytes.startsWithAscii("GIF89a") ||
        (bytes.startsWithAscii("RIFF") && bytes.hasAsciiAt(8, "WEBP")) ||
        bytes.startsWithAscii("BM") ||
        bytes.startsWith(0x00, 0x00, 0x01, 0x00) ||
        bytes.hasIsoBaseMediaImageBrand()

private fun ByteArray.startsWith(vararg signature: Int): Boolean =
    size >= signature.size && signature.indices.all { index -> this[index].toInt() and 0xFF == signature[index] }

private fun ByteArray.startsWithAscii(signature: String): Boolean = hasAsciiAt(0, signature)

private fun ByteArray.hasAsciiAt(offset: Int, signature: String): Boolean =
    size >= offset + signature.length && signature.indices.all { index ->
        this[offset + index].toInt() and 0xFF == signature[index].code
    }

private fun ByteArray.hasIsoBaseMediaImageBrand(): Boolean {
    if (!hasAsciiAt(4, "ftyp") || size < 12) return false
    val imageBrands = setOf("heic", "heix", "hevc", "hevx", "heim", "heis", "mif1", "msf1", "avif", "avis")
    var offset = 8
    while (offset + 4 <= size && offset <= 32) {
        if (imageBrands.any { brand -> hasAsciiAt(offset, brand) }) return true
        offset += 4
    }
    return false
}

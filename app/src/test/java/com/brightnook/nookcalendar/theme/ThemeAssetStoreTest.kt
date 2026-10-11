package com.brightnook.nookcalendar.theme

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assume
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ThemeAssetStoreTest {
    private lateinit var context: Context
    private lateinit var assetDirectory: File
    private lateinit var sourceDirectory: File
    private lateinit var redirectedDirectory: File
    private lateinit var store: PrivateThemeAssetStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        assetDirectory = File(context.filesDir, "theme-assets")
        sourceDirectory = File(context.cacheDir, "theme-asset-test-sources")
        redirectedDirectory = File(context.filesDir, "redirected-theme-assets")
        deleteAssetDirectoryWithoutFollowingLinks()
        redirectedDirectory.deleteRecursively()
        sourceDirectory.deleteRecursively()
        assertTrue(sourceDirectory.mkdirs())
        store = PrivateThemeAssetStore(context)
    }

    @After
    fun tearDown() {
        deleteAssetDirectoryWithoutFollowingLinks()
        redirectedDirectory.deleteRecursively()
        sourceDirectory.deleteRecursively()
    }

    @Test
    fun rejectedImportLeavesExistingAssetUntouched() = runTest {
        // Catches validation failure deleting an already imported background.
        assertTrue(assetDirectory.mkdirs())
        val previous = File(assetDirectory, "old.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val invalid = File(sourceDirectory, "invalid.png").apply { writeText("not an image") }

        assertEquals(
            ThemeAssetImport.Failure(ThemeAssetFailure.UNSUPPORTED),
            store.import(Uri.fromFile(invalid)),
        )
        assertTrue(previous.exists())
        assertEquals(listOf<Byte>(1, 2, 3), previous.readBytes().toList())
    }

    @Test
    fun importReturnsRelativePrivateAssetKey() = runTest {
        // Catches persistence of an external URI or a path that escapes private app storage.
        val source = createPng(File(sourceDirectory, "wallpaper.png"), 32, 24)

        val result = store.import(Uri.fromFile(source))

        assertTrue(result is ThemeAssetImport.Success)
        val key = (result as ThemeAssetImport.Success).assetKey
        assertFalse(File(key).isAbsolute)
        assertFalse(key.contains(".."))
        assertFalse(key.contains("://"))
        val imported = store.fileFor(key)
        assertNotNull(imported)
        assertEquals(assetDirectory.canonicalFile, imported!!.parentFile!!.canonicalFile)
        assertTrue(imported.exists())
    }

    @Test
    fun importRejectsContentLargerThanTwentyMebibytes() = runTest {
        // Catches an off-by-one or missing byte limit accepting oversized picker content.
        val oversized = File(sourceDirectory, "oversized.png")
        RandomAccessFile(oversized, "rw").use { it.setLength(20L * 1024L * 1024L + 1L) }

        assertEquals(
            ThemeAssetImport.Failure(ThemeAssetFailure.TOO_LARGE),
            store.import(Uri.fromFile(oversized)),
        )
        assertTrue(assetDirectory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun missingSourceReturnsIoFailure() = runTest {
        // Catches unreadable picker content being mislabeled as unsupported image data.
        val missing = File(sourceDirectory, "missing.png")

        assertEquals(
            ThemeAssetImport.Failure(ThemeAssetFailure.IO),
            store.import(Uri.fromFile(missing)),
        )
    }

    @Test
    fun validationReadFailureReturnsIoFailure() = runTest {
        // Catches a private temporary-file read failure being mislabeled as unsupported content.
        val source = createPng(File(sourceDirectory, "read-failure.png"), 16, 16)
        val failingStore = PrivateThemeAssetStore(
            context = context,
            readValidationBytes = { throw IOException("validation read failed") },
        )

        assertEquals(
            ThemeAssetImport.Failure(ThemeAssetFailure.IO),
            failingStore.import(Uri.fromFile(source)),
        )
        assertTrue(assetDirectory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun symlinkedAssetDirectoryCannotRedirectImportOrRemoval() = runTest {
        // Catches a sibling symlink redirecting both publication and deletion outside theme-assets.
        assertTrue(redirectedDirectory.mkdirs())
        createDirectoryLinkOrSkip(assetDirectory, redirectedDirectory)
        val victim = File(redirectedDirectory, "victim.png").apply { writeText("keep") }
        val source = createPng(File(sourceDirectory, "symlink.png"), 16, 16)

        assertEquals(
            ThemeAssetImport.Failure(ThemeAssetFailure.IO),
            store.import(Uri.fromFile(source)),
        )
        store.remove("victim.png")

        assertTrue(victim.exists())
        assertEquals(listOf("victim.png"), redirectedDirectory.list().orEmpty().sorted())
    }

    @Test
    fun importAcceptsAnimatedGif() = runTest {
        // Catches signature or decoder validation rejecting a real multi-frame GIF.
        val source = AnimatedImageFixtures.writeAnimatedGif(File(sourceDirectory, "animated.gif"))

        assertTrue(store.import(Uri.fromFile(source)) is ThemeAssetImport.Success)
    }

    @Test
    fun importAcceptsAnimatedWebp() = runTest {
        // Catches validation treating a real ANIM/ANMF WebP container as unsupported.
        val source = AnimatedImageFixtures.writeAnimatedWebp(File(sourceDirectory, "animated.webp"))

        assertTrue(store.import(Uri.fromFile(source)) is ThemeAssetImport.Success)
    }

    @Test
    fun removeRejectsAssetKeysOutsidePrivateDirectory() = runTest {
        // Catches path traversal allowing Remove image to delete an unrelated private file.
        val outside = File(context.filesDir, "keep-me.txt").apply { writeText("keep") }

        store.remove("../keep-me.txt")

        assertTrue(outside.exists())
        outside.delete()
    }

    @Test
    fun removeDeletesOnlyTheNamedImportedAsset() = runTest {
        // Catches Remove image doing nothing or deleting a neighboring imported background.
        val first = store.import(Uri.fromFile(createPng(File(sourceDirectory, "first.png"), 16, 16)))
            as ThemeAssetImport.Success
        val second = store.import(Uri.fromFile(createPng(File(sourceDirectory, "second.png"), 16, 16)))
            as ThemeAssetImport.Success

        store.remove(first.assetKey)

        assertEquals(null, store.fileFor(first.assetKey))
        assertNotNull(store.fileFor(second.assetKey))
    }

    private fun createPng(file: File, width: Int, height: Int): File {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0xFF336699.toInt())
        try {
            FileOutputStream(file).use { output ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
        } finally {
            bitmap.recycle()
        }
        return file
    }

    private fun deleteAssetDirectoryWithoutFollowingLinks() {
        val redirected = runCatching { assetDirectory.canonicalFile != assetDirectory.absoluteFile }.getOrDefault(false)
        if (Files.isSymbolicLink(assetDirectory.toPath()) || redirected) {
            Files.deleteIfExists(assetDirectory.toPath())
        } else {
            assetDirectory.deleteRecursively()
        }
    }

    private fun createDirectoryLinkOrSkip(link: File, target: File) {
        try {
            Files.createSymbolicLink(link.toPath(), target.toPath())
            return
        } catch (_: Exception) {
            // Windows commonly denies symbolic links but permits an unprivileged directory junction.
        }
        if (System.getProperty("os.name").orEmpty().contains("Windows", ignoreCase = true)) {
            val process = ProcessBuilder(
                "cmd.exe",
                "/d",
                "/c",
                "mklink",
                "/J",
                link.absolutePath,
                target.absolutePath,
            ).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            if (process.waitFor() == 0) return
            Assume.assumeTrue("Cannot create a directory junction: $output", false)
        }
        Assume.assumeTrue("Symbolic links are unavailable on this host", false)
    }
}

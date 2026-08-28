package com.brightnook.nookcalendar.ui

import android.content.ContextWrapper
import com.brightnook.nookcalendar.domain.AppLanguage
import com.brightnook.nookcalendar.domain.resolveAppLocale
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppLanguageTest {
    @Test
    fun followSystemUsesChineseForEveryChineseSystemLocale() {
        // Catches Chinese locales outside zh-CN falling back to the default English resources.
        assertEquals(Locale.SIMPLIFIED_CHINESE, resolveAppLocale(AppLanguage.FOLLOW_SYSTEM, Locale.TRADITIONAL_CHINESE))
    }

    @Test
    fun followSystemUsesJapaneseForJapaneseSystemLocale() {
        // Catches Japanese devices falling back to the default English resources.
        assertEquals(Locale.JAPANESE, resolveAppLocale(AppLanguage.FOLLOW_SYSTEM, Locale.JAPANESE))
    }

    @Test
    fun manualChineseOverridesTheSystemLocale() {
        assertEquals(Locale.SIMPLIFIED_CHINESE, resolveAppLocale(AppLanguage.CHINESE, Locale.US))
    }

    @Test
    fun manualEnglishOverridesTheSystemLocale() {
        assertEquals(Locale.ENGLISH, resolveAppLocale(AppLanguage.ENGLISH, Locale.JAPANESE))
    }

    @Test
    fun manualJapaneseOverridesTheSystemLocale() {
        assertEquals(Locale.JAPANESE, resolveAppLocale(AppLanguage.JAPANESE, Locale.US))
    }

    @Test
    fun contextLookupFindsTheOriginalHostThroughLocalizedWrapper() {
        val host = MarkerContext()
        val localizedWrapper = ContextWrapper(host)

        assertEquals(host, localizedWrapper.findContext(MarkerContext::class.java))
    }

    private class MarkerContext : ContextWrapper(null)
}

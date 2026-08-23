package com.victor2319.ioscalendar.ui

import android.content.ContextWrapper
import com.victor2319.ioscalendar.domain.AppLanguage
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
    fun followSystemUsesEnglishForJapaneseSystemLocale() {
        // Catches Android's Japanese resource and date fallback appearing in the app.
        assertEquals(Locale.ENGLISH, resolveAppLocale(AppLanguage.FOLLOW_SYSTEM, Locale.JAPANESE))
    }

    @Test
    fun contextLookupFindsTheOriginalHostThroughLocalizedWrapper() {
        val host = MarkerContext()
        val localizedWrapper = ContextWrapper(host)

        assertEquals(host, localizedWrapper.findContext(MarkerContext::class.java))
    }

    private class MarkerContext : ContextWrapper(null)
}

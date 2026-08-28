package com.brightnook.nookcalendar.data

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.brightnook.nookcalendar.R
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppLanguageResourceTest {
    @Test
    fun japaneseCatalogProvidesTheJapaneseLanguageLabel() {
        // Catches a missing Japanese catalog falling back to the base English label.
        val japaneseContext = ApplicationProvider.getApplicationContext<Context>()
            .createConfigurationContext(Configuration().apply { setLocale(Locale.JAPANESE) })

        assertEquals("日本語", japaneseContext.getString(R.string.settings_language_japanese))
    }
}

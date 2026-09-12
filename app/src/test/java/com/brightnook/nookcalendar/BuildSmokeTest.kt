package com.brightnook.nookcalendar

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BuildSmokeTest {
    @Test
    fun packageNameIsStable() {
        assertEquals("com.brightnook.nookcalendar", BuildConfig.APPLICATION_ID)
    }

    @Test
    fun releaseBuildUsesTheNextPlayVersion() {
        assertEquals("1.0.21", BuildConfig.VERSION_NAME)
        assertEquals(22, BuildConfig.VERSION_CODE)
    }

    @Test
    fun defaultApplicationNameUsesTheStorefrontLabel() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertEquals("NookCalendar", context.getString(R.string.app_name))
    }
}

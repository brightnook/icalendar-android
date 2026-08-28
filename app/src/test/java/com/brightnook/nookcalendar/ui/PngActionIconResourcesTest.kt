package com.brightnook.nookcalendar.ui

import android.graphics.drawable.BitmapDrawable
import androidx.test.core.app.ApplicationProvider
import com.brightnook.nookcalendar.BuildConfig
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PngActionIconResourcesTest {
    @Test
    fun approvedActionsResolveToRasterDrawables() {
        // Catches a supplied action icon being absent or replaced with a vector resource.
        val resources = ApplicationProvider.getApplicationContext<android.content.Context>().resources
        listOf(
            "ic_action_today",
            "ic_action_view_switch",
            "ic_action_search",
            "ic_action_refresh",
            "ic_action_settings",
            "ic_action_close",
            "ic_action_logout",
            "ic_action_login",
        ).forEach { resourceName ->
            val resourceId = resources.getIdentifier(resourceName, "drawable", BuildConfig.APPLICATION_ID)
            assertTrue("Missing PNG drawable $resourceName", resourceId != 0)
            assertTrue(
                "$resourceName must render from a PNG bitmap",
                resources.getDrawable(resourceId, null) is BitmapDrawable,
            )
        }
    }
}

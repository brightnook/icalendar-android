package com.victor2319.ioscalendar.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.victor2319.ioscalendar.domain.AppLanguage
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test

class LocalizedAppContentTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun localizedContentRetainsTheHostActivityResultRegistryOwner() {
        // Catches a localized Context hiding the host needed by rememberLauncherForActivityResult.
        var resolvedOwner: ActivityResultRegistryOwner? = null

        composeRule.setContent {
            LocalizedAppContent(AppLanguage.FOLLOW_SYSTEM) {
                resolvedOwner = LocalActivityResultRegistryOwner.current
            }
        }
        composeRule.waitForIdle()

        assertSame(composeRule.activity, resolvedOwner)
    }
}

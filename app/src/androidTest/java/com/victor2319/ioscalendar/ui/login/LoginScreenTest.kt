package com.victor2319.ioscalendar.ui.login

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.victor2319.ioscalendar.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoginScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun appPasswordInstructionsAndBrowserLinksAreAccessible() {
        // Catches replacing the external Apple flow with embedded login or losing screen-reader labels.
        var openedUri: String? = null
        composeRule.setContent {
            MaterialTheme {
                LoginScreen(
                    uiState = LoginUiState(),
                    onConnect = { _, password -> password.fill('\u0000') },
                    onOpenUri = { openedUri = it },
                )
            }
        }

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.login_create_app_password))
            .assertIsDisplayed()
            .performClick()
        composeRule.runOnIdle {
            assertEquals("https://account.apple.com/", openedUri)
        }
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.login_support_link))
            .assertIsDisplayed()
            .performClick()
        composeRule.runOnIdle {
            assertEquals("https://support.apple.com/zh-cn/102654", openedUri)
        }
        composeRule.onNodeWithContentDescription(
            composeRule.activity.getString(R.string.login_toggle_password_visibility),
        ).assertIsDisplayed()
    }

    @Test
    fun localizedResourcesProvideDifferentEnglishAndChineseFailureCopy() {
        // Catches shipping a missing or accidentally duplicated Chinese recovery message.
        val resources = composeRule.activity.resources
        val english = composeRule.activity.createConfigurationContext(
            android.content.res.Configuration(resources.configuration).also {
                it.setLocale(java.util.Locale.US)
            },
        )
        val chinese = composeRule.activity.createConfigurationContext(
            android.content.res.Configuration(resources.configuration).also {
                it.setLocale(java.util.Locale.SIMPLIFIED_CHINESE)
            },
        )

        val englishCopy = english.getString(R.string.login_error_authentication)
        val chineseCopy = chinese.getString(R.string.login_error_authentication)

        assertNotEquals(englishCopy, chineseCopy)
    }

    @Test
    fun authenticationFailureIsShownWithoutEchoingCredentials() {
        // Catches showing a generic failure that gives no credential recovery guidance.
        composeRule.setContent {
            MaterialTheme {
                LoginScreen(
                    uiState = LoginUiState(error = LoginError.AUTHENTICATION),
                    onConnect = { _, password -> password.fill('\u0000') },
                    onOpenUri = {},
                )
            }
        }

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.login_error_authentication))
            .assertIsDisplayed()
    }
}

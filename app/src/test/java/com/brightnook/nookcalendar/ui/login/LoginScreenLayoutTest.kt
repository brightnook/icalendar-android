package com.brightnook.nookcalendar.ui.login

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LoginScreenLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun loginActionRemainsReachableOnShortScreens() {
        compose.setContent {
            MaterialTheme {
                Box(Modifier.size(width = 360.dp, height = 400.dp)) {
                    LoginScreen(LoginUiState(), onConnect = { _, _ -> },
                        onViewCalendars = {}, onOpenUri = {})
                }
            }
        }
        compose.onNodeWithTag("login-connect-icon", useUnmergedTree = true)
            .performScrollTo().assertIsDisplayed()
    }
}

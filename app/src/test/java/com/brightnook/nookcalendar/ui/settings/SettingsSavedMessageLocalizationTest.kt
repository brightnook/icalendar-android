package com.brightnook.nookcalendar.ui.settings

import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsSavedMessageLocalizationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun savedMessageUsesTheCurrentLanguageAfterLanguageChanges() {
        val savedEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        var savedMessage by mutableStateOf("Settings saved")

        composeRule.setContent {
            val snackbarHostState = remember { SnackbarHostState() }
            SettingsSavedMessageHandler(
                savedEvents = savedEvents,
                savedMessage = savedMessage,
                snackbarHostState = snackbarHostState,
            )
            SnackbarHost(hostState = snackbarHostState)
        }
        composeRule.waitForIdle()

        composeRule.runOnIdle { savedMessage = "設定を保存しました" }
        composeRule.waitForIdle()
        composeRule.runOnIdle { savedEvents.tryEmit(Unit) }

        composeRule.onNodeWithText("設定を保存しました").assertIsDisplayed()
    }
}

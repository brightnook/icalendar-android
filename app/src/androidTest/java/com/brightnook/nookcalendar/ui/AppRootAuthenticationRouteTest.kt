package com.brightnook.nookcalendar.ui

import com.brightnook.nookcalendar.MainActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.brightnook.nookcalendar.auth.Credentials
import com.brightnook.nookcalendar.auth.CredentialsStore
import com.brightnook.nookcalendar.auth.LoginCoordinator
import com.brightnook.nookcalendar.auth.LoginResult
import com.brightnook.nookcalendar.domain.AccountAuthenticationState
import com.brightnook.nookcalendar.sync.SyncCoordinator
import com.brightnook.nookcalendar.sync.SyncRequest
import com.brightnook.nookcalendar.sync.SyncResult
import com.brightnook.nookcalendar.ui.login.LoginViewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

@Suppress("DEPRECATION")
class AppRootAuthenticationRouteTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test fun restoredAuthenticatedAccountRendersCalendarWithoutAnotherLogin() {
        setRoot(AccountAuthenticationState.Authenticated("p***@example.test"))
        composeRule.onNodeWithText("iCloud Calendar connected").assertIsDisplayed()
    }

    @Test fun signedOutAccountKeepsLoginAfterInitialSuccess() {
        setRoot(AccountAuthenticationState.SignedOut)
        connect()
        composeRule.onNodeWithText("Connect iCloud Calendar").assertIsDisplayed()
    }

    private fun setRoot(state: AccountAuthenticationState) {
        composeRule.setContent { AppRoot(loginFactory(), MutableStateFlow(state)) }
    }
    private fun connect() {
        composeRule.onNodeWithContentDescription("Apple Account email").performTextInput("person@example.test")
        composeRule.onNodeWithContentDescription("App-specific password").performTextInput("abcd-efgh-ijkl-mnop")
        composeRule.onNodeWithText("Connect and sync").performClick()
        composeRule.waitForIdle()
    }
    private fun loginFactory() = LoginViewModelFactory(
        loginCoordinator = object : LoginCoordinator { override suspend fun connect(appleId: String, appPassword: CharArray) = LoginResult.Success },
        credentialsStore = object : CredentialsStore { override suspend fun save(credentials: Credentials) = Unit; override suspend fun load() = null; override suspend fun clear() = Unit },
        syncCoordinator = object : SyncCoordinator { override suspend fun sync(request: SyncRequest) = SyncResult.Success() },
    )
}

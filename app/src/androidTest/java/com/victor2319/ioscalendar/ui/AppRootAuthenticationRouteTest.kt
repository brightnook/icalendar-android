package com.victor2319.ioscalendar.ui

import com.victor2319.ioscalendar.MainActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.victor2319.ioscalendar.auth.Credentials
import com.victor2319.ioscalendar.auth.CredentialsStore
import com.victor2319.ioscalendar.auth.LoginCoordinator
import com.victor2319.ioscalendar.auth.LoginResult
import com.victor2319.ioscalendar.domain.AccountAuthenticationState
import com.victor2319.ioscalendar.sync.SyncCoordinator
import com.victor2319.ioscalendar.sync.SyncRequest
import com.victor2319.ioscalendar.sync.SyncResult
import com.victor2319.ioscalendar.ui.login.LoginViewModelFactory
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

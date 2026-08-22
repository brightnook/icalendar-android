package com.victor2319.ioscalendar.ui.login

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.anyIntent
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasData
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.victor2319.ioscalendar.R
import com.victor2319.ioscalendar.auth.Credentials
import com.victor2319.ioscalendar.auth.CredentialsStore
import com.victor2319.ioscalendar.auth.LoginCoordinator
import com.victor2319.ioscalendar.auth.LoginResult
import com.victor2319.ioscalendar.sync.SyncCoordinator
import com.victor2319.ioscalendar.sync.SyncRequest
import com.victor2319.ioscalendar.sync.SyncResult
import com.victor2319.ioscalendar.ui.AppRoot
import com.victor2319.ioscalendar.ui.login.LoginDestination
import com.victor2319.ioscalendar.ui.login.LoginError
import com.victor2319.ioscalendar.ui.login.LoginScreen
import com.victor2319.ioscalendar.ui.login.LoginUiState
import org.hamcrest.CoreMatchers.allOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import kotlinx.coroutines.CompletableDeferred

@RunWith(AndroidJUnit4::class)
class LoginScreenTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun appRootOpensExactExternalAppleUrls() {
        Intents.init()
        try {
            intending(anyIntent()).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent()))
            composeRule.setContent { MaterialTheme { AppRoot(factory(LoginResult.Success)) } }
            composeRule.onNodeWithText(composeRule.activity.getString(R.string.login_create_app_password)).performClick()
            intended(allOf(hasAction(Intent.ACTION_VIEW), hasData(APPLE_ACCOUNT_URL)))
            composeRule.onNodeWithText(composeRule.activity.getString(R.string.login_support_link)).performClick()
            intended(allOf(hasAction(Intent.ACTION_VIEW), hasData(APPLE_APP_PASSWORD_SUPPORT_URL)))
        } finally { Intents.release() }
    }

    @Test
    fun appRootProvidesAccessibleInputsAndClearsFailedPassword() {
        composeRule.setContent { MaterialTheme { AppRoot(factory(LoginResult.AuthenticationFailure)) } }
        fillCredentials()
        composeRule.onNodeWithContentDescription(composeRule.activity.getString(R.string.login_toggle_password_visibility))
            .assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.login_connect_and_sync)).performClick()
        composeRule.waitUntil { composeRule.onAllNodesWithText(composeRule.activity.getString(R.string.login_error_authentication)).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithContentDescription(composeRule.activity.getString(R.string.login_app_password_label)).assertTextEquals("")
    }

    @Test
    fun appRootNavigatesToCalendarAfterSuccessfulFirstSync() {
        composeRule.setContent { MaterialTheme { AppRoot(factory(LoginResult.Success)) } }
        fillCredentials()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.login_connect_and_sync)).performClick()
        composeRule.waitUntil { composeRule.onAllNodesWithText(composeRule.activity.getString(R.string.login_connected_title)).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun appRootKeepsAnInFlightSyncWhenItsCompositionIsRecreated() {
        val syncCoordinator = BlockingSyncCoordinator()
        val loginViewModelFactory = factory(LoginResult.Success, syncCoordinator)
        var compositionGeneration by mutableIntStateOf(0)

        composeRule.setContent {
            key(compositionGeneration) {
                MaterialTheme { AppRoot(loginViewModelFactory) }
            }
        }
        fillCredentials()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.login_connect_and_sync)).performClick()
        composeRule.waitUntil {
            composeRule.onAllNodesWithText(composeRule.activity.getString(R.string.login_progress_syncing))
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.runOnIdle { compositionGeneration += 1 }
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.login_progress_syncing)).assertIsDisplayed()

        syncCoordinator.release()
        composeRule.waitUntil {
            composeRule.onAllNodesWithText(composeRule.activity.getString(R.string.login_connected_title))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun appRootShowsEnglishAuthenticationFailureCopy() {
        setLocalizedAppRoot(Locale.ENGLISH, R.string.login_error_authentication)
    }

    @Test
    fun appRootShowsChineseAuthenticationFailureCopy() {
        setLocalizedAppRoot(Locale.SIMPLIFIED_CHINESE, R.string.login_error_authentication)
    }

    @Test
    fun partialFirstSyncKeepsItsWarningAndOffersCalendarView() {
        // Catches a partial result trapping a user on login even after one or more calendars are usable.
        composeRule.setContent {
            MaterialTheme {
                LoginScreen(
                    uiState = LoginUiState(
                        error = LoginError.PARTIAL_SYNC,
                        destination = LoginDestination.CALENDAR,
                    ),
                    onConnect = { _, password -> password.fill('\u0000') },
                    onViewCalendars = {},
                    onOpenUri = {},
                )
            }
        }

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.login_error_partial_sync))
            .assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.login_view_calendars)).assertIsDisplayed()
    }

    @Test
    fun connectActionShowsTheSuppliedLoginIcon() {
        composeRule.setContent {
            MaterialTheme {
                LoginScreen(
                    uiState = LoginUiState(),
                    onConnect = { _, password -> password.fill('\u0000') },
                    onViewCalendars = {},
                    onOpenUri = {},
                )
            }
        }

        composeRule.onNodeWithTag("login-connect-icon").assertIsDisplayed()
    }

    private fun fillCredentials(context: Context = composeRule.activity) {
        composeRule.onNodeWithContentDescription(context.getString(R.string.login_apple_id_label))
            .performTextInput("person@example.test")
        composeRule.onNodeWithContentDescription(context.getString(R.string.login_app_password_label))
            .performTextInput("abcd-efgh-ijkl-mnop")
    }

    private fun setLocalizedAppRoot(locale: Locale, expectedMessage: Int) {
        val localizedContext = composeRule.activity.createConfigurationContext(
            Configuration(composeRule.activity.resources.configuration).apply { setLocale(locale) },
        )
        composeRule.setContent {
            CompositionLocalProvider(LocalContext provides localizedContext) {
                MaterialTheme { AppRoot(factory(LoginResult.AuthenticationFailure)) }
            }
        }
        fillCredentials(localizedContext)
        composeRule.onNodeWithText(localizedContext.getString(R.string.login_connect_and_sync)).performClick()
        composeRule.waitUntil {
            composeRule.onAllNodesWithText(localizedContext.getString(expectedMessage)).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(localizedContext.getString(expectedMessage)).assertIsDisplayed()
    }
}

private fun factory(result: LoginResult, syncCoordinator: SyncCoordinator = SuccessSyncCoordinator) = LoginViewModelFactory(
    loginCoordinator = object : LoginCoordinator {
        override suspend fun connect(appleId: String, appPassword: CharArray) = result
    },
    credentialsStore = object : CredentialsStore {
        private var saved: Credentials? = null
        override suspend fun save(credentials: Credentials) { saved = Credentials(credentials.appleId, credentials.appSpecificPassword.copyOf()) }
        override suspend fun load() = saved?.let { Credentials(it.appleId, it.appSpecificPassword.copyOf()) }
        override suspend fun clear() { saved = null }
    },
    syncCoordinator = syncCoordinator,
)

private object SuccessSyncCoordinator : SyncCoordinator {
    override suspend fun sync(request: SyncRequest) = SyncResult.Success()
}

private class BlockingSyncCoordinator : SyncCoordinator {
    private val releaseSignal = CompletableDeferred<Unit>()

    override suspend fun sync(request: SyncRequest): SyncResult {
        releaseSignal.await()
        return SyncResult.Success()
    }

    fun release() {
        releaseSignal.complete(Unit)
    }
}

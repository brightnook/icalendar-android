package com.brightnook.nookcalendar.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.brightnook.nookcalendar.domain.AppLanguage
import com.brightnook.nookcalendar.domain.ThemeSettings
import com.brightnook.nookcalendar.monetization.MonetizationUiState
import com.brightnook.nookcalendar.monetization.PurchaseUiState
import com.brightnook.nookcalendar.reminder.ReminderScheduler
import com.brightnook.nookcalendar.ui.settings.SettingsScreen
import com.brightnook.nookcalendar.ui.settings.SettingsUiState
import com.brightnook.nookcalendar.ui.theme.calendarThemePresentation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

private val testThemePresentation = calendarThemePresentation(ThemeSettings(), null)

@RunWith(RobolectricTestRunner::class)
class AppRootPurchaseActionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun activityResultRegistryOwnerKeepsTheHostActivityAvailableAfterLocalization() {
        // Catches using localized LocalContext as the billing/UMP Activity host.
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()

        assertSame(activity, activityFromRegistryOwner(activity))
    }

    @Test
    fun nonActivityPurchaseDoesNotInvokeBillingCallback() = runTest {
        // Catches a non-Activity composition starting Play Billing without a valid Activity host.
        var calls = 0

        launchPurchaseIfActivity(null, this) { calls++ }
        advanceUntilIdle()

        assertEquals(0, calls)
    }

    @Test
    fun ownedPurchaseStateRequestsReminderReconciliation() = runTest {
        val scheduler = RecordingReminderScheduler()

        reconcileRemindersForPurchaseState(PurchaseUiState.OWNED, scheduler)

        assertEquals(1, scheduler.reconcileCalls)
    }

    @Test
    fun changingDisplayLanguageDoesNotConvertTheStorefrontPrice() {
        // Catches a price renderer deriving currency from the selected app language.
        var language by mutableStateOf(AppLanguage.ENGLISH)
        composeRule.setContent {
            LocalizedAppContent(language) {
                MaterialTheme {
                    SettingsScreen(
                        state = SettingsUiState(accountLoading = false),
                        themePresentation = testThemePresentation,
                        onClose = {},
                        onSelectSyncMode = {},
                        onSetCalendarVisible = { _, _ -> },
                        onClearHistory = {},
                        onClearCache = {},
                        onLogout = {},
                        onDismissConfirmation = {},
                        onConfirmDestructiveAction = {},
                        monetization = MonetizationUiState(
                            canShowAds = true,
                            purchaseState = PurchaseUiState.AVAILABLE,
                            price = "$2.49",
                        ),
                    )
                }
            }
        }

        composeRule.onNodeWithText("Google Play price: $2.49").assertExists()

        composeRule.runOnIdle { language = AppLanguage.JAPANESE }

        composeRule.onNodeWithText("Google Play の価格: $2.49").assertExists()
    }

    private class RecordingReminderScheduler : ReminderScheduler {
        override val hasUpcomingReminders = MutableStateFlow(false)
        var reconcileCalls = 0

        override suspend fun reconcile() {
            reconcileCalls++
        }

        override fun initialize() = Unit
    }
}

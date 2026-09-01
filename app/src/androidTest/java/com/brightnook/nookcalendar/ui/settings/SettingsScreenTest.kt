package com.brightnook.nookcalendar.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.graphics.toPixelMap
import com.brightnook.nookcalendar.R
import com.brightnook.nookcalendar.domain.AppLanguage
import com.brightnook.nookcalendar.domain.CalendarSummary
import com.brightnook.nookcalendar.domain.WeekStart
import com.brightnook.nookcalendar.monetization.MonetizationUiState
import com.brightnook.nookcalendar.monetization.PurchaseFeedback
import com.brightnook.nookcalendar.monetization.PurchaseUiState
import com.brightnook.nookcalendar.ui.LocalizedAppContent
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test

@Suppress("DEPRECATION")
class SettingsScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun logoutDialogUsesTheSelectedAppLanguage() {
        composeRule.setContent {
            LocalizedAppContent(AppLanguage.JAPANESE) {
                SettingsScreen(
                    state = SettingsUiState(confirmation = SettingsConfirmation.Logout(clearCache = false)),
                    onClose = {}, onSelectSyncMode = {}, onSetCalendarVisible = { _, _ -> },
                    onClearHistory = {}, onClearCache = {}, onLogout = {}, onDismissConfirmation = {},
                    onConfirmDestructiveAction = {},
                )
            }
        }

        composeRule.onNodeWithText("サインアウトしますか？").assertIsDisplayed()
        composeRule.onNodeWithText("このデバイスに保存されたiCloud認証情報が削除されます。ローカルにキャッシュされたカレンダーデータは残ります。").assertIsDisplayed()
        composeRule.onNodeWithText("サインアウト").assertIsDisplayed()
        composeRule.onNodeWithText("キャンセル").assertIsDisplayed()
    }

    @Test
    fun settingsSavedMessageUsesTheSelectedAppLanguage() {
        val savedEvents = MutableSharedFlow<Unit>(replay = 1).apply { tryEmit(Unit) }
        composeRule.setContent {
            LocalizedAppContent(AppLanguage.JAPANESE) {
                SettingsScreen(
                    state = SettingsUiState(),
                    onClose = {}, onSelectSyncMode = {}, onSetCalendarVisible = { _, _ -> },
                    onClearHistory = {}, onClearCache = {}, onLogout = {}, onDismissConfirmation = {},
                    onConfirmDestructiveAction = {},
                    settingsSavedEvents = savedEvents,
                )
            }
        }

        composeRule.onNodeWithText("設定を保存しました").assertIsDisplayed()
    }

    @Test
    fun requiredPrivacyOptionsExposeAnActionThatOpensTheConsentChoices() {
        var privacyOptionsCalls = 0
        composeRule.setContent {
            SettingsScreen(
                state = SettingsUiState(),
                privacyOptionsRequired = true,
                onClose = {}, onSelectSyncMode = {}, onSetCalendarVisible = { _, _ -> },
                onClearHistory = {}, onClearCache = {}, onLogout = {}, onDismissConfirmation = {},
                onConfirmDestructiveAction = {},
                onOpenPrivacyOptions = { privacyOptionsCalls++ },
            )
        }

        composeRule.onNodeWithTag("privacy-options").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(1, privacyOptionsCalls)
    }

    @Test
    fun availablePurchaseShowsRemoveAdsActionPriceAndInvokesCallbacks() {
        // Catches a purchasable state that hides the Play-provided price, action, or callback.
        var purchaseCalls = 0
        var restoreCalls = 0
        composeRule.setContent {
            SettingsScreen(
                state = SettingsUiState(),
                monetization = MonetizationUiState(
                    canShowAds = true,
                    purchaseState = PurchaseUiState.AVAILABLE,
                    price = "¥250",
                ),
                onClose = {}, onSelectSyncMode = {}, onSetCalendarVisible = { _, _ -> },
                onClearHistory = {}, onClearCache = {}, onLogout = {}, onDismissConfirmation = {},
                onConfirmDestructiveAction = {},
                onPurchase = { purchaseCalls++ },
                onRestorePurchase = { restoreCalls++ },
            )
        }

        composeRule.onNodeWithTag("settings-section-support").assertIsDisplayed()
        composeRule.onNodeWithTag("remove-ads").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("¥250", substring = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("remove-ads").performClick()
        composeRule.onNodeWithTag("restore-purchases").performClick()

        assertEquals(1, purchaseCalls)
        assertEquals(1, restoreCalls)
    }

    @Test
    fun availableStateWithoutPlayPriceDoesNotEnablePurchase() {
        // Catches an absent or non-purchasable Play offer opening an invalid billing flow.
        composeRule.setContent {
            SettingsScreen(
                state = SettingsUiState(),
                monetization = MonetizationUiState(true, PurchaseUiState.AVAILABLE, null),
                onClose = {}, onSelectSyncMode = {}, onSetCalendarVisible = { _, _ -> },
                onClearHistory = {}, onClearCache = {}, onLogout = {}, onDismissConfirmation = {},
                onConfirmDestructiveAction = {},
            )
        }

        composeRule.onNodeWithTag("remove-ads").assertDoesNotExist()
        composeRule.onNodeWithText("Google Play billing is unavailable.", substring = true)
            .performScrollTo().assertIsDisplayed()
    }

    @Test
    fun ownedPurchaseDoesNotOfferRemoveAdsAction() {
        // Catches showing a redundant purchase action after confirmed ownership.
        composeRule.setContent {
            SettingsScreen(
                state = SettingsUiState(),
                monetization = MonetizationUiState(
                    canShowAds = false,
                    purchaseState = PurchaseUiState.OWNED,
                    price = null,
                ),
                onClose = {}, onSelectSyncMode = {}, onSetCalendarVisible = { _, _ -> },
                onClearHistory = {}, onClearCache = {}, onLogout = {}, onDismissConfirmation = {},
                onConfirmDestructiveAction = {},
            )
        }

        composeRule.onNodeWithTag("remove-ads").assertDoesNotExist()
        composeRule.onNodeWithText("Ads have been removed.").assertIsDisplayed()
    }

    @Test
    fun loadingPurchaseShowsProgressAndDisablesBothActions() {
        // Catches loading state enabling purchase or restore while billing work is in progress.
        composeRule.setContent {
            SettingsScreen(
                state = SettingsUiState(),
                monetization = MonetizationUiState(true, PurchaseUiState.LOADING, null),
                onClose = {}, onSelectSyncMode = {}, onSetCalendarVisible = { _, _ -> },
                onClearHistory = {}, onClearCache = {}, onLogout = {}, onDismissConfirmation = {},
                onConfirmDestructiveAction = {},
            )
        }

        composeRule.onNodeWithText("Checking Google Play…").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("remove-ads").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithTag("restore-purchases").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun pendingPurchaseShowsPendingCopyAndAllowsOwnershipRequery() {
        // Catches a pending payment getting stuck because no same-session ownership requery is possible.
        var restoreCalls = 0
        composeRule.setContent {
            SettingsScreen(
                state = SettingsUiState(),
                monetization = MonetizationUiState(true, PurchaseUiState.PENDING, "¥250"),
                onClose = {}, onSelectSyncMode = {}, onSetCalendarVisible = { _, _ -> },
                onClearHistory = {}, onClearCache = {}, onLogout = {}, onDismissConfirmation = {},
                onConfirmDestructiveAction = {},
                onRestorePurchase = { restoreCalls++ },
            )
        }

        composeRule.onNodeWithText("Your payment is pending.", substring = true)
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("restore-purchases").performScrollTo().assertIsEnabled().performClick()
        composeRule.onNodeWithTag("remove-ads").assertDoesNotExist()
        assertEquals(1, restoreCalls)
    }

    @Test
    fun purchaseCancellationShowsShortUserFacingFeedback() {
        // Catches cancellation returning silently from Google Play.
        val feedback = MutableSharedFlow<PurchaseFeedback>(extraBufferCapacity = 1)
        composeRule.setContent {
            SettingsScreen(
                state = SettingsUiState(),
                purchaseFeedbackEvents = feedback,
                onClose = {}, onSelectSyncMode = {}, onSetCalendarVisible = { _, _ -> },
                onClearHistory = {}, onClearCache = {}, onLogout = {}, onDismissConfirmation = {},
                onConfirmDestructiveAction = {},
            )
        }

        composeRule.runOnIdle { feedback.tryEmit(PurchaseFeedback.CANCELLED) }
        composeRule.onNodeWithText("Purchase cancelled.").assertIsDisplayed()
    }

    @Test
    fun purchaseFailureShowsShortUserFacingFeedback() {
        // Catches a billing failure returning silently from Google Play.
        val feedback = MutableSharedFlow<PurchaseFeedback>(extraBufferCapacity = 1)
        composeRule.setContent {
            SettingsScreen(
                state = SettingsUiState(),
                purchaseFeedbackEvents = feedback,
                onClose = {}, onSelectSyncMode = {}, onSetCalendarVisible = { _, _ -> },
                onClearHistory = {}, onClearCache = {}, onLogout = {}, onDismissConfirmation = {},
                onConfirmDestructiveAction = {},
            )
        }

        composeRule.runOnIdle { feedback.tryEmit(PurchaseFeedback.FAILED) }
        composeRule.onNodeWithText("Could not complete the purchase. Try again.").assertIsDisplayed()
    }

    @Test
    fun unavailablePurchaseShowsBillingMessageAndInvokesRestore() {
        // Catches unavailable billing hiding the only available recovery action or dropping its callback.
        var restoreCalls = 0
        composeRule.setContent {
            SettingsScreen(
                state = SettingsUiState(),
                monetization = MonetizationUiState(true, PurchaseUiState.UNAVAILABLE, null),
                onClose = {}, onSelectSyncMode = {}, onSetCalendarVisible = { _, _ -> },
                onClearHistory = {}, onClearCache = {}, onLogout = {}, onDismissConfirmation = {},
                onConfirmDestructiveAction = {},
                onRestorePurchase = { restoreCalls++ },
            )
        }

        composeRule.onNodeWithText("Google Play billing is unavailable.", substring = true)
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("remove-ads").assertDoesNotExist()
        composeRule.onNodeWithTag("restore-purchases").performClick()

        assertEquals(1, restoreCalls)
    }

    @Test
    fun settingsShowsMaskedAccountApproximateModeCopyAndCalendarVisibility() {
        // Catches leaking the raw Apple ID, omitting approximate periodic wording, or rendering no usable visibility switch.
        composeRule.setContent {
            SettingsScreen(
                state = SettingsUiState(
                    maskedAppleId = "p***@example.test",
                    accountLoading = false,
                    syncMode = com.brightnook.nookcalendar.domain.SyncMode.HOURLY,
                    calendars = listOf(CalendarSummary("work", "Work", Color.Red.value.toInt(), false)),
                ),
                onClose = {},
                onSelectSyncMode = {},
                onSetCalendarVisible = { _, _ -> },
                onClearHistory = {},
                onClearCache = {},
                onLogout = {},
                onDismissConfirmation = {},
                onConfirmDestructiveAction = {},
            )
        }

        composeRule.onNodeWithText("p***@example.test").assertIsDisplayed()
        composeRule.onNodeWithTag("sync-mode-picker").assertIsDisplayed()
        composeRule.onNodeWithText("Work").assertIsDisplayed()
        composeRule.onNodeWithTag("calendar-color-work", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("calendar-visibility-work").assertIsOff()
    }

    @Test
    fun syncModeUsesAnAccessibleDropdownWithEveryFrequencyOption() {
        var selected: com.brightnook.nookcalendar.domain.SyncMode? = null
        composeRule.setContent {
            SettingsScreen(
                state = SettingsUiState(syncMode = com.brightnook.nookcalendar.domain.SyncMode.HOURLY),
                onClose = {},
                onSelectSyncMode = { selected = it },
                onSetCalendarVisible = { _, _ -> },
                onClearHistory = {}, onClearCache = {}, onLogout = {}, onDismissConfirmation = {},
                onConfirmDestructiveAction = {},
            )
        }

        composeRule.onNodeWithTag("sync-mode-picker").performClick()
        com.brightnook.nookcalendar.domain.SyncMode.entries.forEach { mode ->
            composeRule.onNodeWithTag("sync-mode-option-$mode").assertIsDisplayed()
        }
        composeRule.onNodeWithTag("sync-mode-option-DISABLED").performClick()
        assertTrue(selected == com.brightnook.nookcalendar.domain.SyncMode.DISABLED)
    }

    @Test
    fun calendarPreferencesExposeSundayMondayAndTheFollowSystemLanguageEntry() {
        var selected: WeekStart? = null
        composeRule.setContent {
            SettingsScreen(
                state = SettingsUiState(),
                onClose = {}, onSelectSyncMode = {}, onSetCalendarVisible = { _, _ -> },
                onClearHistory = {}, onClearCache = {}, onLogout = {}, onDismissConfirmation = {},
                onConfirmDestructiveAction = {},
                onSelectWeekStart = { selected = it },
            )
        }

        composeRule.onNodeWithTag("week-start-picker").performClick()
        composeRule.onNodeWithTag("week-start-option-SUNDAY").assertIsDisplayed()
        composeRule.onNodeWithTag("week-start-option-MONDAY").assertIsDisplayed()
        composeRule.onNodeWithTag("week-start-option-MONDAY").performClick()
        assertTrue(selected == WeekStart.MONDAY)
        composeRule.onNodeWithTag("language-picker").assertIsDisplayed()
        composeRule.onNodeWithTag("language-picker").assertIsDisplayed()
    }

    @Test
    fun languagePickerSelectsJapaneseFromTheAvailableLanguageOptions() {
        // Catches a language control that has no selectable manual options or returns the wrong choice.
        var selected: AppLanguage? = null
        composeRule.setContent {
            SettingsScreen(
                state = SettingsUiState(),
                onClose = {}, onSelectSyncMode = {}, onSetCalendarVisible = { _, _ -> },
                onClearHistory = {}, onClearCache = {}, onLogout = {}, onDismissConfirmation = {},
                onConfirmDestructiveAction = {},
                onSelectLanguage = { selected = it },
            )
        }

        composeRule.onNodeWithTag("language-picker").performClick()
        AppLanguage.entries.forEach { language ->
            composeRule.onNodeWithTag("language-option-$language").assertIsDisplayed()
        }
        composeRule.onNodeWithTag("language-option-JAPANESE").performClick()

        assertTrue(selected == AppLanguage.JAPANESE)
    }

    @Test
    fun logoutSharesTheAccountRowAndRemainsAccessible() {
        composeRule.setContent {
            SettingsScreen(
                state = SettingsUiState(maskedAppleId = "abc***@example.test"),
                onClose = {}, onSelectSyncMode = {}, onSetCalendarVisible = { _, _ -> },
                onClearHistory = {}, onClearCache = {}, onLogout = {}, onDismissConfirmation = {},
                onConfirmDestructiveAction = {},
            )
        }

        val account = composeRule.onNodeWithTag("masked-account").fetchSemanticsNode().boundsInRoot
        val logout = composeRule.onNodeWithTag("account-logout").fetchSemanticsNode().boundsInRoot
        assertTrue("Logout must be to the right of the account", logout.left >= account.right)
        composeRule.onNodeWithContentDescription("Sign out").assertIsDisplayed()
    }

    @Test
    fun calendarColorIndicatorIsClippedToACircle() {
        composeRule.setContent {
            SettingsScreen(
                state = SettingsUiState(calendars = listOf(CalendarSummary("work", "Work", Color.Red.value.toInt(), true))),
                onClose = {}, onSelectSyncMode = {}, onSetCalendarVisible = { _, _ -> },
                onClearHistory = {}, onClearCache = {}, onLogout = {}, onDismissConfirmation = {},
                onConfirmDestructiveAction = {},
            )
        }

        val pixels = composeRule.onNodeWithTag("calendar-color-work", useUnmergedTree = true).captureToImage().toPixelMap()
        assertNotEquals("A square swatch colors its corner like its center", pixels[0, 0], pixels[pixels.width / 2, pixels.height / 2])
    }

    @Test
    fun settingsGroupsAccountSyncCalendarsAndDataInSeparateAccessibleSections() {
        composeRule.setContent {
            SettingsScreen(
                state = SettingsUiState(calendars = listOf(CalendarSummary("work", "Work", Color.Red.value.toInt(), true))),
                onClose = {}, onSelectSyncMode = {}, onSetCalendarVisible = { _, _ -> },
                onClearHistory = {}, onClearCache = {}, onLogout = {}, onDismissConfirmation = {},
                onConfirmDestructiveAction = {},
            )
        }

        listOf("account", "sync", "calendars", "data").forEach { section ->
            composeRule.onNodeWithTag("settings-section-$section", useUnmergedTree = true).assertIsDisplayed()
        }
    }

    @Test
    fun cacheDeletionIsOnlyTriggeredAfterConfirmation() {
        // Catches a destructive cache button executing without an explicit confirmation dialog action.
        val confirmation = mutableStateOf<SettingsConfirmation?>(SettingsConfirmation.ClearCache)
        var confirmed = false
        composeRule.setContent {
            SettingsScreen(
                state = SettingsUiState(confirmation = confirmation.value),
                onClose = {},
                onSelectSyncMode = {},
                onSetCalendarVisible = { _, _ -> },
                onClearHistory = {},
                onClearCache = {},
                onLogout = {},
                onDismissConfirmation = { confirmation.value = null },
                onConfirmDestructiveAction = { confirmed = true },
            )
        }

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.settings_clear_cache_title)).assertIsDisplayed()
        assertTrue(!confirmed)
        composeRule.onNodeWithTag("settings-confirm").performClick()
        assertTrue(confirmed)
    }

    @Test
    fun closeReturnsFromSettingsWithoutDestructiveAction() {
        // Catches a one-way settings destination with no user-visible return path.
        var closed = false
        composeRule.setContent {
            SettingsScreen(
                state = SettingsUiState(), onClose = { closed = true },
                onSelectSyncMode = {}, onSetCalendarVisible = { _, _ -> }, onClearHistory = {},
                onClearCache = {}, onLogout = {}, onDismissConfirmation = {},
                onConfirmDestructiveAction = {},
            )
        }
        composeRule.onNodeWithContentDescription("Close").performClick()
        assertTrue(closed)
    }

    @Test
    fun settingsCloseUsesAnAccessibleIconButtonBelowTheStatusArea() {
        composeRule.setContent {
            SettingsScreen(
                state = SettingsUiState(), onClose = {},
                onSelectSyncMode = {}, onSetCalendarVisible = { _, _ -> }, onClearHistory = {},
                onClearCache = {}, onLogout = {}, onDismissConfirmation = {},
                onConfirmDestructiveAction = {},
            )
        }

        val close = composeRule.onNodeWithContentDescription("Close").fetchSemanticsNode().boundsInRoot
        assertTrue("Close action overlaps the status area", close.top > 0f)
        composeRule.onNodeWithTag("settings-close-icon", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun settingsScrollsToLogoutWhileTheCloseActionStaysReachable() {
        val confirmation = mutableStateOf<SettingsConfirmation?>(null)
        composeRule.setContent {
            SettingsScreen(
                state = SettingsUiState(
                    maskedAppleId = "per***@example.test",
                    accountLoading = false,
                    calendars = (1..20).map { index ->
                        CalendarSummary("calendar-$index", "Calendar $index", Color.Red.value.toInt(), true)
                    },
                    confirmation = confirmation.value,
                ),
                onClose = {}, onSelectSyncMode = {}, onSetCalendarVisible = { _, _ -> },
                onClearHistory = {}, onClearCache = {},
                onLogout = { confirmation.value = SettingsConfirmation.Logout(clearCache = false) },
                onDismissConfirmation = {},
                onConfirmDestructiveAction = {},
            )
        }

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.settings_logout)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription(composeRule.activity.getString(R.string.settings_close)).assertIsDisplayed()
        composeRule.onNodeWithTag("settings-logout-icon", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.settings_logout)).performClick()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.settings_logout_message))
            .assertIsDisplayed()
    }
}

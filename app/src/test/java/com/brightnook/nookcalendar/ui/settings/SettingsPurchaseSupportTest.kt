package com.brightnook.nookcalendar.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.brightnook.nookcalendar.domain.AppLanguage
import com.brightnook.nookcalendar.domain.ThemeSettings
import com.brightnook.nookcalendar.monetization.MonetizationUiState
import kotlinx.coroutines.test.runTest
import com.brightnook.nookcalendar.monetization.PurchaseUiState
import com.brightnook.nookcalendar.ui.LocalizedAppContent
import com.brightnook.nookcalendar.ui.theme.calendarThemePresentation
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val testThemePresentation = calendarThemePresentation(ThemeSettings(), null)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsPurchaseSupportTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun openingSettingsRefreshesThePlayPriceForAnAccountWithoutPremium() = runTest {
        var refreshCalls = 0

        refreshPurchaseSupportWhenSettingsOpened(PurchaseUiState.AVAILABLE) { refreshCalls++ }

        assertEquals(1, refreshCalls)
    }

    @Test
    fun openingSettingsDoesNotReloadProductDetailsForAnOwnedPremiumAccount() = runTest {
        var refreshCalls = 0

        refreshPurchaseSupportWhenSettingsOpened(PurchaseUiState.OWNED) { refreshCalls++ }

        assertEquals(0, refreshCalls)
    }

    @Test
    fun loadingPurchaseSupportKeepsTheKnownStorefrontPriceVisible() {
        // Catches loading UI replacing a confirmed Play price with a generic status message.
        composeRule.setContent {
            LocalizedAppContent(AppLanguage.ENGLISH) {
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
                            purchaseState = PurchaseUiState.LOADING,
                            price = "¥390",
                        ),
                    )
                }
            }
        }

        composeRule.onNodeWithText("Google Play price: ¥390").assertExists()
        composeRule.onNodeWithText("Checking Google Play…").assertDoesNotExist()
    }

    @Test
    fun restoreCheckDisablesOnlyTheRestoreAction() {
        // Catches a restore check blocking a new purchase action as well.
        composeRule.setContent {
            LocalizedAppContent(AppLanguage.ENGLISH) {
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
                            purchaseState = PurchaseUiState.LOADING,
                            price = "¥390",
                            activePurchaseAction = com.brightnook.nookcalendar.monetization.PurchaseAction.RESTORE,
                        ),
                    )
                }
            }
        }

        composeRule.onNodeWithTag("remove-ads").assertIsEnabled()
        composeRule.onNodeWithTag("restore-purchases").assertIsNotEnabled()
    }
}

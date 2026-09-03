package com.brightnook.nookcalendar.ui.settings

import kotlinx.coroutines.test.runTest
import com.brightnook.nookcalendar.monetization.PurchaseUiState
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsPurchaseSupportTest {
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
}

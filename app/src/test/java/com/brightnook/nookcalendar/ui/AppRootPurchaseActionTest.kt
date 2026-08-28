package com.brightnook.nookcalendar.ui

import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AppRootPurchaseActionTest {
    @Test
    fun nonActivityPurchaseDoesNotInvokeBillingCallback() = runTest {
        // Catches a non-Activity composition starting Play Billing without a valid Activity host.
        var calls = 0

        launchPurchaseIfActivity(null, this) { calls++ }
        advanceUntilIdle()

        assertEquals(0, calls)
    }
}

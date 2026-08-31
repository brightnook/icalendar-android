package com.brightnook.nookcalendar.ui

import androidx.activity.ComponentActivity
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppRootPurchaseActionTest {
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
}

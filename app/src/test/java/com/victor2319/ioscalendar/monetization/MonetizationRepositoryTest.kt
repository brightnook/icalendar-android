package com.victor2319.ioscalendar.monetization

import android.app.Activity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], graphicsMode = Config.GraphicsMode.LEGACY)
class MonetizationRepositoryTest {
    @Test
    fun entitledPurchaseDisablesAdsAndExposesPrice() = runTest {
        val gateway = FakeBillingGateway(BillingSnapshot(entitled = true, productPrice = "¥250"))
        val repository = MonetizationRepository(gateway)

        repository.refresh()

        assertFalse(repository.state.value.canShowAds)
        assertEquals(PurchaseUiState.OWNED, repository.state.value.purchaseState)
        assertEquals("¥250", repository.state.value.price)
    }

    @Test
    fun pendingPurchaseNeverDisablesAds() = runTest {
        val gateway = FakeBillingGateway(
            BillingSnapshot(entitled = true, productPrice = "¥250", pending = true),
        )
        val repository = MonetizationRepository(gateway)

        repository.refresh()

        assertTrue(repository.state.value.canShowAds)
        assertEquals(PurchaseUiState.PENDING, repository.state.value.purchaseState)
    }

    @Test
    fun recoverableBillingFailureIsUnavailableAndKeepsAdsVisible() = runTest {
        val gateway = FakeBillingGateway(failure = IllegalStateException("temporary billing failure"))
        val repository = MonetizationRepository(gateway)

        repository.refresh()

        assertTrue(repository.state.value.canShowAds)
        assertEquals(PurchaseUiState.UNAVAILABLE, repository.state.value.purchaseState)
        assertEquals(null, repository.state.value.price)
    }

    @Test
    fun completedPurchaseDisablesAdsAndMarksOwnership() = runTest {
        val gateway = FakeBillingGateway(
            purchaseOutcome = PurchaseOutcome.Completed(BillingSnapshot(true, "¥250")),
        )
        val repository = MonetizationRepository(gateway)

        repository.purchase(Robolectric.buildActivity(Activity::class.java).get())

        assertFalse(repository.state.value.canShowAds)
        assertEquals(PurchaseUiState.OWNED, repository.state.value.purchaseState)
    }

    @Test
    fun cancelledPurchaseKeepsAdsVisibleAndRestoresOfferPrice() = runTest {
        val gateway = FakeBillingGateway(
            purchaseOutcome = PurchaseOutcome.Cancelled("¥250"),
        )
        val repository = MonetizationRepository(gateway)

        repository.purchase(Robolectric.buildActivity(Activity::class.java).get())

        assertTrue(repository.state.value.canShowAds)
        assertEquals(PurchaseUiState.AVAILABLE, repository.state.value.purchaseState)
        assertEquals("¥250", repository.state.value.price)
    }

    private class FakeBillingGateway(
        private val snapshot: BillingSnapshot = BillingSnapshot(false, "¥250"),
        private val failure: Throwable? = null,
        private val purchaseOutcome: PurchaseOutcome = PurchaseOutcome.Failed,
    ) : BillingGateway {
        override suspend fun refresh(): BillingSnapshot = failure?.let { throw it } ?: snapshot

        override suspend fun restore(): BillingSnapshot = failure?.let { throw it } ?: snapshot

        override suspend fun purchase(activity: Activity): PurchaseOutcome = purchaseOutcome
    }
}

package com.victor2319.ioscalendar.monetization

import android.app.Activity
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
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
@Config(sdk = [36])
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
    fun snapshotWithoutPurchasableOfferIsUnavailable() = runTest {
        // Catches a missing product or one-time offer enabling a purchase with no Play price.
        val repository = MonetizationRepository(
            FakeBillingGateway(BillingSnapshot(entitled = false, productPrice = null)),
        )

        repository.refresh()

        assertTrue(repository.state.value.canShowAds)
        assertEquals(PurchaseUiState.UNAVAILABLE, repository.state.value.purchaseState)
        assertEquals(null, repository.state.value.price)
    }

    @Test
    fun missingOfferDuringPurchaseClearsStalePriceAndBecomesUnavailable() = runTest {
        // Catches a product removed from Play remaining purchasable from a stale known price.
        val repository = MonetizationRepository(
            FakeBillingGateway(
                snapshot = BillingSnapshot(false, "¥250"),
                purchaseOutcome = PurchaseOutcome.Unavailable,
            ),
        )
        repository.refresh()

        repository.purchase(Robolectric.buildActivity(Activity::class.java).get())

        assertTrue(repository.state.value.canShowAds)
        assertEquals(PurchaseUiState.UNAVAILABLE, repository.state.value.purchaseState)
        assertEquals(null, repository.state.value.price)
    }

    @Test
    fun pendingPurchaseCanConvergeToOwnedThroughRestoreInSameSession() = runTest {
        // Catches pending state becoming permanent even after Play reports confirmed ownership.
        val repository = MonetizationRepository(
            FakeBillingGateway(
                restoreSnapshot = BillingSnapshot(true, "¥250"),
                purchaseOutcome = PurchaseOutcome.Pending(BillingSnapshot(false, "¥250", pending = true)),
            ),
        )
        repository.purchase(Robolectric.buildActivity(Activity::class.java).get())
        assertEquals(PurchaseUiState.PENDING, repository.state.value.purchaseState)

        repository.restore()

        assertFalse(repository.state.value.canShowAds)
        assertEquals(PurchaseUiState.OWNED, repository.state.value.purchaseState)
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
            snapshot = BillingSnapshot(false, "¥250"),
            purchaseOutcome = PurchaseOutcome.Cancelled(null),
        )
        val repository = MonetizationRepository(gateway)
        repository.refresh()

        repository.purchase(Robolectric.buildActivity(Activity::class.java).get())

        assertTrue(repository.state.value.canShowAds)
        assertEquals(PurchaseUiState.AVAILABLE, repository.state.value.purchaseState)
        assertEquals("¥250", repository.state.value.price)
    }

    @Test
    fun cancelledPurchaseEmitsUserFeedback() = runTest {
        val repository = MonetizationRepository(
            FakeBillingGateway(purchaseOutcome = PurchaseOutcome.Cancelled("¥250")),
        )
        val feedback = async(start = CoroutineStart.UNDISPATCHED) {
            repository.purchaseFeedbackEvents.first()
        }

        repository.purchase(Robolectric.buildActivity(Activity::class.java).get())

        assertEquals(PurchaseFeedback.CANCELLED, feedback.await())
    }

    @Test
    fun failedPurchaseRetainsKnownOfferAndEmitsUserFeedback() = runTest {
        val repository = MonetizationRepository(
            FakeBillingGateway(
                snapshot = BillingSnapshot(false, "¥250"),
                purchaseOutcome = PurchaseOutcome.Failed,
            ),
        )
        repository.refresh()
        val feedback = async(start = CoroutineStart.UNDISPATCHED) {
            repository.purchaseFeedbackEvents.first()
        }

        repository.purchase(Robolectric.buildActivity(Activity::class.java).get())

        assertTrue(repository.state.value.canShowAds)
        assertEquals(PurchaseUiState.AVAILABLE, repository.state.value.purchaseState)
        assertEquals("¥250", repository.state.value.price)
        assertEquals(PurchaseFeedback.FAILED, feedback.await())
    }

    private class FakeBillingGateway(
        private val snapshot: BillingSnapshot = BillingSnapshot(false, "¥250"),
        private val restoreSnapshot: BillingSnapshot = snapshot,
        private val failure: Throwable? = null,
        private val purchaseOutcome: PurchaseOutcome = PurchaseOutcome.Failed,
    ) : BillingGateway {
        override suspend fun refresh(): BillingSnapshot = failure?.let { throw it } ?: snapshot

        override suspend fun restore(): BillingSnapshot = failure?.let { throw it } ?: restoreSnapshot

        override suspend fun purchase(activity: Activity): PurchaseOutcome = purchaseOutcome
    }
}

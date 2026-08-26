package com.victor2319.ioscalendar.monetization

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

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

    private class FakeBillingGateway(
        private val snapshot: BillingSnapshot = BillingSnapshot(false, "¥250"),
        private val failure: Throwable? = null,
    ) : BillingGateway {
        override suspend fun refresh(): BillingSnapshot = failure?.let { throw it } ?: snapshot

        override suspend fun restore(): BillingSnapshot = failure?.let { throw it } ?: snapshot
    }
}

package com.victor2319.ioscalendar.monetization

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PlayBillingMapperTest {
    @Test
    fun purchasedItemMapsToAnEntitledSnapshot() {
        val purchasedItem = PlayPurchase(
            state = PlayPurchaseState.PURCHASED,
            acknowledged = true,
        )

        val snapshot = purchaseSnapshot(purchasedItem, "¥250") { error("already acknowledged") }

        assertEquals(BillingSnapshot(true, "¥250"), snapshot)
    }

    @Test
    fun pendingItemMapsToPendingSnapshot() {
        val pendingItem = PlayPurchase(
            state = PlayPurchaseState.PENDING,
            acknowledged = false,
        )

        val snapshot = purchaseSnapshot(pendingItem, "¥250") { error("pending purchases are not acknowledged") }

        assertEquals(BillingSnapshot(false, "¥250", pending = true), snapshot)
    }

    @Test
    fun unacknowledgedPurchasedItemIsAcknowledgedExactlyOnce() {
        var acknowledgementCount = 0
        val purchasedItem = PlayPurchase(
            state = PlayPurchaseState.PURCHASED,
            acknowledged = false,
        )

        purchaseSnapshot(purchasedItem, "¥250") { acknowledgementCount += 1 }

        assertEquals(1, acknowledgementCount)
    }

    @Test
    fun concurrentRefreshesAcknowledgeTheSamePurchaseTokenOnlyOnce() = runTest {
        val acknowledgements = PurchaseAcknowledgements()
        val acknowledgementStarted = CompletableDeferred<Unit>()
        val allowAcknowledgementToFinish = CompletableDeferred<Unit>()
        var acknowledgementCount = 0

        val firstRefresh = async {
            acknowledgements.acknowledgeOnce("purchase-token") {
                acknowledgementCount += 1
                acknowledgementStarted.complete(Unit)
                allowAcknowledgementToFinish.await()
            }
        }
        acknowledgementStarted.await()
        val secondRefresh = async {
            acknowledgements.acknowledgeOnce("purchase-token") { acknowledgementCount += 1 }
        }
        secondRefresh.await()

        assertEquals(1, acknowledgementCount)
        allowAcknowledgementToFinish.complete(Unit)
        firstRefresh.await()
    }
}

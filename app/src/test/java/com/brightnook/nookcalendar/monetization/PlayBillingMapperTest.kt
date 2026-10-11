package com.brightnook.nookcalendar.monetization

import com.android.billingclient.api.BillingClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PlayBillingMapperTest {
    @Test
    fun completedPlayPurchaseMapsToCompletedOutcome() {
        val purchase = PlayPurchase(
            state = PlayPurchaseState.PURCHASED,
            acknowledged = true,
        )

        val outcome = purchaseOutcome(
            responseCode = BillingClient.BillingResponseCode.OK,
            purchases = listOf(purchase),
            price = "¥250",
        )

        assertEquals(PurchaseOutcome.Completed(BillingSnapshot(true, "¥250")), outcome)
    }

    @Test
    fun userCancelledResultMapsToCancelledOutcome() {
        val outcome = purchaseOutcome(
            responseCode = BillingClient.BillingResponseCode.USER_CANCELED,
            purchases = emptyList(),
            price = "¥250",
        )

        assertEquals(PurchaseOutcome.Cancelled("¥250"), outcome)
    }

    @Test
    fun pendingPlayPurchaseMapsToPendingOutcome() {
        val purchase = PlayPurchase(
            state = PlayPurchaseState.PENDING,
            acknowledged = false,
        )

        val outcome = purchaseOutcome(
            responseCode = BillingClient.BillingResponseCode.OK,
            purchases = listOf(purchase),
            price = "¥250",
        )

        assertEquals(PurchaseOutcome.Pending(BillingSnapshot(false, "¥250", pending = true)), outcome)
    }

    @Test
    fun nonOkPlayResultMapsToFailedOutcome() {
        val outcome = purchaseOutcome(
            responseCode = BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
            purchases = emptyList(),
            price = "¥250",
        )

        assertEquals(PurchaseOutcome.Failed, outcome)
    }

    @Test
    fun cancelledCallerKeepsItsPlaySessionUntilTheTerminalCallback() = runTest {
        val sessions = PurchaseCallbackSessions()
        var launchCount = 0
        val firstAwaiting = CompletableDeferred<Unit>()
        val cancelledCaller = async {
            sessions.runPurchaseSession(
                price = "¥250",
                launch = { launchCount += 1 },
                awaitOutcome = { session ->
                    firstAwaiting.complete(Unit)
                    session.awaiter.await()
                },
            )
        }
        firstAwaiting.await()

        cancelledCaller.cancelAndJoin()
        val retryAwaiting = CompletableDeferred<Unit>()
        val retry = async {
            sessions.runPurchaseSession(
                price = "¥250",
                launch = { launchCount += 1 },
                awaitOutcome = { session ->
                    retryAwaiting.complete(Unit)
                    session.awaiter.await()
                },
            )
        }
        retryAwaiting.await()

        assertEquals(1, launchCount)
        sessions.completeCurrent(PurchaseOutcome.Cancelled("¥250"), emptyList())

        assertEquals(PurchaseOutcome.Cancelled("¥250"), retry.await())
    }

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

package com.victor2319.ioscalendar.monetization

import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class PlayBillingGateway private constructor(
    private val billingClient: BillingClient,
) : BillingGateway {
    private val connectionMutex = Mutex()
    private val purchaseAcknowledgements = PurchaseAcknowledgements()

    override suspend fun refresh(): BillingSnapshot = loadSnapshot()

    override suspend fun restore(): BillingSnapshot = loadSnapshot()

    private suspend fun loadSnapshot(): BillingSnapshot {
        ensureConnected()
        val productDetailsResult = billingClient.queryProductDetails(productDetailsQuery())
        productDetailsResult.billingResult.requireSuccess()
        val price = productDetailsResult.productDetailsList.orEmpty()
            .firstOrNull { it.productId == REMOVE_ADS_FOREVER }
            ?.oneTimePurchaseOfferDetails
            ?.formattedPrice

        val purchasesResult = billingClient.queryPurchasesAsync(purchasesQuery())
        purchasesResult.billingResult.requireSuccess()
        val purchase = purchasesResult.purchasesList
            .firstOrNull { REMOVE_ADS_FOREVER in it.products }
            ?: return BillingSnapshot(entitled = false, productPrice = price)

        var acknowledgementRequired = false
        val snapshot = purchaseSnapshot(purchase.toPlayPurchase(), price) {
            acknowledgementRequired = true
        }
        if (acknowledgementRequired) {
            purchaseAcknowledgements.acknowledgeOnce(purchase.purchaseToken) {
                acknowledge(purchase)
            }
        }
        return snapshot
    }

    private suspend fun ensureConnected() {
        if (billingClient.isReady) return

        connectionMutex.withLock {
            if (billingClient.isReady) return
            billingClient.awaitConnection().requireSuccess()
        }
    }

    private suspend fun BillingClient.awaitConnection(): BillingResult = suspendCancellableCoroutine { continuation ->
        startConnection(
            object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    if (continuation.isActive) continuation.resume(billingResult)
                }

                override fun onBillingServiceDisconnected() = Unit
            },
        )
    }

    private suspend fun acknowledge(purchase: Purchase) {
        billingClient.acknowledgePurchase(
            AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build(),
        ).requireSuccess()
    }

    private fun BillingResult.requireSuccess() {
        check(responseCode == BillingClient.BillingResponseCode.OK) {
            debugMessage.ifBlank { "Google Play Billing request failed ($responseCode)." }
        }
    }

    companion object {
        const val REMOVE_ADS_FOREVER = "remove_ads_forever"

        fun productDetailsQuery(): QueryProductDetailsParams =
            QueryProductDetailsParams.newBuilder()
                .setProductList(
                    listOf(
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(REMOVE_ADS_FOREVER)
                            .setProductType(BillingClient.ProductType.INAPP)
                            .build(),
                    ),
                )
                .build()

        fun purchasesQuery(): QueryPurchasesParams = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        private fun Purchase.toPlayPurchase(): PlayPurchase = PlayPurchase(
            state = when (purchaseState) {
                Purchase.PurchaseState.PURCHASED -> PlayPurchaseState.PURCHASED
                Purchase.PurchaseState.PENDING -> PlayPurchaseState.PENDING
                else -> PlayPurchaseState.UNAVAILABLE
            },
            acknowledged = isAcknowledged,
        )

        fun create(context: Context): PlayBillingGateway {
            val billingClient = BillingClient.newBuilder(context.applicationContext)
                .setListener { _, _ -> Unit }
                .enablePendingPurchases(
                    PendingPurchasesParams.newBuilder()
                        .enableOneTimeProducts()
                        .build(),
                )
                .enableAutoServiceReconnection()
                .build()
            return PlayBillingGateway(billingClient)
        }
    }
}

internal enum class PlayPurchaseState {
    PURCHASED,
    PENDING,
    UNAVAILABLE,
}

internal data class PlayPurchase(
    val state: PlayPurchaseState,
    val acknowledged: Boolean,
)

internal fun purchaseSnapshot(
    purchase: PlayPurchase,
    productPrice: String?,
    acknowledge: () -> Unit = {},
): BillingSnapshot = when (purchase.state) {
    PlayPurchaseState.PURCHASED -> {
        if (!purchase.acknowledged) acknowledge()
        BillingSnapshot(entitled = true, productPrice = productPrice)
    }

    PlayPurchaseState.PENDING -> BillingSnapshot(
        entitled = false,
        productPrice = productPrice,
        pending = true,
    )

    PlayPurchaseState.UNAVAILABLE -> BillingSnapshot(entitled = false, productPrice = productPrice)
}

internal class PurchaseAcknowledgements {
    private val mutex = Mutex()
    private val inFlightTokens = mutableSetOf<String>()

    suspend fun acknowledgeOnce(purchaseToken: String, acknowledge: suspend () -> Unit) {
        val claimed = mutex.withLock { inFlightTokens.add(purchaseToken) }
        if (!claimed) return

        try {
            acknowledge()
        } finally {
            mutex.withLock { inFlightTokens.remove(purchaseToken) }
        }
    }
}

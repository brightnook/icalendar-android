package com.brightnook.nookcalendar.monetization

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class PlayBillingGateway private constructor(
    private val billingClient: BillingClient,
) : BillingGateway {
    private val connectionMutex = Mutex()
    private val purchaseMutex = Mutex()
    private val purchaseAcknowledgements = PurchaseAcknowledgements()
    private val purchaseSessions = PurchaseCallbackSessions()
    private val lastFormattedPrice = AtomicReference<String?>(null)

    override suspend fun refresh(): BillingSnapshot = loadSnapshot()

    override suspend fun restore(): BillingSnapshot = loadSnapshot()

    override suspend fun purchase(activity: Activity): PurchaseOutcome = purchaseMutex.withLock {
        purchaseSessions.current()?.let { activeSession ->
            return@withLock awaitPurchase(activeSession)
        }

        ensureConnected()
        val productDetailsResult = billingClient.queryProductDetails(productDetailsQuery())
        if (productDetailsResult.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            return@withLock PurchaseOutcome.Failed
        }
        val productDetails = productDetailsResult.productDetailsList.orEmpty()
            .firstOrNull { it.productId == REMOVE_ADS_FOREVER }
            ?: return@withLock PurchaseOutcome.Unavailable
        val offerDetails = productDetails.oneTimePurchaseOfferDetails
            ?: return@withLock PurchaseOutcome.Unavailable
        val offerToken = offerDetails.offerToken
            ?: return@withLock PurchaseOutcome.Unavailable
        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .setOfferToken(offerToken)
            .build()
        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()
        purchaseSessions.runPurchaseSession(
            price = rememberFormattedPrice(offerDetails.formattedPrice),
            launch = { session ->
                val launchResult = billingClient.launchBillingFlow(activity, billingFlowParams)
                if (launchResult.responseCode != BillingClient.BillingResponseCode.OK) {
                    completePurchase(session, launchResult, emptyList())
                }
            },
            awaitOutcome = ::awaitPurchase,
        )
    }

    private suspend fun loadSnapshot(): BillingSnapshot {
        ensureConnected()
        val purchasesResult = billingClient.queryPurchasesAsync(purchasesQuery())
        purchasesResult.billingResult.requireSuccess()
        val purchase = purchasesResult.purchasesList
            .firstOrNull { REMOVE_ADS_FOREVER in it.products }

        if (purchase != null) {
            var acknowledgementRequired = false
            val snapshot = purchaseSnapshot(purchase.toPlayPurchase(), productPrice = lastFormattedPrice.get()) {
                acknowledgementRequired = true
            }
            if (acknowledgementRequired) {
                purchaseAcknowledgements.acknowledgeOnce(purchase.purchaseToken) {
                    acknowledge(purchase)
                }
            }
            return snapshot
        }

        val productDetailsResult = billingClient.queryProductDetails(productDetailsQuery())
        productDetailsResult.billingResult.requireSuccess()
        val price = productDetailsResult.productDetailsList.orEmpty()
            .firstOrNull { it.productId == REMOVE_ADS_FOREVER }
            ?.oneTimePurchaseOfferDetails
            ?.formattedPrice
            ?.also(::rememberFormattedPrice)

        return BillingSnapshot(entitled = false, productPrice = price)
    }

    private suspend fun ensureConnected() {
        if (billingClient.isReady) return

        connectionMutex.withLock {
            if (billingClient.isReady) return
            billingClient.awaitConnection().requireSuccess()
        }
    }

    private fun rememberFormattedPrice(price: String): String = price.also { lastFormattedPrice.set(it) }

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

    private suspend fun awaitPurchase(session: PurchaseCallbackSession): PurchaseOutcome {
        val outcome = session.awaiter.await()
        if (outcome is PurchaseOutcome.Completed) {
            session.callbackPurchases.get().orEmpty()
                .filter { purchase ->
                    REMOVE_ADS_FOREVER in purchase.products &&
                        purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
                        !purchase.isAcknowledged
                }
                .forEach { purchase ->
                    purchaseAcknowledgements.acknowledgeOnce(purchase.purchaseToken) {
                        acknowledge(purchase)
                    }
                }
        }
        return outcome
    }

    private fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>) {
        val session = purchaseSessions.current() ?: return
        completePurchase(session, billingResult, purchases)
    }

    private fun completePurchase(
        session: PurchaseCallbackSession,
        billingResult: BillingResult,
        purchases: List<Purchase>,
    ) {
        val matchingPurchases = purchases.filter { REMOVE_ADS_FOREVER in it.products }
        purchaseSessions.complete(
            session = session,
            callbackPurchases = matchingPurchases,
            outcome = purchaseOutcome(
                responseCode = billingResult.responseCode,
                purchases = matchingPurchases.map { it.toPlayPurchase() },
                price = session.price,
            ),
        )
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
            val purchasesUpdatedBridge = PurchasesUpdatedBridge()
            val billingClient = BillingClient.newBuilder(context.applicationContext)
                .setListener(purchasesUpdatedBridge)
                .enablePendingPurchases(
                    PendingPurchasesParams.newBuilder()
                        .enableOneTimeProducts()
                        .build(),
                )
                .enableAutoServiceReconnection()
                .build()
            return PlayBillingGateway(billingClient).also { gateway ->
                purchasesUpdatedBridge.onPurchasesUpdated = gateway::onPurchasesUpdated
            }
        }
    }
}

internal data class PurchaseCallbackSession(
    val price: String?,
    val awaiter: CompletableDeferred<PurchaseOutcome> = CompletableDeferred(),
    val callbackPurchases: AtomicReference<List<Purchase>?> = AtomicReference(null),
)

private data class PurchaseCallbackSessionClaim(
    val session: PurchaseCallbackSession,
    val shouldLaunch: Boolean,
)

internal class PurchaseCallbackSessions {
    private val activeSession = AtomicReference<PurchaseCallbackSession?>(null)

    fun current(): PurchaseCallbackSession? = activeSession.get()

    private fun beginOrJoin(price: String?): PurchaseCallbackSessionClaim {
        while (true) {
            activeSession.get()?.let { active ->
                return PurchaseCallbackSessionClaim(active, shouldLaunch = false)
            }
            val newSession = PurchaseCallbackSession(price)
            if (activeSession.compareAndSet(null, newSession)) {
                return PurchaseCallbackSessionClaim(newSession, shouldLaunch = true)
            }
        }
    }

    suspend fun runPurchaseSession(
        price: String?,
        launch: (PurchaseCallbackSession) -> Unit,
        awaitOutcome: suspend (PurchaseCallbackSession) -> PurchaseOutcome,
    ): PurchaseOutcome {
        val claim = beginOrJoin(price)
        if (claim.shouldLaunch) {
            try {
                launch(claim.session)
            } catch (failure: Throwable) {
                complete(claim.session, PurchaseOutcome.Failed, emptyList())
                throw failure
            }
        }
        return awaitOutcome(claim.session)
    }

    fun completeCurrent(outcome: PurchaseOutcome, callbackPurchases: List<Purchase>): Boolean {
        val session = activeSession.get() ?: return false
        return complete(session, outcome, callbackPurchases)
    }

    fun complete(
        session: PurchaseCallbackSession,
        outcome: PurchaseOutcome,
        callbackPurchases: List<Purchase>,
    ): Boolean {
        if (activeSession.get() !== session) return false
        if (!session.callbackPurchases.compareAndSet(null, callbackPurchases)) return false
        if (!session.awaiter.complete(outcome)) return false
        activeSession.compareAndSet(session, null)
        return true
    }
}

private class PurchasesUpdatedBridge : PurchasesUpdatedListener {
    @Volatile
    var onPurchasesUpdated: ((BillingResult, List<Purchase>) -> Unit)? = null

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        onPurchasesUpdated?.invoke(billingResult, purchases.orEmpty())
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

internal fun purchaseOutcome(
    responseCode: Int,
    purchases: List<PlayPurchase>,
    price: String?,
): PurchaseOutcome {
    if (responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
        return PurchaseOutcome.Cancelled(price)
    }
    if (responseCode != BillingClient.BillingResponseCode.OK) return PurchaseOutcome.Failed

    val purchased = purchases.firstOrNull { it.state == PlayPurchaseState.PURCHASED }
    if (purchased != null) {
        return PurchaseOutcome.Completed(BillingSnapshot(entitled = true, productPrice = price))
    }
    val pending = purchases.firstOrNull { it.state == PlayPurchaseState.PENDING }
    if (pending != null) {
        return PurchaseOutcome.Pending(
            BillingSnapshot(entitled = false, productPrice = price, pending = true),
        )
    }
    return PurchaseOutcome.Failed
}

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

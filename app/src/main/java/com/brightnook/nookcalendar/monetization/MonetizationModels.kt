package com.brightnook.nookcalendar.monetization

enum class PurchaseUiState {
    AVAILABLE,
    LOADING,
    OWNED,
    PENDING,
    UNAVAILABLE,
}

data class BillingSnapshot(
    val entitled: Boolean,
    val productPrice: String?,
    val pending: Boolean = false,
)

sealed interface PurchaseOutcome {
    data class Completed(val snapshot: BillingSnapshot) : PurchaseOutcome

    data class Pending(val snapshot: BillingSnapshot) : PurchaseOutcome

    data class Cancelled(val price: String?) : PurchaseOutcome

    data object Unavailable : PurchaseOutcome

    data object Failed : PurchaseOutcome
}

enum class PurchaseFeedback {
    CANCELLED,
    FAILED,
}

data class MonetizationUiState(
    val canShowAds: Boolean,
    val purchaseState: PurchaseUiState,
    val price: String?,
)

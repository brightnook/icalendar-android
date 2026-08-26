package com.victor2319.ioscalendar.monetization

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

data class MonetizationUiState(
    val canShowAds: Boolean,
    val purchaseState: PurchaseUiState,
    val price: String?,
)

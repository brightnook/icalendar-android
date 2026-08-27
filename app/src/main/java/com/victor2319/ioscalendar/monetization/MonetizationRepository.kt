package com.victor2319.ioscalendar.monetization

import android.app.Activity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException

class MonetizationRepository(
    private val billingGateway: BillingGateway,
) {
    private val _state = MutableStateFlow(
        MonetizationUiState(
            canShowAds = true,
            purchaseState = PurchaseUiState.AVAILABLE,
            price = null,
        ),
    )

    val state: StateFlow<MonetizationUiState> = _state.asStateFlow()

    suspend fun refresh() {
        load { billingGateway.refresh() }
    }

    suspend fun restore() {
        load { billingGateway.restore() }
    }

    suspend fun purchase(activity: Activity) {
        _state.value = _state.value.copy(purchaseState = PurchaseUiState.LOADING)
        try {
            _state.value = billingGateway.purchase(activity).toUiState()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            _state.value = unavailableUiState()
        }
    }

    private suspend fun load(operation: suspend () -> BillingSnapshot) {
        _state.value = _state.value.copy(purchaseState = PurchaseUiState.LOADING)
        try {
            _state.value = operation().toUiState()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            _state.value = unavailableUiState()
        }
    }

    private fun PurchaseOutcome.toUiState(): MonetizationUiState = when (this) {
        is PurchaseOutcome.Completed -> snapshot.toUiState()
        is PurchaseOutcome.Pending -> snapshot.toUiState()
        is PurchaseOutcome.Cancelled -> MonetizationUiState(
            canShowAds = true,
            purchaseState = PurchaseUiState.AVAILABLE,
            price = price,
        )
        PurchaseOutcome.Failed -> unavailableUiState()
    }

    private fun unavailableUiState() = MonetizationUiState(
        canShowAds = true,
        purchaseState = PurchaseUiState.UNAVAILABLE,
        price = null,
    )

    private fun BillingSnapshot.toUiState(): MonetizationUiState {
        val purchaseState = when {
            pending -> PurchaseUiState.PENDING
            entitled -> PurchaseUiState.OWNED
            else -> PurchaseUiState.AVAILABLE
        }
        return MonetizationUiState(
            canShowAds = !entitled || pending,
            purchaseState = purchaseState,
            price = productPrice,
        )
    }
}

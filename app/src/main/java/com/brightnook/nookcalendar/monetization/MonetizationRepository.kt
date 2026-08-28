package com.brightnook.nookcalendar.monetization

import android.app.Activity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class MonetizationRepository(
    private val billingGateway: BillingGateway,
) {
    private val _state = MutableStateFlow(
        MonetizationUiState(
            canShowAds = true,
            purchaseState = PurchaseUiState.UNAVAILABLE,
            price = null,
        ),
    )

    val state: StateFlow<MonetizationUiState> = _state.asStateFlow()
    private val _purchaseFeedbackEvents = MutableSharedFlow<PurchaseFeedback>(extraBufferCapacity = 1)
    val purchaseFeedbackEvents: SharedFlow<PurchaseFeedback> = _purchaseFeedbackEvents.asSharedFlow()

    suspend fun refresh() {
        load { billingGateway.refresh() }
    }

    suspend fun restore() {
        load { billingGateway.restore() }
    }

    suspend fun purchase(activity: Activity) {
        val previousState = _state.value
        _state.value = previousState.copy(purchaseState = PurchaseUiState.LOADING)
        try {
            when (val outcome = billingGateway.purchase(activity)) {
                is PurchaseOutcome.Cancelled -> {
                    _state.value = outcome.toUiState(previousState)
                    _purchaseFeedbackEvents.tryEmit(PurchaseFeedback.CANCELLED)
                }
                PurchaseOutcome.Failed -> {
                    _state.value = previousState.afterRecoverableFailure()
                    _purchaseFeedbackEvents.tryEmit(PurchaseFeedback.FAILED)
                }
                PurchaseOutcome.Unavailable -> _state.value = unavailableUiState()
                else -> _state.value = outcome.toUiState(previousState)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            _state.value = previousState.afterRecoverableFailure()
            _purchaseFeedbackEvents.tryEmit(PurchaseFeedback.FAILED)
        }
    }

    private suspend fun load(operation: suspend () -> BillingSnapshot) {
        val previousState = _state.value
        _state.value = previousState.copy(purchaseState = PurchaseUiState.LOADING)
        try {
            _state.value = operation().toUiState()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            _state.value = previousState.afterRecoverableFailure()
            _purchaseFeedbackEvents.tryEmit(PurchaseFeedback.FAILED)
        }
    }

    private fun PurchaseOutcome.toUiState(previousState: MonetizationUiState): MonetizationUiState = when (this) {
        is PurchaseOutcome.Completed -> snapshot.toUiState()
        is PurchaseOutcome.Pending -> snapshot.toUiState()
        is PurchaseOutcome.Cancelled -> (price ?: previousState.price).toOfferUiState()
        PurchaseOutcome.Unavailable -> unavailableUiState()
        PurchaseOutcome.Failed -> previousState.afterRecoverableFailure()
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
            productPrice.isNullOrBlank() -> PurchaseUiState.UNAVAILABLE
            else -> PurchaseUiState.AVAILABLE
        }
        return MonetizationUiState(
            canShowAds = !entitled || pending,
            purchaseState = purchaseState,
            price = productPrice,
        )
    }

    private fun MonetizationUiState.afterRecoverableFailure(): MonetizationUiState = when {
        purchaseState == PurchaseUiState.OWNED -> this
        purchaseState == PurchaseUiState.PENDING -> this
        else -> price.toOfferUiState()
    }

    private fun String?.toOfferUiState(): MonetizationUiState = if (isNullOrBlank()) {
        unavailableUiState()
    } else {
        MonetizationUiState(
            canShowAds = true,
            purchaseState = PurchaseUiState.AVAILABLE,
            price = this,
        )
    }
}

package com.brightnook.nookcalendar.monetization

import android.app.Activity

interface BillingGateway {
    suspend fun refresh(): BillingSnapshot

    suspend fun restore(): BillingSnapshot

    suspend fun purchase(activity: Activity): PurchaseOutcome = PurchaseOutcome.Failed
}

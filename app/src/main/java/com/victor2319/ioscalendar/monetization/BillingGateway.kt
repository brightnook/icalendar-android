package com.victor2319.ioscalendar.monetization

interface BillingGateway {
    suspend fun refresh(): BillingSnapshot

    suspend fun restore(): BillingSnapshot
}

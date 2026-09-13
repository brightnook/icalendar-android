package com.brightnook.nookcalendar.domain

import kotlinx.coroutines.flow.Flow

interface EventEditingRepository {
    fun observeWritableCalendars(): Flow<List<CalendarSummary>>
    suspend fun create(draft: EventDraft): EventEditingResult
    suspend fun update(instanceId: String, draft: EventDraft): EventEditingResult
    suspend fun delete(instanceId: String): EventEditingResult
}

sealed interface EventEditingResult {
    data object Success : EventEditingResult
    data object SavedRefreshPending : EventEditingResult
    data object AuthenticationRequired : EventEditingResult
    data object NotFound : EventEditingResult
    data object ReadOnly : EventEditingResult
    data object RecurringUnsupported : EventEditingResult
    data object Conflict : EventEditingResult
    data object NetworkFailure : EventEditingResult
    data object Invalid : EventEditingResult
}

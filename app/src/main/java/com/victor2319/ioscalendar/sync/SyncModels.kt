package com.victor2319.ioscalendar.sync

import java.time.YearMonth

sealed interface SyncRequest {
    data object Initial : SyncRequest
    data object Refresh : SyncRequest
    data class EnsureMonth(val month: YearMonth) : SyncRequest
}

data class SyncCounts(
    val calendars: Int = 0,
    val events: Int = 0,
    val instances: Int = 0,
) {
    operator fun plus(other: SyncCounts) = SyncCounts(
        calendars + other.calendars,
        events + other.events,
        instances + other.instances,
    )
}

enum class SyncFailureCategory {
    AUTHENTICATION,
    TEMPORARY,
    NETWORK,
    INVALID_DATA,
    REPOSITORY,
}

sealed interface SyncResult {
    val counts: SyncCounts
    val calendarIds: Set<String>

    data class Success(
        override val counts: SyncCounts = SyncCounts(),
        override val calendarIds: Set<String> = emptySet(),
    ) : SyncResult

    data class PartialSuccess(
        override val counts: SyncCounts,
        override val calendarIds: Set<String>,
        val failures: Map<String, SyncFailureCategory>,
    ) : SyncResult

    data class AuthenticationRequired(
        override val counts: SyncCounts = SyncCounts(),
        override val calendarIds: Set<String> = emptySet(),
    ) : SyncResult

    data class RetryableFailure(
        val category: SyncFailureCategory,
        override val counts: SyncCounts = SyncCounts(),
        override val calendarIds: Set<String> = emptySet(),
    ) : SyncResult
}

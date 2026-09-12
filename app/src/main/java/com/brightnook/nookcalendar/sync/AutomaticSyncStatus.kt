package com.brightnook.nookcalendar.sync

import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

data class AutomaticSyncStatus(
    val completedAt: Instant,
    val outcome: AutomaticSyncOutcome,
)

enum class AutomaticSyncOutcome {
    COMPLETED,
    RETRYING,
    AUTHENTICATION_REQUIRED,
}

fun interface AutomaticSyncStatusRecorder {
    suspend fun record(status: AutomaticSyncStatus)
}

interface AutomaticSyncStatusStore : AutomaticSyncStatusRecorder {
    val status: Flow<AutomaticSyncStatus?>
}

object NoOpAutomaticSyncStatusRecorder : AutomaticSyncStatusRecorder {
    override suspend fun record(status: AutomaticSyncStatus) = Unit
}

object NoOpAutomaticSyncStatusStore : AutomaticSyncStatusStore {
    override val status: Flow<AutomaticSyncStatus?> = flowOf(null)

    override suspend fun record(status: AutomaticSyncStatus) = Unit
}

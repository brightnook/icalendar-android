package com.victor2319.ioscalendar.sync

interface SyncCoordinator {
    suspend fun sync(request: SyncRequest): SyncResult
}

package com.brightnook.nookcalendar.sync

interface SyncCoordinator {
    suspend fun sync(request: SyncRequest): SyncResult
    suspend fun syncManually(request: SyncRequest): SyncResult = sync(request)
}

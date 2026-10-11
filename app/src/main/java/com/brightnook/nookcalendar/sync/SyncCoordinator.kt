package com.brightnook.nookcalendar.sync

interface SyncCoordinator {
    suspend fun sync(request: SyncRequest): SyncResult
}

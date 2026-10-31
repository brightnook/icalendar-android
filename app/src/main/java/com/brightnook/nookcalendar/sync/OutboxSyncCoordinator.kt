package com.brightnook.nookcalendar.sync

import com.brightnook.nookcalendar.data.EventOutboxProcessor
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class OutboxSyncCoordinator(
    private val delegate: SyncCoordinator,
    private val processor: EventOutboxProcessor,
) : SyncCoordinator {
    private val transitions = Mutex()
    override suspend fun sync(request: SyncRequest): SyncResult = run(request, false)
    override suspend fun syncManually(request: SyncRequest): SyncResult = run(request, true)
    private suspend fun run(request: SyncRequest, manual: Boolean): SyncResult = transitions.withLock {
        processor.process(manual)
        val result = delegate.sync(request)
        processor.reconcile()
        result
    }
}

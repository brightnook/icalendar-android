package com.victor2319.ioscalendar.sync

import kotlinx.coroutines.sync.Mutex

internal object SyncMutex {
    val instance = Mutex()
}

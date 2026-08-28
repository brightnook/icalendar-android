package com.brightnook.nookcalendar.sync

import kotlinx.coroutines.sync.Mutex

internal object SyncMutex {
    val instance = Mutex()
}

package com.brightnook.nookcalendar.ui.main

import androidx.annotation.StringRes
import com.brightnook.nookcalendar.R
import com.brightnook.nookcalendar.domain.PendingEventSyncState

@StringRes
internal fun PendingEventSyncState.statusResource(): Int = when (this) {
    PendingEventSyncState.PENDING -> R.string.event_sync_pending
    PendingEventSyncState.SYNCING -> R.string.event_sync_sending
    PendingEventSyncState.FAILED -> R.string.event_sync_failed
    PendingEventSyncState.CONFLICT -> R.string.event_sync_conflict
}

package com.brightnook.nookcalendar.sync

import android.content.Context
import androidx.work.*
import com.brightnook.nookcalendar.CalendarApplication

class EventOutboxWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as CalendarApplication).container
        container.syncCoordinator.sync(SyncRequest.Refresh)
        container.widgetUpdater.refreshAll()
        container.reminderScheduler.reconcile()
        // Upload failures stay visible in the journal. WorkManager must not retry them automatically.
        return Result.success()
    }

    companion object {
        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<EventOutboxWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
            WorkManager.getInstance(context).enqueueUniqueWork("event-outbox", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}

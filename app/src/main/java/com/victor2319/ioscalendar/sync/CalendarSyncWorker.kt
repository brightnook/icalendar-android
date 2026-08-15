package com.victor2319.ioscalendar.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.victor2319.ioscalendar.CalendarApplication

class CalendarSyncWorker @JvmOverloads constructor(
    appContext: Context,
    workerParams: WorkerParameters,
    private val coordinator: SyncCoordinator =
        (appContext.applicationContext as CalendarApplication).container.syncCoordinator,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result = when (coordinator.sync(SyncRequest.Refresh)) {
        is SyncResult.Success,
        is SyncResult.PartialSuccess,
        -> Result.success()

        is SyncResult.AuthenticationRequired -> Result.failure()
        is SyncResult.RetryableFailure -> Result.retry()
    }
}

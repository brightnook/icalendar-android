package com.brightnook.nookcalendar.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.brightnook.nookcalendar.CalendarApplication
import com.brightnook.nookcalendar.widget.WidgetRefreshNotifier

class CalendarSyncWorker @JvmOverloads constructor(
    appContext: Context,
    workerParams: WorkerParameters,
    private val coordinator: SyncCoordinator =
        (appContext.applicationContext as CalendarApplication).container.syncCoordinator,
    private val widgetUpdater: WidgetRefreshNotifier =
        (appContext.applicationContext as CalendarApplication).container.widgetUpdater,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val result = coordinator.sync(SyncRequest.Refresh)
        if (result is SyncResult.Success || result is SyncResult.PartialSuccess) widgetUpdater.refreshAll()
        return when (result) {
            is SyncResult.Success,
            is SyncResult.PartialSuccess,
            -> Result.success()

            is SyncResult.AuthenticationRequired -> Result.failure()
            is SyncResult.RetryableFailure -> Result.retry()
        }
    }
}

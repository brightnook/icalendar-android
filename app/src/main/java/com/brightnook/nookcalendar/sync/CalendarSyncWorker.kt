package com.brightnook.nookcalendar.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.brightnook.nookcalendar.CalendarApplication
import com.brightnook.nookcalendar.widget.WidgetRefreshNotifier
import java.time.Instant

class CalendarSyncWorker @JvmOverloads constructor(
    appContext: Context,
    workerParams: WorkerParameters,
    private val coordinator: SyncCoordinator =
        (appContext.applicationContext as CalendarApplication).container.syncCoordinator,
    private val widgetUpdater: WidgetRefreshNotifier =
        (appContext.applicationContext as CalendarApplication).container.widgetUpdater,
    private val statusRecorder: AutomaticSyncStatusRecorder =
        (appContext.applicationContext as CalendarApplication).container.automaticSyncStatusStore,
    private val now: () -> Instant = Instant::now,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val result = coordinator.sync(SyncRequest.Refresh)
        if (result is SyncResult.Success || result is SyncResult.PartialSuccess) widgetUpdater.refreshAll()
        if (inputData.getBoolean(INPUT_IS_AUTOMATIC, false)) {
            statusRecorder.record(
                AutomaticSyncStatus(
                    completedAt = now(),
                    outcome = when (result) {
                        is SyncResult.Success -> AutomaticSyncOutcome.COMPLETED
                        is SyncResult.PartialSuccess -> if (
                            result.failures.values.any {
                                it == SyncFailureCategory.NETWORK || it == SyncFailureCategory.TEMPORARY
                            }
                        ) AutomaticSyncOutcome.RETRYING else AutomaticSyncOutcome.COMPLETED
                        is SyncResult.AuthenticationRequired -> AutomaticSyncOutcome.AUTHENTICATION_REQUIRED
                        is SyncResult.RetryableFailure -> AutomaticSyncOutcome.RETRYING
                    },
                ),
            )
        }
        return when (result) {
            is SyncResult.Success -> Result.success()

            is SyncResult.PartialSuccess -> if (
                result.failures.values.any {
                    it == SyncFailureCategory.NETWORK || it == SyncFailureCategory.TEMPORARY
                }
            ) {
                Result.retry()
            } else {
                Result.success()
            }

            is SyncResult.AuthenticationRequired -> Result.failure()
            is SyncResult.RetryableFailure -> Result.retry()
        }
    }

    companion object {
        const val INPUT_IS_AUTOMATIC = "is_automatic_sync"
    }
}

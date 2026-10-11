package com.brightnook.nookcalendar.widget

import android.app.job.JobParameters
import android.app.job.JobService
import com.brightnook.nookcalendar.CalendarApplication
import com.brightnook.nookcalendar.sync.SyncFailureCategory
import com.brightnook.nookcalendar.sync.SyncRequest
import com.brightnook.nookcalendar.sync.SyncResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class WidgetSyncJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var runningJob: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        runningJob?.cancel()
        runningJob = scope.launch {
            val shouldRetry = try {
                val container = (application as CalendarApplication).container
                val result = container.syncCoordinator.sync(SyncRequest.Refresh)
                if (
                    (result is SyncResult.Success || result is SyncResult.PartialSuccess) &&
                    result.counts.eventDataChanged
                ) {
                    container.widgetUpdater.refreshEventDataAll()
                }
                result.shouldRetry()
            } catch (_: CancellationException) {
                return@launch
            } catch (_: Throwable) {
                true
            }
            jobFinished(params, shouldRetry)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        runningJob?.cancel()
        runningJob = null
        return true
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

private fun SyncResult.shouldRetry(): Boolean = when (this) {
    is SyncResult.RetryableFailure -> true
    is SyncResult.PartialSuccess -> failures.values.any {
        it == SyncFailureCategory.NETWORK || it == SyncFailureCategory.TEMPORARY
    }
    is SyncResult.Success,
    is SyncResult.AuthenticationRequired,
    -> false
}

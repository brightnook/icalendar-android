package com.victor2319.ioscalendar.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CalendarSyncWorkerTest {
    @Test
    fun retryableCoordinatorFailureRequestsWorkManagerRetry() = runTest {
        // Catches treating a temporary server or network failure as terminal work.
        val result = worker(SyncResult.RetryableFailure(SyncFailureCategory.NETWORK)).doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
    }

    @Test
    fun authenticationRequirementFailsWorkWithoutRetryingCredentials() = runTest {
        // Catches background retries after the user must provide new credentials.
        val result = worker(SyncResult.AuthenticationRequired()).doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
    }

    @Test
    fun successAndPartialSuccessCompleteWork() = runTest {
        // Catches a partial local calendar update being retried despite a completed coordinator pass.
        val success = worker(SyncResult.Success()).doWork()
        val partial = worker(
            SyncResult.PartialSuccess(
                counts = SyncCounts(calendars = 1),
                calendarIds = setOf("work"),
                failures = mapOf("home" to SyncFailureCategory.INVALID_DATA),
            ),
        ).doWork()

        assertEquals(ListenableWorker.Result.success(), success)
        assertEquals(ListenableWorker.Result.success(), partial)
    }

    private fun worker(syncResult: SyncResult): CalendarSyncWorker {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return TestListenableWorkerBuilder<CalendarSyncWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ) = CalendarSyncWorker(appContext, workerParameters, FakeCoordinator(syncResult))
            })
            .build()
    }
}

private class FakeCoordinator(private val result: SyncResult) : SyncCoordinator {
    override suspend fun sync(request: SyncRequest): SyncResult = result
}

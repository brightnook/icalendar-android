package com.brightnook.nookcalendar.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.time.Instant
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

    @Test
    fun periodicSuccessRecordsTheCompletedTimeForSettingsStatus() = runTest {
        // Catches a background pass finishing normally without leaving users any evidence of its last run.
        val completedAt = Instant.parse("2026-09-10T08:00:00Z")
        val statusRecorder = RecordingAutomaticSyncStatusRecorder()

        val result = worker(
            syncResult = SyncResult.Success(),
            statusRecorder = statusRecorder,
            now = { completedAt },
            isAutomatic = true,
        ).doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(
            AutomaticSyncStatus(completedAt, AutomaticSyncOutcome.COMPLETED),
            statusRecorder.status,
        )
    }

    @Test
    fun manualSuccessDoesNotReplaceTheLastAutomaticSyncStatus() = runTest {
        // Catches tapping Refresh making the settings screen claim that a scheduled sync just ran.
        val statusRecorder = RecordingAutomaticSyncStatusRecorder()

        val result = worker(
            syncResult = SyncResult.Success(),
            statusRecorder = statusRecorder,
        ).doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(null, statusRecorder.status)
    }

    @Test
    fun partialNetworkFailureRequestsRetryAfterPublishingSuccessfulCalendars() = runTest {
        // Catches a failed calendar being silently abandoned just because another calendar completed.
        val result = worker(
            SyncResult.PartialSuccess(
                counts = SyncCounts(calendars = 2),
                calendarIds = setOf("personal", "work"),
                failures = mapOf("shared" to SyncFailureCategory.NETWORK),
            ),
        ).doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
    }

    private fun worker(
        syncResult: SyncResult,
        statusRecorder: AutomaticSyncStatusRecorder = NoOpAutomaticSyncStatusRecorder,
        now: () -> Instant = { Instant.EPOCH },
        isAutomatic: Boolean = false,
    ): CalendarSyncWorker {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return TestListenableWorkerBuilder<CalendarSyncWorker>(context)
            .setInputData(
                workDataOf(CalendarSyncWorker.INPUT_IS_AUTOMATIC to isAutomatic),
            )
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ) = CalendarSyncWorker(
                    appContext,
                    workerParameters,
                    FakeCoordinator(syncResult),
                    statusRecorder = statusRecorder,
                    now = now,
                )
            })
            .build()
    }
}

private class FakeCoordinator(private val result: SyncResult) : SyncCoordinator {
    override suspend fun sync(request: SyncRequest): SyncResult = result
}

private class RecordingAutomaticSyncStatusRecorder : AutomaticSyncStatusRecorder {
    var status: AutomaticSyncStatus? = null

    override suspend fun record(status: AutomaticSyncStatus) {
        this.status = status
    }
}

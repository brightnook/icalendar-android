package com.victor2319.ioscalendar.auth

import com.victor2319.ioscalendar.domain.*
import com.victor2319.ioscalendar.sync.SyncScheduler
import com.victor2319.ioscalendar.sync.SyncSchedulerReadiness
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.async
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LocalAccountManagerTest {
    @Test
    fun logoutWaitsForCancellationBeforeClearingCredentialsOrCache() = runTest {
        // Catches treating WorkManager cancellation as fire-and-forget and racing cache deletion with a worker commit.
        val operations = mutableListOf<String>()
        val cancellationStarted = CompletableDeferred<Unit>()
        val cancellationReleased = CompletableDeferred<Unit>()
        val manager = LocalAccountManager(
            credentialsStore = object : CredentialsStore {
                override suspend fun save(credentials: Credentials) = Unit
                override suspend fun load(): Credentials? = null
                override suspend fun clear() { operations += "credentials" }
            },
            repository = emptyRepository { operations += "cache" },
            syncScheduler = object : SyncScheduler {
                override val readiness = MutableStateFlow(SyncSchedulerReadiness.READY)
                override suspend fun applyMode(mode: SyncMode) = Unit
                override fun enqueueManual(): UUID = UUID.randomUUID()
                override suspend fun cancelAll() { operations += "cancel"; cancellationStarted.complete(Unit); cancellationReleased.await() }
            },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )
        manager.markAuthenticated("person@example.test")
        val logout = async { manager.logout(clearCache = true) }
        cancellationStarted.await()
        assertEquals(listOf("cancel"), operations)
        cancellationReleased.complete(Unit)
        assertEquals(true, logout.await())
        assertEquals(listOf("cancel", "credentials", "cache"), operations)
    }
    @Test
    fun logoutSignsOutEvenWhenOptionalCacheDeletionFails() = runTest {
        // Catches cache cleanup failure preventing the required signed-out route after credentials are cleared.
        val operations = mutableListOf<String>()
        val manager = LocalAccountManager(
            credentialsStore = object : CredentialsStore {
                override suspend fun save(credentials: Credentials) = Unit
                override suspend fun load(): Credentials? = null
                override suspend fun clear() { operations += "credentials" }
            },
            repository = object : CalendarRepository {
                override fun observeCalendars(): Flow<List<CalendarSummary>> = emptyFlow()
                override fun observeAgenda(range: TimeRange, calendarIds: Set<String>) = emptyFlow<List<EventInstance>>()
                override fun observeDay(day: LocalDate, zoneId: ZoneId) = emptyFlow<List<EventInstance>>()
                override fun observeEvent(instanceId: String) = emptyFlow<EventDetail?>()
                override suspend fun getSyncState(calendarId: String) = CalendarSyncState()
                override suspend fun replaceCalendarSnapshot(snapshot: CalendarSnapshot) = Unit
                override suspend fun applyCalendarDelta(delta: CalendarDelta) = Unit
                override suspend fun setCalendarVisible(calendarId: String, visible: Boolean) = Unit
                override suspend fun recordCoverage(calendarId: String, range: TimeRange) = Unit
                override suspend fun clearCalendarHistory(before: CalendarHistoryBoundary) = Unit
                override suspend fun clearCalendarCache() { operations += "cache"; error("private cache failure") }
            },
            syncScheduler = object : SyncScheduler {
                override val readiness: StateFlow<SyncSchedulerReadiness> = MutableStateFlow(SyncSchedulerReadiness.READY)
                override suspend fun applyMode(mode: SyncMode) = Unit
                override fun enqueueManual(): UUID = UUID.randomUUID()
                override suspend fun cancelAll() { operations += "cancel" }
            },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )
        manager.markAuthenticated("person@example.test")

        assertFalse(manager.logout(clearCache = true))

        assertEquals(listOf("cancel", "credentials", "cache"), operations)
        assertEquals(AccountAuthenticationState.SignedOut, manager.authenticationState.value)
    }
}

private fun emptyRepository(onClearCache: suspend () -> Unit) = object : CalendarRepository {
    override fun observeCalendars(): Flow<List<CalendarSummary>> = emptyFlow()
    override fun observeAgenda(range: TimeRange, calendarIds: Set<String>) = emptyFlow<List<EventInstance>>()
    override fun observeDay(day: LocalDate, zoneId: ZoneId) = emptyFlow<List<EventInstance>>()
    override fun observeEvent(instanceId: String) = emptyFlow<EventDetail?>()
    override suspend fun getSyncState(calendarId: String) = CalendarSyncState()
    override suspend fun replaceCalendarSnapshot(snapshot: CalendarSnapshot) = Unit
    override suspend fun applyCalendarDelta(delta: CalendarDelta) = Unit
    override suspend fun setCalendarVisible(calendarId: String, visible: Boolean) = Unit
    override suspend fun recordCoverage(calendarId: String, range: TimeRange) = Unit
    override suspend fun clearCalendarHistory(before: CalendarHistoryBoundary) = Unit
    override suspend fun clearCalendarCache() = onClearCache()
}

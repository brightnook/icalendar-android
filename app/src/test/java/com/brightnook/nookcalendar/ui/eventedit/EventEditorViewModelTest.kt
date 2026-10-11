package com.brightnook.nookcalendar.ui.eventedit

import com.brightnook.nookcalendar.domain.CalendarSummary
import com.brightnook.nookcalendar.domain.EventDetail
import com.brightnook.nookcalendar.domain.EventDraft
import com.brightnook.nookcalendar.domain.EventEditingRepository
import com.brightnook.nookcalendar.domain.EventEditingResult
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EventEditorViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val calendar = CalendarSummary("work", "Work", 0, true, true)

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun newEventUsesSelectedDateAndFirstWritableCalendar() = runTest(dispatcher) {
        val viewModel = viewModel(selectedDate = LocalDate.of(2026, 9, 20))
        advanceUntilIdle()

        assertEquals(
            LocalDate.of(2026, 9, 20),
            viewModel.uiState.value.draft.startInstant?.atZone(ZoneId.of("Asia/Tokyo"))?.toLocalDate(),
        )
        assertEquals("work", viewModel.uiState.value.draft.calendarId)
    }

    @Test
    fun invalidTitleDoesNotSubmit() = runTest(dispatcher) {
        val repository = FakeEditingRepository(calendar)
        val viewModel = viewModel(repository = repository)
        advanceUntilIdle()

        viewModel.save()
        advanceUntilIdle()

        assertEquals(EventEditorError.TitleRequired, viewModel.uiState.value.error)
        assertEquals(0, repository.createCalls)
    }

    @Test
    fun conflictKeepsDraftAndAllowsAnotherAttempt() = runTest(dispatcher) {
        val repository = FakeEditingRepository(calendar).apply { nextResult = EventEditingResult.Conflict }
        val viewModel = viewModel(repository = repository)
        advanceUntilIdle()
        viewModel.updateTitle("Changed title")

        viewModel.save()
        advanceUntilIdle()

        assertEquals("Changed title", viewModel.uiState.value.draft.title)
        assertEquals(EventEditorError.Conflict, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun freeUserIsGatedBeforeSave() = runTest(dispatcher) {
        val repository = FakeEditingRepository(calendar)
        val viewModel = viewModel(repository = repository, isPremium = false)
        advanceUntilIdle()
        viewModel.updateTitle("Meeting")

        viewModel.save()
        advanceUntilIdle()

        assertEquals(EventEditorError.PremiumRequired, viewModel.uiState.value.error)
        assertEquals(0, repository.createCalls)
    }

    @Test
    fun signedOutUserIsGatedBeforeSave() = runTest(dispatcher) {
        val repository = FakeEditingRepository(calendar)
        val viewModel = viewModel(repository = repository, isSignedIn = false)
        advanceUntilIdle()
        viewModel.updateTitle("Meeting")

        viewModel.save()
        advanceUntilIdle()

        assertEquals(EventEditorError.SignInRequired, viewModel.uiState.value.error)
        assertEquals(0, repository.createCalls)
    }

    private fun viewModel(
        repository: FakeEditingRepository = FakeEditingRepository(calendar),
        selectedDate: LocalDate = LocalDate.of(2026, 9, 14),
        isPremium: Boolean = true,
        isSignedIn: Boolean = true,
    ) = EventEditorViewModel(
        repository = repository,
        selectedDate = selectedDate,
        instanceId = null,
        isPremium = { isPremium },
        isSignedIn = { isSignedIn },
        zoneId = ZoneId.of("Asia/Tokyo"),
        now = { Instant.parse("2026-09-13T00:00:00Z") },
    )
}

private class FakeEditingRepository(private val calendar: CalendarSummary) : EventEditingRepository {
    var createCalls = 0
    var nextResult: EventEditingResult = EventEditingResult.Success
    override fun observeWritableCalendars(): Flow<List<CalendarSummary>> = flowOf(listOf(calendar))
    override fun observeEvent(instanceId: String): Flow<EventDetail?> = flowOf(null)
    override suspend fun create(draft: EventDraft): EventEditingResult { createCalls++; return nextResult }
    override suspend fun update(instanceId: String, draft: EventDraft) = nextResult
    override suspend fun delete(instanceId: String) = nextResult
}

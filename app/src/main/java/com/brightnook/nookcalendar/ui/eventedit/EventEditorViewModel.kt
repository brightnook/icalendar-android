package com.brightnook.nookcalendar.ui.eventedit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.brightnook.nookcalendar.domain.EventDetail
import com.brightnook.nookcalendar.domain.EventDraft
import com.brightnook.nookcalendar.domain.EventEditingRepository
import com.brightnook.nookcalendar.domain.EventEditingResult
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class EventEditorViewModel(
    private val repository: EventEditingRepository,
    selectedDate: LocalDate,
    private val instanceId: String?,
    private val isPremium: () -> Boolean,
    private val isSignedIn: () -> Boolean,
    private val zoneId: ZoneId,
    private val now: () -> Instant = Instant::now,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        EventEditorState(
            draft = defaultDraft(selectedDate, zoneId),
            isLoading = instanceId != null,
        ),
    )
    val uiState: StateFlow<EventEditorState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val calendars = repository.observeWritableCalendars().first()
            val current = _uiState.value
            _uiState.value = current.copy(
                calendars = calendars,
                draft = if (current.draft.calendarId.isBlank()) {
                    current.draft.copy(calendarId = calendars.firstOrNull()?.id.orEmpty())
                } else current.draft,
            )
        }
        if (instanceId != null) {
            viewModelScope.launch {
                val detail = repository.observeEvent(instanceId).first()
                _uiState.value = if (detail == null) {
                    _uiState.value.copy(isLoading = false, error = EventEditorError.NotFound)
                } else {
                    _uiState.value.copy(draft = detail.toDraft(), isLoading = false)
                }
            }
        }
    }

    fun updateTitle(value: String) = updateDraft { copy(title = value) }
    fun updateLocation(value: String) = updateDraft { copy(location = value) }
    fun updateNotes(value: String) = updateDraft { copy(notes = value) }
    fun updateDraft(transform: EventDraft.() -> EventDraft) {
        if (_uiState.value.isSaving || _uiState.value.isDeleting) return
        _uiState.value = _uiState.value.copy(draft = _uiState.value.draft.transform(), error = null)
    }

    fun save() {
        val state = _uiState.value
        if (state.isSaving || state.isDeleting) return
        val validation = validate(state.draft)
        if (validation != null) {
            _uiState.value = state.copy(error = validation)
            return
        }
        if (!isPremium()) {
            _uiState.value = state.copy(error = EventEditorError.PremiumRequired)
            return
        }
        if (!isSignedIn()) {
            _uiState.value = state.copy(error = EventEditorError.SignInRequired)
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            val result = try {
                if (instanceId == null) repository.create(_uiState.value.draft)
                else repository.update(instanceId, _uiState.value.draft)
            } catch (cancelled: CancellationException) {
                throw cancelled
            }
            applyResult(result, saving = true)
        }
    }

    fun delete() {
        val id = instanceId ?: return
        val state = _uiState.value
        if (state.isSaving || state.isDeleting) return
        if (!isPremium()) {
            _uiState.value = state.copy(error = EventEditorError.PremiumRequired)
            return
        }
        if (!isSignedIn()) {
            _uiState.value = state.copy(error = EventEditorError.SignInRequired)
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDeleting = true, error = null)
            applyResult(repository.delete(id), saving = false)
        }
    }

    private fun applyResult(result: EventEditingResult, saving: Boolean) {
        _uiState.value = _uiState.value.copy(
            isSaving = if (saving) false else _uiState.value.isSaving,
            isDeleting = if (!saving) false else _uiState.value.isDeleting,
            completed = result == EventEditingResult.Success,
            error = result.toError(),
        )
    }

    private fun validate(draft: EventDraft): EventEditorError? = when {
        draft.title.isBlank() -> EventEditorError.TitleRequired
        draft.calendarId.isBlank() -> EventEditorError.ReadOnly
        draft.isAllDay && (draft.startDate == null || draft.endDateExclusive == null || !draft.startDate.isBefore(draft.endDateExclusive)) ->
            EventEditorError.InvalidTimeRange
        !draft.isAllDay && (draft.startInstant == null || draft.endInstant == null || !draft.startInstant.isBefore(draft.endInstant)) ->
            EventEditorError.InvalidTimeRange
        else -> null
    }

    private fun EventEditingResult.toError(): EventEditorError? = when (this) {
        EventEditingResult.Success -> null
        EventEditingResult.SavedRefreshPending -> EventEditorError.SavedRefreshPending
        EventEditingResult.AuthenticationRequired -> EventEditorError.SignInRequired
        EventEditingResult.NotFound -> EventEditorError.NotFound
        EventEditingResult.ReadOnly -> EventEditorError.ReadOnly
        EventEditingResult.RecurringUnsupported -> EventEditorError.RecurringUnsupported
        EventEditingResult.Conflict -> EventEditorError.Conflict
        EventEditingResult.NetworkFailure -> EventEditorError.Network
        EventEditingResult.Invalid -> EventEditorError.Invalid
    }
}

private fun defaultDraft(date: LocalDate, zoneId: ZoneId): EventDraft {
    val start = date.atTime(9, 0).atZone(zoneId).toInstant()
    return EventDraft(
        calendarId = "",
        title = "",
        isAllDay = false,
        startInstant = start,
        endInstant = start.plusSeconds(3600),
        timeZoneId = zoneId.id,
    )
}

private fun EventDetail.toDraft() = EventDraft(
    calendarId = calendarId,
    title = title,
    location = location,
    notes = notes,
    isAllDay = isAllDay,
    startInstant = startInstant,
    endInstant = endInstant,
    startDate = startDate,
    endDateExclusive = endDateExclusive,
    timeZoneId = originalTimeZoneId,
    reminders = reminders,
)

class EventEditorViewModelFactory(
    private val repository: EventEditingRepository,
    private val selectedDate: LocalDate,
    private val instanceId: String?,
    private val isPremium: () -> Boolean,
    private val isSignedIn: () -> Boolean,
    private val zoneId: ZoneId,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(EventEditorViewModel::class.java))
        return EventEditorViewModel(
            repository = repository,
            selectedDate = selectedDate,
            instanceId = instanceId,
            isPremium = isPremium,
            isSignedIn = isSignedIn,
            zoneId = zoneId,
        ) as T
    }
}

package com.brightnook.nookcalendar.ui.eventedit

import com.brightnook.nookcalendar.domain.CalendarSummary
import com.brightnook.nookcalendar.domain.EventDraft

data class EventEditorState(
    val draft: EventDraft,
    val calendars: List<CalendarSummary> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val error: EventEditorError? = null,
    val completed: Boolean = false,
)

enum class EventEditorError {
    TitleRequired,
    InvalidTimeRange,
    PremiumRequired,
    SignInRequired,
    NotFound,
    ReadOnly,
    RecurringUnsupported,
    Conflict,
    Network,
    Invalid,
    SavedRefreshPending,
}

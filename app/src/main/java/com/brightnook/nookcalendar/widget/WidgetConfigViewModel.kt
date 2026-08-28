package com.brightnook.nookcalendar.widget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brightnook.nookcalendar.domain.CalendarSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WidgetConfigUiState(
    val calendars: List<CalendarSummary> = emptyList(),
    val selectedCalendarIds: Set<String> = emptySet(),
    val range: WidgetRange = WidgetRange.SEVEN_DAYS,
)

class WidgetConfigViewModel(
    private val appWidgetId: Int,
    private val preferences: WidgetPreferences,
    calendars: Flow<List<CalendarSummary>>,
) : ViewModel() {
    private val configured = MutableStateFlow<WidgetConfig?>(null)
    private val selectedOverride = MutableStateFlow<Set<String>?>(null)
    private val rangeOverride = MutableStateFlow<WidgetRange?>(null)

    val uiState: StateFlow<WidgetConfigUiState> = combine(
        calendars,
        configured,
        selectedOverride,
        rangeOverride,
    ) { calendarList, saved, selection, range ->
        val visible = calendarList.filter(CalendarSummary::isVisible)
        val visibleIds = visible.mapTo(linkedSetOf(), CalendarSummary::id)
        WidgetConfigUiState(
            calendars = visible,
            selectedCalendarIds = (selection ?: saved?.calendarIds ?: visibleIds).intersect(visibleIds),
            range = range ?: saved?.range ?: WidgetRange.SEVEN_DAYS,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, WidgetConfigUiState())

    init {
        viewModelScope.launch { configured.value = preferences.configFor(appWidgetId) }
    }

    fun setCalendarSelected(calendarId: String, selected: Boolean) {
        val available = uiState.value.calendars.mapTo(linkedSetOf(), CalendarSummary::id)
        if (calendarId !in available) return
        selectedOverride.value = uiState.value.selectedCalendarIds.toMutableSet().apply {
            if (selected) add(calendarId) else remove(calendarId)
        }
    }

    fun setRange(range: WidgetRange) {
        rangeOverride.value = range
    }

    fun save(onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            preferences.save(
                WidgetConfig(
                    appWidgetId = appWidgetId,
                    calendarIds = selectedOverride.value ?: uiState.value.selectedCalendarIds,
                    range = rangeOverride.value ?: uiState.value.range,
                ),
            )
            configured.value = preferences.configFor(appWidgetId)
            selectedOverride.value = null
            rangeOverride.value = null
            onSaved()
        }
    }
}

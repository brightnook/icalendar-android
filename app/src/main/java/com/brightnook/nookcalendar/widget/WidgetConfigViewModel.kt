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

data class WidgetManagementItem(
    val appWidgetId: Int,
    val range: WidgetRange,
    val displayOrdinal: Int,
)

internal fun widgetManagementItems(
    activeWidgetIds: IntArray,
    configs: Map<Int, WidgetConfig>,
): List<WidgetManagementItem> = activeWidgetIds.distinct().mapNotNull { appWidgetId ->
    val config = configs[appWidgetId]?.takeIf { it.displayOrdinal > 0 } ?: return@mapNotNull null
    WidgetManagementItem(
        appWidgetId = appWidgetId,
        range = config.range,
        displayOrdinal = config.displayOrdinal,
    )
}

internal suspend fun loadWidgetManagementItems(
    activeWidgetIds: IntArray,
    preferences: WidgetPreferences,
): List<WidgetManagementItem> {
    val configs = activeWidgetIds.distinct().associateWith { appWidgetId ->
        val saved = preferences.configFor(appWidgetId)
        if (saved == null || saved.displayOrdinal <= 0) {
            preferences.save(saved ?: WidgetConfig(
                appWidgetId, emptySet(), WidgetRange.SEVEN_DAYS, followVisibleCalendars = true,
            ))
            preferences.configFor(appWidgetId)
        } else saved
    }.mapNotNull { (appWidgetId, config) ->
        config?.let { appWidgetId to it }
    }.toMap()
    return widgetManagementItems(activeWidgetIds, configs)
}

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
        val followsVisibleCalendars = selection == null && (saved?.followVisibleCalendars ?: true)
        WidgetConfigUiState(
            calendars = visible,
            selectedCalendarIds = (
                if (followsVisibleCalendars) visibleIds else selection ?: saved?.calendarIds.orEmpty()
            ).intersect(visibleIds),
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
            val saved = configured.value
            val followsVisibleCalendars = selectedOverride.value == null && (saved?.followVisibleCalendars ?: true)
            preferences.save(
                WidgetConfig(
                    appWidgetId = appWidgetId,
                    calendarIds = if (followsVisibleCalendars) emptySet() else selectedOverride.value ?: uiState.value.selectedCalendarIds,
                    range = rangeOverride.value ?: uiState.value.range,
                    followVisibleCalendars = followsVisibleCalendars,
                ),
            )
            configured.value = preferences.configFor(appWidgetId)
            selectedOverride.value = null
            rangeOverride.value = null
            onSaved()
        }
    }
}

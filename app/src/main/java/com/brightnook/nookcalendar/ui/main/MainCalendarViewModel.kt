package com.brightnook.nookcalendar.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.brightnook.nookcalendar.domain.CalendarRepository
import com.brightnook.nookcalendar.domain.CalendarSummary
import com.brightnook.nookcalendar.domain.ClockProvider
import com.brightnook.nookcalendar.domain.EventDetail
import com.brightnook.nookcalendar.domain.EventInstance
import com.brightnook.nookcalendar.domain.SystemClockProvider
import com.brightnook.nookcalendar.sync.SyncCoordinator
import com.brightnook.nookcalendar.sync.SyncDisabledException
import com.brightnook.nookcalendar.sync.SyncRequest
import com.brightnook.nookcalendar.sync.SyncResult
import com.brightnook.nookcalendar.sync.SyncScheduler
import com.brightnook.nookcalendar.sync.SyncSchedulerReadiness
import com.brightnook.nookcalendar.sync.SyncSettingsNotReadyException
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MainCalendarViewModel(
    private val repository: CalendarRepository,
    private val syncCoordinator: SyncCoordinator,
    private val syncScheduler: SyncScheduler,
    private val clock: ClockProvider = SystemClockProvider,
    private val zoneIds: StateFlow<ZoneId> = MutableStateFlow(clock.zoneId),
) : ViewModel() {
    private val initialDate = clock.now.atZone(clock.zoneId).toLocalDate()
    private val selectedDate = MutableStateFlow(initialDate)
    private val displayedMonth = MutableStateFlow(YearMonth.from(initialDate))
    private val selectedInstanceId = MutableStateFlow<String?>(null)
    private val message = MutableStateFlow<MainCalendarMessage?>(null)
    private val viewMode = MutableStateFlow(CalendarViewMode.COMBINED)
    private val monthsBeingEnsured = mutableSetOf<YearMonth>()

    private val calendars: StateFlow<List<CalendarSummary>> = repository.observeCalendars()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val agenda: StateFlow<List<EventInstance>> = combine(selectedDate, zoneIds) { date, zoneId ->
        date to zoneId
    }
        .flatMapLatest { (date, zoneId) -> repository.observeDay(date, zoneId) }
        .map { instances ->
            instances.sortedWith(
                compareByDescending<EventInstance> { it.isAllDay }
                    .thenBy { it.startInstant }
                    .thenBy { it.title.lowercase() }
                    .thenBy { it.instanceId },
            )
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val dayMarkerColors: StateFlow<Map<LocalDate, List<Int>>> = combine(
        displayedMonth,
        calendars,
        zoneIds,
        viewMode,
    ) { month, calendarList, zoneId, mode -> MarkerQuery(month, calendarList, zoneId, mode) }
        .flatMapLatest { query ->
            val (month, calendarList, zoneId, mode) = query
            val visibleCalendarIds = calendarList.filter(CalendarSummary::isVisible).mapTo(linkedSetOf()) { it.id }
            val markerRange = markerTimeRange(month, mode, zoneId)
            repository.observeAgenda(markerRange, visibleCalendarIds).map { instances ->
                val firstDay = markerRange.start.atZone(zoneId).toLocalDate()
                val lastDay = markerRange.endExclusive.minusNanos(1).atZone(zoneId).toLocalDate()
                dayMarkerColors(instances, visibleCalendarIds, firstDay, lastDay, zoneId)
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val openEvent: StateFlow<EventDetail?> = selectedInstanceId
        .flatMapLatest { instanceId ->
            if (instanceId == null) flowOf(null) else repository.observeEvent(instanceId)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val contentState: Flow<MainCalendarState> = combine(
        selectedDate,
        displayedMonth,
        calendars,
        agenda,
        dayMarkerColors,
    ) { date, month, calendarList, dayAgenda, markerColors ->
        MainCalendarState(
            selectedDate = date,
            displayedMonth = month,
            calendars = calendarList,
            agenda = dayAgenda,
            dayMarkerColors = markerColors,
        )
    }.combine(viewMode) { content, mode -> content.copy(viewMode = mode) }
        .combine(openEvent) { content, detail -> content.copy(openEvent = detail) }

    val uiState: StateFlow<MainCalendarState> = combine(contentState, message) { content, currentMessage ->
        content.copy(message = currentMessage)
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        MainCalendarState(initialDate, YearMonth.from(initialDate)),
    )

    init {
        viewModelScope.launch {
            val visibleCalendarIds = calendars
                .map { calendarList ->
                    calendarList.filter(CalendarSummary::isVisible).map(CalendarSummary::id).sorted()
                }
                .distinctUntilChanged()
            combine(displayedMonth, visibleCalendarIds, zoneIds) { month, calendarIds, zoneId ->
                Triple(month, calendarIds, zoneId)
            }.collectLatest { (month, calendarIds, zoneId) ->
                ensureMonthIfMissing(month, calendarIds, zoneId)
            }
        }
    }

    fun selectDate(date: LocalDate) {
        selectedDate.value = date
        val month = YearMonth.from(date)
        if (displayedMonth.value != month) displayedMonth.value = month
    }

    fun showMonth(month: YearMonth) {
        displayedMonth.value = month
    }

    fun showToday() {
        selectDate(clock.now.atZone(zoneIds.value).toLocalDate())
        viewMode.value = CalendarViewMode.COMBINED
    }

    fun toggleViewMode() {
        viewMode.value = if (viewMode.value == CalendarViewMode.COMBINED) CalendarViewMode.YEAR else CalendarViewMode.COMBINED
    }

    fun selectDateAndShowCombined(date: LocalDate) {
        selectDate(date)
        viewMode.value = CalendarViewMode.COMBINED
    }

    fun openEvent(instanceId: String) {
        if (instanceId.isNotBlank()) selectedInstanceId.value = instanceId
    }

    fun closeEvent() {
        selectedInstanceId.value = null
    }

    fun handleLaunchTarget(target: CalendarLaunchTarget) {
        selectDate(target.date)
        selectedInstanceId.value = target.instanceId
    }

    fun refresh() {
        message.value = null
        if (syncScheduler.readiness.value != SyncSchedulerReadiness.READY) {
            message.value = MainCalendarMessage.SYNC_NOT_READY
            return
        }
        try {
            syncScheduler.enqueueManual()
        } catch (_: SyncDisabledException) {
            message.value = MainCalendarMessage.SYNC_DISABLED
        } catch (_: SyncSettingsNotReadyException) {
            message.value = MainCalendarMessage.SYNC_NOT_READY
        } catch (_: RuntimeException) {
            message.value = MainCalendarMessage.SYNC_FAILED
        }
    }

    private suspend fun ensureMonthIfMissing(
        month: YearMonth,
        calendarIds: List<String>,
        zoneId: ZoneId,
    ) {
        if (calendarIds.isEmpty()) return
        val requiredStart = month.atDay(1).atStartOfDay(zoneId).toInstant()
        val requiredEnd = month.plusMonths(1).atDay(1).atStartOfDay(zoneId).toInstant()
        val fullyCovered = try {
            calendarIds.all { calendarId ->
                repository.getSyncState(calendarId).coverage.any { coverage ->
                    coverage.zoneId == zoneId &&
                        coverage.start == requiredStart &&
                        coverage.endExclusive == requiredEnd
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            message.value = MainCalendarMessage.SYNC_FAILED
            return
        }
        if (fullyCovered || !monthsBeingEnsured.add(month)) return
        message.value = null
        try {
            when (syncCoordinator.sync(SyncRequest.EnsureMonth(month))) {
                is SyncResult.Success -> Unit
                is SyncResult.PartialSuccess,
                is SyncResult.AuthenticationRequired,
                is SyncResult.RetryableFailure -> message.value = MainCalendarMessage.SYNC_FAILED
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            message.value = MainCalendarMessage.SYNC_FAILED
        } finally {
            monthsBeingEnsured.remove(month)
        }
    }

    private fun monthTimeRange(month: YearMonth, zoneId: ZoneId) = com.brightnook.nookcalendar.domain.TimeRange(
        start = month.atDay(1).atStartOfDay(zoneId).toInstant(),
        endExclusive = month.plusMonths(1).atDay(1).atStartOfDay(zoneId).toInstant(),
        zoneId = zoneId,
    )

    private fun markerTimeRange(month: YearMonth, mode: CalendarViewMode, zoneId: ZoneId): com.brightnook.nookcalendar.domain.TimeRange {
        val firstDay = when (mode) {
            CalendarViewMode.COMBINED -> month.atDay(1).minusDays(7)
            CalendarViewMode.YEAR -> LocalDate.of(month.year, 1, 1)
        }
        val endExclusive = when (mode) {
            CalendarViewMode.COMBINED -> month.plusMonths(1).atDay(1).plusDays(14)
            CalendarViewMode.YEAR -> LocalDate.of(month.year + 1, 1, 1)
        }
        return com.brightnook.nookcalendar.domain.TimeRange(
            start = firstDay.atStartOfDay(zoneId).toInstant(),
            endExclusive = endExclusive.atStartOfDay(zoneId).toInstant(),
            zoneId = zoneId,
        )
    }

    private data class MarkerQuery(
        val month: YearMonth,
        val calendars: List<CalendarSummary>,
        val zoneId: ZoneId,
        val mode: CalendarViewMode,
    )
}

class MainCalendarViewModelFactory(
    private val repository: CalendarRepository,
    private val syncCoordinator: SyncCoordinator,
    private val syncScheduler: SyncScheduler,
    private val clock: ClockProvider = SystemClockProvider,
    private val zoneIds: StateFlow<ZoneId> = MutableStateFlow(clock.zoneId),
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(MainCalendarViewModel::class.java))
        return MainCalendarViewModel(repository, syncCoordinator, syncScheduler, clock, zoneIds) as T
    }
}

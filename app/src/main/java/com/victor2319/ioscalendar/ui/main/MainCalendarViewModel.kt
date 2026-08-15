package com.victor2319.ioscalendar.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.victor2319.ioscalendar.domain.CalendarRepository
import com.victor2319.ioscalendar.domain.CalendarSummary
import com.victor2319.ioscalendar.domain.ClockProvider
import com.victor2319.ioscalendar.domain.EventDetail
import com.victor2319.ioscalendar.domain.EventInstance
import com.victor2319.ioscalendar.domain.SystemClockProvider
import com.victor2319.ioscalendar.sync.SyncCoordinator
import com.victor2319.ioscalendar.sync.SyncDisabledException
import com.victor2319.ioscalendar.sync.SyncRequest
import com.victor2319.ioscalendar.sync.SyncResult
import com.victor2319.ioscalendar.sync.SyncScheduler
import com.victor2319.ioscalendar.sync.SyncSchedulerReadiness
import com.victor2319.ioscalendar.sync.SyncSettingsNotReadyException
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
        openEvent,
    ) { date, month, calendarList, dayAgenda, detail ->
        MainCalendarState(date, month, calendarList, dayAgenda, detail)
    }

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

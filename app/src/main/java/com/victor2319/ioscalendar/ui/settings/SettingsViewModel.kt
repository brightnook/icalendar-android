package com.victor2319.ioscalendar.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.victor2319.ioscalendar.data.SettingsStore
import com.victor2319.ioscalendar.domain.AccountAuthenticationState
import com.victor2319.ioscalendar.domain.AppLanguage
import com.victor2319.ioscalendar.domain.AccountManager
import com.victor2319.ioscalendar.domain.CalendarHistoryBoundary
import com.victor2319.ioscalendar.domain.CalendarRepository
import com.victor2319.ioscalendar.domain.CalendarSummary
import com.victor2319.ioscalendar.domain.ClockProvider
import com.victor2319.ioscalendar.domain.SyncMode
import com.victor2319.ioscalendar.domain.WeekStart
import com.victor2319.ioscalendar.domain.SystemClockProvider
import com.victor2319.ioscalendar.sync.SyncScheduler
import com.victor2319.ioscalendar.widget.WidgetRefreshNotifier
import java.time.YearMonth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: CalendarRepository,
    private val settingsStore: SettingsStore,
    private val syncScheduler: SyncScheduler,
    private val accountManager: AccountManager,
    private val clock: ClockProvider = SystemClockProvider,
    private val widgetUpdater: WidgetRefreshNotifier = WidgetRefreshNotifier { },
) : ViewModel() {
    private val confirmation = MutableStateFlow<SettingsConfirmation?>(null)
    private val destructiveActionInProgress = MutableStateFlow(false)
    private val showCleanupWarning = MutableStateFlow(false)
    private val showOperationError = MutableStateFlow(false)

    private val content = combine(
        settingsStore.settings,
        repository.observeCalendars(),
        accountManager.authenticationState,
    ) { settings, calendars, accountState ->
        SettingsUiState(
            syncMode = settings.syncMode,
            weekStart = settings.weekStart,
            language = settings.language,
            calendars = calendars,
            maskedAppleId = (accountState as? AccountAuthenticationState.Authenticated)?.maskedAppleId,
            accountLoading = accountState == AccountAuthenticationState.Loading,
        )
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        content,
        confirmation,
        destructiveActionInProgress,
        showCleanupWarning,
        showOperationError,
    ) { content, pendingConfirmation, inProgress, cleanupWarning, operationError ->
        content.copy(
            confirmation = pendingConfirmation,
            destructiveActionInProgress = inProgress,
            showCleanupWarning = cleanupWarning,
            showOperationError = operationError,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState())

    fun selectSyncMode(mode: SyncMode) = launchSafe { syncScheduler.applyMode(mode) }

    fun selectWeekStart(weekStart: WeekStart) = launchSafe { settingsStore.setWeekStart(weekStart) }

    fun selectLanguage(language: AppLanguage) = launchSafe { settingsStore.setLanguage(language) }

    fun setCalendarVisible(calendarId: String, visible: Boolean) {
        if (calendarId.isBlank()) return
        launchSafe {
            repository.setCalendarVisible(calendarId, visible)
            widgetUpdater.refreshAll()
        }
    }

    fun requestClearHistory() = requestConfirmation(SettingsConfirmation.ClearHistory)
    fun requestClearCache() = requestConfirmation(SettingsConfirmation.ClearCache)
    fun requestLogout(clearCache: Boolean) = requestConfirmation(SettingsConfirmation.Logout(clearCache))

    fun dismissConfirmation() {
        if (!destructiveActionInProgress.value) confirmation.value = null
    }

    fun confirmDestructiveAction() {
        val action = confirmation.value ?: return
        if (destructiveActionInProgress.value) return
        destructiveActionInProgress.value = true
        viewModelScope.launch {
            try {
                when (action) {
                    SettingsConfirmation.ClearHistory -> repository.clearCalendarHistory(historyBoundary())
                    SettingsConfirmation.ClearCache -> {
                        repository.clearCalendarCache()
                        widgetUpdater.refreshAll()
                    }
                    is SettingsConfirmation.Logout -> showCleanupWarning.value = !accountManager.logout(action.clearCache)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                showOperationError.value = true
            } finally {
                confirmation.value = null
                destructiveActionInProgress.value = false
            }
        }
    }

    private fun requestConfirmation(action: SettingsConfirmation) {
        if (!destructiveActionInProgress.value) confirmation.value = action
    }

    private fun launchSafe(block: suspend () -> Unit) {
        if (destructiveActionInProgress.value) return
        viewModelScope.launch {
            showOperationError.value = false
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                showOperationError.value = true
            }
        }
    }

    private fun historyBoundary(): CalendarHistoryBoundary {
        val firstDay = YearMonth.from(clock.now.atZone(clock.zoneId)).atDay(1)
        return CalendarHistoryBoundary(firstDay.atStartOfDay(clock.zoneId).toInstant(), firstDay)
    }
}

data class SettingsUiState(
    val syncMode: SyncMode = SyncMode.MANUAL,
    val weekStart: WeekStart = WeekStart.SUNDAY,
    val language: AppLanguage = AppLanguage.FOLLOW_SYSTEM,
    val calendars: List<CalendarSummary> = emptyList(),
    val maskedAppleId: String? = null,
    val accountLoading: Boolean = true,
    val confirmation: SettingsConfirmation? = null,
    val destructiveActionInProgress: Boolean = false,
    val showCleanupWarning: Boolean = false,
    val showOperationError: Boolean = false,
)

sealed interface SettingsConfirmation {
    data object ClearHistory : SettingsConfirmation
    data object ClearCache : SettingsConfirmation
    data class Logout(val clearCache: Boolean) : SettingsConfirmation
}

class SettingsViewModelFactory(
    private val repository: CalendarRepository,
    private val settingsStore: SettingsStore,
    private val syncScheduler: SyncScheduler,
    private val accountManager: AccountManager,
    private val widgetUpdater: WidgetRefreshNotifier = WidgetRefreshNotifier { },
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(SettingsViewModel::class.java))
        return SettingsViewModel(repository, settingsStore, syncScheduler, accountManager, widgetUpdater = widgetUpdater) as T
    }
}

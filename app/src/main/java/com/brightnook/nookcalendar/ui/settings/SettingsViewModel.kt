package com.brightnook.nookcalendar.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.brightnook.nookcalendar.data.SettingsStore
import com.brightnook.nookcalendar.domain.AccountAuthenticationState
import com.brightnook.nookcalendar.domain.AppLanguage
import com.brightnook.nookcalendar.domain.AccountManager
import com.brightnook.nookcalendar.domain.CalendarHistoryBoundary
import com.brightnook.nookcalendar.domain.CalendarRepository
import com.brightnook.nookcalendar.domain.CalendarSummary
import com.brightnook.nookcalendar.domain.ClockProvider
import com.brightnook.nookcalendar.domain.SyncMode
import com.brightnook.nookcalendar.domain.ThemeBackgroundMode
import com.brightnook.nookcalendar.domain.ThemeSettings
import com.brightnook.nookcalendar.domain.WeekStart
import com.brightnook.nookcalendar.domain.SystemClockProvider
import com.brightnook.nookcalendar.sync.SyncScheduler
import com.brightnook.nookcalendar.sync.AutomaticSyncStatus
import com.brightnook.nookcalendar.sync.AutomaticSyncStatusStore
import com.brightnook.nookcalendar.sync.NoOpAutomaticSyncStatusStore
import com.brightnook.nookcalendar.theme.ThemeAssetFailure
import com.brightnook.nookcalendar.theme.ThemeAssetImport
import com.brightnook.nookcalendar.theme.ThemeAssetStore
import com.brightnook.nookcalendar.widget.WidgetRefreshNotifier
import com.brightnook.nookcalendar.reminder.ReminderScheduler
import com.brightnook.nookcalendar.reminder.NoOpReminderScheduler
import java.io.File
import java.time.YearMonth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: CalendarRepository,
    private val settingsStore: SettingsStore,
    private val syncScheduler: SyncScheduler,
    private val accountManager: AccountManager,
    private val clock: ClockProvider = SystemClockProvider,
    private val widgetUpdater: WidgetRefreshNotifier = WidgetRefreshNotifier { },
    private val themeAssetStore: ThemeAssetStore = UnavailableThemeAssetStore,
    private val reminderScheduler: ReminderScheduler = NoOpReminderScheduler,
    private val automaticSyncStatusStore: AutomaticSyncStatusStore = NoOpAutomaticSyncStatusStore,
) : ViewModel() {
    private val confirmation = MutableStateFlow<SettingsConfirmation?>(null)
    private val destructiveActionInProgress = MutableStateFlow(false)
    private val showCleanupWarning = MutableStateFlow(false)
    private val showOperationError = MutableStateFlow(false)
    private val themeImportError = MutableStateFlow<ThemeAssetFailure?>(null)
    private val _settingsSavedEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val settingsSavedEvents: SharedFlow<Unit> = _settingsSavedEvents.asSharedFlow()

    private val content = combine(
        settingsStore.settings,
        repository.observeCalendars(),
        accountManager.authenticationState,
        automaticSyncStatusStore.status,
        automaticSyncStatusStore.authenticationRequired,
    ) { settings, calendars, accountState, automaticSyncStatus, authenticationRequired ->
        SettingsUiState(
            syncMode = settings.syncMode,
            weekStart = settings.weekStart,
            language = settings.language,
            theme = settings.theme,
            calendars = calendars,
            maskedAppleId = (accountState as? AccountAuthenticationState.Authenticated)?.maskedAppleId,
            accountLoading = accountState == AccountAuthenticationState.Loading,
            automaticSyncStatus = automaticSyncStatus,
            authenticationRequired = authenticationRequired && accountState is AccountAuthenticationState.Authenticated,
        )
    }

    private val feedback = combine(
        confirmation,
        destructiveActionInProgress,
        showCleanupWarning,
        showOperationError,
        themeImportError,
    ) { pendingConfirmation, inProgress, cleanupWarning, operationError, importError ->
        SettingsFeedback(
            confirmation = pendingConfirmation,
            destructiveActionInProgress = inProgress,
            showCleanupWarning = cleanupWarning,
            showOperationError = operationError,
            themeImportError = importError,
        )
    }

    val uiState: StateFlow<SettingsUiState> = combine(content, feedback) { content, feedback ->
        content.copy(
            confirmation = feedback.confirmation,
            destructiveActionInProgress = feedback.destructiveActionInProgress,
            showCleanupWarning = feedback.showCleanupWarning,
            showOperationError = feedback.showOperationError,
            themeImportError = feedback.themeImportError,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState())

    fun selectSyncMode(mode: SyncMode) = launchSettingsSave {
        if (settingsStore.settings.first().syncMode == mode) false
        else {
            syncScheduler.applyMode(mode)
            true
        }
    }

    fun selectWeekStart(weekStart: WeekStart) = launchSettingsSave {
        if (settingsStore.settings.first().weekStart == weekStart) false
        else {
            settingsStore.setWeekStart(weekStart)
            true
        }
    }

    fun selectLanguage(language: AppLanguage) = launchSettingsSave {
        if (settingsStore.settings.first().language == language) false
        else {
            settingsStore.setLanguage(language)
            true
        }
    }

    fun selectTheme(mode: ThemeBackgroundMode) {
        if (mode == ThemeBackgroundMode.IMAGE) return
        launchThemeAction {
            val current = settingsStore.settings.first().theme
            if (current.mode != mode) {
                persistTheme(current.copy(mode = mode))
                true
            } else {
                false
            }
        }
    }

    fun importTheme(uri: Uri, focusX: Float = 0f, focusY: Float = 0f, scale: Float = 1f) = launchThemeAction {
        val current = settingsStore.settings.first().theme
        when (val result = themeAssetStore.import(uri)) {
            is ThemeAssetImport.Failure -> {
                themeImportError.value = result.reason
                false
            }
            is ThemeAssetImport.Success -> {
                persistTheme(
                    current.copy(
                        mode = ThemeBackgroundMode.IMAGE,
                        assetKey = result.assetKey,
                        wallpaperFocusX = focusX.coerceIn(-1f, 1f),
                        wallpaperFocusY = focusY.coerceIn(-1f, 1f),
                        wallpaperScale = scale.coerceIn(1f, 4f),
                    ),
                )
                current.assetKey
                    ?.takeIf { previousKey -> previousKey != result.assetKey }
                    ?.let { previousKey -> themeAssetStore.remove(previousKey) }
                true
            }
        }
    }

    fun removeThemeImage() = launchThemeAction {
        val current = settingsStore.settings.first().theme
        val assetKey = current.assetKey ?: return@launchThemeAction false
        val updatedMode = if (current.mode == ThemeBackgroundMode.IMAGE) {
            ThemeBackgroundMode.WHITE
        } else {
            current.mode
        }
        persistTheme(current.copy(mode = updatedMode, assetKey = null))
        themeAssetStore.remove(assetKey)
        true
    }

    fun setApplyThemeToWidget(enabled: Boolean) = launchThemeAction {
        val current = settingsStore.settings.first().theme
        if (current.applyToWidget != enabled) {
            persistTheme(current.copy(applyToWidget = enabled))
            true
        } else {
            false
        }
    }

    fun setCalendarVisible(calendarId: String, visible: Boolean) {
        if (calendarId.isBlank()) return
        launchSettingsSave {
            repository.setCalendarVisible(calendarId, visible)
            widgetUpdater.refreshAll()
            reminderScheduler.reconcile()
            true
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
                        reminderScheduler.reconcile()
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

    private fun launchThemeAction(block: suspend () -> Boolean) {
        themeImportError.value = null
        launchSettingsSave(block)
    }

    private fun launchSettingsSave(block: suspend () -> Boolean) {
        if (destructiveActionInProgress.value) return
        viewModelScope.launch {
            showOperationError.value = false
            try {
                if (block()) _settingsSavedEvents.tryEmit(Unit)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                showOperationError.value = true
            }
        }
    }

    private suspend fun persistTheme(theme: ThemeSettings) {
        settingsStore.setTheme(theme)
        widgetUpdater.refreshAll()
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
    val theme: ThemeSettings = ThemeSettings(),
    val themeImportError: ThemeAssetFailure? = null,
    val calendars: List<CalendarSummary> = emptyList(),
    val maskedAppleId: String? = null,
    val accountLoading: Boolean = true,
    val confirmation: SettingsConfirmation? = null,
    val destructiveActionInProgress: Boolean = false,
    val showCleanupWarning: Boolean = false,
    val showOperationError: Boolean = false,
    val automaticSyncStatus: AutomaticSyncStatus? = null,
    val authenticationRequired: Boolean = false,
)

private data class SettingsFeedback(
    val confirmation: SettingsConfirmation?,
    val destructiveActionInProgress: Boolean,
    val showCleanupWarning: Boolean,
    val showOperationError: Boolean,
    val themeImportError: ThemeAssetFailure?,
)

private object UnavailableThemeAssetStore : ThemeAssetStore {
    override suspend fun import(uri: Uri): ThemeAssetImport =
        ThemeAssetImport.Failure(ThemeAssetFailure.IO)

    override suspend fun remove(assetKey: String) = Unit

    override fun fileFor(assetKey: String): File? = null
}

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
    private val themeAssetStore: ThemeAssetStore = UnavailableThemeAssetStore,
    private val reminderScheduler: ReminderScheduler = NoOpReminderScheduler,
    private val automaticSyncStatusStore: AutomaticSyncStatusStore = NoOpAutomaticSyncStatusStore,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(SettingsViewModel::class.java))
        return SettingsViewModel(
            repository = repository,
            settingsStore = settingsStore,
            syncScheduler = syncScheduler,
            accountManager = accountManager,
            widgetUpdater = widgetUpdater,
            themeAssetStore = themeAssetStore,
            reminderScheduler = reminderScheduler,
            automaticSyncStatusStore = automaticSyncStatusStore,
        ) as T
    }
}

package com.victor2319.ioscalendar.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.victor2319.ioscalendar.AppContainer
import com.victor2319.ioscalendar.R
import com.victor2319.ioscalendar.auth.CalDavLoginCoordinator
import com.victor2319.ioscalendar.domain.AccountAuthenticationState
import com.victor2319.ioscalendar.ui.settings.SettingsScreen
import com.victor2319.ioscalendar.ui.settings.SettingsViewModel
import com.victor2319.ioscalendar.ui.settings.SettingsViewModelFactory
import com.victor2319.ioscalendar.ui.login.LoginDestination
import com.victor2319.ioscalendar.ui.login.LoginScreen
import com.victor2319.ioscalendar.ui.login.LoginViewModel
import com.victor2319.ioscalendar.ui.login.LoginViewModelFactory
import com.victor2319.ioscalendar.ui.main.CalendarLaunchTarget
import com.victor2319.ioscalendar.ui.main.ExternalAction
import com.victor2319.ioscalendar.ui.main.MainCalendarScreen
import com.victor2319.ioscalendar.ui.main.MainCalendarViewModel
import com.victor2319.ioscalendar.ui.main.MainCalendarViewModelFactory
import java.time.ZoneId
import kotlinx.coroutines.flow.StateFlow

@Composable
fun AppRoot(
    container: AppContainer,
    zoneIds: StateFlow<ZoneId>,
    launchTarget: CalendarLaunchTarget? = null,
    openSettingsRequest: Int = 0,
    onLaunchTargetConsumed: (CalendarLaunchTarget) -> Unit = {},
) {
    val accountState by container.accountManager.authenticationState.collectAsState()
    AppRootContent(
        loginViewModelFactory = LoginViewModelFactory(
            loginCoordinator = CalDavLoginCoordinator(container.calDavGateway),
            credentialsStore = container.credentialsStore,
            syncCoordinator = container.syncCoordinator,
            onAuthenticated = container.accountManager::markAuthenticated,
        ),
        authenticatedContent = {
            MainCalendarDestination(
                factory = MainCalendarViewModelFactory(
                    repository = container.calendarRepository,
                    syncCoordinator = container.syncCoordinator,
                    syncScheduler = container.syncScheduler,
                    zoneIds = zoneIds,
                ),
                zoneIds = zoneIds,
                launchTarget = launchTarget,
                openSettingsRequest = openSettingsRequest,
                onLaunchTargetConsumed = onLaunchTargetConsumed,
                settingsFactory = SettingsViewModelFactory(container.calendarRepository, container.settingsStore, container.syncScheduler, container.accountManager, container.widgetUpdater),
            )
        },
        isAuthenticated = routesToAuthenticatedContent(accountState),
    )
}

@Composable
fun AppRoot(loginViewModelFactory: ViewModelProvider.Factory) {
    AppRootContent(loginViewModelFactory, authenticatedContent = { AuthenticatedPlaceholder() }, isAuthenticated = true)
}

@Composable
fun AppRoot(
    loginViewModelFactory: ViewModelProvider.Factory,
    authenticationState: StateFlow<AccountAuthenticationState>,
) {
    val accountState by authenticationState.collectAsState()
    AppRootContent(
        loginViewModelFactory,
        authenticatedContent = { AuthenticatedPlaceholder() },
        isAuthenticated = routesToAuthenticatedContent(accountState),
    )
}

internal fun routesToAuthenticatedContent(state: AccountAuthenticationState): Boolean =
    state is AccountAuthenticationState.Authenticated

@Composable
private fun AppRootContent(
    loginViewModelFactory: ViewModelProvider.Factory,
    authenticatedContent: @Composable () -> Unit,
    isAuthenticated: Boolean,
) {
    val context = LocalContext.current
    val loginViewModel: LoginViewModel = viewModel(factory = loginViewModelFactory)
    val uiState by loginViewModel.uiState.collectAsState()

    if (isAuthenticated) {
        authenticatedContent()
    } else {
        LoginScreen(
            uiState = uiState,
            onConnect = loginViewModel::connect,
            onViewCalendars = loginViewModel::viewCalendars,
            onOpenUri = { url -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
        )
    }
}

@Composable
private fun MainCalendarDestination(
    factory: ViewModelProvider.Factory,
    zoneIds: StateFlow<ZoneId>,
    launchTarget: CalendarLaunchTarget?,
    openSettingsRequest: Int,
    onLaunchTargetConsumed: (CalendarLaunchTarget) -> Unit,
    settingsFactory: ViewModelProvider.Factory,
) {
    val context = LocalContext.current
    val viewModel: MainCalendarViewModel = viewModel(key = "main-calendar", factory = factory)
    val uiState by viewModel.uiState.collectAsState()
    val zoneId by zoneIds.collectAsState()
    val consumeTarget by rememberUpdatedState(onLaunchTargetConsumed)
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(launchTarget) {
        launchTarget?.let {
            viewModel.handleLaunchTarget(it)
            consumeTarget(it)
        }
    }
    LaunchedEffect(openSettingsRequest) {
        if (openSettingsRequest != 0) settingsOpen = true
    }
    if (settingsOpen) {
        val settingsViewModel: SettingsViewModel = viewModel(key = "settings", factory = settingsFactory)
        val settingsState by settingsViewModel.uiState.collectAsState()
        SettingsScreen(
            state = settingsState,
            onClose = { settingsOpen = false },
            onSelectSyncMode = settingsViewModel::selectSyncMode,
            onSetCalendarVisible = settingsViewModel::setCalendarVisible,
            onClearHistory = settingsViewModel::requestClearHistory,
            onClearCache = settingsViewModel::requestClearCache,
            onLogout = { settingsViewModel.requestLogout(clearCache = false) },
            onDismissConfirmation = settingsViewModel::dismissConfirmation,
            onConfirmDestructiveAction = settingsViewModel::confirmDestructiveAction,
        )
    } else MainCalendarScreen(
        state = uiState,
        zoneId = zoneId,
        onSelectDate = viewModel::selectDate,
        onShowMonth = viewModel::showMonth,
        onRefresh = viewModel::refresh,
        onOpenSettings = { settingsOpen = true },
        onOpenEvent = viewModel::openEvent,
        onCloseEvent = viewModel::closeEvent,
        onOpenExternalAction = { action -> openSafeExternalAction(context, action) },
        onToday = viewModel::showToday,
        onToggleViewMode = viewModel::toggleViewMode,
        onSelectDateAndShowCombined = viewModel::selectDateAndShowCombined,
    )
}

private fun openSafeExternalAction(context: android.content.Context, action: ExternalAction) {
    val uri = when (action) {
        is ExternalAction.Web -> Uri.parse(action.url)
        is ExternalAction.Telephone -> Uri.fromParts("tel", action.number, null)
        is ExternalAction.Geo -> Uri.parse("geo:${action.coordinate}")
    }
    val intent = Intent(Intent.ACTION_VIEW, uri)
    try {
        context.startActivity(intent)
    } catch (_: android.content.ActivityNotFoundException) {
        Unit
    } catch (_: SecurityException) {
        Unit
    }
}

@Composable
private fun AuthenticatedPlaceholder() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.login_connected_title))
        Text(stringResource(R.string.login_connected_placeholder))
    }
}

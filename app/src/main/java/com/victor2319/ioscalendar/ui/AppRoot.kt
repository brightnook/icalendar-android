package com.victor2319.ioscalendar.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.victor2319.ioscalendar.AppContainer
import com.victor2319.ioscalendar.R
import com.victor2319.ioscalendar.data.SettingsStore
import com.victor2319.ioscalendar.domain.AppSettings
import com.victor2319.ioscalendar.auth.CalDavLoginCoordinator
import com.victor2319.ioscalendar.domain.AccountAuthenticationState
import com.victor2319.ioscalendar.monetization.AdConsentManager
import com.victor2319.ioscalendar.monetization.MonetizationUiState
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
import com.victor2319.ioscalendar.theme.ThemeAssetStore
import com.victor2319.ioscalendar.ui.theme.calendarThemePresentation
import java.time.ZoneId
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun AppRoot(
    container: AppContainer,
    zoneIds: StateFlow<ZoneId>,
    launchTarget: CalendarLaunchTarget? = null,
    openSettingsRequest: Int = 0,
    onLaunchTargetConsumed: (CalendarLaunchTarget) -> Unit = {},
) {
    val activity = LocalContext.current as? Activity
    val coroutineScope = rememberCoroutineScope()
    val accountState by container.accountManager.authenticationState.collectAsState()
    AppRootContent(
        loginViewModelFactory = LoginViewModelFactory(
            loginCoordinator = CalDavLoginCoordinator(container.calDavGateway),
            credentialsStore = container.credentialsStore,
            syncCoordinator = container.syncCoordinator,
            onAuthenticated = container.accountManager::markAuthenticated,
        ),
        authenticatedContent = {
            val monetization by container.monetizationRepository.state.collectAsState()
            LaunchedEffect(container.monetizationRepository) {
                container.monetizationRepository.refresh()
            }
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
                settingsFactory = SettingsViewModelFactory(
                    repository = container.calendarRepository,
                    settingsStore = container.settingsStore,
                    syncScheduler = container.syncScheduler,
                    accountManager = container.accountManager,
                    widgetUpdater = container.widgetUpdater,
                    themeAssetStore = container.themeAssetStore,
                ),
                settingsStore = container.settingsStore,
                themeAssetStore = container.themeAssetStore,
                monetization = monetization,
                onPurchase = {
                    launchPurchaseIfActivity(activity, coroutineScope) { purchaseActivity ->
                        container.monetizationRepository.purchase(purchaseActivity)
                    }
                },
                onRestorePurchase = {
                    coroutineScope.launch { container.monetizationRepository.restore() }
                },
            )
        },
        accountState = accountState,
    )
}

internal fun launchPurchaseIfActivity(
    activity: Activity?,
    coroutineScope: CoroutineScope,
    onPurchase: suspend (Activity) -> Unit,
) {
    activity?.let { purchaseActivity ->
        coroutineScope.launch { onPurchase(purchaseActivity) }
    }
}

@Composable
fun AppRoot(loginViewModelFactory: ViewModelProvider.Factory) {
    AppRootContent(
        loginViewModelFactory,
        authenticatedContent = { AuthenticatedPlaceholder() },
        accountState = AccountAuthenticationState.Authenticated("preview"),
    )
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
        accountState = accountState,
    )
}

internal fun routesToAuthenticatedContent(state: AccountAuthenticationState): Boolean =
    state is AccountAuthenticationState.Authenticated

enum class AppStartupDestination { LOADING, LOGIN, CALENDAR }

internal fun appStartupDestination(state: AccountAuthenticationState): AppStartupDestination = when (state) {
    AccountAuthenticationState.Loading -> AppStartupDestination.LOADING
    AccountAuthenticationState.SignedOut -> AppStartupDestination.LOGIN
    is AccountAuthenticationState.Authenticated -> AppStartupDestination.CALENDAR
}

@Composable
private fun AppRootContent(
    loginViewModelFactory: ViewModelProvider.Factory,
    authenticatedContent: @Composable () -> Unit,
    accountState: AccountAuthenticationState,
) {
    val context = LocalContext.current
    val loginViewModel: LoginViewModel = viewModel(factory = loginViewModelFactory)
    val uiState by loginViewModel.uiState.collectAsState()

    when (appStartupDestination(accountState)) {
        AppStartupDestination.LOADING -> StartupLoadingScreen()
        AppStartupDestination.CALENDAR -> authenticatedContent()
        AppStartupDestination.LOGIN -> {
            LoginScreen(
                uiState = uiState,
                onConnect = loginViewModel::connect,
                onViewCalendars = loginViewModel::viewCalendars,
                onOpenUri = { url -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
            )
        }
    }
}

@Composable
private fun StartupLoadingScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(R.mipmap.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(96.dp),
        )
        CircularProgressIndicator(modifier = Modifier.padding(top = 20.dp).size(24.dp))
        Text(
            text = stringResource(R.string.startup_loading),
            modifier = Modifier.padding(top = 12.dp),
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
    settingsStore: SettingsStore,
    themeAssetStore: ThemeAssetStore,
    monetization: MonetizationUiState,
    onPurchase: () -> Unit,
    onRestorePurchase: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: MainCalendarViewModel = viewModel(key = "main-calendar", factory = factory)
    val uiState by viewModel.uiState.collectAsState()
    val zoneId by zoneIds.collectAsState()
    val appSettings by settingsStore.settings.collectAsState(initial = AppSettings())
    val themePresentation = calendarThemePresentation(
        theme = appSettings.theme,
        assetFile = appSettings.theme.assetKey?.let(themeAssetStore::fileFor),
    )
    val adConsentReady = rememberAdConsentReady()
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
            onSelectWeekStart = settingsViewModel::selectWeekStart,
            onSelectLanguage = settingsViewModel::selectLanguage,
            onSelectTheme = settingsViewModel::selectTheme,
            onImportTheme = settingsViewModel::importTheme,
            onRemoveThemeImage = settingsViewModel::removeThemeImage,
            onSetApplyThemeToWidget = settingsViewModel::setApplyThemeToWidget,
            settingsSavedEvents = settingsViewModel.settingsSavedEvents,
            onSetCalendarVisible = settingsViewModel::setCalendarVisible,
            onClearHistory = settingsViewModel::requestClearHistory,
            onClearCache = settingsViewModel::requestClearCache,
            onLogout = { settingsViewModel.requestLogout(clearCache = false) },
            onDismissConfirmation = settingsViewModel::dismissConfirmation,
            onConfirmDestructiveAction = settingsViewModel::confirmDestructiveAction,
            monetization = monetization,
            onPurchase = onPurchase,
            onRestorePurchase = onRestorePurchase,
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
        weekStart = appSettings.weekStart,
        themePresentation = themePresentation,
        monetization = monetization.copy(canShowAds = monetization.canShowAds && adConsentReady),
    )
}

@Composable
private fun rememberAdConsentReady(): Boolean {
    val activity = LocalContext.current as? Activity ?: return false
    val consentManager = remember(activity) { AdConsentManager(activity) }
    var ready by remember { mutableStateOf(false) }

    LaunchedEffect(activity, consentManager) {
        ready = false
        consentManager.initialize(activity) { ready = true }
    }
    return ready
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

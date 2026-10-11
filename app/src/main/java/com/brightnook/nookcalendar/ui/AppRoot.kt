package com.brightnook.nookcalendar.ui

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.core.content.ContextCompat
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.brightnook.nookcalendar.AppContainer
import com.brightnook.nookcalendar.R
import com.brightnook.nookcalendar.data.SettingsStore
import com.brightnook.nookcalendar.domain.AppSettings
import com.brightnook.nookcalendar.domain.EventEditingRepository
import com.brightnook.nookcalendar.auth.CalDavLoginCoordinator
import com.brightnook.nookcalendar.domain.AccountAuthenticationState
import com.brightnook.nookcalendar.monetization.AdConsentManager
import com.brightnook.nookcalendar.monetization.MonetizationUiState
import com.brightnook.nookcalendar.monetization.PurchaseFeedback
import com.brightnook.nookcalendar.monetization.PurchaseUiState
import com.brightnook.nookcalendar.reminder.ReminderScheduler
import com.brightnook.nookcalendar.ui.settings.SettingsScreen
import com.brightnook.nookcalendar.ui.settings.SettingsViewModel
import com.brightnook.nookcalendar.ui.settings.SettingsViewModelFactory
import com.brightnook.nookcalendar.ui.login.LoginDestination
import com.brightnook.nookcalendar.ui.login.LoginScreen
import com.brightnook.nookcalendar.ui.login.LoginViewModel
import com.brightnook.nookcalendar.ui.login.LoginViewModelFactory
import com.brightnook.nookcalendar.ui.main.CalendarLaunchTarget
import com.brightnook.nookcalendar.ui.main.ExternalAction
import com.brightnook.nookcalendar.ui.main.MainCalendarScreen
import com.brightnook.nookcalendar.ui.main.MainCalendarViewModel
import com.brightnook.nookcalendar.ui.main.MainCalendarViewModelFactory
import com.brightnook.nookcalendar.ui.eventedit.EventEditorScreen
import com.brightnook.nookcalendar.ui.eventedit.EventEditorViewModel
import com.brightnook.nookcalendar.ui.eventedit.EventEditorViewModelFactory
import com.brightnook.nookcalendar.theme.ThemeAssetStore
import com.brightnook.nookcalendar.ui.theme.calendarThemePresentation
import com.brightnook.nookcalendar.widget.AgendaWidgetProvider
import com.brightnook.nookcalendar.widget.WidgetManagementItem
import com.brightnook.nookcalendar.widget.WidgetPreferences
import com.brightnook.nookcalendar.widget.WidgetIntentFactory
import com.brightnook.nookcalendar.widget.loadWidgetManagementItems
import java.time.ZoneId
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun AppRoot(
    container: AppContainer,
    zoneIds: StateFlow<ZoneId>,
    launchTarget: CalendarLaunchTarget? = null,
    openSettingsRequest: Int = 0,
    createEventRequest: Int = 0,
    onLaunchTargetConsumed: (CalendarLaunchTarget) -> Unit = {},
    settingsOpen: Boolean = false,
    onSettingsOpenChanged: (Boolean) -> Unit = {},
) {
    val activity = activityFromRegistryOwner(LocalActivityResultRegistryOwner.current)
    val coroutineScope = rememberCoroutineScope()
    val accountState by container.accountManager.authenticationState.collectAsState()
    var handledCreateEventRequest by remember { mutableStateOf(0) }
    AppRootContent(
        loginViewModelFactory = LoginViewModelFactory(
            loginCoordinator = CalDavLoginCoordinator(container.calDavGateway),
            credentialsStore = container.credentialsStore,
            syncCoordinator = container.syncCoordinator,
            onAuthenticated = container.accountManager::markAuthenticated,
        ),
        calendarContent = { onOpenLogin, showLoginPrompt ->
            val monetization by container.monetizationRepository.state.collectAsState()
            var purchaseStateReady by remember { mutableStateOf(false) }
            LaunchedEffect(container.monetizationRepository) {
                container.monetizationRepository.refresh()
                purchaseStateReady = true
            }
            MainCalendarDestination(
                factory = MainCalendarViewModelFactory(
                    repository = container.calendarRepository,
                    syncCoordinator = container.syncCoordinator,
                    syncScheduler = container.syncScheduler,
                    zoneIds = zoneIds,
                    isSignedIn = { container.accountManager.authenticationState.value is AccountAuthenticationState.Authenticated },
                ),
                zoneIds = zoneIds,
                launchTarget = launchTarget,
                openSettingsRequest = openSettingsRequest,
                createEventRequest = createEventRequest,
                handledCreateEventRequest = handledCreateEventRequest,
                onCreateEventRequestHandled = { handledCreateEventRequest = it },
                purchaseStateReady = purchaseStateReady,
                onLaunchTargetConsumed = onLaunchTargetConsumed,
                settingsOpen = settingsOpen,
                onSettingsOpenChanged = onSettingsOpenChanged,
                settingsFactory = SettingsViewModelFactory(
                    repository = container.calendarRepository,
                    settingsStore = container.settingsStore,
                    syncScheduler = container.syncScheduler,
                    accountManager = container.accountManager,
                    widgetUpdater = container.widgetUpdater,
                    themeAssetStore = container.themeAssetStore,
                    reminderScheduler = container.reminderScheduler,
                    automaticSyncStatusStore = container.automaticSyncStatusStore,
                ),
                settingsStore = container.settingsStore,
                themeAssetStore = container.themeAssetStore,
                widgetPreferences = container.widgetPreferences,
                hostActivity = activity,
                monetization = monetization,
                purchaseFeedbackEvents = container.monetizationRepository.purchaseFeedbackEvents,
                reminderAvailability = container.reminderScheduler.hasUpcomingReminders,
                reminderScheduler = container.reminderScheduler,
                eventEditingRepository = container.eventEditingRepository,
                showLoginPrompt = showLoginPrompt,
                onOpenLogin = onOpenLogin,
                onPurchase = {
                    launchPurchaseIfActivity(activity, coroutineScope) { purchaseActivity ->
                        container.monetizationRepository.purchase(purchaseActivity)
                    }
                },
                onRestorePurchase = {
                    coroutineScope.launch { container.monetizationRepository.restore() }
                },
                onRefreshPurchaseSupport = container.monetizationRepository::refresh,
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

internal fun activityFromRegistryOwner(owner: ActivityResultRegistryOwner?): Activity? = owner as? Activity

@Composable
fun AppRoot(loginViewModelFactory: ViewModelProvider.Factory) {
    AppRootContent(
        loginViewModelFactory,
        calendarContent = { _, _ -> AuthenticatedPlaceholder() },
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
        calendarContent = { _, _ -> AuthenticatedPlaceholder() },
        accountState = accountState,
    )
}

internal fun routesToAuthenticatedContent(state: AccountAuthenticationState): Boolean =
    state is AccountAuthenticationState.Authenticated

enum class AppStartupDestination { LOADING, LOGIN, CALENDAR }

internal fun appStartupDestination(@Suppress("UNUSED_PARAMETER") state: AccountAuthenticationState): AppStartupDestination =
    AppStartupDestination.CALENDAR

internal fun shouldShowLoginScreen(state: AccountAuthenticationState, loginRequested: Boolean): Boolean =
    state is AccountAuthenticationState.SignedOut && loginRequested

@Composable
private fun AppRootContent(
    loginViewModelFactory: ViewModelProvider.Factory,
    calendarContent: @Composable (onOpenLogin: () -> Unit, showLoginPrompt: Boolean) -> Unit,
    accountState: AccountAuthenticationState,
) {
    val context = LocalContext.current
    val loginViewModel: LoginViewModel = viewModel(factory = loginViewModelFactory)
    val uiState by loginViewModel.uiState.collectAsState()

    var loginRequested by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(accountState) {
        if (accountState is AccountAuthenticationState.Authenticated) loginRequested = false
    }
    if (shouldShowLoginScreen(accountState, loginRequested)) {
            LoginScreen(
                uiState = uiState,
                onConnect = loginViewModel::connect,
                onViewCalendars = loginViewModel::viewCalendars,
                onOpenUri = { url -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
                onClose = { loginRequested = false },
            )
    } else {
        calendarContent(
            { loginRequested = true },
            accountState is AccountAuthenticationState.SignedOut,
        )
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
    createEventRequest: Int,
    handledCreateEventRequest: Int,
    onCreateEventRequestHandled: (Int) -> Unit,
    purchaseStateReady: Boolean,
    onLaunchTargetConsumed: (CalendarLaunchTarget) -> Unit,
    settingsOpen: Boolean,
    onSettingsOpenChanged: (Boolean) -> Unit,
    settingsFactory: ViewModelProvider.Factory,
    settingsStore: SettingsStore,
    themeAssetStore: ThemeAssetStore,
    widgetPreferences: WidgetPreferences,
    hostActivity: Activity?,
    monetization: MonetizationUiState,
    purchaseFeedbackEvents: Flow<PurchaseFeedback>,
    reminderAvailability: StateFlow<Boolean>,
    reminderScheduler: ReminderScheduler,
    eventEditingRepository: EventEditingRepository,
    showLoginPrompt: Boolean,
    onOpenLogin: () -> Unit,
    onPurchase: () -> Unit,
    onRestorePurchase: () -> Unit,
    onRefreshPurchaseSupport: suspend () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: MainCalendarViewModel = viewModel(key = "main-calendar", factory = factory)
    val uiState by viewModel.uiState.collectAsState()
    val zoneId by zoneIds.collectAsState()
    val appSettings by settingsStore.settings.collectAsState(initial = AppSettings())
    var managedWidgets by remember { mutableStateOf(emptyList<WidgetManagementItem>()) }
    var widgetManagementRefreshRequest by remember { mutableStateOf(0) }
    var editorSequence by rememberSaveable { mutableStateOf(0) }
    var editorRequest by remember { mutableStateOf<EventEditorRequest?>(null) }
    val hasUpcomingReminders by reminderAvailability.collectAsState()
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    val widgetConfiguration = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) widgetManagementRefreshRequest += 1
    }
    LaunchedEffect(hasUpcomingReminders) {
        if (hasUpcomingReminders && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val themePresentation = calendarThemePresentation(
        theme = appSettings.theme,
        assetFile = appSettings.theme.assetKey?.let(themeAssetStore::fileFor),
    )
    val adConsent = rememberAdConsentState(hostActivity)
    LaunchedEffect(monetization.purchaseState) {
        reconcileRemindersForPurchaseState(monetization.purchaseState, reminderScheduler)
    }
    val consumeTarget by rememberUpdatedState(onLaunchTargetConsumed)
    LaunchedEffect(launchTarget) {
        launchTarget?.let {
            viewModel.handleLaunchTarget(it)
            consumeTarget(it)
        }
    }
    LaunchedEffect(openSettingsRequest) {
        if (openSettingsRequest != 0) onSettingsOpenChanged(true)
    }
    LaunchedEffect(createEventRequest, purchaseStateReady, monetization.purchaseState) {
        if (!purchaseStateReady || monetization.purchaseState == PurchaseUiState.LOADING ||
            createEventRequest == 0 || createEventRequest == handledCreateEventRequest
        ) return@LaunchedEffect
        onCreateEventRequestHandled(createEventRequest)
        onSettingsOpenChanged(false)
        val today = LocalDate.now(zoneId)
        viewModel.selectDate(today)
        when {
            showLoginPrompt -> onOpenLogin()
            monetization.purchaseState == PurchaseUiState.OWNED -> {
                editorSequence += 1
                editorRequest = EventEditorRequest(editorSequence, null, false, today)
            }
            else -> onPurchase()
        }
    }
    LaunchedEffect(settingsOpen, widgetManagementRefreshRequest) {
        managedWidgets = if (settingsOpen) {
            val activeIds = activeAgendaWidgetIds(context)
            loadWidgetManagementItems(activeIds, widgetPreferences)
        } else {
            emptyList()
        }
    }
    if (settingsOpen) {
        val settingsViewModel: SettingsViewModel = viewModel(key = "settings", factory = settingsFactory)
        val settingsState by settingsViewModel.uiState.collectAsState()
        SettingsScreen(
            state = settingsState,
            themePresentation = themePresentation,
            onClose = { onSettingsOpenChanged(false) },
            onSelectSyncMode = settingsViewModel::selectSyncMode,
            onSelectWeekStart = settingsViewModel::selectWeekStart,
            onSelectLanguage = settingsViewModel::selectLanguage,
            onSelectTheme = settingsViewModel::selectTheme,
            onImportTheme = settingsViewModel::importTheme,
            onRemoveThemeImage = settingsViewModel::removeThemeImage,
            onSetApplyThemeToWidget = settingsViewModel::setApplyThemeToWidget,
            managedWidgets = managedWidgets,
            onConfigureWidget = { appWidgetId ->
                widgetConfiguration.launch(WidgetIntentFactory.configurationIntent(context, appWidgetId))
            },
            settingsSavedEvents = settingsViewModel.settingsSavedEvents,
            purchaseFeedbackEvents = purchaseFeedbackEvents,
            onLogin = onOpenLogin,
            onSetCalendarVisible = settingsViewModel::setCalendarVisible,
            onClearHistory = settingsViewModel::requestClearHistory,
            onClearCache = settingsViewModel::requestClearCache,
            onLogout = { settingsViewModel.requestLogout(clearCache = false) },
            onDismissConfirmation = settingsViewModel::dismissConfirmation,
            onConfirmDestructiveAction = settingsViewModel::confirmDestructiveAction,
            monetization = monetization,
            onPurchase = onPurchase,
            onRestorePurchase = onRestorePurchase,
            onRefreshPurchaseSupport = onRefreshPurchaseSupport,
            privacyOptionsRequired = adConsent.privacyOptionsRequired,
            onOpenPrivacyOptions = {
                hostActivity?.let { consentActivity ->
                    AdConsentManager(consentActivity).showPrivacyOptions(consentActivity) {}
                }
            },
        )
    } else MainCalendarScreen(
        state = uiState,
        zoneId = zoneId,
        onSelectDate = viewModel::selectDate,
        onShowMonth = viewModel::showMonth,
        onRefresh = viewModel::refresh,
        onOpenSettings = { onSettingsOpenChanged(true) },
        onOpenEvent = viewModel::openEvent,
        onCloseEvent = viewModel::closeEvent,
        onOpenExternalAction = { action -> openSafeExternalAction(context, action) },
        onToday = viewModel::showToday,
        onToggleViewMode = viewModel::toggleViewMode,
        onSelectDateAndShowCombined = viewModel::selectDateAndShowCombined,
        weekStart = appSettings.weekStart,
        themePresentation = themePresentation,
        monetization = monetization.copy(canShowAds = monetization.canShowAds && adConsent.ready),
        showLoginPrompt = showLoginPrompt,
        canRefresh = !showLoginPrompt,
        onOpenLogin = onOpenLogin,
        onCreateEvent = {
            if (monetization.purchaseState == PurchaseUiState.OWNED) {
                editorSequence += 1
                editorRequest = EventEditorRequest(editorSequence, null, false)
            } else onPurchase()
        },
        onEditEvent = { instanceId ->
            viewModel.closeEvent()
            if (monetization.purchaseState == PurchaseUiState.OWNED) {
                editorSequence += 1
                editorRequest = EventEditorRequest(editorSequence, instanceId, false)
            } else onPurchase()
        },
        onDeleteEvent = { instanceId ->
            if (monetization.purchaseState == PurchaseUiState.OWNED) {
                editorSequence += 1
                editorRequest = EventEditorRequest(editorSequence, instanceId, true)
            } else onPurchase()
        },
    )

    editorRequest?.let { request ->
        val editorViewModel: EventEditorViewModel = viewModel(
            key = "event-editor-${request.sequence}",
            factory = EventEditorViewModelFactory(
                repository = eventEditingRepository,
                selectedDate = request.selectedDate ?: uiState.selectedDate,
                instanceId = request.instanceId,
                isPremium = { monetization.purchaseState == PurchaseUiState.OWNED },
                isSignedIn = { !showLoginPrompt },
                zoneId = zoneId,
            ),
        )
        val editorState by editorViewModel.uiState.collectAsState()
        LaunchedEffect(editorState.completed) {
            if (editorState.completed) editorRequest = null
        }
        EventEditorScreen(
            state = editorState,
            zoneId = zoneId,
            isEditing = request.instanceId != null,
            themePresentation = themePresentation,
            onUpdateDraft = { updated -> editorViewModel.updateDraft { updated } },
            onSave = editorViewModel::save,
            onDelete = editorViewModel::delete,
            onDismiss = { editorRequest = null },
            showDeleteConfirmationInitially = request.confirmDelete,
        )
    }
}

private data class EventEditorRequest(
    val sequence: Int,
    val instanceId: String?,
    val confirmDelete: Boolean,
    val selectedDate: LocalDate? = null,
)

@Composable
private fun rememberAdConsentState(activity: Activity?): AdConsentState {
    activity ?: return AdConsentState()
    val consentManager = remember(activity) { AdConsentManager(activity) }
    var ready by remember { mutableStateOf(false) }
    var privacyOptionsRequired by remember { mutableStateOf(false) }

    LaunchedEffect(activity, consentManager) {
        ready = false
        privacyOptionsRequired = false
        consentManager.initialize(activity) {
            ready = true
            privacyOptionsRequired = consentManager.privacyOptionsRequired()
        }
    }
    return AdConsentState(
        ready = ready,
        privacyOptionsRequired = privacyOptionsRequired,
    )
}

private data class AdConsentState(
    val ready: Boolean = false,
    val privacyOptionsRequired: Boolean = false,
)

internal suspend fun reconcileRemindersForPurchaseState(
    state: PurchaseUiState,
    reminderScheduler: ReminderScheduler,
) {
    if (state == PurchaseUiState.OWNED) reminderScheduler.reconcile()
}

internal fun activeAgendaWidgetIds(context: Context): IntArray = AppWidgetManager.getInstance(context)
    .getAppWidgetIds(ComponentName(context, AgendaWidgetProvider::class.java))

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

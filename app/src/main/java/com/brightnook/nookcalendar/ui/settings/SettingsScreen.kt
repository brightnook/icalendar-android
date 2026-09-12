package com.brightnook.nookcalendar.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.brightnook.nookcalendar.R
import com.brightnook.nookcalendar.BuildConfig
import com.brightnook.nookcalendar.domain.CalendarSummary
import com.brightnook.nookcalendar.domain.AppLanguage
import com.brightnook.nookcalendar.domain.SyncMode
import com.brightnook.nookcalendar.domain.ThemeBackgroundMode
import com.brightnook.nookcalendar.domain.WeekStart
import com.brightnook.nookcalendar.sync.AutomaticSyncOutcome
import com.brightnook.nookcalendar.sync.AutomaticSyncStatus
import com.brightnook.nookcalendar.monetization.MonetizationUiState
import com.brightnook.nookcalendar.monetization.PurchaseAction
import com.brightnook.nookcalendar.monetization.PurchaseFeedback
import com.brightnook.nookcalendar.monetization.PurchaseUiState
import com.brightnook.nookcalendar.ui.PrimaryControlRippleScope
import com.brightnook.nookcalendar.ui.transparentLabeledActionPresentation
import com.brightnook.nookcalendar.ui.theme.CalendarBackground
import com.brightnook.nookcalendar.ui.theme.CalendarThemePresentation
import com.brightnook.nookcalendar.widget.WidgetManagementItem
import com.brightnook.nookcalendar.widget.WidgetRange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

internal const val SETTINGS_ROOT_SURFACE_TAG = "settings-root-surface"
internal val SettingsContainerColorSemantics = SemanticsPropertyKey<Color>("SettingsContainerColor")
internal val SettingsSectionColorSemantics = SemanticsPropertyKey<Color>("SettingsSectionColor")
internal val SettingsContentColorSemantics = SemanticsPropertyKey<Color>("SettingsContentColor")
internal val SettingsActionColorSemantics = SemanticsPropertyKey<Color>("SettingsActionColor")

internal data class SettingsSurfacePresentation(
    val containerColor: Color,
    val sectionColor: Color,
    val modalContainerColor: Color,
    val modalScrimColor: Color,
    val contentColor: Color,
    val secondaryContentColor: Color,
    val outlineColor: Color,
    val actionContentColor: Color,
    val errorColor: Color,
)

internal fun settingsSurfacePresentation(
    themePresentation: CalendarThemePresentation,
) = SettingsSurfacePresentation(
    containerColor = themePresentation.settingsContainerColor,
    sectionColor = themePresentation.settingsSectionColor,
    modalContainerColor = themePresentation.modalContainerColor,
    modalScrimColor = themePresentation.modalScrimColor,
    contentColor = themePresentation.contentColor,
    secondaryContentColor = themePresentation.secondaryContentColor,
    outlineColor = themePresentation.outlineColor,
    actionContentColor = themePresentation.actionContentColor,
    errorColor = themePresentation.errorColor,
)

@Composable
internal fun SettingsSavedMessageHandler(
    savedEvents: Flow<Unit>,
    savedMessage: String,
    snackbarHostState: SnackbarHostState,
) {
    val currentSavedMessage by rememberUpdatedState(savedMessage)
    LaunchedEffect(savedEvents) {
        savedEvents.collect {
            snackbarHostState.showSnackbar(currentSavedMessage)
        }
    }
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    themePresentation: CalendarThemePresentation,
    onClose: () -> Unit,
    onSelectSyncMode: (SyncMode) -> Unit,
    onSetCalendarVisible: (String, Boolean) -> Unit,
    onClearHistory: () -> Unit,
    onClearCache: () -> Unit,
    onLogout: () -> Unit,
    onDismissConfirmation: () -> Unit,
    onConfirmDestructiveAction: () -> Unit,
    onSelectWeekStart: (WeekStart) -> Unit = {},
    onSelectLanguage: (AppLanguage) -> Unit = {},
    onSelectTheme: (ThemeBackgroundMode) -> Unit = {},
    onImportTheme: (Uri) -> Unit = {},
    onRemoveThemeImage: () -> Unit = {},
    onSetApplyThemeToWidget: (Boolean) -> Unit = {},
    managedWidgets: List<WidgetManagementItem> = emptyList(),
    onConfigureWidget: (Int) -> Unit = {},
    settingsSavedEvents: Flow<Unit> = emptyFlow(),
    purchaseFeedbackEvents: Flow<PurchaseFeedback> = emptyFlow(),
    monetization: MonetizationUiState = MonetizationUiState(
        canShowAds = true,
        purchaseState = PurchaseUiState.UNAVAILABLE,
        price = null,
    ),
    onPurchase: () -> Unit = {},
    onRestorePurchase: () -> Unit = {},
    onRefreshPurchaseSupport: suspend () -> Unit = {},
    onLogin: () -> Unit = {},
    privacyOptionsRequired: Boolean = false,
    onOpenPrivacyOptions: () -> Unit = {},
) {
    BackHandler(onBack = onClose)
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(onImportTheme)
    }
    val closeDescription = stringResource(R.string.settings_close)
    val logoutDescription = stringResource(R.string.settings_logout)
    val settingsSavedMessage = stringResource(R.string.settings_saved)
    val purchaseCancelledMessage = stringResource(R.string.settings_purchase_cancelled)
    val purchaseFailedMessage = stringResource(R.string.settings_purchase_failed)
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        refreshPurchaseSupportWhenSettingsOpened(monetization.purchaseState, onRefreshPurchaseSupport)
    }
    SettingsSavedMessageHandler(
        savedEvents = settingsSavedEvents,
        savedMessage = settingsSavedMessage,
        snackbarHostState = snackbarHostState,
    )
    LaunchedEffect(purchaseFeedbackEvents) {
        purchaseFeedbackEvents.collect { feedback ->
            snackbarHostState.showSnackbar(
                when (feedback) {
                    PurchaseFeedback.CANCELLED -> purchaseCancelledMessage
                    PurchaseFeedback.FAILED -> purchaseFailedMessage
                },
            )
        }
    }
    val actionPresentation = transparentLabeledActionPresentation()
    val settingsPresentation = settingsSurfacePresentation(themePresentation)
    val settingsColorScheme = MaterialTheme.colorScheme.copy(
        primary = settingsPresentation.actionContentColor,
        background = settingsPresentation.containerColor,
        surface = settingsPresentation.sectionColor,
        surfaceVariant = settingsPresentation.sectionColor,
        surfaceContainer = settingsPresentation.modalContainerColor,
        surfaceContainerHigh = settingsPresentation.modalContainerColor,
        onBackground = settingsPresentation.contentColor,
        onSurface = settingsPresentation.contentColor,
        onSurfaceVariant = settingsPresentation.secondaryContentColor,
        outline = settingsPresentation.outlineColor,
        error = settingsPresentation.errorColor,
        scrim = settingsPresentation.modalScrimColor,
    )
    MaterialTheme(colorScheme = settingsColorScheme) {
    CalendarBackground(presentation = themePresentation) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag(SETTINGS_ROOT_SURFACE_TAG)
            .semantics {
                this[SettingsContainerColorSemantics] = settingsPresentation.containerColor
                this[SettingsContentColorSemantics] = settingsPresentation.contentColor
            },
        color = settingsPresentation.containerColor,
        contentColor = settingsPresentation.contentColor,
    ) {
    Box(Modifier.fillMaxSize()) {
    PrimaryControlRippleScope {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings-header")
                .semantics {
                    this[SettingsSectionColorSemantics] = settingsPresentation.sectionColor
                    this[SettingsContentColorSemantics] = settingsPresentation.contentColor
                },
            shape = RoundedCornerShape(20.dp),
            color = settingsPresentation.sectionColor,
            contentColor = settingsPresentation.contentColor,
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.settings_title),
                    Modifier.weight(1f).heightIn(min = 48.dp).wrapContentHeight(Alignment.CenterVertically).testTag("settings-title"),
                )
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.semantics { contentDescription = closeDescription },
                ) {
                    Icon(
                        painter = painterResource(settingsCloseArtwork(themePresentation)),
                        contentDescription = null,
                        modifier = Modifier.testTag("settings-close-icon"),
                        tint = themePresentation.actionIconTint(),
                    )
                }
            }
        }
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsSection(R.string.settings_account, "account", settingsPresentation) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        state.maskedAppleId ?: stringResource(R.string.settings_account_signed_out),
                        Modifier.weight(1f).testTag("masked-account"),
                    )
                    if (state.maskedAppleId != null) {
                        TextButton(
                            onClick = onLogout,
                            modifier = Modifier.heightIn(min = actionPresentation.minimumTouchTarget).testTag("account-logout").semantics {
                                contentDescription = logoutDescription
                            },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_action_logout_transparent),
                                contentDescription = null,
                                modifier = Modifier.testTag("settings-logout-icon"),
                                tint = themePresentation.actionIconTint(),
                            )
                            Spacer(Modifier.width(actionPresentation.iconTextSpacing))
                            Text(logoutDescription)
                        }
                    } else if (!state.accountLoading) {
                        TextButton(onClick = onLogin, modifier = Modifier.testTag("account-login")) {
                            Text(stringResource(R.string.calendar_login_action))
                        }
                    }
                }
            }
            SettingsSection(R.string.settings_support, "support", settingsPresentation) {
                PurchaseSupportContent(
                    monetization = monetization,
                    onPurchase = onPurchase,
                    onRestorePurchase = onRestorePurchase,
                )
                if (privacyOptionsRequired) {
                    TextButton(
                        onClick = onOpenPrivacyOptions,
                        modifier = Modifier.testTag("privacy-options"),
                    ) { Text(stringResource(R.string.settings_privacy_options)) }
                }
            }
            SettingsSection(R.string.settings_sync_mode, "sync", settingsPresentation) {
                SyncModePicker(
                    selectedMode = state.syncMode,
                    automaticSyncStatus = state.automaticSyncStatus,
                    settingsPresentation = settingsPresentation,
                    onSelectMode = onSelectSyncMode,
                )
            }
            SettingsSection(R.string.settings_week_start, "week-start", settingsPresentation) {
                WeekStartPicker(state.weekStart, settingsPresentation, onSelectWeekStart)
            }
            SettingsSection(R.string.settings_language, "language", settingsPresentation) {
                LanguagePicker(state.language, settingsPresentation, onSelectLanguage)
            }
            SettingsSection(R.string.settings_theme, "theme", settingsPresentation) {
                ThemeSettingsSection(
                    theme = state.theme,
                    settingsPresentation = settingsPresentation,
                    importError = state.themeImportError,
                    photoPicker = photoPicker,
                    onSelectTheme = onSelectTheme,
                    onRemoveThemeImage = onRemoveThemeImage,
                    onSetApplyThemeToWidget = onSetApplyThemeToWidget,
                )
            }
            SettingsSection(R.string.settings_widgets, "widgets", settingsPresentation) {
                if (managedWidgets.isEmpty()) {
                    Text(
                        stringResource(R.string.settings_widgets_empty),
                        modifier = Modifier.testTag("widget-management-empty"),
                    )
                } else {
                    managedWidgets.forEach { widget ->
                        TextButton(
                            onClick = { onConfigureWidget(widget.appWidgetId) },
                            modifier = Modifier.fillMaxWidth()
                                .heightIn(min = actionPresentation.minimumTouchTarget)
                                .testTag("widget-management-${widget.appWidgetId}"),
                        ) {
                            Text(
                                stringResource(
                                    R.string.settings_widget_configure,
                                    widget.displayOrdinal,
                                    widget.range.label(),
                                ),
                            )
                        }
                    }
                }
            }
            SettingsSection(R.string.settings_calendars, "calendars", settingsPresentation) {
                state.calendars.forEach { calendar ->
                    CalendarRow(calendar, settingsPresentation, onSetCalendarVisible)
                }
            }
            SettingsSection(R.string.settings_data_and_sign_out, "data", settingsPresentation) {
                TextButton(
                    onClick = onClearHistory,
                    modifier = Modifier.heightIn(min = actionPresentation.minimumTouchTarget).testTag("clear-history"),
                ) { Text(stringResource(R.string.settings_clear_history)) }
                TextButton(
                    onClick = onClearCache,
                    modifier = Modifier.heightIn(min = actionPresentation.minimumTouchTarget).testTag("clear-cache"),
                ) { Text(stringResource(R.string.settings_clear_cache)) }
                if (state.showCleanupWarning) Text(stringResource(R.string.settings_cleanup_warning))
                if (state.showOperationError) Text(stringResource(R.string.settings_operation_failed))
            }
            SettingsSection(R.string.settings_about, "about", settingsPresentation) {
                Text(
                    stringResource(
                        R.string.settings_version,
                        BuildConfig.VERSION_NAME,
                        BuildConfig.VERSION_CODE,
                    ),
                    modifier = Modifier.testTag("app-version"),
                )
            }
        }
    }
    }
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
        snackbar = { data ->
            Snackbar(
                snackbarData = data,
                containerColor = settingsPresentation.modalContainerColor,
                contentColor = settingsPresentation.contentColor,
                actionColor = settingsPresentation.actionContentColor,
                dismissActionContentColor = settingsPresentation.actionContentColor,
            )
        },
    )
    }
    }
    }
    state.confirmation?.let { action ->
        AlertDialog(
            onDismissRequest = onDismissConfirmation,
            containerColor = settingsPresentation.modalContainerColor,
            titleContentColor = settingsPresentation.contentColor,
            textContentColor = settingsPresentation.secondaryContentColor,
            title = { Text(action.title()) },
            text = { Text(action.message()) },
            confirmButton = {
                TextButton(
                    onClick = onConfirmDestructiveAction,
                    enabled = !state.destructiveActionInProgress,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = settingsPresentation.actionContentColor,
                    ),
                    modifier = Modifier.testTag("settings-confirm"),
                ) { Text(action.confirmLabel()) }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismissConfirmation,
                    enabled = !state.destructiveActionInProgress,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = settingsPresentation.actionContentColor,
                    ),
                ) { Text(stringResource(R.string.settings_cancel)) }
            },
        )
    }
    }
}

internal suspend fun refreshPurchaseSupportWhenSettingsOpened(
    purchaseState: PurchaseUiState,
    refresh: suspend () -> Unit,
) {
    if (purchaseState != PurchaseUiState.OWNED) refresh()
}

@Composable
private fun PurchaseSupportContent(
    monetization: MonetizationUiState,
    onPurchase: () -> Unit,
    onRestorePurchase: () -> Unit,
) {
    when (monetization.purchaseState) {
        PurchaseUiState.AVAILABLE -> {
            if (monetization.price.isNullOrBlank()) {
                Text(stringResource(R.string.settings_billing_unavailable))
                RestorePurchasesAction(onRestorePurchase)
            } else {
                Text(stringResource(R.string.settings_play_price, monetization.price))
                TextButton(
                    onClick = onPurchase,
                    modifier = Modifier.testTag("remove-ads"),
                ) { Text(stringResource(R.string.settings_remove_ads_forever)) }
                RestorePurchasesAction(onRestorePurchase)
            }
        }
        PurchaseUiState.LOADING -> {
            if (monetization.price.isNullOrBlank()) {
                Text(stringResource(R.string.settings_billing_loading))
            } else {
                Text(stringResource(R.string.settings_play_price, monetization.price))
            }
            TextButton(
                onClick = onPurchase,
                enabled = monetization.activePurchaseAction != PurchaseAction.PURCHASE,
                modifier = Modifier.testTag("remove-ads"),
            ) { Text(stringResource(R.string.settings_remove_ads_forever)) }
            RestorePurchasesAction(
                onRestorePurchase,
                enabled = monetization.activePurchaseAction != PurchaseAction.RESTORE,
            )
        }
        PurchaseUiState.PENDING -> {
            monetization.price?.takeIf { it.isNotBlank() }?.let { price ->
                Text(stringResource(R.string.settings_play_price, price))
            }
            Text(stringResource(R.string.settings_payment_pending))
            RestorePurchasesAction(onRestorePurchase)
        }
        PurchaseUiState.OWNED -> Text(stringResource(R.string.settings_ads_removed))
        PurchaseUiState.UNAVAILABLE -> {
            Text(stringResource(R.string.settings_billing_unavailable))
            RestorePurchasesAction(onRestorePurchase)
        }
    }
}

@Composable
private fun RestorePurchasesAction(onRestorePurchase: () -> Unit, enabled: Boolean = true) {
    TextButton(
        onClick = onRestorePurchase,
        enabled = enabled,
        modifier = Modifier.testTag("restore-purchases"),
    ) { Text(stringResource(R.string.settings_restore_purchases)) }
}

@Composable
private fun SyncModePicker(
    selectedMode: SyncMode,
    automaticSyncStatus: AutomaticSyncStatus?,
    settingsPresentation: SettingsSurfacePresentation,
    onSelectMode: (SyncMode) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val title = stringResource(R.string.settings_sync_mode)
    val selectedLabel = selectedMode.label()
    Column {
        Box {
        TextButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("sync-mode-picker").semantics {
                contentDescription = "$title: $selectedLabel"
            },
        ) {
            Text(selectedLabel, modifier = Modifier.weight(1f))
            Text("⌄")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = settingsPresentation.modalContainerColor,
        ) {
            syncModePickerOptions().forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.label()) },
                    onClick = {
                        expanded = false
                        onSelectMode(mode)
                    },
                    colors = MenuDefaults.itemColors(
                        textColor = settingsPresentation.contentColor,
                    ),
                    modifier = Modifier.testTag("sync-mode-option-$mode"),
                )
            }
        }
        }
        if (selectedMode == SyncMode.HOURLY || selectedMode == SyncMode.DAILY) {
            val locale = LocalConfiguration.current.locales[0]
            val statusText = automaticSyncStatus?.let { status ->
                stringResource(
                    R.string.settings_last_automatic_sync,
                    formatAutomaticSyncTimestamp(status.completedAt, locale),
                    status.outcome.label(),
                )
            } ?: stringResource(R.string.settings_last_automatic_sync_none)
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = settingsPresentation.secondaryContentColor,
                modifier = Modifier.padding(start = 16.dp, top = 2.dp),
            )
        }
    }
}

@Composable
private fun WeekStartPicker(
    selectedWeekStart: WeekStart,
    settingsPresentation: SettingsSurfacePresentation,
    onSelectWeekStart: (WeekStart) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val title = stringResource(R.string.settings_week_start)
    val selectedLabel = selectedWeekStart.label()
    Box {
        TextButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("week-start-picker").semantics {
                contentDescription = "$title: $selectedLabel"
            },
        ) {
            Text(selectedLabel, modifier = Modifier.weight(1f))
            Text("⌄")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = settingsPresentation.modalContainerColor,
        ) {
            WeekStart.entries.forEach { weekStart ->
                DropdownMenuItem(
                    text = { Text(weekStart.label()) },
                    onClick = {
                        expanded = false
                        onSelectWeekStart(weekStart)
                    },
                    colors = MenuDefaults.itemColors(
                        textColor = settingsPresentation.contentColor,
                    ),
                    modifier = Modifier.testTag("week-start-option-$weekStart"),
                )
            }
        }
    }
}

@Composable
private fun LanguagePicker(
    selectedLanguage: AppLanguage,
    settingsPresentation: SettingsSurfacePresentation,
    onSelectLanguage: (AppLanguage) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val title = stringResource(R.string.settings_language)
    val selectedLabel = selectedLanguage.label()
    Box {
        TextButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("language-picker").semantics {
                contentDescription = "$title: $selectedLabel"
            },
        ) {
            Text(selectedLabel, modifier = Modifier.weight(1f))
            Text("⌄")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = settingsPresentation.modalContainerColor,
        ) {
            AppLanguage.entries.forEach { language ->
                DropdownMenuItem(
                    text = { Text(language.label()) },
                    onClick = {
                        expanded = false
                        onSelectLanguage(language)
                    },
                    colors = MenuDefaults.itemColors(
                        textColor = settingsPresentation.contentColor,
                    ),
                    modifier = Modifier.testTag("language-option-$language"),
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: Int,
    tag: String,
    settingsPresentation: SettingsSurfacePresentation,
    content: @Composable ColumnScope.() -> Unit,
) = Surface(
    modifier = Modifier
        .fillMaxWidth()
        .testTag("settings-section-$tag")
        .semantics {
            this[SettingsSectionColorSemantics] = settingsPresentation.sectionColor
            this[SettingsContentColorSemantics] = settingsPresentation.contentColor
        },
    shape = RoundedCornerShape(20.dp),
    color = settingsPresentation.sectionColor,
    contentColor = settingsPresentation.contentColor,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            content()
        },
    )
}

@Composable private fun CalendarRow(
    calendar: CalendarSummary,
    settingsPresentation: SettingsSurfacePresentation,
    update: (String, Boolean) -> Unit,
) = Row(Modifier.fillMaxWidth()) {
    Box(Modifier.padding(top = 14.dp).size(14.dp).clip(CircleShape).background(Color(calendar.colorArgb)).testTag("calendar-color-${calendar.id}"))
    Text(calendar.name, Modifier.weight(1f).padding(start = 12.dp, top = 10.dp))
    Switch(
        checked = calendar.isVisible,
        onCheckedChange = { update(calendar.id, it) },
        modifier = Modifier.testTag("calendar-visibility-${calendar.id}"),
        colors = settingsSwitchColors(settingsPresentation),
    )
}

@Composable
private fun settingsSwitchColors(settingsPresentation: SettingsSurfacePresentation) = SwitchDefaults.colors(
    checkedThumbColor = settingsPresentation.modalContainerColor,
    checkedTrackColor = settingsPresentation.actionContentColor,
    checkedBorderColor = settingsPresentation.actionContentColor,
    uncheckedThumbColor = settingsPresentation.secondaryContentColor,
    uncheckedTrackColor = Color.Transparent,
    uncheckedBorderColor = settingsPresentation.outlineColor,
)

private fun CalendarThemePresentation.actionIconTint(): Color =
    if (useOriginalActionArtwork) Color.Unspecified else actionContentColor

internal fun settingsCloseArtwork(presentation: CalendarThemePresentation): Int =
    if (presentation.useOriginalActionArtwork) {
        R.drawable.ic_action_close
    } else {
        R.drawable.ic_action_close_transparent
    }

@Composable private fun SyncMode.label() = stringResource(when (this) { SyncMode.MANUAL -> R.string.settings_mode_manual; SyncMode.HOURLY -> R.string.settings_mode_hourly; SyncMode.DAILY -> R.string.settings_mode_daily; SyncMode.DISABLED -> R.string.settings_mode_disabled })
@Composable private fun AutomaticSyncOutcome.label() = stringResource(
    when (this) {
        AutomaticSyncOutcome.COMPLETED -> R.string.settings_automatic_sync_completed
        AutomaticSyncOutcome.RETRYING -> R.string.settings_automatic_sync_retrying
        AutomaticSyncOutcome.AUTHENTICATION_REQUIRED -> R.string.settings_automatic_sync_authentication_required
    },
)
@Composable private fun WeekStart.label() = stringResource(if (this == WeekStart.SUNDAY) R.string.settings_week_start_sunday else R.string.settings_week_start_monday)
@Composable private fun AppLanguage.label() = stringResource(
    when (this) {
        AppLanguage.FOLLOW_SYSTEM -> R.string.settings_language_follow_system
        AppLanguage.CHINESE -> R.string.settings_language_chinese
        AppLanguage.ENGLISH -> R.string.settings_language_english
        AppLanguage.JAPANESE -> R.string.settings_language_japanese
    },
)
@Composable private fun WidgetRange.label() = stringResource(
    when (this) {
        WidgetRange.TODAY -> R.string.widget_range_today
        WidgetRange.THREE_DAYS -> R.string.widget_range_three_days
        WidgetRange.SEVEN_DAYS -> R.string.widget_range_seven_days
        WidgetRange.THIRTY_DAYS -> R.string.widget_range_thirty_days
    },
)
internal fun syncModePickerOptions(): List<SyncMode> = SyncMode.entries
@Composable private fun SettingsConfirmation.title() = stringResource(when (this) { SettingsConfirmation.ClearHistory -> R.string.settings_clear_history_title; SettingsConfirmation.ClearCache -> R.string.settings_clear_cache_title; is SettingsConfirmation.Logout -> R.string.settings_logout_title })
@Composable private fun SettingsConfirmation.message() = stringResource(when (this) { SettingsConfirmation.ClearHistory -> R.string.settings_clear_history_message; SettingsConfirmation.ClearCache -> R.string.settings_clear_cache_message; is SettingsConfirmation.Logout -> R.string.settings_logout_message })
@Composable private fun SettingsConfirmation.confirmLabel() = stringResource(
    when (this) {
        SettingsConfirmation.ClearHistory -> R.string.settings_clear_history_confirm
        SettingsConfirmation.ClearCache -> R.string.settings_clear_cache_confirm
        is SettingsConfirmation.Logout -> R.string.settings_logout_confirm
    },
)

package com.victor2319.ioscalendar.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.victor2319.ioscalendar.R
import com.victor2319.ioscalendar.domain.CalendarSummary
import com.victor2319.ioscalendar.domain.SyncMode

@Composable
fun SettingsScreen(state: SettingsUiState, onClose: () -> Unit, onSelectSyncMode: (SyncMode) -> Unit, onSetCalendarVisible: (String, Boolean) -> Unit, onClearHistory: () -> Unit, onClearCache: () -> Unit, onLogout: () -> Unit, onDismissConfirmation: () -> Unit, onConfirmDestructiveAction: () -> Unit) {
    BackHandler(onBack = onClose)
    val closeDescription = stringResource(R.string.settings_close)
    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_title), Modifier.weight(1f))
            IconButton(
                onClick = onClose,
                modifier = Modifier.semantics { contentDescription = closeDescription },
            ) { Text("×", style = MaterialTheme.typography.titleLarge) }
        }
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(state.maskedAppleId ?: stringResource(R.string.settings_account_signed_out), Modifier.testTag("masked-account"))
            Text(stringResource(R.string.settings_sync_mode))
            SyncMode.entries.forEach { mode -> Row(Modifier.fillMaxWidth()) { RadioButton(state.syncMode == mode, { onSelectSyncMode(mode) }); Text(mode.label(), Modifier.padding(top = 12.dp)) } }
            Text(stringResource(R.string.settings_calendars))
            state.calendars.forEach { calendar -> CalendarRow(calendar, onSetCalendarVisible) }
            Button(onClick = onClearHistory) { Text(stringResource(R.string.settings_clear_history)) }
            Button(onClick = onClearCache) { Text(stringResource(R.string.settings_clear_cache)) }
            Button(onClick = onLogout) { Text(stringResource(R.string.settings_logout)) }
            if (state.showCleanupWarning) Text(stringResource(R.string.settings_cleanup_warning))
            if (state.showOperationError) Text(stringResource(R.string.settings_operation_failed))
        }
    }
    state.confirmation?.let { action -> AlertDialog(onDismissRequest = onDismissConfirmation, title = { Text(action.title()) }, text = { Text(action.message()) }, confirmButton = { TextButton(onClick = onConfirmDestructiveAction, enabled = !state.destructiveActionInProgress) { Text(stringResource(R.string.settings_delete)) } }, dismissButton = { TextButton(onClick = onDismissConfirmation, enabled = !state.destructiveActionInProgress) { Text(stringResource(R.string.settings_cancel)) } }) }
}

@Composable private fun CalendarRow(calendar: CalendarSummary, update: (String, Boolean) -> Unit) = Row(Modifier.fillMaxWidth()) {
    Box(Modifier.padding(top = 14.dp).size(14.dp).clip(CircleShape).background(Color(calendar.colorArgb)).testTag("calendar-color-${calendar.id}"))
    Text(calendar.name, Modifier.weight(1f).padding(start = 12.dp, top = 10.dp))
    Switch(calendar.isVisible, { update(calendar.id, it) }, Modifier.testTag("calendar-visibility-${calendar.id}"))
}

@Composable private fun SyncMode.label() = stringResource(when (this) { SyncMode.MANUAL -> R.string.settings_mode_manual; SyncMode.HOURLY -> R.string.settings_mode_hourly; SyncMode.DAILY -> R.string.settings_mode_daily; SyncMode.DISABLED -> R.string.settings_mode_disabled })
@Composable private fun SettingsConfirmation.title() = stringResource(when (this) { SettingsConfirmation.ClearHistory -> R.string.settings_clear_history_title; SettingsConfirmation.ClearCache -> R.string.settings_clear_cache_title; is SettingsConfirmation.Logout -> R.string.settings_logout_title })
@Composable private fun SettingsConfirmation.message() = stringResource(when (this) { SettingsConfirmation.ClearHistory -> R.string.settings_clear_history_message; SettingsConfirmation.ClearCache -> R.string.settings_clear_cache_message; is SettingsConfirmation.Logout -> R.string.settings_logout_message })

package com.victor2319.ioscalendar.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.victor2319.ioscalendar.R
import com.victor2319.ioscalendar.ui.PrimaryControlRippleScope
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

const val SELECTED_DATE_TAG = "selected-date"
const val EVENT_DETAIL_SHEET_TAG = "event-detail-sheet"
const val BOTTOM_TODAY_TAG = "bottom-today"
const val BOTTOM_VIEW_TAG = "bottom-view"
const val BOTTOM_PICK_DATE_TAG = "bottom-pick-date"
const val BOTTOM_REFRESH_TAG = "bottom-refresh"
const val BOTTOM_SETTINGS_TAG = "bottom-settings"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainCalendarScreen(
    state: MainCalendarState,
    zoneId: ZoneId,
    onSelectDate: (java.time.LocalDate) -> Unit,
    onShowMonth: (java.time.YearMonth) -> Unit,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onOpenEvent: (String) -> Unit,
    onCloseEvent: () -> Unit,
    onOpenExternalAction: (ExternalAction) -> Unit,
    onToday: () -> Unit = {},
    onToggleViewMode: () -> Unit = {},
    onSelectDateAndShowCombined: (java.time.LocalDate) -> Unit = onSelectDate,
) {
    val agendaListState = rememberLazyListState()
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        if (state.viewMode == CalendarViewMode.YEAR) {
            YearCalendar(state.displayedMonth.year, state.selectedDate, state.dayMarkerColors, onSelectDateAndShowCombined, Modifier.fillMaxWidth().weight(1f))
        } else {
            MonthGrid(
                month = state.displayedMonth, selectedDate = state.selectedDate, dayMarkerColors = state.dayMarkerColors,
                onSelectDate = onSelectDate, onShowMonth = onShowMonth,
                modifier = Modifier.fillMaxWidth().weight(0.58f).padding(horizontal = 8.dp),
            )
            HorizontalDivider()
            Column(Modifier.fillMaxWidth().weight(0.42f)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    state.selectedDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(currentLocale())),
                    modifier = Modifier.weight(1f).testTag(SELECTED_DATE_TAG),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Box(Modifier.fillMaxWidth().height(32.dp)) {
                state.message?.let {
                    Text(
                        stringResource(it.stringResource),
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            AgendaList(
                agenda = state.agenda,
                calendars = state.calendars,
                zoneId = zoneId,
                listState = agendaListState,
                onOpenEvent = onOpenEvent,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            }
        }
        HorizontalDivider()
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly) {
            ActionIcon(BOTTOM_TODAY_TAG, R.string.calendar_today, R.drawable.ic_action_today, onToday)
            ActionIcon(BOTTOM_VIEW_TAG, if (state.viewMode == CalendarViewMode.COMBINED) R.string.calendar_show_year else R.string.calendar_show_combined, R.drawable.ic_action_view_switch, onToggleViewMode)
            ActionIcon(BOTTOM_PICK_DATE_TAG, R.string.calendar_pick_date, R.drawable.ic_action_search) {
                android.app.DatePickerDialog(context, { _, y, m, d -> onSelectDateAndShowCombined(java.time.LocalDate.of(y, m + 1, d)) }, state.selectedDate.year, state.selectedDate.monthValue - 1, state.selectedDate.dayOfMonth).show()
            }
            ActionIcon(BOTTOM_REFRESH_TAG, R.string.calendar_refresh, R.drawable.ic_action_refresh, onRefresh)
            ActionIcon(BOTTOM_SETTINGS_TAG, R.string.settings_title, R.drawable.ic_action_settings, onOpenSettings)
        }
    }

    state.openEvent?.let { detail ->
        ModalBottomSheet(
            onDismissRequest = onCloseEvent,
            modifier = Modifier.testTag(EVENT_DETAIL_SHEET_TAG),
        ) {
            EventDetailContent(detail, zoneId, onCloseEvent, onOpenExternalAction)
        }
    }
}

@Composable private fun ActionIcon(tag: String, description: Int, icon: Int, onClick: () -> Unit) {
    val name = stringResource(description)
    PrimaryControlRippleScope {
    IconButton(onClick, Modifier.testTag(tag).semantics { contentDescription = name }) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.testTag("$tag-icon"),
            tint = Color.Unspecified,
        )
    }
    }
}

private val MainCalendarMessage.stringResource: Int
    get() = when (this) {
        MainCalendarMessage.SYNC_NOT_READY -> R.string.calendar_sync_not_ready
        MainCalendarMessage.SYNC_DISABLED -> R.string.calendar_sync_disabled
        MainCalendarMessage.SYNC_FAILED -> R.string.calendar_sync_failed
    }

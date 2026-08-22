package com.victor2319.ioscalendar.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.victor2319.ioscalendar.R
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

const val SELECTED_DATE_TAG = "selected-date"
const val EVENT_DETAIL_SHEET_TAG = "event-detail-sheet"

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
) {
    val agendaListState = rememberLazyListState()
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        MonthGrid(
            month = state.displayedMonth,
            selectedDate = state.selectedDate,
            onSelectDate = onSelectDate,
            onShowMonth = onShowMonth,
            modifier = Modifier.fillMaxWidth().weight(0.58f).padding(horizontal = 8.dp),
        )
        HorizontalDivider()
        Column(Modifier.fillMaxWidth().weight(0.42f)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    state.selectedDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(currentLocale())),
                    modifier = Modifier.weight(1f).testTag(SELECTED_DATE_TAG),
                    style = MaterialTheme.typography.titleMedium,
                )
                Button(onClick = onRefresh) { Text(stringResource(R.string.calendar_refresh)) }
                Button(onClick = onOpenSettings) { Text(stringResource(R.string.settings_title)) }
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

    state.openEvent?.let { detail ->
        ModalBottomSheet(
            onDismissRequest = onCloseEvent,
            modifier = Modifier.testTag(EVENT_DETAIL_SHEET_TAG),
        ) {
            EventDetailContent(detail, zoneId, onCloseEvent, onOpenExternalAction)
        }
    }
}

private val MainCalendarMessage.stringResource: Int
    get() = when (this) {
        MainCalendarMessage.SYNC_NOT_READY -> R.string.calendar_sync_not_ready
        MainCalendarMessage.SYNC_DISABLED -> R.string.calendar_sync_disabled
        MainCalendarMessage.SYNC_FAILED -> R.string.calendar_sync_failed
    }

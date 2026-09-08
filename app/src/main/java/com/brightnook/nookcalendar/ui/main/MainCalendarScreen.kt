package com.brightnook.nookcalendar.ui.main

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.brightnook.nookcalendar.R
import com.brightnook.nookcalendar.domain.ThemeBackgroundMode
import com.brightnook.nookcalendar.domain.ThemeSettings
import com.brightnook.nookcalendar.domain.WeekStart
import com.brightnook.nookcalendar.monetization.MonetizationUiState
import com.brightnook.nookcalendar.monetization.PurchaseUiState
import com.brightnook.nookcalendar.ui.PrimaryControlRippleScope
import com.brightnook.nookcalendar.ui.theme.CalendarBackground
import com.brightnook.nookcalendar.ui.theme.CalendarThemePresentation
import com.brightnook.nookcalendar.ui.theme.calendarThemePresentation
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

const val SELECTED_DATE_TAG = "selected-date"
const val EVENT_DETAIL_SHEET_TAG = "event-detail-sheet"
const val DATE_PICKER_SURFACE_TAG = "date-picker-surface"
const val BOTTOM_TODAY_TAG = "bottom-today"
const val BOTTOM_VIEW_TAG = "bottom-view"
const val BOTTOM_PICK_DATE_TAG = "bottom-pick-date"
const val BOTTOM_REFRESH_TAG = "bottom-refresh"
const val BOTTOM_SETTINGS_TAG = "bottom-settings"

internal data class CalendarModalPresentation(
    val containerColor: Color,
    val scrimColor: Color,
    val outlineColor: Color,
    val primaryContentColor: Color,
    val secondaryContentColor: Color,
    val actionContentColor: Color,
    val calendarHighlightColor: Color,
    val calendarHighlightContentColor: Color,
    val saturdayColor: Color,
    val sundayColor: Color,
)

internal fun calendarModalPresentation(
    presentation: CalendarThemePresentation,
    inheritedPrimary: Color,
    inheritedOnPrimary: Color,
): CalendarModalPresentation {
    val preserveCalendarAccent = presentation.resolvedMode == ThemeBackgroundMode.WHITE
    return CalendarModalPresentation(
        containerColor = presentation.modalContainerColor,
        scrimColor = presentation.modalScrimColor,
        outlineColor = presentation.outlineColor,
        primaryContentColor = presentation.contentColor,
        secondaryContentColor = presentation.secondaryContentColor,
        actionContentColor = presentation.actionContentColor,
        calendarHighlightColor = if (preserveCalendarAccent) inheritedPrimary else presentation.actionContentColor,
        calendarHighlightContentColor = if (preserveCalendarAccent) inheritedOnPrimary else presentation.modalContainerColor,
        saturdayColor = presentation.saturdayColor,
        sundayColor = presentation.sundayColor,
    )
}

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
    weekStart: WeekStart = WeekStart.SUNDAY,
    themePresentation: CalendarThemePresentation = calendarThemePresentation(ThemeSettings(), null),
    monetization: MonetizationUiState = MonetizationUiState(
        canShowAds = false,
        purchaseState = PurchaseUiState.UNAVAILABLE,
        price = null,
    ),
    showLoginPrompt: Boolean = false,
    canRefresh: Boolean = true,
    onOpenLogin: () -> Unit = {},
) {
    val agendaListState = rememberLazyListState()
    val context = LocalContext.current
    val locale = currentLocale()
    var datePickerOpen by rememberSaveable { mutableStateOf(false) }
    val modalPresentation = calendarModalPresentation(
        presentation = themePresentation,
        inheritedPrimary = MaterialTheme.colorScheme.primary,
        inheritedOnPrimary = MaterialTheme.colorScheme.onPrimary,
    )
    val dividerColor = if (themePresentation.resolvedMode == ThemeBackgroundMode.WHITE) {
        MaterialTheme.colorScheme.outlineVariant
    } else {
        themePresentation.contentColor.copy(alpha = 0.24f)
    }
    CalendarBackground(themePresentation) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        if (state.viewMode == CalendarViewMode.YEAR) {
            YearCalendar(
                state.displayedMonth.year,
                state.selectedDate,
                state.dayMarkerColors,
                onSelectDateAndShowCombined,
                Modifier.fillMaxWidth().weight(1f).calendarPanel(themePresentation, horizontalPadding = 0.dp),
                weekStart,
                contentColor = themePresentation.contentColor,
                secondaryContentColor = themePresentation.secondaryContentColor,
                saturdayColor = themePresentation.saturdayColor,
                sundayColor = themePresentation.sundayColor,
            )
        } else {
            MonthGrid(
                month = state.displayedMonth, selectedDate = state.selectedDate, dayMarkerColors = state.dayMarkerColors,
                onSelectDate = onSelectDate, onShowMonth = onShowMonth,
                modifier = Modifier.fillMaxWidth().weight(0.58f).calendarPanel(themePresentation),
                weekStart = weekStart,
                contentColor = themePresentation.contentColor,
                secondaryContentColor = themePresentation.secondaryContentColor,
                saturdayColor = themePresentation.saturdayColor,
                sundayColor = themePresentation.sundayColor,
            )
            HorizontalDivider(color = dividerColor)
            Column(Modifier.fillMaxWidth().weight(0.42f).calendarPanel(themePresentation, horizontalPadding = 0.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    state.selectedDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)),
                    modifier = Modifier.weight(1f).testTag(SELECTED_DATE_TAG),
                    color = themePresentation.contentColor,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Box(Modifier.fillMaxWidth().heightIn(min = 40.dp)) {
                if (showLoginPrompt) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.calendar_login_hint),
                            modifier = Modifier.weight(1f),
                            color = themePresentation.secondaryContentColor,
                        )
                        TextButton(onClick = onOpenLogin, modifier = Modifier.testTag("calendar-login")) {
                            Text(stringResource(R.string.calendar_login_action))
                        }
                    }
                } else state.message?.let {
                    Text(
                        stringResource(it.stringResource),
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = themePresentation.errorColor,
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
                contentColor = themePresentation.contentColor,
                secondaryContentColor = themePresentation.secondaryContentColor,
            )
            }
        }
        AdBannerSlot(monetization)
        HorizontalDivider(color = dividerColor)
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .calendarPanel(themePresentation, horizontalPadding = 8.dp, verticalPadding = 0.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly,
        ) {
            ActionIcon(BOTTOM_TODAY_TAG, R.string.calendar_today, R.drawable.ic_action_today, themePresentation, onToday)
            ActionIcon(BOTTOM_VIEW_TAG, if (state.viewMode == CalendarViewMode.COMBINED) R.string.calendar_show_year else R.string.calendar_show_combined, R.drawable.ic_action_view_switch, themePresentation, onToggleViewMode)
            ActionIcon(
                BOTTOM_PICK_DATE_TAG,
                R.string.calendar_pick_date,
                R.drawable.ic_action_search,
                themePresentation,
                onClick = { datePickerOpen = true },
            )
            ActionIcon(
                BOTTOM_REFRESH_TAG,
                R.string.calendar_refresh,
                R.drawable.ic_action_refresh,
                themePresentation,
                onClick = {
                    if (canRefresh) {
                        onRefresh()
                        Toast.makeText(context, R.string.calendar_refresh_started, Toast.LENGTH_SHORT).show()
                    } else {
                        onOpenLogin()
                    }
                },
                enabled = !state.isManualSyncInProgress,
            )
            ActionIcon(BOTTOM_SETTINGS_TAG, R.string.settings_title, R.drawable.ic_action_settings, themePresentation, onOpenSettings)
        }
    }
    }

    if (datePickerOpen) CompactDatePickerDialog(
        initialDate = state.selectedDate,
        weekStart = weekStart,
        modalPresentation = modalPresentation,
        onDismiss = { datePickerOpen = false },
        onConfirm = {
            onSelectDateAndShowCombined(it)
            datePickerOpen = false
        },
    )

    state.openEvent?.let { detail ->
        ModalBottomSheet(
            onDismissRequest = onCloseEvent,
            modifier = Modifier.testTag(EVENT_DETAIL_SHEET_TAG),
            containerColor = modalPresentation.containerColor,
            contentColor = modalPresentation.primaryContentColor,
            scrimColor = modalPresentation.scrimColor,
            dragHandle = { BottomSheetDefaults.DragHandle(color = modalPresentation.outlineColor) },
        ) {
            EventDetailContent(
                detail = detail,
                zoneId = zoneId,
                onClose = onCloseEvent,
                onOpenExternalAction = onOpenExternalAction,
                contentColor = modalPresentation.primaryContentColor,
                secondaryContentColor = modalPresentation.secondaryContentColor,
                surfaceColor = modalPresentation.containerColor,
                actionContentColor = modalPresentation.actionContentColor,
                premium = monetization.purchaseState == PurchaseUiState.OWNED,
            )
        }
    }
}

internal fun datePickerTitle(date: java.time.LocalDate, locale: java.util.Locale): String =
    date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale))

internal fun compactDatePickerMonthTitle(month: java.time.YearMonth, locale: java.util.Locale): String =
    month.atDay(1).format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))

@Composable
private fun CompactDatePickerDialog(
    initialDate: java.time.LocalDate,
    weekStart: WeekStart,
    modalPresentation: CalendarModalPresentation,
    onDismiss: () -> Unit,
    onConfirm: (java.time.LocalDate) -> Unit,
) {
    var selectedDateValue by rememberSaveable { mutableStateOf(initialDate.toString()) }
    var displayedMonthValue by rememberSaveable { mutableStateOf(java.time.YearMonth.from(initialDate).toString()) }
    val selectedDate = java.time.LocalDate.parse(selectedDateValue)
    val displayedMonth = java.time.YearMonth.parse(displayedMonthValue)
    val locale = currentLocale()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(modalPresentation.scrimColor),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("date-picker-scrim")
                    .clickable(onClick = onDismiss),
            )
            Surface(
                shape = RoundedCornerShape(24.dp),
                tonalElevation = 6.dp,
                color = modalPresentation.containerColor,
                contentColor = modalPresentation.primaryContentColor,
                border = BorderStroke(1.dp, modalPresentation.outlineColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .testTag(DATE_PICKER_SURFACE_TAG),
            ) {
                MaterialTheme(
                    colorScheme = MaterialTheme.colorScheme.copy(
                        primary = modalPresentation.calendarHighlightColor,
                        onPrimary = modalPresentation.calendarHighlightContentColor,
                        surface = modalPresentation.containerColor,
                        onSurface = modalPresentation.primaryContentColor,
                        onSurfaceVariant = modalPresentation.secondaryContentColor,
                        outline = modalPresentation.outlineColor,
                    ),
                    typography = MaterialTheme.typography,
                ) {
                    Column(Modifier.padding(16.dp).height(430.dp)) {
                        Text(
                            datePickerTitle(selectedDate, locale),
                            style = MaterialTheme.typography.titleMedium,
                            color = modalPresentation.primaryContentColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                        MonthGrid(
                            month = displayedMonth,
                            selectedDate = selectedDate,
                            dayMarkerColors = emptyMap(),
                            onSelectDate = { date ->
                                selectedDateValue = date.toString()
                                displayedMonthValue = java.time.YearMonth.from(date).toString()
                            },
                            onShowMonth = { month -> displayedMonthValue = month.toString() },
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            weekStart = weekStart,
                            localeOverride = locale,
                            contentColor = modalPresentation.primaryContentColor,
                            secondaryContentColor = modalPresentation.secondaryContentColor,
                            saturdayColor = modalPresentation.saturdayColor,
                            sundayColor = modalPresentation.sundayColor,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
                        ) {
                            TextButton(onClick = onDismiss) {
                                Text(
                                    stringResource(R.string.settings_cancel),
                                    color = modalPresentation.actionContentColor,
                                )
                            }
                            TextButton(onClick = { onConfirm(selectedDate) }) {
                                Text(
                                    stringResource(R.string.calendar_date_picker_confirm),
                                    color = modalPresentation.actionContentColor,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun Modifier.calendarPanel(
    presentation: CalendarThemePresentation,
    horizontalPadding: androidx.compose.ui.unit.Dp = 8.dp,
    verticalPadding: androidx.compose.ui.unit.Dp = 4.dp,
): Modifier = if (presentation.resolvedMode == ThemeBackgroundMode.WHITE) {
    padding(horizontal = horizontalPadding)
} else {
    padding(4.dp)
        .background(presentation.panelColor, RoundedCornerShape(16.dp))
        .padding(horizontal = horizontalPadding, vertical = verticalPadding)
}

@Composable
private fun ActionIcon(
    tag: String,
    description: Int,
    icon: Int,
    presentation: CalendarThemePresentation,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val name = stringResource(description)
    val themedIcon = if (presentation.useOriginalActionArtwork) icon else transparentActionArtwork(icon)
    val containerModifier = if (presentation.useOriginalActionArtwork) {
        Modifier
    } else {
        Modifier.background(presentation.bottomActionContainerColor, RoundedCornerShape(14.dp))
    }
    PrimaryControlRippleScope {
    IconButton(
        onClick = onClick,
        modifier = containerModifier.testTag(tag).semantics { contentDescription = name },
        enabled = enabled,
    ) {
        Icon(
            painter = painterResource(themedIcon),
            contentDescription = null,
            modifier = Modifier.testTag("$tag-icon"),
            tint = Color.Unspecified,
        )
    }
    }
}

private fun transparentActionArtwork(icon: Int): Int = when (icon) {
    R.drawable.ic_action_today -> R.drawable.ic_action_today_transparent
    R.drawable.ic_action_view_switch -> R.drawable.ic_action_view_switch_transparent
    R.drawable.ic_action_search -> R.drawable.ic_action_search_transparent
    R.drawable.ic_action_refresh -> R.drawable.ic_action_refresh_transparent
    R.drawable.ic_action_settings -> R.drawable.ic_action_settings_transparent
    else -> icon
}

private val MainCalendarMessage.stringResource: Int
    get() = when (this) {
        MainCalendarMessage.SYNC_NOT_READY -> R.string.calendar_sync_not_ready
        MainCalendarMessage.SYNC_DISABLED -> R.string.calendar_sync_disabled
        MainCalendarMessage.SYNC_FAILED -> R.string.calendar_sync_failed
    }

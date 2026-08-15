package com.victor2319.ioscalendar.ui.main

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.victor2319.ioscalendar.R
import com.victor2319.ioscalendar.domain.EventDetail
import com.victor2319.ioscalendar.domain.EventInstance
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainCalendarScreenTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()
    private val selectedDate = LocalDate.of(2026, 8, 15)

    @Test
    fun monthGridRemainsAboveAgendaAndAllDayEventsAreOrderedFirst() {
        setScreen(
            state = state(
                agenda = listOf(
                    timed("timed", "Timed meeting", 9),
                    allDay("all-day", "All day conference"),
                ),
            ),
        )

        val monthTop = composeRule.onNodeWithTag(MONTH_GRID_TAG).fetchSemanticsNode().boundsInRoot.top
        val agendaTop = composeRule.onNodeWithTag(AGENDA_LIST_TAG).fetchSemanticsNode().boundsInRoot.top
        assert(monthTop < agendaTop)
        composeRule.onNodeWithTag("agenda-item-all-day").assertIsDisplayed()
        composeRule.onNodeWithTag("agenda-item-timed").assertIsDisplayed()
        val allDayTop = composeRule.onNodeWithTag("agenda-item-all-day").fetchSemanticsNode().boundsInRoot.top
        val timedTop = composeRule.onNodeWithTag("agenda-item-timed").fetchSemanticsNode().boundsInRoot.top
        assert(allDayTop < timedTop)
    }

    @Test
    fun eventUsesTheSharedSheetAndClosingItKeepsTheSelectedDate() {
        val events = (0 until 20).map { index ->
            timed("instance-$index", "Planning $index", 8 + (index % 8))
        }
        var screenState by mutableStateOf(state(agenda = events))
        composeRule.setContent {
            MaterialTheme {
                MainCalendarScreen(
                    state = screenState,
                    zoneId = ZoneId.of("Asia/Tokyo"),
                    onSelectDate = {}, onShowMonth = {}, onRefresh = {},
                    onOpenEvent = { screenState = screenState.copy(openEvent = detail(it)) },
                    onCloseEvent = { screenState = screenState.copy(openEvent = null) },
                    onOpenExternalAction = {},
                )
            }
        }

        composeRule.onNodeWithTag(AGENDA_LIST_TAG).performScrollToIndex(15)
        composeRule.onNodeWithTag("agenda-item-instance-15").performClick()
        composeRule.onNodeWithTag(EVENT_DETAIL_SHEET_TAG).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.calendar_close_detail)).performClick()
        composeRule.onNodeWithTag(SELECTED_DATE_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag("agenda-item-instance-15").assertIsDisplayed()
    }

    @Test
    fun launchTargetResultUsesTheSameSelectedDateAndDetailSheetSemantics() {
        val targetDate = LocalDate.of(2026, 10, 8)
        setScreen(
            state = MainCalendarState(
                selectedDate = targetDate,
                displayedMonth = YearMonth.of(2026, 10),
                openEvent = detail("target").copy(
                    startInstant = targetDate.atTime(9, 0).atZone(ZoneId.of("Asia/Tokyo")).toInstant(),
                    endInstant = targetDate.atTime(10, 0).atZone(ZoneId.of("Asia/Tokyo")).toInstant(),
                ),
            ),
        )

        composeRule.onNodeWithTag(SELECTED_DATE_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(EVENT_DETAIL_SHEET_TAG).assertIsDisplayed()
    }

    @Test
    fun largeFontAndDarkThemeKeepCoreCalendarSemanticsAvailable() {
        composeRule.activity.requestedOrientation = Configuration.ORIENTATION_UNDEFINED
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.8f)) {
                MaterialTheme(
                    colorScheme = androidx.compose.material3.darkColorScheme(),
                    typography = MaterialTheme.typography,
                ) {
                    MainCalendarScreen(
                        state = state(agenda = listOf(allDay("accessible", "Accessible event"))),
                        zoneId = ZoneId.of("Asia/Tokyo"),
                        onSelectDate = {},
                        onShowMonth = {},
                        onRefresh = {},
                        onOpenEvent = {},
                        onCloseEvent = {},
                        onOpenExternalAction = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag(MONTH_GRID_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(AGENDA_LIST_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Accessible event").assertIsDisplayed()
    }

    @Test
    fun simplifiedChineseResourcesKeepTheSameCalendarAndSheetSemantics() {
        val localized = composeRule.activity.createConfigurationContext(
            Configuration(composeRule.activity.resources.configuration).apply {
                setLocale(Locale.SIMPLIFIED_CHINESE)
                fontScale = 1.8f
                uiMode = Configuration.UI_MODE_NIGHT_YES
            },
        )
        composeRule.setContent {
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalContext provides localized) {
                MaterialTheme {
                    MainCalendarScreen(
                        state = state(openEvent = detail("localized")),
                        zoneId = ZoneId.of("Asia/Tokyo"),
                        onSelectDate = {}, onShowMonth = {}, onRefresh = {},
                        onOpenEvent = {}, onCloseEvent = {}, onOpenExternalAction = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag(MONTH_GRID_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(EVENT_DETAIL_SHEET_TAG).assertIsDisplayed()
        composeRule.onNodeWithText(localized.getString(R.string.calendar_close_detail)).assertIsDisplayed()
    }

    private fun setScreen(
        state: MainCalendarState,
        onOpenEvent: (String) -> Unit = {},
        onCloseEvent: () -> Unit = {},
    ) {
        composeRule.setContent {
            MaterialTheme {
                MainCalendarScreen(
                    state = state,
                    zoneId = ZoneId.of("Asia/Tokyo"),
                    onSelectDate = {},
                    onShowMonth = {},
                    onRefresh = {},
                    onOpenEvent = onOpenEvent,
                    onCloseEvent = onCloseEvent,
                    onOpenExternalAction = {},
                )
            }
        }
    }

    private fun state(
        agenda: List<EventInstance> = emptyList(),
        openEvent: EventDetail? = null,
    ) = MainCalendarState(
        selectedDate = selectedDate,
        displayedMonth = YearMonth.of(2026, 8),
        agenda = agenda,
        openEvent = openEvent,
    )

    private fun timed(id: String, title: String, hour: Int) = EventInstance(
        id, "event-$id", "work", title, 0xff336699.toInt(), false,
        selectedDate.atTime(hour, 0).atZone(ZoneId.of("Asia/Tokyo")).toInstant(),
        selectedDate.atTime(hour + 1, 0).atZone(ZoneId.of("Asia/Tokyo")).toInstant(),
        null, null, "Tokyo",
    )

    private fun allDay(id: String, title: String) = EventInstance(
        id, "event-$id", "work", title, 0xffcc3333.toInt(), true,
        null, null, selectedDate, selectedDate.plusDays(1), "Convention Center",
    )

    private fun detail(id: String) = EventDetail(
        id, "event-$id", "work", "Work", 0xff336699.toInt(), "Planning", "Tokyo",
        "Notes https://example.test", false,
        selectedDate.atTime(9, 0).atZone(ZoneId.of("Asia/Tokyo")).toInstant(),
        selectedDate.atTime(10, 0).atZone(ZoneId.of("Asia/Tokyo")).toInstant(),
        null, null, "Asia/Tokyo", "FREQ=WEEKLY;BYDAY=SA",
    )
}

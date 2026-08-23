package com.victor2319.ioscalendar.ui.main

import android.content.res.Configuration
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.click
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.victor2319.ioscalendar.R
import com.victor2319.ioscalendar.domain.CalendarSummary
import com.victor2319.ioscalendar.domain.EventDetail
import com.victor2319.ioscalendar.domain.EventInstance
import com.victor2319.ioscalendar.domain.WeekStart
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainCalendarScreenTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()
    private val selectedDate = LocalDate.of(2026, 8, 15)

    @Test
    fun calendarContentStartsBelowTheSystemStatusBar() {
        setScreen(state())

        assertTrue(
            "Month grid overlaps the status bar",
            composeRule.onNodeWithTag(MONTH_GRID_TAG).fetchSemanticsNode().boundsInRoot.top > 0f,
        )
    }

    @Test
    fun monthGridUsesSevenEqualDateColumns() {
        setScreen(state())

        val dates = listOf(10, 11, 12, 13, 14, 15, 16).map { day ->
            composeRule.onNodeWithTag("calendar-day-${LocalDate.of(2026, 8, day)}")
                .fetchSemanticsNode().boundsInRoot
        }
        assertTrue(dates.maxOf { it.width } - dates.minOf { it.width } <= 1f)
    }

    @Test
    fun monthGridPlacesSundayFirstWhenSelectedAndMondayFirstWhenSelected() {
        fun headerLeft(weekStart: WeekStart, day: String): Float {
            composeRule.setContent {
                MaterialTheme {
                    MonthGrid(
                        month = YearMonth.of(2026, 8),
                        selectedDate = selectedDate,
                        weekStart = weekStart,
                        onSelectDate = {},
                        onShowMonth = {},
                    )
                }
            }
            return composeRule.onNodeWithTag("calendar-weekday-$day").fetchSemanticsNode().boundsInRoot.left
        }

        assertTrue(headerLeft(WeekStart.SUNDAY, "SUNDAY") < headerLeft(WeekStart.SUNDAY, "MONDAY"))
        assertTrue(headerLeft(WeekStart.MONDAY, "MONDAY") < headerLeft(WeekStart.MONDAY, "SUNDAY"))
    }

    @Test
    fun yearViewUsesTheSameWeekStartForDateCells() {
        val markedDay = LocalDate.of(2026, 8, 3)
        composeRule.setContent {
            MaterialTheme {
                YearCalendar(
                    year = 2026,
                    selectedDate = selectedDate,
                    markerColors = mapOf(markedDay to listOf(0xff336699.toInt())),
                    weekStart = WeekStart.MONDAY,
                    onSelectDate = {},
                )
            }
        }

        val monday = composeRule.onNodeWithTag("year-weekday-2026-08-MONDAY").fetchSemanticsNode().boundsInRoot
        val sunday = composeRule.onNodeWithTag("year-weekday-2026-08-SUNDAY").fetchSemanticsNode().boundsInRoot
        assertTrue(monday.left < sunday.left)
        assertEquals(
            markedDay,
            miniMonthDateAt(
                month = YearMonth.of(2026, 8),
                weekStart = WeekStart.MONDAY,
                gridSize = IntSize(width = 140, height = 288),
                tap = Offset(x = 10f, y = 72f),
            ),
        )
    }

    @Test
    fun twoDigitDateLabelRemainsInsideItsEqualWidthCell() {
        setScreen(state())

        val cell = composeRule.onNodeWithTag("calendar-day-2026-08-10")
            .fetchSemanticsNode().boundsInRoot
        val label = composeRule.onNodeWithText("10", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue(label.left >= cell.left && label.right <= cell.right)
    }

    @Test
    fun selectedDateBackgroundKeepsTheSameRoundedSquareSizeForOneAndTwoDigitDates() {
        var selected by mutableStateOf(LocalDate.of(2026, 8, 1))
        composeRule.setContent {
            MaterialTheme {
                MonthGrid(
                    month = YearMonth.of(2026, 8),
                    selectedDate = selected,
                    onSelectDate = { selected = it },
                    onShowMonth = {},
                )
            }
        }

        val oneDigit = composeRule.onNodeWithTag("calendar-date-background-2026-08-01")
            .fetchSemanticsNode().boundsInRoot
        composeRule.onNodeWithTag("calendar-day-2026-08-10").performClick()
        val twoDigit = composeRule.onNodeWithTag("calendar-date-background-2026-08-10")
            .fetchSemanticsNode().boundsInRoot

        assertEquals(oneDigit.width, twoDigit.width)
        assertEquals(oneDigit.height, twoDigit.height)
    }

    @Test
    fun dayWithVisibleEventsShowsUpToThreeCalendarColorMarkers() {
        // Catches a populated day looking the same as an empty day, or rendering more than three dots.
        val day = LocalDate.of(2026, 8, 15)
        setScreen(
            MainCalendarState(
                selectedDate = day,
                displayedMonth = YearMonth.of(2026, 8),
                calendars = listOf(
                    CalendarSummary("one", "One", 0xff336699.toInt(), true),
                    CalendarSummary("two", "Two", 0xffcc3333.toInt(), true),
                    CalendarSummary("three", "Three", 0xff33aa66.toInt(), true),
                    CalendarSummary("four", "Four", 0xffaa6633.toInt(), true),
                ),
                agenda = listOf(
                    timed("one", "One", 8).copy(calendarId = "one", calendarColorArgb = 0xff336699.toInt()),
                    timed("two", "Two", 9).copy(calendarId = "two", calendarColorArgb = 0xffcc3333.toInt()),
                    timed("three", "Three", 10).copy(calendarId = "three", calendarColorArgb = 0xff33aa66.toInt()),
                    timed("four", "Four", 11).copy(calendarId = "four", calendarColorArgb = 0xffaa6633.toInt()),
                ),
                dayMarkerColors = mapOf(
                    day to listOf(0xff336699.toInt(), 0xffcc3333.toInt(), 0xff33aa66.toInt()),
                ),
            ),
        )

        (0 until 3).forEach { index ->
            composeRule.onNodeWithTag("calendar-marker-2026-08-15-$index").assertIsDisplayed()
        }
        assertTrue(composeRule.onAllNodesWithTag("calendar-marker-2026-08-15-3")
            .fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithTag("calendar-marker-2026-08-14-0")
            .fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun refreshAndSettingsAreIconActionsWithAVisibleGap() {
        setScreen(state())

        val refreshDescription = composeRule.activity.getString(R.string.calendar_refresh)
        val settingsDescription = composeRule.activity.getString(R.string.settings_title)
        val refresh = composeRule.onNodeWithContentDescription(refreshDescription)
            .fetchSemanticsNode().boundsInRoot
        val settings = composeRule.onNodeWithContentDescription(settingsDescription)
            .fetchSemanticsNode().boundsInRoot
        assertTrue("Refresh and Settings actions touch", settings.left > refresh.right)
        assertTrue("Refresh must be icon-only", composeRule.onAllNodesWithText(refreshDescription)
            .fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun bottomActionBarExposesFiveAccessibleIconActions() {
        setScreen(state())
        listOf(BOTTOM_TODAY_TAG, BOTTOM_VIEW_TAG, BOTTOM_PICK_DATE_TAG, BOTTOM_REFRESH_TAG, BOTTOM_SETTINGS_TAG)
            .forEach { composeRule.onNodeWithTag(it).assertIsDisplayed() }
    }

    @Test
    fun suppliedPngDrawablesRenderInsideAllFiveBottomActions() {
        setScreen(state())

        listOf(BOTTOM_TODAY_TAG, BOTTOM_VIEW_TAG, BOTTOM_PICK_DATE_TAG, BOTTOM_REFRESH_TAG, BOTTOM_SETTINGS_TAG)
            .forEach { composeRule.onNodeWithTag("$it-icon").assertIsDisplayed() }
    }

    @Test
    fun applicationUsesAnAdaptiveLauncherIcon() {
        val appInfo = composeRule.activity.packageManager.getApplicationInfo(
            composeRule.activity.packageName,
            0,
        )

        assertTrue("Application icon must be app-owned", appInfo.icon != 0)
        assertEquals(
            "${composeRule.activity.packageName}:mipmap/ic_launcher",
            composeRule.activity.resources.getResourceName(appInfo.icon),
        )
        assertTrue(
            "Launcher icon must be adaptive",
            composeRule.activity.getDrawable(appInfo.icon) is AdaptiveIconDrawable,
        )
    }

    @Test
    fun tappingADayInYearViewReturnsToCombinedViewWithThatDateSelected() {
        var screenState by mutableStateOf(state().copy(viewMode = CalendarViewMode.YEAR))
        composeRule.setContent {
            MaterialTheme {
                MainCalendarScreen(
                    state = screenState, zoneId = ZoneId.of("Asia/Tokyo"),
                    onSelectDate = { screenState = screenState.copy(selectedDate = it, displayedMonth = YearMonth.from(it)) },
                    onShowMonth = {}, onRefresh = {}, onOpenEvent = {}, onCloseEvent = {}, onOpenExternalAction = {},
                    onSelectDateAndShowCombined = { screenState = screenState.copy(selectedDate = it, displayedMonth = YearMonth.from(it), viewMode = CalendarViewMode.COMBINED) },
                )
            }
        }

        composeRule.onNodeWithTag("year-month-grid-2026-10").performTouchInput {
            click(Offset(width * (4.5f / 7f), height * 0.25f))
        }
        composeRule.runOnIdle {
            assertEquals(LocalDate.of(2026, 10, 8), screenState.selectedDate)
            assertEquals(CalendarViewMode.COMBINED, screenState.viewMode)
        }
    }

    @Test
    fun yearMonthGridUsesOneAccessibleTargetWithDateActions() {
        val target = LocalDate.of(2026, 8, 21)
        composeRule.setContent {
            MaterialTheme {
                YearCalendar(
                    year = 2026,
                    selectedDate = selectedDate,
                    markerColors = emptyMap(),
                    onSelectDate = {},
                )
            }
        }

        val grid = composeRule.onNodeWithTag("year-month-grid-2026-08")
            .fetchSemanticsNode()
        val minimumTargetPx = 48f * composeRule.activity.resources.displayMetrics.density
        assertTrue("Year month target is narrower than 48dp", grid.boundsInRoot.width >= minimumTargetPx)
        assertTrue("Year month target is shorter than 48dp", grid.boundsInRoot.height >= minimumTargetPx)
        assertTrue(
            "Year month target does not expose the selected date as an accessibility action",
            grid.config[SemanticsActions.CustomActions]
                .any { action -> action.label == yearDateContentDescription(target, Locale.getDefault()) },
        )
    }

    @Test
    fun agendaHeaderDateIsVerticallyCenteredWithItsIconActions() {
        setScreen(state())

        val date = composeRule.onNodeWithTag(SELECTED_DATE_TAG).fetchSemanticsNode().boundsInRoot
        val refresh = composeRule.onNodeWithContentDescription(
            composeRule.activity.getString(R.string.calendar_refresh),
        ).fetchSemanticsNode().boundsInRoot
        val settings = composeRule.onNodeWithContentDescription(
            composeRule.activity.getString(R.string.settings_title),
        ).fetchSemanticsNode().boundsInRoot

        assertTrue("Date is not centered with Refresh", kotlin.math.abs(date.center.y - refresh.center.y) <= 1f)
        assertTrue("Date is not centered with Settings", kotlin.math.abs(date.center.y - settings.center.y) <= 1f)
    }

    @Test
    fun syncMessageDoesNotMoveTheMonthAndAgendaSplit() {
        var screenState by mutableStateOf(state())
        composeRule.setContent {
            MaterialTheme {
                MainCalendarScreen(
                    state = screenState,
                    zoneId = ZoneId.of("Asia/Tokyo"),
                    onSelectDate = {}, onShowMonth = {}, onRefresh = {},
                    onOpenEvent = {}, onCloseEvent = {}, onOpenExternalAction = {},
                )
            }
        }
        val monthBottomWithoutMessage = composeRule.onNodeWithTag(MONTH_GRID_TAG)
            .fetchSemanticsNode().boundsInRoot.bottom
        val agendaTopWithoutMessage = composeRule.onNodeWithTag(AGENDA_LIST_TAG)
            .fetchSemanticsNode().boundsInRoot.top

        screenState = screenState.copy(message = MainCalendarMessage.SYNC_FAILED)
        composeRule.waitForIdle()

        assertEquals(
            monthBottomWithoutMessage,
            composeRule.onNodeWithTag(MONTH_GRID_TAG).fetchSemanticsNode().boundsInRoot.bottom,
        )
        assertEquals(
            agendaTopWithoutMessage,
            composeRule.onNodeWithTag(AGENDA_LIST_TAG).fetchSemanticsNode().boundsInRoot.top,
        )
    }

    @Test
    fun verticalSwipeChangesMonthAndStillSelectsTheTappedDate() {
        var displayedMonth by mutableStateOf(YearMonth.of(2026, 8))
        var selected by mutableStateOf(selectedDate)
        composeRule.setContent {
            MaterialTheme {
                MonthGrid(
                    month = displayedMonth,
                    selectedDate = selected,
                    onSelectDate = { selected = it },
                    onShowMonth = { displayedMonth = it },
                )
            }
        }

        composeRule.onNodeWithTag(MONTH_GRID_TAG).performTouchInput { swipeUp() }
        composeRule.runOnIdle {
            assertEquals(YearMonth.of(2026, 9), displayedMonth)
        }
        composeRule.onNodeWithTag("calendar-day-2026-09-10").performClick()
        composeRule.runOnIdle {
            assertEquals(LocalDate.of(2026, 9, 10), selected)
        }
    }

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
    fun longEventDetailsScrollToTheCloseActionAtLargeFont() {
        val longText = "Long detail text ".repeat(180)
        val density = composeRule.activity.resources.displayMetrics.density
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 1.8f)) {
                MaterialTheme {
                    MainCalendarScreen(
                        state = state(openEvent = detail("long").copy(
                            title = longText,
                            location = longText,
                            notes = longText,
                        )),
                        zoneId = ZoneId.of("Asia/Tokyo"),
                        onSelectDate = {}, onShowMonth = {}, onRefresh = {},
                        onOpenEvent = {}, onCloseEvent = {}, onOpenExternalAction = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.calendar_close_detail))
            .performScrollTo()
            .assertIsDisplayed()
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
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.calendar_refresh)).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.settings_title)).assertIsDisplayed()
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

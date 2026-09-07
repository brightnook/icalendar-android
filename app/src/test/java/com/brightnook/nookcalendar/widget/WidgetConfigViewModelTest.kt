package com.brightnook.nookcalendar.widget

import com.brightnook.nookcalendar.domain.CalendarSummary
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WidgetConfigViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun newWidgetUsesEveryGloballyVisibleCalendarForSevenDays() = runTest(dispatcher) {
        // Catches a fresh widget being initialized with hidden calendars or a range other than seven days.
        val preferences = InMemoryWidgetPreferences()
        val calendars = MutableStateFlow(
            listOf(calendar("work", true), calendar("private", false), calendar("family", true)),
        )

        val viewModel = WidgetConfigViewModel(101, preferences, calendars)
        advanceUntilIdle()

        assertEquals(setOf("work", "family"), viewModel.uiState.value.selectedCalendarIds)
        assertEquals(WidgetRange.SEVEN_DAYS, viewModel.uiState.value.range)
    }

    @Test
    fun widgetIdsPersistIndependentCalendarFilters() = runTest(dispatcher) {
        // Catches one widget's save replacing another widget's calendar selection.
        val preferences = InMemoryWidgetPreferences()
        val calendars = MutableStateFlow(listOf(calendar("work", true), calendar("family", true)))
        val first = WidgetConfigViewModel(101, preferences, calendars)
        val second = WidgetConfigViewModel(202, preferences, calendars)
        advanceUntilIdle()

        first.setCalendarSelected("work", false)
        first.setRange(WidgetRange.THIRTY_DAYS)
        first.save()
        second.setCalendarSelected("family", false)
        second.setRange(WidgetRange.TODAY)
        second.save()
        advanceUntilIdle()

        assertEquals(
            WidgetConfig(101, setOf("family"), WidgetRange.THIRTY_DAYS),
            preferences.configFor(101),
        )
        assertEquals(
            WidgetConfig(202, setOf("work"), WidgetRange.TODAY),
            preferences.configFor(202),
        )
    }

    @Test
    fun globallyHiddenCalendarsAreNotRenderedEvenWhenSavedForWidget() = runTest(dispatcher) {
        // Catches a widget surfacing a calendar after it has been hidden in global settings.
        val preferences = InMemoryWidgetPreferences().also {
            it.save(WidgetConfig(101, setOf("work", "private"), WidgetRange.SEVEN_DAYS))
        }
        val calendars = MutableStateFlow(listOf(calendar("work", true), calendar("private", true)))
        val viewModel = WidgetConfigViewModel(101, preferences, calendars)
        advanceUntilIdle()

        calendars.value = listOf(calendar("work", true), calendar("private", false))
        advanceUntilIdle()

        assertEquals(setOf("work"), viewModel.uiState.value.selectedCalendarIds)
        assertTrue(viewModel.uiState.value.calendars.none { it.id == "private" })
    }

    @Test
    fun widgetManagementUsesActiveIdsAndTheirSavedRanges() {
        val configs = mapOf(
            101 to WidgetConfig(101, emptySet(), WidgetRange.THIRTY_DAYS),
        )

        assertEquals(
            listOf(
                WidgetManagementItem(101, WidgetRange.THIRTY_DAYS),
                WidgetManagementItem(202, WidgetRange.SEVEN_DAYS),
            ),
            widgetManagementItems(intArrayOf(101, 202), configs),
        )
    }

    private fun calendar(id: String, visible: Boolean) = CalendarSummary(id, id, 0xff335577.toInt(), visible)
}

private class InMemoryWidgetPreferences : WidgetPreferences {
    private val configs = mutableMapOf<Int, WidgetConfig>()

    override suspend fun configFor(appWidgetId: Int): WidgetConfig? = configs[appWidgetId]

    override suspend fun save(config: WidgetConfig) {
        configs[config.appWidgetId] = config
    }

    override suspend fun delete(appWidgetId: Int) {
        configs.remove(appWidgetId)
    }
}

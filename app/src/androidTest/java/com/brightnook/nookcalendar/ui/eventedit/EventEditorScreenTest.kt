package com.brightnook.nookcalendar.ui.eventedit

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withClassName
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.brightnook.nookcalendar.domain.CalendarSummary
import com.brightnook.nookcalendar.domain.AppLanguage
import com.brightnook.nookcalendar.domain.EventDraft
import com.brightnook.nookcalendar.ui.LocalizedAppContent
import android.widget.DatePicker
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import org.hamcrest.Matchers.`is`
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EventEditorScreenTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun timePickerOpensWhenAppLanguageOverridesSystemLanguage() {
        var updatedDraft: EventDraft? = null
        composeRule.setContent {
            LocalizedAppContent(AppLanguage.JAPANESE) {
                MaterialTheme {
                    EventEditorScreen(
                        state = EventEditorState(
                            draft = EventDraft(
                                calendarId = "work",
                                title = "Meeting",
                                isAllDay = false,
                                startInstant = Instant.parse("2026-09-14T01:00:00Z"),
                                endInstant = Instant.parse("2026-09-14T02:00:00Z"),
                            ),
                        ),
                        zoneId = ZoneId.of("Asia/Tokyo"),
                        isEditing = true,
                        onUpdateDraft = { updatedDraft = it },
                        onSave = {},
                        onDelete = {},
                        onDismiss = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("10:00", substring = true).performClick()
        composeRule.onNodeWithTag("event-editor-time-picker").assertIsDisplayed()
        composeRule.onNodeWithText("選択").performClick()
        composeRule.runOnIdle {
            assertEquals(Instant.parse("2026-09-14T01:00:00Z"), updatedDraft?.startInstant)
        }
    }

    @Test
    fun datePickerOpensWhenAppLanguageOverridesSystemLanguage() {
        composeRule.setContent {
            LocalizedAppContent(AppLanguage.JAPANESE) {
                MaterialTheme {
                    EventEditorScreen(
                        state = EventEditorState(
                            draft = EventDraft(
                                calendarId = "work",
                                title = "Meeting",
                                isAllDay = false,
                                startInstant = Instant.parse("2026-09-14T01:00:00Z"),
                                endInstant = Instant.parse("2026-09-14T02:00:00Z"),
                            ),
                        ),
                        zoneId = ZoneId.of("Asia/Tokyo"),
                        isEditing = true,
                        onUpdateDraft = {},
                        onSave = {},
                        onDelete = {},
                        onDismiss = {},
                    )
                }
            }
        }

        val dateText = LocalDate.of(2026, 9, 14).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
        composeRule.onAllNodesWithText(dateText).onFirst().performClick()
        onView(withClassName(`is`(DatePicker::class.java.name)))
            .check(matches(isDisplayed()))
            .check { view, _ -> assertEquals("ja", view.context.resources.configuration.locales[0].language) }
    }

    @Test
    fun saveActionStaysBelowTheStatusBar() {
        var statusBarHeight: Dp = 0.dp
        composeRule.setContent {
            val density = LocalDensity.current
            statusBarHeight = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
            MaterialTheme {
                EventEditorScreen(
                    state = EventEditorState(
                        draft = EventDraft(
                            calendarId = "work",
                            title = "Meeting",
                            isAllDay = false,
                            startInstant = Instant.parse("2026-09-14T01:00:00Z"),
                            endInstant = Instant.parse("2026-09-14T02:00:00Z"),
                        ),
                    ),
                    zoneId = ZoneId.of("Asia/Tokyo"),
                    isEditing = true,
                    onUpdateDraft = {},
                    onSave = {},
                    onDelete = {},
                    onDismiss = {},
                )
            }
        }

        assertTrue("The test device must report a status bar inset", statusBarHeight > 0.dp)
        val saveTop = composeRule.onNodeWithTag("event-editor-save").getUnclippedBoundsInRoot().top
        assertTrue("Save action overlaps the status bar", saveTop >= statusBarHeight)
    }

    @Test
    fun editorShowsApprovedFieldsAndActions() {
        composeRule.setContent {
            MaterialTheme {
                EventEditorScreen(
                    state = EventEditorState(
                        draft = EventDraft(
                            calendarId = "work",
                            title = "",
                            isAllDay = false,
                            startInstant = Instant.parse("2026-09-14T01:00:00Z"),
                            endInstant = Instant.parse("2026-09-14T02:00:00Z"),
                        ),
                        calendars = listOf(CalendarSummary("work", "Work", 0, true, true)),
                    ),
                    zoneId = ZoneId.of("Asia/Tokyo"),
                    isEditing = false,
                    onUpdateDraft = {},
                    onSave = {},
                    onDelete = {},
                    onDismiss = {},
                )
            }
        }

        listOf(
            "event-editor-title", "event-editor-location", "event-editor-all-day",
            "event-editor-start", "event-editor-end", "event-editor-calendar",
            "event-editor-reminders", "event-editor-notes", "event-editor-save",
        ).forEach { tag -> composeRule.onNodeWithTag(tag).assertIsDisplayed() }
    }
}

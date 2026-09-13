package com.brightnook.nookcalendar.ui.eventedit

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.brightnook.nookcalendar.domain.CalendarSummary
import com.brightnook.nookcalendar.domain.EventDraft
import java.time.Instant
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EventEditorScreenTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

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

package com.brightnook.nookcalendar.ui.eventedit

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.brightnook.nookcalendar.domain.EventDraft
import com.brightnook.nookcalendar.ui.theme.CALENDAR_BACKGROUND_TAG
import java.time.Instant
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EventEditorPresentationPolicyTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun editorUsesTheSharedCalendarThemeBackground() {
        composeRule.setContent {
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
                    isEditing = false,
                    onUpdateDraft = {},
                    onSave = {},
                    onDelete = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag(CALENDAR_BACKGROUND_TAG).assertIsDisplayed()
    }
}

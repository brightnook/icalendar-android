package com.brightnook.nookcalendar.ui.eventedit

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.brightnook.nookcalendar.domain.EventDraft
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en")
class EventDeletionConfirmationTest {
    @get:Rule val compose = createComposeRule()
    private var deletes = 0
    private var dismissals = 0

    private fun showConfirmation() {
        val day = LocalDate.of(2026, 9, 28)
        compose.setContent {
            MaterialTheme {
                EventEditorScreen(
                    EventEditorState(EventDraft("work", "Planning", isAllDay = true,
                        startDate = day, endDateExclusive = day.plusDays(1))),
                    ZoneId.of("Asia/Tokyo"), isEditing = true,
                    onUpdateDraft = {}, onSave = {}, onDelete = { deletes++ },
                    onDismiss = { dismissals++ }, showDeleteConfirmationInitially = true,
                )
            }
        }
    }

    @Test fun agendaDeletionShowsOnlyConfirmationAndCancelReturnsToAgenda() {
        showConfirmation()
        compose.onNodeWithText("Delete this event?").assertIsDisplayed()
        compose.onNodeWithTag("event-editor-title").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, deletes) }
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle {
            assertEquals(1, dismissals)
            assertEquals(0, deletes)
        }
    }

    @Test fun deletionRunsOnlyAfterExplicitConfirmation() {
        showConfirmation()
        compose.runOnIdle { assertEquals(0, deletes) }
        compose.onNodeWithText("Delete event").performClick()
        compose.runOnIdle { assertEquals(1, deletes) }
    }
}

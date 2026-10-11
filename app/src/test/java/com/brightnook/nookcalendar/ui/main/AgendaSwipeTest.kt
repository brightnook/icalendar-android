package com.brightnook.nookcalendar.ui.main

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.brightnook.nookcalendar.domain.EventInstance
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AgendaSwipeTest {
    @get:Rule val compose = createComposeRule()
    private var deleteRequests = 0

    private fun showAgenda() {
        val day = LocalDate.of(2026, 9, 28)
        val event = EventInstance("one", "event-one", "work", "Planning", 0xff336699.toInt(),
            true, null, null, day, day.plusDays(1), "Tokyo")
        compose.setContent {
            MaterialTheme {
                AgendaList(listOf(event), emptyList(), ZoneId.of("Asia/Tokyo"),
                    rememberLazyListState(), onOpenEvent = {},
                    onDeleteEvent = { deleteRequests++ })
            }
        }
    }

    @Test fun deleteActionIsHiddenUntilSwiped() {
        showAgenda()
        compose.onNodeWithTag("agenda-delete-one").assertDoesNotExist()
        compose.onNodeWithTag("agenda-edit-one").assertDoesNotExist()
    }

    @Test fun leftSwipeKeepsEventVisibleAndOnlyRequestsDeletionAfterClick() {
        showAgenda()
        compose.onNodeWithTag("agenda-item-one").performTouchInput { swipeLeft() }
        compose.onNodeWithText("Planning").assertIsDisplayed()
        compose.onNodeWithTag("agenda-delete-one").assertIsDisplayed()
        compose.onNodeWithTag("agenda-edit-one").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, deleteRequests) }
        compose.onNodeWithTag("agenda-delete-one").performClick()
        compose.runOnIdle { assertEquals(1, deleteRequests) }
    }

    @Test fun rightSwipeKeepsEventVisibleAndReverseSwipeClosesAction() {
        showAgenda()
        compose.onNodeWithTag("agenda-item-one").performTouchInput { swipeRight() }
        compose.onNodeWithText("Planning").assertIsDisplayed()
        compose.onNodeWithTag("agenda-delete-one").assertIsDisplayed()
        compose.onNodeWithTag("agenda-item-one").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("agenda-delete-one").assertDoesNotExist()
        compose.onNodeWithText("Planning").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, deleteRequests) }
    }
}

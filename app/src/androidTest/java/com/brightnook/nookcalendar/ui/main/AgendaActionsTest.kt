package com.brightnook.nookcalendar.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.brightnook.nookcalendar.domain.EventInstance
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AgendaActionsTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test fun leftSwipeRevealsDeleteWithoutRemovingEvent() = verifySwipe(left = true)
    @Test fun rightSwipeRevealsDeleteWithoutRemovingEvent() = verifySwipe(left = false)

    private fun verifySwipe(left: Boolean) {
        var deleteRequests = 0
        val event = EventInstance(
            "one", "event-one", "work", "Test appointment", 0xff336699.toInt(), false,
            Instant.parse("2026-09-18T01:00:00Z"), Instant.parse("2026-09-18T02:00:00Z"),
            null, null, "Test location",
        )
        composeRule.setContent {
            MaterialTheme {
                AgendaList(
                    agenda = listOf(event), calendars = emptyList(), zoneId = ZoneId.of("Asia/Tokyo"),
                    listState = rememberLazyListState(), onOpenEvent = {},
                    onDeleteEvent = { deleteRequests++ }, modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeRule.onNodeWithTag("agenda-delete-one").assertDoesNotExist()
        composeRule.onNodeWithTag("agenda-item-one").performTouchInput {
            if (left) swipeLeft() else swipeRight()
        }
        composeRule.onNodeWithTag("agenda-item-one").assertIsDisplayed()
        composeRule.onNodeWithTag("agenda-delete-one").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(0, deleteRequests) }
        composeRule.onNodeWithTag("agenda-delete-one").performClick()
        composeRule.onNodeWithTag("agenda-item-one").assertIsDisplayed()
        composeRule.onNodeWithTag("agenda-delete-one").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(1, deleteRequests) }
    }
}

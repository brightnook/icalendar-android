package com.brightnook.nookcalendar.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.brightnook.nookcalendar.domain.EventDetail
import com.brightnook.nookcalendar.domain.EventDraft
import com.brightnook.nookcalendar.domain.EventInstance
import com.brightnook.nookcalendar.domain.PendingEventSyncState
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PendingEventUiTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()
    private val start = Instant.parse("2026-10-31T01:00:00Z")
    private val end = start.plusSeconds(3600)

    @Test fun pendingAgendaShowsStatusAndDoesNotExposeDelete() {
        var deletes = 0
        composeRule.setContent {
            MaterialTheme {
                AgendaList(listOf(EventInstance("one", "event", "work", "Local draft", 0, false,
                    start, end, null, null, null, PendingEventSyncState.PENDING)), emptyList(),
                    ZoneId.of("UTC"), rememberLazyListState(), {}, onDeleteEvent = { deletes++ })
            }
        }
        composeRule.onNodeWithTag("agenda-sync-one", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("agenda-item-one").performTouchInput { swipeLeft() }
        composeRule.onNodeWithTag("agenda-delete-one").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(0, deletes) }
    }

    @Test fun failedDetailOffersExplicitRetry() {
        var retries = 0
        showDetail(PendingEventSyncState.FAILED, onRetry = { retries++ })
        composeRule.onNodeWithTag("event-detail-edit").assertIsNotEnabled()
        composeRule.onNodeWithTag("event-detail-sync-status").assertIsDisplayed()
        composeRule.onNodeWithTag("event-sync-retry").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(1, retries) }
        composeRule.onNodeWithTag("event-conflict-use-remote").assertDoesNotExist()
    }

    @Test fun useRemoteRequiresConfirmationAndCancelPreservesLocalChoice() {
        val choices = mutableListOf<Boolean>()
        showDetail(PendingEventSyncState.CONFLICT, onResolve = { choices.add(it) })
        composeRule.onNodeWithText("Remote appointment").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("event-conflict-use-remote").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(emptyList<Boolean>(), choices) }
        composeRule.onNodeWithTag("event-conflict-cancel-remote").performClick()
        composeRule.runOnIdle { assertEquals(emptyList<Boolean>(), choices) }
        composeRule.onNodeWithTag("event-conflict-use-remote").performScrollTo().performClick()
        composeRule.onNodeWithTag("event-conflict-confirm-remote").performClick()
        composeRule.runOnIdle { assertEquals(listOf(false), choices) }
    }

    @Test fun keepLocalExplicitlyRequestsRetryWithoutSelectingRemote() {
        val choices = mutableListOf<Boolean>()
        showDetail(PendingEventSyncState.CONFLICT, onResolve = { choices.add(it) })
        composeRule.onNodeWithTag("event-conflict-keep-local").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(listOf(true), choices) }
        composeRule.onNodeWithTag("event-conflict-confirm-remote").assertDoesNotExist()
    }

    private fun showDetail(status: PendingEventSyncState, onRetry: () -> Unit = {}, onResolve: (Boolean) -> Unit = {}) {
        val detail = EventDetail("one", "event", "work", "Work", 0, "Local appointment", null, null,
            false, start, end, null, null, "UTC", null, pendingSyncState = status,
            remoteConflict = EventDraft("work", "Remote appointment", notes = "Changed remotely", isAllDay = false,
                startInstant = start, endInstant = end))
        composeRule.setContent {
            MaterialTheme {
                EventDetailContent(detail, ZoneId.of("UTC"), {}, {}, Color.Black, Color.DarkGray,
                    Color.White, Color.Blue, true, onRetrySync = onRetry, onResolveConflict = onResolve)
            }
        }
    }
}

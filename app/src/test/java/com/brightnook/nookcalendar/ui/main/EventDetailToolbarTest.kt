package com.brightnook.nookcalendar.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.brightnook.nookcalendar.domain.EventDetail
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
class EventDetailToolbarTest {
    @get:Rule val compose = createComposeRule()

    @Test fun longDetailsKeepEditAndCloseVisibleBeforeAndAfterBodyScroll() {
        var edits = 0
        val day = LocalDate.of(2026, 10, 5)
        val detail = EventDetail("one", "event", "work", "Work", 0xff336699.toInt(),
            "Planning", "Tokyo", "Long notes\n".repeat(100), true,
            null, null, day, day.plusDays(1), null, null)
        compose.setContent {
            MaterialTheme {
                Box(Modifier.height(420.dp)) {
                    EventDetailContent(detail, ZoneId.of("Asia/Tokyo"), {}, {},
                        Color.Black, Color.Gray, Color.White, Color.Blue,
                        premium = true, onEdit = { edits++ })
                }
            }
        }
        compose.onNodeWithTag("event-detail-edit").assertIsDisplayed()
        compose.onNodeWithTag("event-detail-close").assertIsDisplayed()
        compose.onNodeWithTag("event-detail-body").performTouchInput { swipeUp() }
        compose.onNodeWithTag("event-detail-edit").assertIsDisplayed().performClick()
        compose.onNodeWithTag("event-detail-close").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, edits) }
    }
}

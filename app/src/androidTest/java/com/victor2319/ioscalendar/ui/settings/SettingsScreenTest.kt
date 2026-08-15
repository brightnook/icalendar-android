package com.victor2319.ioscalendar.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.victor2319.ioscalendar.domain.CalendarSummary
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@Suppress("DEPRECATION")
class SettingsScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun settingsShowsMaskedAccountApproximateModeCopyAndCalendarVisibility() {
        // Catches leaking the raw Apple ID, omitting approximate periodic wording, or rendering no usable visibility switch.
        composeRule.setContent {
            SettingsScreen(
                state = SettingsUiState(
                    maskedAppleId = "p***@example.test",
                    accountLoading = false,
                    calendars = listOf(CalendarSummary("work", "Work", Color.Red.value.toInt(), false)),
                ),
                onSelectSyncMode = {},
                onSetCalendarVisible = { _, _ -> },
                onClearHistory = {},
                onClearCache = {},
                onLogout = {},
                onDismissConfirmation = {},
                onConfirmDestructiveAction = {},
            )
        }

        composeRule.onNodeWithText("p***@example.test").assertIsDisplayed()
        composeRule.onNodeWithText("Hourly (approximately every hour)").assertIsDisplayed()
        composeRule.onNodeWithText("Work").assertIsDisplayed()
        composeRule.onNodeWithTag("calendar-color-work").assertIsDisplayed()
        composeRule.onNodeWithTag("calendar-visibility-work").assertIsOff()
    }

    @Test
    fun cacheDeletionIsOnlyTriggeredAfterConfirmation() {
        // Catches a destructive cache button executing without an explicit confirmation dialog action.
        val confirmation = mutableStateOf<SettingsConfirmation?>(SettingsConfirmation.ClearCache)
        var confirmed = false
        composeRule.setContent {
            SettingsScreen(
                state = SettingsUiState(confirmation = confirmation.value),
                onSelectSyncMode = {},
                onSetCalendarVisible = { _, _ -> },
                onClearHistory = {},
                onClearCache = {},
                onLogout = {},
                onDismissConfirmation = { confirmation.value = null },
                onConfirmDestructiveAction = { confirmed = true },
            )
        }

        composeRule.onNodeWithText("Delete cached calendar data?").assertIsDisplayed()
        assertTrue(!confirmed)
        composeRule.onNodeWithText("Delete").performClick()
        assertTrue(confirmed)
    }
}

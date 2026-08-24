package com.victor2319.ioscalendar.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.graphics.toPixelMap
import com.victor2319.ioscalendar.domain.AppLanguage
import com.victor2319.ioscalendar.domain.CalendarSummary
import com.victor2319.ioscalendar.domain.WeekStart
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotEquals
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
                onClose = {},
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
    fun syncModeUsesAnAccessibleDropdownWithEveryFrequencyOption() {
        var selected: com.victor2319.ioscalendar.domain.SyncMode? = null
        composeRule.setContent {
            SettingsScreen(
                state = SettingsUiState(syncMode = com.victor2319.ioscalendar.domain.SyncMode.HOURLY),
                onClose = {},
                onSelectSyncMode = { selected = it },
                onSetCalendarVisible = { _, _ -> },
                onClearHistory = {}, onClearCache = {}, onLogout = {}, onDismissConfirmation = {},
                onConfirmDestructiveAction = {},
            )
        }

        composeRule.onNodeWithTag("sync-mode-picker").performClick()
        composeRule.onNodeWithText("Manual").assertIsDisplayed()
        composeRule.onNodeWithText("Hourly (approximately every hour)").assertIsDisplayed()
        composeRule.onNodeWithText("Daily (approximately once a day)").assertIsDisplayed()
        composeRule.onNodeWithText("Disabled").assertIsDisplayed()
        composeRule.onNodeWithTag("sync-mode-option-DISABLED").performClick()
        assertTrue(selected == com.victor2319.ioscalendar.domain.SyncMode.DISABLED)
    }

    @Test
    fun calendarPreferencesExposeSundayMondayAndTheFollowSystemLanguageEntry() {
        var selected: WeekStart? = null
        composeRule.setContent {
            SettingsScreen(
                state = SettingsUiState(),
                onClose = {}, onSelectSyncMode = {}, onSetCalendarVisible = { _, _ -> },
                onClearHistory = {}, onClearCache = {}, onLogout = {}, onDismissConfirmation = {},
                onConfirmDestructiveAction = {},
                onSelectWeekStart = { selected = it },
            )
        }

        composeRule.onNodeWithTag("week-start-picker").performClick()
        composeRule.onNodeWithText("Sunday").assertIsDisplayed()
        composeRule.onNodeWithText("Monday").assertIsDisplayed()
        composeRule.onNodeWithTag("week-start-option-MONDAY").performClick()
        assertTrue(selected == WeekStart.MONDAY)
        composeRule.onNodeWithTag("language-picker").assertIsDisplayed()
        composeRule.onNodeWithText("Follow system").assertIsDisplayed()
    }

    @Test
    fun languagePickerSelectsJapaneseFromTheAvailableLanguageOptions() {
        // Catches a language control that has no selectable manual options or returns the wrong choice.
        var selected: AppLanguage? = null
        composeRule.setContent {
            SettingsScreen(
                state = SettingsUiState(),
                onClose = {}, onSelectSyncMode = {}, onSetCalendarVisible = { _, _ -> },
                onClearHistory = {}, onClearCache = {}, onLogout = {}, onDismissConfirmation = {},
                onConfirmDestructiveAction = {},
                onSelectLanguage = { selected = it },
            )
        }

        composeRule.onNodeWithTag("language-picker").performClick()
        AppLanguage.entries.forEach { language ->
            composeRule.onNodeWithTag("language-option-$language").assertIsDisplayed()
        }
        composeRule.onNodeWithTag("language-option-JAPANESE").performClick()

        assertTrue(selected == AppLanguage.JAPANESE)
    }

    @Test
    fun logoutSharesTheAccountRowAndRemainsAccessible() {
        composeRule.setContent {
            SettingsScreen(
                state = SettingsUiState(maskedAppleId = "abc***@example.test"),
                onClose = {}, onSelectSyncMode = {}, onSetCalendarVisible = { _, _ -> },
                onClearHistory = {}, onClearCache = {}, onLogout = {}, onDismissConfirmation = {},
                onConfirmDestructiveAction = {},
            )
        }

        val account = composeRule.onNodeWithTag("masked-account").fetchSemanticsNode().boundsInRoot
        val logout = composeRule.onNodeWithTag("account-logout").fetchSemanticsNode().boundsInRoot
        assertTrue("Logout must be to the right of the account", logout.left >= account.right)
        composeRule.onNodeWithContentDescription("Sign out").assertIsDisplayed()
    }

    @Test
    fun calendarColorIndicatorIsClippedToACircle() {
        composeRule.setContent {
            SettingsScreen(
                state = SettingsUiState(calendars = listOf(CalendarSummary("work", "Work", Color.Red.value.toInt(), true))),
                onClose = {}, onSelectSyncMode = {}, onSetCalendarVisible = { _, _ -> },
                onClearHistory = {}, onClearCache = {}, onLogout = {}, onDismissConfirmation = {},
                onConfirmDestructiveAction = {},
            )
        }

        val pixels = composeRule.onNodeWithTag("calendar-color-work").captureToImage().toPixelMap()
        assertNotEquals("A square swatch colors its corner like its center", pixels[0, 0], pixels[pixels.width / 2, pixels.height / 2])
    }

    @Test
    fun settingsGroupsAccountSyncCalendarsAndDataInSeparateAccessibleSections() {
        composeRule.setContent {
            SettingsScreen(
                state = SettingsUiState(calendars = listOf(CalendarSummary("work", "Work", Color.Red.value.toInt(), true))),
                onClose = {}, onSelectSyncMode = {}, onSetCalendarVisible = { _, _ -> },
                onClearHistory = {}, onClearCache = {}, onLogout = {}, onDismissConfirmation = {},
                onConfirmDestructiveAction = {},
            )
        }

        listOf("account", "sync", "calendars", "data").forEach { section ->
            composeRule.onNodeWithTag("settings-section-$section").assertIsDisplayed()
        }
    }

    @Test
    fun cacheDeletionIsOnlyTriggeredAfterConfirmation() {
        // Catches a destructive cache button executing without an explicit confirmation dialog action.
        val confirmation = mutableStateOf<SettingsConfirmation?>(SettingsConfirmation.ClearCache)
        var confirmed = false
        composeRule.setContent {
            SettingsScreen(
                state = SettingsUiState(confirmation = confirmation.value),
                onClose = {},
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

    @Test
    fun closeReturnsFromSettingsWithoutDestructiveAction() {
        // Catches a one-way settings destination with no user-visible return path.
        var closed = false
        composeRule.setContent {
            SettingsScreen(
                state = SettingsUiState(), onClose = { closed = true },
                onSelectSyncMode = {}, onSetCalendarVisible = { _, _ -> }, onClearHistory = {},
                onClearCache = {}, onLogout = {}, onDismissConfirmation = {},
                onConfirmDestructiveAction = {},
            )
        }
        composeRule.onNodeWithContentDescription("Close").performClick()
        assertTrue(closed)
    }

    @Test
    fun settingsCloseUsesAnAccessibleIconButtonBelowTheStatusArea() {
        composeRule.setContent {
            SettingsScreen(
                state = SettingsUiState(), onClose = {},
                onSelectSyncMode = {}, onSetCalendarVisible = { _, _ -> }, onClearHistory = {},
                onClearCache = {}, onLogout = {}, onDismissConfirmation = {},
                onConfirmDestructiveAction = {},
            )
        }

        val close = composeRule.onNodeWithContentDescription("Close").fetchSemanticsNode().boundsInRoot
        assertTrue("Close action overlaps the status area", close.top > 0f)
        composeRule.onNodeWithTag("settings-close-icon").assertIsDisplayed()
    }

    @Test
    fun settingsScrollsToLogoutWhileTheCloseActionStaysReachable() {
        val confirmation = mutableStateOf<SettingsConfirmation?>(null)
        composeRule.setContent {
            SettingsScreen(
                state = SettingsUiState(
                    calendars = (1..20).map { index ->
                        CalendarSummary("calendar-$index", "Calendar $index", Color.Red.value.toInt(), true)
                    },
                    confirmation = confirmation.value,
                ),
                onClose = {}, onSelectSyncMode = {}, onSetCalendarVisible = { _, _ -> },
                onClearHistory = {}, onClearCache = {},
                onLogout = { confirmation.value = SettingsConfirmation.Logout(clearCache = false) },
                onDismissConfirmation = {},
                onConfirmDestructiveAction = {},
            )
        }

        composeRule.onNodeWithText("Sign out").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Close").assertIsDisplayed()
        composeRule.onNodeWithTag("settings-logout-icon").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Sign out").performClick()
        composeRule.onNodeWithText("Your saved iCloud credentials will be removed from this device.")
            .assertIsDisplayed()
    }
}

package com.brightnook.nookcalendar.ui.eventedit

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.graphics.luminance
import com.brightnook.nookcalendar.domain.EventDraft
import com.brightnook.nookcalendar.domain.ThemeSettings
import com.brightnook.nookcalendar.domain.ThemeBackgroundMode
import com.brightnook.nookcalendar.ui.theme.calendarThemePresentation
import java.nio.file.Files
import org.junit.Assert.assertTrue
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
    fun editorControlsHaveReadableContrastForEveryAppTheme() {
        val image = Files.createTempFile("editor-theme", ".png").toFile()
        try {
            ThemeBackgroundMode.entries.forEach { mode ->
                val colors = eventEditorColorScheme(calendarThemePresentation(ThemeSettings(mode), image))
                val pairs = listOf(
                    colors.onErrorContainer to colors.errorContainer,
                    colors.onSurfaceVariant to colors.surfaceContainerHighest,
                    colors.onSurface to colors.surfaceContainer,
                    colors.onSurfaceVariant to colors.surfaceContainer,
                    colors.onSurface to colors.surfaceContainerHigh,
                    colors.onSurface to colors.surfaceContainerHighest,
                    colors.onSurfaceVariant to colors.surfaceVariant,
                    colors.onPrimary to colors.primary,
                    colors.onPrimaryContainer to colors.primaryContainer,
                    colors.onSecondaryContainer to colors.secondaryContainer,
                )
                pairs.forEach { (text, background) ->
                    val contrast = (maxOf(text.luminance(), background.luminance()) + 0.05f) /
                        (minOf(text.luminance(), background.luminance()) + 0.05f)
                    assertTrue("$mode picker contrast $contrast: $text on $background", contrast >= 4.5f)
                }
            }
        } finally {
            image.delete()
        }
    }

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

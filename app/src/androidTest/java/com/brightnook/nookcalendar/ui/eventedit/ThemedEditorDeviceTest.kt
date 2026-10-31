package com.brightnook.nookcalendar.ui.eventedit

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.brightnook.nookcalendar.domain.*
import com.brightnook.nookcalendar.ui.theme.calendarThemePresentation
import java.io.File
import java.time.Instant
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Test fixtures only; screenshots contain no connected account or existing event. */
@RunWith(AndroidJUnit4::class)
class ThemedEditorDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun blackThemeTimePickerRemainsReadable() {
        show(ThemeBackgroundMode.BLACK)
        compose.onNodeWithText("10:00", substring = true).performClick()
        compose.onNodeWithTag("event-editor-time-picker").assertIsDisplayed()
        screenshot("black-time-picker")
    }

    @Test fun imageThemeTimePickerRemainsReadable() {
        show(ThemeBackgroundMode.IMAGE)
        compose.onNodeWithText("10:00", substring = true).performClick()
        compose.onNodeWithTag("event-editor-time-picker").assertIsDisplayed()
        screenshot("image-time-picker")
    }

    @Test fun blackThemeCalendarMenuAndDeleteRemainReadable() {
        show(ThemeBackgroundMode.BLACK)
        compose.onNodeWithText("Work").performClick()
        compose.onAllNodesWithText("Work").assertCountEquals(2)
        screenshot("black-calendar-menu")
        compose.onAllNodesWithText("Work").onLast().performClick()
        compose.onNodeWithTag("event-editor-delete").performScrollTo().assertIsEnabled()
        screenshot("black-delete-button")
    }

    private fun show(mode: ThemeBackgroundMode) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val image = File(context.cacheDir, "disposable-editor-test-wallpaper.png")
        if (mode == ThemeBackgroundMode.IMAGE) {
            val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.DKGRAY)
            image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        val theme = calendarThemePresentation(ThemeSettings(mode, image.name), image.takeIf { mode == ThemeBackgroundMode.IMAGE })
        compose.setContent {
            MaterialTheme {
                EventEditorScreen(EventEditorState(EventDraft("work", "Device contrast test", isAllDay = false,
                    startInstant = Instant.parse("2026-10-31T01:00:00Z"), endInstant = Instant.parse("2026-10-31T02:00:00Z")),
                    calendars = listOf(CalendarSummary("work", "Work", 0, true, true))),
                    ZoneId.of("Asia/Tokyo"), true, {}, {}, {}, {}, themePresentation = theme)
            }
        }
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.waitForIdle(500, 5000)
        val destination = File(instrumentation.targetContext.getExternalFilesDir(null), "test-visuals/$name.png")
        destination.parentFile!!.mkdirs()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        destination.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}

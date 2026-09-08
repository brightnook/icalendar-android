package com.brightnook.nookcalendar.ui.main

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.geometry.Offset
import com.brightnook.nookcalendar.domain.EventDetail
import com.brightnook.nookcalendar.domain.ThemeBackgroundMode
import com.brightnook.nookcalendar.domain.ThemeSettings
import com.brightnook.nookcalendar.ui.theme.CalendarThemePresentation
import com.brightnook.nookcalendar.ui.theme.calendarThemePresentation
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ModalPresentationPolicyTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun whiteThemeDatePickerPreservesTheInheritedCalendarAccent() {
        val presentation = calendarThemePresentation(
            theme = ThemeSettings(mode = ThemeBackgroundMode.WHITE),
            assetFile = null,
        )
        val inheritedPrimary = Color(0xFF6750A4)
        val inheritedOnPrimary = Color.White

        val modal = calendarModalPresentation(
            presentation = presentation,
            inheritedPrimary = inheritedPrimary,
            inheritedOnPrimary = inheritedOnPrimary,
        )

        assertEquals(inheritedPrimary, modal.calendarHighlightColor)
        assertEquals(inheritedOnPrimary, modal.calendarHighlightContentColor)
    }

    @Test
    fun blackThemeDatePickerRendersTheResolvedModalPalette() {
        val presentation = calendarThemePresentation(
            theme = ThemeSettings(mode = ThemeBackgroundMode.BLACK),
            assetFile = null,
        )
        val modal = calendarModalPresentation(
            presentation = presentation,
            inheritedPrimary = Color(0xFF6750A4),
            inheritedOnPrimary = Color.White,
        )
        renderCalendar(state(), presentation)

        composeRule.onNodeWithTag(BOTTOM_PICK_DATE_TAG).performClick()

        composeRule.onNodeWithTag(DATE_PICKER_SURFACE_TAG).assertIsDisplayed()
        assertEquals(presentation.modalContainerColor, modal.containerColor)
        assertEquals(presentation.modalScrimColor, modal.scrimColor)
        assertEquals(presentation.outlineColor, modal.outlineColor)
        assertEquals(presentation.contentColor, modal.primaryContentColor)
        assertEquals(presentation.secondaryContentColor, modal.secondaryContentColor)
        assertEquals(presentation.actionContentColor, modal.actionContentColor)
        assertEquals(presentation.actionContentColor, modal.calendarHighlightColor)
        assertEquals(presentation.modalContainerColor, modal.calendarHighlightContentColor)
        assertTrue(contrastRatio(modal.primaryContentColor, modal.containerColor) >= 4.5f)
        assertTrue(contrastRatio(modal.actionContentColor, modal.containerColor) >= 4.5f)
    }

    @Test
    fun datePickerDismissesFromTheScrimButKeepsOpenForDateSelection() {
        val presentation = calendarThemePresentation(
            theme = ThemeSettings(mode = ThemeBackgroundMode.BLACK),
            assetFile = null,
        )
        renderCalendar(state(), presentation)

        composeRule.onNodeWithTag(BOTTOM_PICK_DATE_TAG).performClick()
        composeRule.onNodeWithTag("date-picker-scrim").performTouchInput {
            click(Offset(1f, 1f))
        }
        composeRule.onNodeWithTag(DATE_PICKER_SURFACE_TAG).assertDoesNotExist()

        composeRule.onNodeWithTag(BOTTOM_PICK_DATE_TAG).performClick()
        composeRule.onAllNodesWithTag("calendar-day-2026-08-15")[1].performClick()
        composeRule.onNodeWithTag(DATE_PICKER_SURFACE_TAG).assertIsDisplayed()
    }

    @Test
    fun imageThemeEventDetailRendersAnOpaqueReadableModalPalette() {
        val asset = createValidThemeImage()
        try {
            val presentation = calendarThemePresentation(
                theme = ThemeSettings(mode = ThemeBackgroundMode.IMAGE, asset.name),
                assetFile = asset,
            )
            val modal = calendarModalPresentation(
                presentation = presentation,
                inheritedPrimary = Color(0xFF6750A4),
                inheritedOnPrimary = Color.White,
            )
            renderCalendar(state(openEvent = detail()), presentation)

            composeRule.onNodeWithTag(EVENT_DETAIL_SHEET_TAG).assertIsDisplayed()
            assertEquals(presentation.modalContainerColor, modal.containerColor)
            assertEquals(presentation.modalScrimColor, modal.scrimColor)
            assertEquals(presentation.outlineColor, modal.outlineColor)
            assertEquals(presentation.contentColor, modal.primaryContentColor)
            assertEquals(presentation.secondaryContentColor, modal.secondaryContentColor)
            assertEquals(presentation.actionContentColor, modal.actionContentColor)
            assertEquals(1f, modal.containerColor.alpha)
            assertTrue(modal.containerColor.luminance() < 0.1f)
            assertTrue(contrastRatio(modal.primaryContentColor, modal.containerColor) >= 4.5f)
            assertTrue(contrastRatio(modal.actionContentColor, modal.containerColor) >= 4.5f)
        } finally {
            asset.delete()
        }
    }

    private fun renderCalendar(state: MainCalendarState, presentation: CalendarThemePresentation) {
        composeRule.setContent {
            MaterialTheme {
                MainCalendarScreen(
                    state = state,
                    zoneId = ZoneId.of("Asia/Tokyo"),
                    onSelectDate = {},
                    onShowMonth = {},
                    onRefresh = {},
                    onOpenEvent = {},
                    onCloseEvent = {},
                    onOpenExternalAction = {},
                    themePresentation = presentation,
                )
            }
        }
    }

    private fun state(openEvent: EventDetail? = null) = MainCalendarState(
        selectedDate = LocalDate.of(2026, 8, 15),
        displayedMonth = YearMonth.of(2026, 8),
        openEvent = openEvent,
    )

    private fun detail() = EventDetail(
        instanceId = "instance-1",
        eventId = "event-1",
        calendarId = "work",
        calendarName = "Work",
        calendarColorArgb = 0xff336699.toInt(),
        title = "Planning",
        location = "Tokyo",
        notes = "Notes",
        isAllDay = false,
        startInstant = LocalDate.of(2026, 8, 15).atTime(9, 0).atZone(ZoneId.of("Asia/Tokyo")).toInstant(),
        endInstant = LocalDate.of(2026, 8, 15).atTime(10, 0).atZone(ZoneId.of("Asia/Tokyo")).toInstant(),
        startDate = null,
        endDateExclusive = null,
        originalTimeZoneId = "Asia/Tokyo",
        recurrenceRule = null,
    )

    private fun contrastRatio(first: Color, second: Color): Float {
        val lighter = maxOf(first.luminance(), second.luminance())
        val darker = minOf(first.luminance(), second.luminance())
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    private fun createValidThemeImage() = Files.createTempFile("event-detail-theme", ".png").toFile().also { file ->
        val image = BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until image.height) {
            for (x in 0 until image.width) image.setRGB(x, y, 0xFFFFFFFF.toInt())
        }
        check(ImageIO.write(image, "png", file))
    }
}

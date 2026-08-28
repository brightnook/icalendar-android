package com.brightnook.nookcalendar.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetCompactPresentationTest {
    @Test
    fun narrowTwoColumnWidgetKeepsItsDateVisible() {
        val presentation = widgetPresentationForWidth(120)

        assertTrue(presentation.isCompact)
        assertEquals(14f, presentation.titleTextSizeSp)
        assertEquals(12f, presentation.eventTitleTextSizeSp)
        assertTrue(presentation.showHeaderDate)
    }

    @Test
    fun twoColumnWidgetWidthUsesTheCompactHeader() {
        val presentation = widgetPresentationForWidth(184)

        assertTrue(presentation.isCompact)
        assertTrue(presentation.showHeaderDate)
    }

    @Test
    fun widerWidgetKeepsTheRegularTypography() {
        assertTrue(!widgetPresentationForWidth(240).isCompact)
    }
}

package com.victor2319.ioscalendar.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetCompactPresentationTest {
    @Test
    fun twoByThreeWidgetUsesCompactTypography() {
        val presentation = widgetPresentationForWidth(120)

        assertTrue(presentation.isCompact)
        assertEquals(14f, presentation.titleTextSizeSp)
        assertEquals(12f, presentation.eventTitleTextSizeSp)
    }

    @Test
    fun widerWidgetKeepsTheRegularTypography() {
        assertTrue(!widgetPresentationForWidth(240).isCompact)
    }
}

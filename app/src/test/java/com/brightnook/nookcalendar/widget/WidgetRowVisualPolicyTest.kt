package com.brightnook.nookcalendar.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WidgetRowVisualPolicyTest {
    @Test
    fun dateGroupRunsEdgeToEdgeWhileTitleUsesItsOwnLightBackground() {
        val policy = widgetRowVisualPolicy()

        assertFalse(policy.dateGroupHasRoundedEnds)
        assertEquals(1, policy.titleTopPaddingDp)
        assertEquals("widget_event_title_background", policy.titleBackgroundResource)
    }
}

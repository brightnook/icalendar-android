package com.brightnook.nookcalendar.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetConfigLayoutTest {
    @Test
    fun widgetConfigurationContentStartsBelowTheSystemStatusArea() {
        assertEquals(112, widgetConfigurationTopPadding(statusBarInsetPx = 64, contentPaddingPx = 48))
    }
}

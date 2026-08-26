package com.victor2319.ioscalendar.ui.main

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class AdBannerSlotTest {
    @Test
    fun adaptiveWidthChangesWhenTheAvailableSlotWidthChanges() {
        assertEquals(320, anchoredAdaptiveBannerWidthDp(320.dp))
        assertEquals(600, anchoredAdaptiveBannerWidthDp(600.dp))
    }
}

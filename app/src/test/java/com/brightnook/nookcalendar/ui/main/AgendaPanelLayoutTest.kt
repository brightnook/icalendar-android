package com.brightnook.nookcalendar.ui.main

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class AgendaPanelLayoutTest {
    @Test
    fun agendaPanelHeightScalesWithTheAvailableContentAreaWithinReadableBounds() {
        assertEquals(200.dp, agendaPanelHeight(300.dp))
        assertEquals(225.dp, agendaPanelHeight(500.dp))
        assertEquals(320.dp, agendaPanelHeight(800.dp))
    }
}

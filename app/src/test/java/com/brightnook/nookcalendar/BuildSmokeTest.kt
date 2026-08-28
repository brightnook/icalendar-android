package com.brightnook.nookcalendar

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BuildSmokeTest {
    @Test
    fun packageNameIsStable() {
        assertEquals("com.brightnook.nookcalendar", BuildConfig.APPLICATION_ID)
    }
}

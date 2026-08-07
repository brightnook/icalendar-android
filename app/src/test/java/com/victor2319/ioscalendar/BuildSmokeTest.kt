package com.victor2319.ioscalendar

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BuildSmokeTest {
    @Test
    fun packageNameIsStable() {
        assertEquals("com.victor2319.ioscalendar", BuildConfig.APPLICATION_ID)
    }
}

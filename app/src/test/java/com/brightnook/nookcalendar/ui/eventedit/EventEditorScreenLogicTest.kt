package com.brightnook.nookcalendar.ui.eventedit

import com.brightnook.nookcalendar.domain.EventReminder
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EventEditorScreenLogicTest {
    @Test
    fun customReminderUsesMinutesBeforeStart() {
        assertEquals(
            EventReminder.RelativeToStart(Duration.ofMinutes(-45)),
            customReminderFromMinutes("45"),
        )
    }

    @Test
    fun customReminderRejectsInvalidValues() {
        assertNull(customReminderFromMinutes(""))
        assertNull(customReminderFromMinutes("-1"))
        assertNull(customReminderFromMinutes("999999"))
    }
}

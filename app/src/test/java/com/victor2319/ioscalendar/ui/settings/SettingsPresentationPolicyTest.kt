package com.victor2319.ioscalendar.ui.settings

import com.victor2319.ioscalendar.domain.SyncMode
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsPresentationPolicyTest {
    @Test
    fun syncPickerOffersEveryPersistedFrequency() {
        assertEquals(SyncMode.entries, syncModePickerOptions())
    }
}

package com.brightnook.nookcalendar.ui.settings

import com.brightnook.nookcalendar.domain.SyncMode
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsPresentationPolicyTest {
    @Test
    fun syncPickerOffersEveryPersistedFrequency() {
        assertEquals(SyncMode.entries, syncModePickerOptions())
    }
}

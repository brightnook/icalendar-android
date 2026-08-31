package com.brightnook.nookcalendar.monetization

import com.google.android.ump.ConsentDebugSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdConsentDebugConfigurationTest {
    @Test
    fun debugBuildForcesEeaConsentTestingButReleaseHasNoDebugGeography() {
        assertEquals(
            ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA,
            debugConsentGeographyFor(isDebugBuild = true),
        )
        assertNull(debugConsentGeographyFor(isDebugBuild = false))
    }
}

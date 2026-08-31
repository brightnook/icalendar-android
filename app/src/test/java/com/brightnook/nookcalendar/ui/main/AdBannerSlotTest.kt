package com.brightnook.nookcalendar.ui.main

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class AdBannerSlotTest {
    @Test
    fun debugBuildUsesTheGoogleTestBannerButReleaseUsesTheConfiguredBanner() {
        assertEquals(
            "ca-app-pub-7357814564478740/8148143789",
            RELEASE_BANNER_AD_UNIT_ID,
        )
        assertEquals(
            ANCHORED_ADAPTIVE_TEST_AD_UNIT_ID,
            bannerAdUnitIdFor(
                isDebugBuild = true,
                configuredReleaseAdUnitId = RELEASE_BANNER_AD_UNIT_ID,
            ),
        )
        assertEquals(
            RELEASE_BANNER_AD_UNIT_ID,
            bannerAdUnitIdFor(
                isDebugBuild = false,
                configuredReleaseAdUnitId = RELEASE_BANNER_AD_UNIT_ID,
            ),
        )
    }

    @Test
    fun adaptiveWidthChangesWhenTheAvailableSlotWidthChanges() {
        assertEquals(320, anchoredAdaptiveBannerWidthDp(320.dp))
        assertEquals(600, anchoredAdaptiveBannerWidthDp(600.dp))
    }
}

package com.brightnook.nookcalendar.ui

import com.brightnook.nookcalendar.domain.AccountAuthenticationState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppRootRoutingTest {
    @Test
    fun restoredAuthenticatedAccountRoutesCalendarWithoutLoginViewModelCompletion() {
        // Catches a cold start requiring LoginViewModel's transient CALENDAR destination despite valid credentials.
        assertTrue(routesToAuthenticatedContent(AccountAuthenticationState.Authenticated("p***@example.test")))
        assertFalse(routesToAuthenticatedContent(AccountAuthenticationState.SignedOut))
        assertFalse(routesToAuthenticatedContent(AccountAuthenticationState.Loading))
    }

    @Test
    fun everyAccountStateOpensTheCalendarViewFirst() {
        assertEquals(AppStartupDestination.CALENDAR, appStartupDestination(AccountAuthenticationState.Loading))
        assertEquals(AppStartupDestination.CALENDAR, appStartupDestination(AccountAuthenticationState.SignedOut))
        assertEquals(
            AppStartupDestination.CALENDAR,
            appStartupDestination(AccountAuthenticationState.Authenticated("p***@example.test")),
        )
    }
}

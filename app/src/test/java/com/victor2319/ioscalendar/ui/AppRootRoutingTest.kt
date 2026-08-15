package com.victor2319.ioscalendar.ui

import com.victor2319.ioscalendar.domain.AccountAuthenticationState
import org.junit.Assert.assertFalse
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
}

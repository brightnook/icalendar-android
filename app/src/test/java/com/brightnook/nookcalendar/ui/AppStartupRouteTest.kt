package com.brightnook.nookcalendar.ui

import com.brightnook.nookcalendar.domain.AccountAuthenticationState
import org.junit.Assert.assertEquals
import org.junit.Test

class AppStartupRouteTest {
    @Test
    fun signedOutStartupStillOpensTheCalendarView() {
        assertEquals(
            AppStartupDestination.CALENDAR,
            appStartupDestination(AccountAuthenticationState.SignedOut),
        )
    }

    @Test
    fun loginScreenIsShownOnlyAfterTheUserRequestsItWhileSignedOut() {
        assertEquals(true, shouldShowLoginScreen(AccountAuthenticationState.SignedOut, loginRequested = true))
        assertEquals(false, shouldShowLoginScreen(AccountAuthenticationState.Authenticated("p***@example.test"), loginRequested = true))
    }

}

package com.brightnook.nookcalendar.domain

import kotlinx.coroutines.flow.StateFlow

interface AccountManager {
    val authenticationState: StateFlow<AccountAuthenticationState>
    suspend fun markAuthenticated(appleId: String)
    suspend fun logout(clearCache: Boolean): Boolean
}

sealed interface AccountAuthenticationState {
    data object Loading : AccountAuthenticationState
    data object SignedOut : AccountAuthenticationState
    data class Authenticated(val maskedAppleId: String) : AccountAuthenticationState
}

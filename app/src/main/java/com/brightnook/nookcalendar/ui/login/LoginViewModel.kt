package com.brightnook.nookcalendar.ui.login

import com.brightnook.nookcalendar.auth.Credentials
import com.brightnook.nookcalendar.auth.CredentialsStore
import com.brightnook.nookcalendar.auth.LoginCoordinator
import com.brightnook.nookcalendar.auth.LoginResult
import com.brightnook.nookcalendar.sync.SyncCoordinator
import com.brightnook.nookcalendar.sync.SyncFailureCategory
import com.brightnook.nookcalendar.sync.SyncRequest
import com.brightnook.nookcalendar.sync.SyncResult
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LoginViewModel(
    private val loginCoordinator: LoginCoordinator,
    private val credentialsStore: CredentialsStore,
    private val syncCoordinator: SyncCoordinator,
    private val onAuthenticated: suspend (String) -> Unit = {},
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = mutableUiState.asStateFlow()
    private var pendingCalendarAppleId: String? = null

    fun connect(appleId: String, suppliedPassword: CharArray) {
        viewModelScope.launch { connectInternal(appleId, suppliedPassword) }
    }

    fun viewCalendars() {
        val appleId = pendingCalendarAppleId ?: return
        val state = mutableUiState.value
        if (state.error != LoginError.PARTIAL_SYNC || state.destination != LoginDestination.CALENDAR) return
        viewModelScope.launch {
            onAuthenticated(appleId)
        }
    }

    private suspend fun connectInternal(appleId: String, suppliedPassword: CharArray) {
        var password: CharArray? = null
        try {
            pendingCalendarAppleId = null
            mutableUiState.value = LoginUiState(phase = LoginPhase.VALIDATING)
            val normalizedAppleId = appleId.trim()
            password = trimOuterWhitespace(suppliedPassword)
            if (normalizedAppleId.isEmpty() || password.isEmpty()) {
                fail(LoginError.EMPTY_FIELDS)
                return
            }

            mutableUiState.value = LoginUiState(phase = LoginPhase.DISCOVERING)
            when (loginCoordinator.connect(normalizedAppleId, password)) {
                LoginResult.Success -> Unit
                LoginResult.AuthenticationFailure -> return fail(LoginError.AUTHENTICATION)
                LoginResult.NetworkFailure -> return fail(LoginError.NETWORK)
                LoginResult.ServerFailure -> return fail(LoginError.SERVER)
            }

            val storedPassword = password.copyOf()
            try {
                credentialsStore.save(Credentials(normalizedAppleId, storedPassword))
            } finally {
                storedPassword.fill('\u0000')
            }

            mutableUiState.value = LoginUiState(phase = LoginPhase.SYNCING)
            when (val result = syncCoordinator.sync(SyncRequest.Initial)) {
                is SyncResult.Success -> {
                    if (result.counts.calendars == 0) {
                        credentialsStore.clear()
                        fail(LoginError.NO_CALENDARS, clearPassword = false)
                        return
                    }
                    onAuthenticated(normalizedAppleId)
                    mutableUiState.value = LoginUiState(
                        phase = LoginPhase.COMPLETE,
                        destination = LoginDestination.CALENDAR,
                    )
                }

                is SyncResult.PartialSuccess -> {
                    if (result.calendarIds.isEmpty()) {
                        fail(LoginError.PARTIAL_SYNC)
                    } else {
                        pendingCalendarAppleId = normalizedAppleId
                        mutableUiState.value = LoginUiState(
                            error = LoginError.PARTIAL_SYNC,
                            passwordClearToken = mutableUiState.value.passwordClearToken + 1,
                            destination = LoginDestination.CALENDAR,
                        )
                    }
                }
                is SyncResult.AuthenticationRequired -> fail(LoginError.AUTHENTICATION)
                is SyncResult.RetryableFailure -> fail(result.category.toLoginError())
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            fail(LoginError.SERVER)
        } finally {
            suppliedPassword.fill('\u0000')
            password?.fill('\u0000')
        }
    }

    private fun fail(error: LoginError, clearPassword: Boolean = true) {
        mutableUiState.value = LoginUiState(
            error = error,
            passwordClearToken = if (clearPassword) mutableUiState.value.passwordClearToken + 1 else 0,
        )
    }
}

class LoginViewModelFactory(
    private val loginCoordinator: LoginCoordinator,
    private val credentialsStore: CredentialsStore,
    private val syncCoordinator: SyncCoordinator,
    private val onAuthenticated: suspend (String) -> Unit = {},
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(LoginViewModel::class.java))
        return LoginViewModel(loginCoordinator, credentialsStore, syncCoordinator, onAuthenticated) as T
    }
}

private fun SyncFailureCategory.toLoginError(): LoginError = when (this) {
    SyncFailureCategory.AUTHENTICATION -> LoginError.AUTHENTICATION
    SyncFailureCategory.NETWORK -> LoginError.NETWORK

    SyncFailureCategory.TEMPORARY,
    SyncFailureCategory.INVALID_DATA,
    SyncFailureCategory.REPOSITORY,
    -> LoginError.SERVER
}

private fun trimOuterWhitespace(value: CharArray): CharArray {
    var first = 0
    var last = value.size
    while (first < last && value[first].isWhitespace()) first += 1
    while (last > first && value[last - 1].isWhitespace()) last -= 1
    return value.copyOfRange(first, last)
}

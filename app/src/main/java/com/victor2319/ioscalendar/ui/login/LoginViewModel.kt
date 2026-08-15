package com.victor2319.ioscalendar.ui.login

import com.victor2319.ioscalendar.auth.Credentials
import com.victor2319.ioscalendar.auth.CredentialsStore
import com.victor2319.ioscalendar.auth.LoginCoordinator
import com.victor2319.ioscalendar.auth.LoginResult
import com.victor2319.ioscalendar.sync.SyncCoordinator
import com.victor2319.ioscalendar.sync.SyncFailureCategory
import com.victor2319.ioscalendar.sync.SyncRequest
import com.victor2319.ioscalendar.sync.SyncResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LoginViewModel(
    private val loginCoordinator: LoginCoordinator,
    private val credentialsStore: CredentialsStore,
    private val syncCoordinator: SyncCoordinator,
) {
    private val mutableUiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = mutableUiState.asStateFlow()

    suspend fun connect(appleId: String, suppliedPassword: CharArray) {
        var password: CharArray? = null
        try {
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
                is SyncResult.Success,
                is SyncResult.PartialSuccess,
                -> mutableUiState.value = LoginUiState(
                    phase = LoginPhase.COMPLETE,
                    destination = LoginDestination.CALENDAR,
                )

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

private fun SyncFailureCategory.toLoginError(): LoginError = when (this) {
    SyncFailureCategory.AUTHENTICATION -> LoginError.AUTHENTICATION
    SyncFailureCategory.NETWORK,
    SyncFailureCategory.TEMPORARY,
    -> LoginError.NETWORK

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

package com.victor2319.ioscalendar.auth

import com.victor2319.ioscalendar.domain.AccountAuthenticationState
import com.victor2319.ioscalendar.domain.AccountManager
import com.victor2319.ioscalendar.domain.CalendarRepository
import com.victor2319.ioscalendar.sync.SyncScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class LocalAccountManager(
    private val credentialsStore: CredentialsStore,
    private val repository: CalendarRepository,
    private val syncScheduler: SyncScheduler,
    scope: CoroutineScope,
) : AccountManager {
    private val transitions = Mutex()
    private val mutableAuthenticationState = MutableStateFlow<AccountAuthenticationState>(AccountAuthenticationState.Loading)
    override val authenticationState: StateFlow<AccountAuthenticationState> = mutableAuthenticationState.asStateFlow()

    init {
        scope.launch {
            val existing = try {
                credentialsStore.load()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                null
            }
            val appleId = existing?.appleId
            existing?.appSpecificPassword?.fill('\u0000')
            transitions.withLock {
                if (mutableAuthenticationState.value == AccountAuthenticationState.Loading) {
                    mutableAuthenticationState.value = appleId?.takeIf(String::isNotBlank)
                        ?.let { AccountAuthenticationState.Authenticated(it.maskAppleId()) }
                        ?: AccountAuthenticationState.SignedOut
                }
            }
        }
    }

    override suspend fun markAuthenticated(appleId: String) {
        transitions.withLock { mutableAuthenticationState.value = AccountAuthenticationState.Authenticated(appleId.maskAppleId()) }
    }

    override suspend fun logout(clearCache: Boolean): Boolean = transitions.withLock {
        syncScheduler.cancelAll()
        credentialsStore.clear()
        mutableAuthenticationState.value = AccountAuthenticationState.SignedOut
        if (!clearCache) return@withLock true
        try {
            repository.clearCalendarCache()
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            false
        }
    }
}

internal fun String.maskAppleId(): String {
    val at = indexOf('@')
    if (at <= 0 || at == lastIndex) return "•••"
    val localPart = substring(0, at)
    val revealedEnd = localPart.offsetByCodePoints(0, minOf(3, localPart.codePointCount(0, localPart.length)))
    return "${localPart.substring(0, revealedEnd)}***${substring(at)}"
}

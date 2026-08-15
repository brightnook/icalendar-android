package com.victor2319.ioscalendar.ui.login

import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.ViewModelProvider
import com.victor2319.ioscalendar.auth.Credentials
import com.victor2319.ioscalendar.auth.CredentialsStore
import com.victor2319.ioscalendar.auth.LoginCoordinator
import com.victor2319.ioscalendar.auth.LoginResult
import com.victor2319.ioscalendar.sync.SyncCoordinator
import com.victor2319.ioscalendar.sync.SyncRequest
import com.victor2319.ioscalendar.sync.SyncResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelLifecycleTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun rotationKeepsTheSavedCredentialAndInFlightInitialSyncOnTheSameViewModel() = runTest {
        // Catches composition disposal cancelling a connect after persistence but before its initial sync finishes.
        val store = ViewModelStore()
        val sync = BlockingSync()
        val factory = LoginViewModelFactory(
            loginCoordinator = object : LoginCoordinator {
                override suspend fun connect(appleId: String, appPassword: CharArray) = LoginResult.Success
            },
            credentialsStore = MemoryCredentialsStore(),
            syncCoordinator = sync,
        )
        try {
            val first = ViewModelProvider(RecreatedOwner(store), factory)[LoginViewModel::class.java]
            first.connect("person@example.test", "abcd-efgh-ijkl-mnop".toCharArray())
            advanceUntilIdle()
            sync.started.await()

            val afterRotation = ViewModelProvider(RecreatedOwner(store), factory)[LoginViewModel::class.java]
            assertSame(first, afterRotation)
            assertEquals(LoginPhase.SYNCING, afterRotation.uiState.value.phase)

            sync.release.complete(Unit)
            advanceUntilIdle()
            assertEquals(LoginDestination.CALENDAR, afterRotation.uiState.value.destination)
        } finally {
            store.clear()
        }
    }
}

private class RecreatedOwner(
    override val viewModelStore: ViewModelStore,
) : ViewModelStoreOwner

private class BlockingSync : SyncCoordinator {
    val started = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()

    override suspend fun sync(request: SyncRequest): SyncResult {
        check(request == SyncRequest.Initial)
        started.complete(Unit)
        release.await()
        return SyncResult.Success()
    }
}

private class MemoryCredentialsStore : CredentialsStore {
    private var value: Credentials? = null
    override suspend fun save(credentials: Credentials) {
        value = Credentials(credentials.appleId, credentials.appSpecificPassword.copyOf())
    }
    override suspend fun load(): Credentials? = value?.let { Credentials(it.appleId, it.appSpecificPassword.copyOf()) }
    override suspend fun clear() { value = null }
}

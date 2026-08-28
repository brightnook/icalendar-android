package com.brightnook.nookcalendar.ui.login

import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.ViewModelProvider
import com.brightnook.nookcalendar.auth.Credentials
import com.brightnook.nookcalendar.auth.CredentialsStore
import com.brightnook.nookcalendar.auth.LoginCoordinator
import com.brightnook.nookcalendar.auth.LoginResult
import com.brightnook.nookcalendar.sync.SyncCoordinator
import com.brightnook.nookcalendar.sync.SyncCounts
import com.brightnook.nookcalendar.sync.SyncRequest
import com.brightnook.nookcalendar.sync.SyncResult
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
        return SyncResult.Success(counts = SyncCounts(calendars = 1))
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

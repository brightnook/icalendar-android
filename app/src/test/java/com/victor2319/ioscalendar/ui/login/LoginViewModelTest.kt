package com.victor2319.ioscalendar.ui.login

import com.victor2319.ioscalendar.auth.Credentials
import com.victor2319.ioscalendar.auth.CredentialsStore
import com.victor2319.ioscalendar.auth.LoginCoordinator
import com.victor2319.ioscalendar.auth.LoginResult
import com.victor2319.ioscalendar.sync.SyncCoordinator
import com.victor2319.ioscalendar.sync.SyncRequest
import com.victor2319.ioscalendar.sync.SyncResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginViewModelTest {
    @Test
    fun emptyFieldsAreRejectedBeforeContactingICloud() = runTest {
        // Catches sending blank credentials to the server or writing them to secure storage.
        val coordinator = RecordingLoginCoordinator(LoginResult.Success)
        val store = RecordingCredentialsStore()
        val viewModel = LoginViewModel(coordinator, store, SyncAfterStored(store))

        val suppliedPassword = "   ".toCharArray()
        viewModel.connect("   ", suppliedPassword)

        assertEquals(LoginError.EMPTY_FIELDS, viewModel.uiState.value.error)
        assertEquals(1L, viewModel.uiState.value.passwordClearToken)
        assertEquals(0, coordinator.connectAttempts)
        assertNull(store.saved)
        assertTrue(suppliedPassword.all { it == '\u0000' })
    }

    @Test
    fun rejectedCredentialsAreNotSavedAndClearThePasswordField() = runTest {
        // Catches persisting an app password before iCloud has accepted it.
        val coordinator = RecordingLoginCoordinator(LoginResult.AuthenticationFailure)
        val store = RecordingCredentialsStore()
        val viewModel = LoginViewModel(coordinator, store, SyncAfterStored(store))
        val suppliedPassword = "abcd-efgh-ijkl-mnop".toCharArray()

        viewModel.connect(" person@example.test ", suppliedPassword)

        assertEquals(LoginError.AUTHENTICATION, viewModel.uiState.value.error)
        assertEquals(1L, viewModel.uiState.value.passwordClearToken)
        assertNull(store.saved)
        assertTrue(suppliedPassword.all { it == '\u0000' })
    }

    @Test
    fun validatedCredentialsAreStoredBeforeInitialSyncAndNavigateOnSuccess() = runTest {
        // Catches first sync starting without the validated credential being available to its store-backed flow.
        val coordinator = RecordingLoginCoordinator(LoginResult.Success)
        val store = RecordingCredentialsStore()
        val viewModel = LoginViewModel(coordinator, store, SyncAfterStored(store))

        viewModel.connect(" person@example.test ", " abcd-efgh-ijkl-mnop ".toCharArray())

        assertEquals("person@example.test", store.saved?.appleId)
        assertEquals("abcd-efgh-ijkl-mnop", store.saved?.appSpecificPassword?.concatToString())
        assertEquals(LoginDestination.CALENDAR, viewModel.uiState.value.destination)
        assertEquals(LoginPhase.COMPLETE, viewModel.uiState.value.phase)
    }

    @Test
    fun authenticationNetworkAndServerFailuresHaveDifferentVisibleMessages() = runTest {
        // Catches collapsing distinct recovery actions into one unhelpful error state.
        val cases = listOf(
            LoginResult.AuthenticationFailure to LoginError.AUTHENTICATION,
            LoginResult.NetworkFailure to LoginError.NETWORK,
            LoginResult.ServerFailure to LoginError.SERVER,
        )

        cases.forEach { (result, expectedError) ->
            val coordinator = RecordingLoginCoordinator(result)
            val store = RecordingCredentialsStore()
            val viewModel = LoginViewModel(coordinator, store, SyncAfterStored(store))

            viewModel.connect("person@example.test", "abcd-efgh-ijkl-mnop".toCharArray())

            assertEquals(expectedError, viewModel.uiState.value.error)
            assertEquals(1L, viewModel.uiState.value.passwordClearToken)
            assertNull(store.saved)
        }
    }
}

private class RecordingLoginCoordinator(
    private val result: LoginResult,
) : LoginCoordinator {
    var connectAttempts = 0

    override suspend fun connect(appleId: String, appPassword: CharArray): LoginResult {
        connectAttempts += 1
        return result
    }
}

private class RecordingCredentialsStore : CredentialsStore {
    var saved: Credentials? = null

    override suspend fun save(credentials: Credentials) {
        saved = Credentials(credentials.appleId, credentials.appSpecificPassword.copyOf())
    }

    override suspend fun load(): Credentials? = saved?.let {
        Credentials(it.appleId, it.appSpecificPassword.copyOf())
    }

    override suspend fun clear() {
        saved?.appSpecificPassword?.fill('\u0000')
        saved = null
    }
}

private class SyncAfterStored(
    private val store: RecordingCredentialsStore,
) : SyncCoordinator {
    override suspend fun sync(request: SyncRequest): SyncResult {
        check(request == SyncRequest.Initial)
        check(store.load() != null) { "Initial sync requires saved credentials" }
        return SyncResult.Success()
    }
}

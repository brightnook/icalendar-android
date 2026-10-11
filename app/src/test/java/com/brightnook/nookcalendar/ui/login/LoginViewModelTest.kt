package com.brightnook.nookcalendar.ui.login

import com.brightnook.nookcalendar.auth.Credentials
import com.brightnook.nookcalendar.auth.CredentialsStore
import com.brightnook.nookcalendar.auth.LoginCoordinator
import com.brightnook.nookcalendar.auth.LoginResult
import com.brightnook.nookcalendar.sync.SyncCoordinator
import com.brightnook.nookcalendar.sync.SyncCounts
import com.brightnook.nookcalendar.sync.SyncFailureCategory
import com.brightnook.nookcalendar.sync.SyncRequest
import com.brightnook.nookcalendar.sync.SyncResult
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()
    @Test
    fun emptyFieldsAreRejectedBeforeContactingICloud() = runTest {
        // Catches sending blank credentials to the server or writing them to secure storage.
        val coordinator = RecordingLoginCoordinator(LoginResult.Success)
        val store = RecordingCredentialsStore()
        val viewModel = LoginViewModel(coordinator, store, SyncAfterStored(store))

        val suppliedPassword = "   ".toCharArray()
        viewModel.connect("   ", suppliedPassword)
        advanceUntilIdle()

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
        advanceUntilIdle()

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
        advanceUntilIdle()

        assertEquals("person@example.test", store.saved?.appleId)
        assertEquals("abcd-efgh-ijkl-mnop", store.saved?.appSpecificPassword?.concatToString())
        assertEquals(LoginDestination.CALENDAR, viewModel.uiState.value.destination)
        assertEquals(LoginPhase.COMPLETE, viewModel.uiState.value.phase)
    }

    @Test
    fun successfulInitialSyncMarksTheAccountAuthenticatedBeforeCalendarRouting() = runTest {
        // Catches exposing the calendar destination before AppRoot's AccountManager gate can become authenticated.
        val marked = mutableListOf<String>()
        val store = RecordingCredentialsStore()
        val viewModel = LoginViewModel(
            RecordingLoginCoordinator(LoginResult.Success),
            store,
            SyncAfterStored(store),
            onAuthenticated = { marked += it },
        )

        viewModel.connect("person@example.test", "abcd-efgh-ijkl-mnop".toCharArray())
        advanceUntilIdle()

        assertEquals(listOf("person@example.test"), marked)
        assertEquals(LoginDestination.CALENDAR, viewModel.uiState.value.destination)
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
            advanceUntilIdle()

            assertEquals(expectedError, viewModel.uiState.value.error)
            assertEquals(1L, viewModel.uiState.value.passwordClearToken)
            assertNull(store.saved)
        }
    }

    @Test
    fun partialFirstSyncWithUsableCalendarsKeepsItsWarningUntilTheUserChoosesToViewThem() = runTest {
        // Catches either trapping a user at login or hiding the partial-sync warning before they choose a view.
        val store = RecordingCredentialsStore()
        val marked = mutableListOf<String>()
        val viewModel = LoginViewModel(
            RecordingLoginCoordinator(LoginResult.Success),
            store,
            SyncAfterStored(
                store,
                SyncResult.PartialSuccess(
                    counts = SyncCounts(calendars = 1),
                    calendarIds = setOf("saved"),
                    failures = mapOf("failed" to SyncFailureCategory.INVALID_DATA),
                ),
            ),
            onAuthenticated = { marked += it },
        )

        viewModel.connect("person@example.test", "abcd-efgh-ijkl-mnop".toCharArray())
        advanceUntilIdle()

        assertEquals(LoginDestination.CALENDAR, viewModel.uiState.value.destination)
        assertEquals(LoginError.PARTIAL_SYNC, viewModel.uiState.value.error)
        assertTrue(marked.isEmpty())

        viewModel.viewCalendars()
        advanceUntilIdle()

        assertEquals(listOf("person@example.test"), marked)
    }

    @Test
    fun partialFirstSyncWithoutUsableCalendarsDoesNotOfferCalendarView() = runTest {
        // Catches treating an all-failed first sync as though it had created a calendar view.
        val marked = mutableListOf<String>()
        val store = RecordingCredentialsStore()
        val viewModel = LoginViewModel(
            RecordingLoginCoordinator(LoginResult.Success),
            store,
            SyncAfterStored(
                store,
                SyncResult.PartialSuccess(SyncCounts(), emptySet(), mapOf("failed" to SyncFailureCategory.INVALID_DATA)),
            ),
            onAuthenticated = { marked += it },
        )

        viewModel.connect("person@example.test", "abcd-efgh-ijkl-mnop".toCharArray())
        advanceUntilIdle()
        viewModel.viewCalendars()
        advanceUntilIdle()

        assertEquals(LoginDestination.LOGIN, viewModel.uiState.value.destination)
        assertEquals(LoginError.PARTIAL_SYNC, viewModel.uiState.value.error)
        assertTrue(marked.isEmpty())
    }

    @Test
    fun successfulAuthenticationWithNoDiscoveredCalendarsStaysOnLoginWithGuidance() = runTest {
        // Catches routing to an empty calendar view when discovery returned no selectable calendars.
        val store = RecordingCredentialsStore()
        val viewModel = LoginViewModel(
            RecordingLoginCoordinator(LoginResult.Success),
            store,
            SyncAfterStored(store, SyncResult.Success(counts = SyncCounts(calendars = 0))),
        )

        viewModel.connect("person@example.test", "abcd-efgh-ijkl-mnop".toCharArray())
        advanceUntilIdle()

        assertEquals(LoginDestination.LOGIN, viewModel.uiState.value.destination)
        assertTrue(viewModel.uiState.value.error != null)
        assertTrue(viewModel.uiState.value.phase != LoginPhase.COMPLETE)
        assertNull(store.saved)
    }

    @Test
    fun initialSyncFailureCategoriesMapToDistinctSafeMessages() = runTest {
        // Catches treating a temporary server failure as a local network outage or losing auth guidance.
        val cases = listOf(
            SyncResult.RetryableFailure(SyncFailureCategory.AUTHENTICATION) to LoginError.AUTHENTICATION,
            SyncResult.RetryableFailure(SyncFailureCategory.NETWORK) to LoginError.NETWORK,
            SyncResult.RetryableFailure(SyncFailureCategory.TEMPORARY) to LoginError.SERVER,
        )

        cases.forEach { (syncResult, expectedError) ->
            val store = RecordingCredentialsStore()
            val viewModel = LoginViewModel(
                RecordingLoginCoordinator(LoginResult.Success),
                store,
                SyncAfterStored(store, syncResult),
            )

            viewModel.connect("person@example.test", "abcd-efgh-ijkl-mnop".toCharArray())
            advanceUntilIdle()

            assertEquals(expectedError, viewModel.uiState.value.error)
            assertEquals(LoginDestination.LOGIN, viewModel.uiState.value.destination)
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule : TestWatcher() {
    private val dispatcher = UnconfinedTestDispatcher()

    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
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
    private val result: SyncResult = SyncResult.Success(counts = SyncCounts(calendars = 1)),
) : SyncCoordinator {
    override suspend fun sync(request: SyncRequest): SyncResult {
        check(request == SyncRequest.Initial)
        check(store.load() != null) { "Initial sync requires saved credentials" }
        return result
    }
}

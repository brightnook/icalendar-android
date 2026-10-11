package com.brightnook.nookcalendar.auth

import com.brightnook.nookcalendar.caldav.CalDavFailure
import com.brightnook.nookcalendar.caldav.CalDavGateway
import kotlinx.coroutines.CancellationException

interface LoginCoordinator {
    suspend fun connect(appleId: String, appPassword: CharArray): LoginResult
}

sealed interface LoginResult {
    data object Success : LoginResult
    data object AuthenticationFailure : LoginResult
    data object NetworkFailure : LoginResult
    data object ServerFailure : LoginResult
}

class CalDavLoginCoordinator(
    private val gateway: CalDavGateway,
) : LoginCoordinator {
    override suspend fun connect(appleId: String, appPassword: CharArray): LoginResult {
        val gatewayPassword = appPassword.copyOf()
        return try {
            // A successful authenticate call installs the gateway session used by subsequent work.
            gateway.authenticate(Credentials(appleId, gatewayPassword))
            LoginResult.Success
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: CalDavFailure.AuthenticationRejected) {
            LoginResult.AuthenticationFailure
        } catch (_: CalDavFailure.NetworkUnavailable) {
            LoginResult.NetworkFailure
        } catch (_: Throwable) {
            LoginResult.ServerFailure
        } finally {
            gatewayPassword.fill('\u0000')
        }
    }
}

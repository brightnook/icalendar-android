package com.brightnook.nookcalendar.caldav

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

sealed class CalDavFailure(message: String) : Exception(message) {
    class AuthenticationRejected : CalDavFailure("iCloud authentication was rejected")

    class Temporary(val retryAfterSeconds: Long? = null) :
        CalDavFailure("The CalDAV service is temporarily unavailable")

    class NetworkUnavailable(
        val detail: NetworkFailureDetail = NetworkFailureDetail.UNKNOWN,
    ) : CalDavFailure("The network is unavailable")

    class Conflict : CalDavFailure("The calendar event changed on the server")

    class InvalidServerData : CalDavFailure("The CalDAV server returned invalid data")
}

enum class NetworkFailureDetail {
    TIMEOUT,
    DNS,
    CONNECTION,
    TLS,
    UNKNOWN;

    companion object {
        fun from(failure: IOException): NetworkFailureDetail = when (failure) {
            is SocketTimeoutException -> TIMEOUT
            is UnknownHostException -> DNS
            is ConnectException -> CONNECTION
            is SSLException -> TLS
            else -> UNKNOWN
        }
    }
}

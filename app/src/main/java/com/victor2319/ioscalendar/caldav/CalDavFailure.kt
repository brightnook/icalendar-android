package com.victor2319.ioscalendar.caldav

sealed class CalDavFailure(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class AuthenticationRejected : CalDavFailure("iCloud authentication was rejected")

    class Temporary(val retryAfterSeconds: Long? = null) :
        CalDavFailure("The CalDAV service is temporarily unavailable")

    class NetworkUnavailable(cause: Throwable) :
        CalDavFailure("The network is unavailable", cause)

    class InvalidServerData : CalDavFailure("The CalDAV server returned invalid data")
}

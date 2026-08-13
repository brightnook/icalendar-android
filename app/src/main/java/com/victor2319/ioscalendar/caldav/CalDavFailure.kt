package com.victor2319.ioscalendar.caldav

sealed class CalDavFailure(message: String) : Exception(message) {
    class AuthenticationRejected : CalDavFailure("iCloud authentication was rejected")

    class Temporary(val retryAfterSeconds: Long? = null) :
        CalDavFailure("The CalDAV service is temporarily unavailable")

    class NetworkUnavailable : CalDavFailure("The network is unavailable")

    class InvalidServerData : CalDavFailure("The CalDAV server returned invalid data")
}

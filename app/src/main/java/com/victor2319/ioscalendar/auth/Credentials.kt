package com.victor2319.ioscalendar.auth

data class Credentials(
    val appleId: String,
    val appSpecificPassword: CharArray,
) {
    override fun toString(): String =
        "Credentials(appleId=<redacted>, appSpecificPassword=<redacted>)"
}

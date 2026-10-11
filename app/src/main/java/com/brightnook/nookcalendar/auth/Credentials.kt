package com.brightnook.nookcalendar.auth

data class Credentials(
    val appleId: String,
    val appSpecificPassword: CharArray,
) {
    override fun toString(): String =
        "Credentials(appleId=<redacted>, appSpecificPassword=<redacted>)"
}

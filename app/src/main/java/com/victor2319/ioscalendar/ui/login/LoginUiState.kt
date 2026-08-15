package com.victor2319.ioscalendar.ui.login

/** Contains only display state; credentials and server responses never enter this state. */
data class LoginUiState(
    val phase: LoginPhase = LoginPhase.IDLE,
    val error: LoginError? = null,
    val passwordClearToken: Long = 0,
    val destination: LoginDestination = LoginDestination.LOGIN,
)

enum class LoginPhase {
    IDLE,
    VALIDATING,
    DISCOVERING,
    SYNCING,
    COMPLETE,
}

enum class LoginError {
    EMPTY_FIELDS,
    AUTHENTICATION,
    NETWORK,
    SERVER,
}

enum class LoginDestination {
    LOGIN,
    CALENDAR,
}

package com.victor2319.ioscalendar.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.victor2319.ioscalendar.R
import com.victor2319.ioscalendar.ui.transparentLabeledActionPresentation

const val APPLE_ACCOUNT_URL = "https://account.apple.com/"
const val APPLE_APP_PASSWORD_SUPPORT_URL = "https://support.apple.com/zh-cn/102654"

@Composable
fun LoginScreen(
    uiState: LoginUiState,
    onConnect: (appleId: String, password: CharArray) -> Unit,
    onViewCalendars: () -> Unit,
    onOpenUri: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var appleId by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val passwordVisibilityDescription = stringResource(R.string.login_toggle_password_visibility)
    val appleIdDescription = stringResource(R.string.login_apple_id_label)
    val passwordDescription = stringResource(R.string.login_app_password_label)
    val connectDescription = stringResource(R.string.login_connect_and_sync)
    val actionPresentation = transparentLabeledActionPresentation()

    LaunchedEffect(uiState.passwordClearToken) {
        if (uiState.passwordClearToken > 0) password = ""
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = stringResource(R.string.login_title))
        Text(text = stringResource(R.string.login_explanation))
        Button(onClick = { onOpenUri(APPLE_ACCOUNT_URL) }) {
            Text(stringResource(R.string.login_create_app_password))
        }
        Button(onClick = { onOpenUri(APPLE_APP_PASSWORD_SUPPORT_URL) }) {
            Text(stringResource(R.string.login_support_link))
        }
        OutlinedTextField(
            value = appleId,
            onValueChange = { appleId = it },
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = appleIdDescription },
            label = { Text(stringResource(R.string.login_apple_id_label)) },
            singleLine = true,
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = passwordDescription },
            label = { Text(stringResource(R.string.login_app_password_label)) },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(
                    onClick = { passwordVisible = !passwordVisible },
                    modifier = Modifier
                        .width(64.dp)
                        .semantics { contentDescription = passwordVisibilityDescription },
                ) {
                    Text(
                        text = if (passwordVisible) {
                            stringResource(R.string.login_hide_password)
                        } else {
                            stringResource(R.string.login_show_password)
                        },
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            },
        )
        TextButton(
            onClick = {
                val submittedPassword = password.toCharArray()
                password = ""
                onConnect(appleId, submittedPassword)
            },
            enabled = uiState.phase != LoginPhase.DISCOVERING && uiState.phase != LoginPhase.SYNCING,
            modifier = Modifier.heightIn(min = actionPresentation.minimumTouchTarget).semantics {
                contentDescription = connectDescription
            },
        ) {
            androidx.compose.material3.Icon(
                painter = painterResource(R.drawable.ic_action_login_transparent),
                contentDescription = null,
                modifier = Modifier.testTag("login-connect-icon"),
                tint = Color.Unspecified,
            )
            androidx.compose.foundation.layout.Spacer(Modifier.width(actionPresentation.iconTextSpacing))
            Text(stringResource(R.string.login_connect_and_sync))
        }
        LoginStatus(uiState, onViewCalendars)
    }
}

@Composable
private fun LoginStatus(uiState: LoginUiState, onViewCalendars: () -> Unit) {
    val messageId = when (uiState.phase) {
        LoginPhase.DISCOVERING -> R.string.login_progress_discovering
        LoginPhase.SYNCING -> R.string.login_progress_syncing
        else -> null
    } ?: when (uiState.error) {
        LoginError.EMPTY_FIELDS -> R.string.login_error_empty_fields
        LoginError.AUTHENTICATION -> R.string.login_error_authentication
        LoginError.NETWORK -> R.string.login_error_network
        LoginError.SERVER -> R.string.login_error_server
        LoginError.PARTIAL_SYNC -> R.string.login_error_partial_sync
        LoginError.NO_CALENDARS -> R.string.login_error_no_calendars
        null -> null
    }
    messageId?.let { Text(stringResource(it)) }
    if (uiState.error == LoginError.PARTIAL_SYNC && uiState.destination == LoginDestination.CALENDAR) {
        Button(onClick = onViewCalendars) {
            Text(stringResource(R.string.login_view_calendars))
        }
    }
}

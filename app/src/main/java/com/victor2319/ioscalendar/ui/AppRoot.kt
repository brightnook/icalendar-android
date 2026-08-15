package com.victor2319.ioscalendar.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.victor2319.ioscalendar.AppContainer
import com.victor2319.ioscalendar.R
import com.victor2319.ioscalendar.auth.CalDavLoginCoordinator
import com.victor2319.ioscalendar.ui.login.LoginDestination
import com.victor2319.ioscalendar.ui.login.LoginScreen
import com.victor2319.ioscalendar.ui.login.LoginViewModel

@Composable
fun AppRoot(container: AppContainer) {
    val context = LocalContext.current
    val loginViewModel = remember(container) {
        LoginViewModel(
            loginCoordinator = CalDavLoginCoordinator(container.calDavGateway),
            credentialsStore = container.credentialsStore,
            syncCoordinator = container.syncCoordinator,
        )
    }
    val uiState by loginViewModel.uiState.collectAsState()

    if (uiState.destination == LoginDestination.CALENDAR) {
        AuthenticatedPlaceholder()
    } else {
        LoginScreen(
            uiState = uiState,
            onConnect = loginViewModel::connect,
            onOpenUri = { url -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
        )
    }
}

@Composable
private fun AuthenticatedPlaceholder() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.login_connected_title))
        Text(stringResource(R.string.login_connected_placeholder))
    }
}

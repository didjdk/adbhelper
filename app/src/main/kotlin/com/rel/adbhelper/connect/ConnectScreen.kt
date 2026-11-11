package com.rel.adbhelper.connect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.rel.adbhelper.core.adb.AdbClient
import com.rel.adbhelper.core.adb.NetworkAdbTransport
import com.rel.adbhelper.core.session.DeviceSession
import com.rel.adbhelper.ui.components.SignalMark

@Composable
fun ConnectScreen(
    modifier: Modifier = Modifier,
    onConnected: () -> Unit = {},
) {
    val context = LocalContext.current
    val viewModel = remember {
        ConnectViewModel(connect = { host, port ->
            AdbClient(NetworkAdbTransport(context.applicationContext, host, port))
                .shell("echo connected")
        })
    }
    val state by viewModel.state.collectAsState()
    var hostPortText by rememberSaveable { mutableStateOf("") }

    // The device hub owns the connected target from here on; detach on leaving it is
    // handled inside DeviceScreen's lifecycle (DisposableEffect) rather than here.
    androidx.compose.runtime.LaunchedEffect(state) {
        if (state is ConnectUiState.Connected) {
            val target = (state as ConnectUiState.Connected).target
            val (host, port) = parseHostPort(target)!!
            DeviceSession.attach(target, AdbClient(NetworkAdbTransport(context.applicationContext, host, port)))
            onConnected()
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        SignalMark(modifier = Modifier.padding(top = 48.dp, bottom = 32.dp))

        OutlinedTextField(
            value = hostPortText,
            onValueChange = { hostPortText = it },
            label = { Text("Device address") },
            placeholder = { Text("192.168.1.5:5555") },
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = { viewModel.onConnectClicked(hostPortText) },
            // No Disconnect action exists yet, so once Connected there's nothing a re-click
            // could do — matches onConnectClicked's own Idle/Failed-only guard.
            enabled = parseHostPort(hostPortText) != null &&
                state !is ConnectUiState.Connecting &&
                state !is ConnectUiState.Connected,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Text("Connect")
        }

        val statusColor = if (state is ConnectUiState.Failed) {
            MaterialTheme.colorScheme.tertiary
        } else {
            MaterialTheme.colorScheme.onBackground
        }
        Text(
            text = statusText(state),
            color = statusColor,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

private fun statusText(state: ConnectUiState): String = when (state) {
    is ConnectUiState.Idle -> ""
    is ConnectUiState.Connecting -> "Connecting…"
    is ConnectUiState.Connected -> "Connected to ${state.target}"
    is ConnectUiState.Failed -> state.reason
}

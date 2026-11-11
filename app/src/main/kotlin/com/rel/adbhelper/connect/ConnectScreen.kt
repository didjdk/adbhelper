package com.rel.adbhelper.connect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rel.adbhelper.core.adb.AdbClient
import com.rel.adbhelper.core.adb.NetworkAdbTransport

@Composable
fun ConnectScreen(modifier: Modifier = Modifier, onOpenSettings: () -> Unit = {}) {
    val context = LocalContext.current
    val viewModel = remember {
        ConnectViewModel(connect = { host, port ->
            AdbClient(NetworkAdbTransport(context.applicationContext, host, port))
                .shell("echo connected")
        })
    }
    val state by viewModel.state.collectAsState()
    var hostPortText by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OutlinedTextField(
            value = hostPortText,
            onValueChange = {
                hostPortText = it
                viewModel.onHostPortChanged(it)
            },
            label = { Text("Device address (host:port)") },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = viewModel::onConnectClicked,
            enabled = hostPortText.isNotBlank() && state !is ConnectUiState.Connecting,
        ) {
            Text("Connect")
        }
        Text(text = statusText(state))
        androidx.compose.material3.TextButton(onClick = onOpenSettings) {
            Text("Settings")
        }
    }
}

private fun statusText(state: ConnectUiState): String = when (state) {
    is ConnectUiState.Idle -> ""
    is ConnectUiState.Connecting -> "Connecting…"
    is ConnectUiState.Connected -> "Connected to ${state.target}"
    is ConnectUiState.Failed -> state.reason
}

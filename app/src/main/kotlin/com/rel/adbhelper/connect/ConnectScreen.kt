package com.rel.adbhelper.connect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.rel.adbhelper.core.adb.AdbClient
import com.rel.adbhelper.core.adb.AdbKeyConnectionManager
import com.rel.adbhelper.core.adb.NetworkAdbTransport
import com.rel.adbhelper.core.net.LanScanner
import com.rel.adbhelper.core.recents.RecentConnections
import com.rel.adbhelper.core.session.DeviceSession
import com.rel.adbhelper.ui.components.SignalMark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

@Composable
fun ConnectScreen(
    modifier: Modifier = Modifier,
    onConnected: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val viewModel = remember {
        ConnectViewModel(connect = { host, port ->
            AdbClient(NetworkAdbTransport(context.applicationContext, host, port))
                .shell("echo connected")
        })
    }
    val recents = remember { RecentConnections(context.applicationContext) }
    val state by viewModel.state.collectAsState()
    val recentEntries by recents.entries.collectAsState(initial = emptyList())
    var hostPortText by rememberSaveable { mutableStateOf("") }
    var scanning by remember { mutableStateOf(false) }
    var scanResults by remember { mutableStateOf<List<String>?>(null) }
    var pairDialog by remember { mutableStateOf(false) }

    // The device hub owns the connected target from here on; detach on leaving it is
    // handled inside DeviceScreen's lifecycle (DisposableEffect) rather than here.
    LaunchedEffect(state) {
        val connected = state as? ConnectUiState.Connected
        if (connected != null) {
            val (host, port) = parseHostPort(connected.target)!!
            DeviceSession.attach(
                connected.target,
                AdbClient(NetworkAdbTransport(context.applicationContext, host, port)),
            )
            recents.add(connected.target)
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

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(onClick = {
                scope.launch {
                    scanning = true
                    scanResults = null
                    val subnet = localSubnet(context.applicationContext)
                    scanResults = if (subnet == null) emptyList() else LanScanner.scan(subnet.first, subnet.second)
                    scanning = false
                }
            }) { Text("Scan") }
            TextButton(onClick = { pairDialog = true }) { Text("Pair") }
        }

        val scan = scanResults
        when {
            scanning -> Text(
                "Scanning for devices on the local network…",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
            scan != null && scan.isEmpty() -> Text(
                "No adb devices found",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
            scan != null -> Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text("Found devices", style = MaterialTheme.typography.titleSmall)
                scan.forEach { target ->
                    TextButton(onClick = {
                        hostPortText = target
                        viewModel.onConnectClicked(target)
                    }) {
                        Text(target, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
                    }
                }
            }
        }

        if (recentEntries.isNotEmpty()) {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Text("Recent", style = MaterialTheme.typography.titleSmall)
                recentEntries.forEach { target ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = {
                            hostPortText = target
                            viewModel.onConnectClicked(target)
                        }, modifier = Modifier.weight(1f)) {
                            Text(
                                target,
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            )
                        }
                        TextButton(onClick = { scope.launch { recents.remove(target) } }) { Text("Remove") }
                    }
                }
            }
        }
    }

    if (pairDialog) {
        PairDialog(onDismiss = { pairDialog = false })
    }
}

@Composable
private fun PairDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var address by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf("") }
    var pairing by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pair with wireless debugging") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Pairing address (ip:port)") },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.filter { c -> c.isDigit() }.take(6) },
                    label = { Text("6-digit pairing code") },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    singleLine = true,
                )
                if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(
                enabled = parseHostPort(address) != null && code.length == 6 && !pairing,
                onClick = {
                    val (host, port) = parseHostPort(address)!!
                    pairing = true
                    status = "Pairing…"
                    scope.launch {
                        status = try {
                            withContext(Dispatchers.IO) {
                                AdbKeyConnectionManager.getInstance(context.applicationContext)
                                    .pair(host, port, code)
                            }
                            "Pairing succeeded"
                        } catch (t: Throwable) {
                            "Pairing failed"
                        }
                        pairing = false
                    }
                },
            ) { Text("Pair") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun statusText(state: ConnectUiState): String = when (state) {
    is ConnectUiState.Idle -> ""
    is ConnectUiState.Connecting -> "Connecting…"
    is ConnectUiState.Connected -> "Connected to ${state.target}"
    is ConnectUiState.Failed -> state.reason
}

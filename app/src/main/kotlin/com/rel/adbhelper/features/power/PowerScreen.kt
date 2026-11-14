package com.rel.adbhelper.features.power

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rel.adbhelper.core.session.DeviceSession

@Composable
fun PowerScreen(modifier: Modifier = Modifier) {
    val viewModel = remember { PowerViewModel(requireNotNull(DeviceSession.client)) }
    val state by viewModel.state.collectAsState()
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }

    LaunchedEffect(Unit) { viewModel.load() }

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        state.message.takeIf { it.isNotEmpty() }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall)
        }

        Text("Advanced reboot", style = MaterialTheme.typography.titleLarge)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            Button(onClick = { confirm = "Reboot the device now?" to { viewModel.reboot() } }) { Text("Reboot") }
            Button(onClick = { confirm = "Reboot to recovery?" to { viewModel.rebootRecovery() } }) { Text("Recovery") }
            Button(onClick = { confirm = "Reboot to bootloader?" to { viewModel.rebootBootloader() } }) { Text("Bootloader") }
        }

        Text("Display DPI", style = MaterialTheme.typography.titleLarge)
        Text(
            "Current: ${state.currentDensity ?: "unknown"}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = state.densityInput,
            onValueChange = viewModel::setDensityInput,
            label = { Text("New DPI") },
            singleLine = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                confirm = "Changing the display DPI may make the UI render incorrectly." to { viewModel.applyDensity() }
            }) { Text("Apply") }
            TextButton(onClick = { viewModel.resetDensity() }) { Text("Reset to system default") }
        }

        Text("Wireless debugging (on this target)", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.enableWirelessDebugging() }) { Text("Turn on") }
            Button(onClick = {
                confirm = "Turning off wireless debugging will disconnect — turn it off?" to {
                    viewModel.disableWirelessDebugging()
                }
            }) { Text("Turn off") }
        }
    }

    confirm?.let { (message, action) ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Confirm") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = {
                    action()
                    confirm = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}

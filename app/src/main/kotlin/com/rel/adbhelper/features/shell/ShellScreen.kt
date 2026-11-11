package com.rel.adbhelper.features.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import com.rel.adbhelper.core.session.DeviceSession

@Composable
fun ShellScreen(modifier: Modifier = Modifier) {
    val viewModel = remember { ShellViewModel(requireNotNull(DeviceSession.client)) }
    val state by viewModel.state.collectAsState()
    var command by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) { viewModel.start() }
    LaunchedEffect(state.output.size) {
        if (state.output.isNotEmpty()) listState.scrollToItem(state.output.size - 1)
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { viewModel.clear() }) { Text("Clear output") }
            TextButton(onClick = { viewModel.restart() }) { Text("Restart terminal") }
            TextButton(onClick = { viewModel.restartAdbServer() }) { Text("Restart adb server") }
        }
        state.message.takeIf { it.isNotEmpty() }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).weight(1f),
        ) {
            items(state.output.size) { index ->
                Text(
                    state.output[index],
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            OutlinedTextField(
                value = command,
                onValueChange = { command = it },
                placeholder = { Text("shell command") },
                // Command input is device data: monospace.
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(0.75f),
            )
            TextButton(
                onClick = {
                    viewModel.send(command)
                    command = ""
                },
                enabled = state.connected && command.isNotBlank(),
            ) { Text("Run") }
        }
    }
}

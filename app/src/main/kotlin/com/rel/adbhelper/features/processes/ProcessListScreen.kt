package com.rel.adbhelper.features.processes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import com.rel.adbhelper.core.proc.ProcessEntry
import com.rel.adbhelper.core.session.DeviceSession

@Composable
fun ProcessListScreen(modifier: Modifier = Modifier) {
    val viewModel = remember { ProcessListViewModel(requireNotNull(DeviceSession.client)) }
    val state by viewModel.state.collectAsState()
    var pendingKill by remember { mutableStateOf<ProcessEntry?>(null) }

    LaunchedEffect(Unit) { viewModel.load() }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        state.message.takeIf { it.isNotEmpty() }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        }
        if (state.loading) {
            Text("Loading…", modifier = Modifier.padding(top = 16.dp))
        }
        LazyColumn {
            items(state.processes, key = { "${it.pid}-${it.name}" }) { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(entry.name, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
                        Text(
                            "pid ${entry.pid} · ${entry.user}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { pendingKill = entry }) { Text("Kill") }
                }
            }
        }
    }

    pendingKill?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingKill = null },
            title = { Text("Kill process?") },
            text = {
                Text(
                    if (entry.isSystem) {
                        "This is a system process — are you sure you want to kill it?"
                    } else {
                        "Kill ${entry.name}?"
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.kill(entry)
                    pendingKill = null
                }) { Text("Kill") }
            },
            dismissButton = { TextButton(onClick = { pendingKill = null }) { Text("Cancel") } },
        )
    }
}

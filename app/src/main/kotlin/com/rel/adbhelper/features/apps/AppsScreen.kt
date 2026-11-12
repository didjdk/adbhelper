package com.rel.adbhelper.features.apps

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.rel.adbhelper.core.apps.AppEntry
import com.rel.adbhelper.core.session.DeviceSession

@Composable
fun AppsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val viewModel = remember { AppsViewModel(requireNotNull(DeviceSession.client)) }
    val state by viewModel.state.collectAsState()
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }

    LaunchedEffect(Unit) { viewModel.load() }

    val installLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null) {
                val name = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
                } ?: "app.apk"
                viewModel.install(bytes, name)
            }
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            FilterChip(
                selected = state.filter == AppsViewModel.Filter.ALL,
                onClick = { viewModel.setFilter(AppsViewModel.Filter.ALL) },
                label = { Text("All") },
            )
            FilterChip(
                selected = state.filter == AppsViewModel.Filter.THIRD_PARTY,
                onClick = { viewModel.setFilter(AppsViewModel.Filter.THIRD_PARTY) },
                label = { Text("Third-party") },
            )
            FilterChip(
                selected = state.filter == AppsViewModel.Filter.SYSTEM,
                onClick = { viewModel.setFilter(AppsViewModel.Filter.SYSTEM) },
                label = { Text("System") },
            )
            Button(onClick = { installLauncher.launch(arrayOf("application/vnd.android.package-archive")) }) {
                Text("Install APK")
            }
        }
        state.busy.takeIf { it.isNotEmpty() }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 4.dp))
        }
        state.message.takeIf { it.isNotEmpty() }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 4.dp))
        }
        if (state.loading) Text("Loading…")
        // Filtered from `state` (the collectAsState snapshot) rather than calling back into
        // viewModel.visibleApps(), which reads _state.value directly. That read isn't observed
        // by Compose's snapshot system, so the Compose compiler's lambda-skipping optimization
        // saw this content lambda as capturing nothing but the stable `viewModel` reference and
        // skipped recomposing it on every state change after the first — live-reproduced: the
        // list rendered empty forever even after diagnostics proved the ViewModel's state held
        // the correct, fully-loaded app list.
        val visibleApps = when (state.filter) {
            AppsViewModel.Filter.ALL -> state.apps
            AppsViewModel.Filter.THIRD_PARTY -> state.apps.filter { !it.isSystem }
            AppsViewModel.Filter.SYSTEM -> state.apps.filter { it.isSystem }
        }
        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            items(visibleApps, key = { it.pkg }) { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            entry.pkg,
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        )
                        Text(
                            listOfNotNull(
                                if (entry.isSystem) "system" else "third-party",
                                if (!entry.isEnabled) "disabled" else null,
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { viewModel.details(entry) }) { Text("Manage") }
                }
            }
        }
    }

    state.details?.let { (entry, details) ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissDetails() },
            title = { Text(entry.pkg) },
            text = {
                Text(
                    listOf(
                        "Version: ${details.versionName}",
                        "minSdk: ${details.minSdk}",
                        "targetSdk: ${details.targetSdk}",
                        "Installed: ${details.firstInstallTime}",
                        "Updated: ${details.lastUpdateTime}",
                        "Permissions: ${details.requestedPermissions.joinToString(", ") { it.substringAfterLast('.') }}",
                    ).joinToString("\n"),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = {
                Column {
                    TextButton(onClick = { viewModel.launch(entry) }) { Text("Launch") }
                    TextButton(onClick = {
                        if (entry.isSystem) {
                            confirm = "This is a system app — disabling it may cause problems." to {
                                viewModel.setEnabled(entry, !entry.isEnabled)
                            }
                        } else {
                            viewModel.setEnabled(entry, !entry.isEnabled)
                        }
                        viewModel.dismissDetails()
                    }) { Text(if (entry.isEnabled) "Disable" else "Enable") }
                    TextButton(onClick = {
                        viewModel.clearCache(entry)
                        viewModel.dismissDetails()
                    }) { Text("Clear cache") }
                    TextButton(onClick = {
                        confirm = "App data cannot be recovered once cleared." to {
                            viewModel.clearData(entry)
                        }
                        viewModel.dismissDetails()
                    }) { Text("Clear data") }
                    TextButton(onClick = {
                        confirm = "Uninstall this app?" to { viewModel.uninstall(entry) }
                        viewModel.dismissDetails()
                    }) { Text("Uninstall") }
                    TextButton(onClick = { viewModel.activate(entry); viewModel.dismissDetails() }) { Text("Activate (needs root)") }
                }
            },
            dismissButton = { TextButton(onClick = { viewModel.dismissDetails() }) { Text("Close") } },
        )
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

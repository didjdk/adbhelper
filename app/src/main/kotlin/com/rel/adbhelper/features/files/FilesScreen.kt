package com.rel.adbhelper.features.files

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.rel.adbhelper.core.files.FileEntry

@Composable
fun FilesScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val viewModel = remember { FilesViewModel(requireNotNull(com.rel.adbhelper.core.session.DeviceSession.client)) }
    val state by viewModel.state.collectAsState()
    var dialog by remember { mutableStateOf<FilesDialog?>(null) }

    LaunchedEffect(Unit) { viewModel.open(state.path) }

    val uploadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: android.net.Uri? ->
        if (uri != null) {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null) {
                val name = queryDisplayName(context, uri) ?: "upload.bin"
                viewModel.upload("${state.path.trimEnd('/')}/$name", bytes)
            }
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { viewModel.up() }) { Text("Up") }
            Text(
                state.path,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth(0.7f).horizontalScroll(rememberScrollState()),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Hidden", style = MaterialTheme.typography.bodySmall)
                Switch(checked = state.showHidden, onCheckedChange = { viewModel.toggleHidden() })
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { dialog = FilesDialog.CreateFolder }) { Text("New folder") }
            TextButton(onClick = { dialog = FilesDialog.CreateFile }) { Text("New file") }
            TextButton(onClick = {
                viewModel.paste()
            }, enabled = state.copiedFrom != null) { Text("Paste") }
            TextButton(onClick = { uploadLauncher.launch(arrayOf("*/*")) }) { Text("Upload") }
        }
        state.message.takeIf { it.isNotEmpty() }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 4.dp))
        }
        if (state.loading) Text("Loading…")
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(
                state.entries.filter { state.showHidden || !it.name.startsWith(".") },
                key = { it.path },
            ) { entry ->
                FileRow(
                    entry = entry,
                    onOpen = { viewModel.entryClick(entry) },
                    onCopy = { viewModel.copy(entry) },
                    onRename = { dialog = FilesDialog.Rename(entry) },
                    onDelete = { dialog = FilesDialog.Delete(entry) },
                    onDownload = { viewModel.download(entry, context) },
                )
            }
        }
    }

    when (val d = dialog) {
        is FilesDialog.CreateFolder -> TextPrompt(
            "Create folder", "Folder name",
            onDismiss = { dialog = null },
        ) { viewModel.createFolder(it); dialog = null }
        is FilesDialog.CreateFile -> TextPrompt(
            "Create file", "File name",
            onDismiss = { dialog = null },
        ) { viewModel.createFile(it); dialog = null }
        is FilesDialog.Rename -> TextPrompt(
            "Rename", "New name", initial = d.entry.name,
            onDismiss = { dialog = null },
        ) { viewModel.rename(d.entry, it); dialog = null }
        is FilesDialog.Delete -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Delete?") },
            text = { Text("Delete ${d.entry.name}? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(d.entry)
                    dialog = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
        )
        null -> {}
    }

    state.selectedForInfo?.let { entry ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissInfo() },
            title = { Text(entry.name) },
            text = {
                Text(
                    listOf(
                        "Path: ${entry.path}",
                        if (entry.isDir) "Type: directory" else "Type: file",
                        if (entry.isSymlink) "Symlink" else "",
                        "Size: ${entry.size} B",
                        "Owner: ${entry.owner}",
                        "Permissions: ${entry.perms}",
                        "Modified: ${entry.mtime}",
                    ).filter { it.isNotEmpty() }.joinToString("\n"),
                    fontFamily = FontFamily.Monospace,
                )
            },
            confirmButton = { TextButton(onClick = { viewModel.dismissInfo() }) { Text("Close") } },
        )
    }
}

/**
 * One file/folder row: tap opens it (directory → browse, file → info dialog, per
 * [FilesViewModel.entryClick]); everything else lives behind the overflow menu so the row stays
 * readable instead of a wall of text buttons at every width.
 */
@Composable
private fun FileRow(
    entry: FileEntry,
    onOpen: () -> Unit,
    onCopy: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDownload: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                // Directory names get a trailing slash — the same convention `ls` itself uses,
                // and the quickest "is this a folder" tell without a dedicated icon asset.
                if (entry.isDir) "${entry.name}/" else entry.name,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    color = if (entry.isDir) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                ),
            )
            Text(
                if (entry.isSymlink) "symlink" else "${entry.size} B · ${entry.perms}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More actions for ${entry.name}")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (!entry.isDir) {
                    DropdownMenuItem(text = { Text("Download") }, onClick = { menuOpen = false; onDownload() })
                }
                DropdownMenuItem(text = { Text("Copy") }, onClick = { menuOpen = false; onCopy() })
                DropdownMenuItem(text = { Text("Rename") }, onClick = { menuOpen = false; onRename() })
                DropdownMenuItem(text = { Text("Delete") }, onClick = { menuOpen = false; onDelete() })
            }
        }
    }
}

private sealed interface FilesDialog {
    data object CreateFolder : FilesDialog
    data object CreateFile : FilesDialog
    data class Rename(val entry: FileEntry) : FilesDialog
    data class Delete(val entry: FileEntry) : FilesDialog
}

/** Simple single-field prompt dialog shared by create/rename flows. */
@Composable
private fun TextPrompt(
    title: String,
    label: String,
    initial: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text(label) }, singleLine = true)
        },
        confirmButton = {
            TextButton(onClick = {
                if (text.isNotBlank()) onConfirm(text)
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun queryDisplayName(context: Context, uri: android.net.Uri): String? =
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
    }

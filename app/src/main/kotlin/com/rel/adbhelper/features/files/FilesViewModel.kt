package com.rel.adbhelper.features.files

import com.rel.adbhelper.core.adb.AdbClient
import com.rel.adbhelper.core.files.FileEntry
import com.rel.adbhelper.core.files.parseLsLa
import com.rel.adbhelper.core.shell.shellQuote
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FilesViewModel(
    private val client: AdbClient,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined),
) {
    data class State(
        val path: String = DEFAULT_ROOT,
        val entries: List<FileEntry> = emptyList(),
        val showHidden: Boolean = false,
        val loading: Boolean = false,
        val message: String = "",
        val copiedFrom: String? = null,
        val selectedForInfo: FileEntry? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun open(path: String) {
        scope.launch {
            _state.value = _state.value.copy(loading = true, message = "")
            try {
                // `ls -la <dir>` on a symlink lists the link itself, so always list with a
                // trailing slash to follow it (e.g. /sdcard -> /storage/self/primary).
                val dir = path.trimEnd('/')
                val raw = client.shell("ls -la ${shellQuote("$dir/")}").let { result ->
                    if (result is com.rel.adbhelper.core.adb.AdbResult.Success) result.output else null
                } ?: throw IllegalStateException("ls failed")
                _state.value = _state.value.copy(
                    path = dir,
                    entries = parseLsLa(raw, dir),
                    loading = false,
                )
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    loading = false,
                    message = "Failed to list ${path}: ${t.message ?: "unknown error"}",
                )
            }
        }
    }

    fun up() {
        val dir = _state.value.path.trimEnd('/')
        val parent = dir.substringBeforeLast('/', "")
        open(parent.ifEmpty { "/" })
    }

    fun toggleHidden() {
        _state.value = _state.value.copy(showHidden = !_state.value.showHidden)
    }

    fun entryClick(entry: FileEntry) {
        if (entry.isSymlink && !entry.isDir) {
            _state.value = _state.value.copy(selectedForInfo = entry)
        } else if (entry.isDir) {
            open(entry.path)
        } else {
            _state.value = _state.value.copy(selectedForInfo = entry)
        }
    }

    fun dismissInfo() {
        _state.value = _state.value.copy(selectedForInfo = null)
    }

    fun createFolder(name: String) = runCommand("mkdir -p ${shellQuote(join(name))}", "Folder created")
    fun createFile(name: String) = runCommand("touch ${shellQuote(join(name))}", "File created")
    fun rename(entry: FileEntry, newName: String) =
        runCommand("mv ${shellQuote(entry.path)} ${shellQuote(join(newName))}", "Renamed")
    fun delete(entry: FileEntry) =
        runCommand("rm -rf ${shellQuote(entry.path)}", "Deleted")
    fun copy(entry: FileEntry) {
        _state.value = _state.value.copy(copiedFrom = entry.path, message = "Copied ${entry.name} — open the destination and paste")
    }
    fun paste() {
        val from = _state.value.copiedFrom ?: return
        runCommand("cp -r ${shellQuote(from)} ${shellQuote(_state.value.path)}", "Pasted")
        _state.value = _state.value.copy(copiedFrom = null)
    }

    fun upload(path: String, bytes: ByteArray) {
        scope.launch {
            try {
                client.push(path, bytes)
                _state.value = _state.value.copy(message = "Uploaded ${path.substringAfterLast('/')}")
                open(_state.value.path)
            } catch (t: Throwable) {
                _state.value = _state.value.copy(message = "Upload failed")
            }
        }
    }

    fun download(entry: FileEntry, saveContext: android.content.Context) {
        scope.launch {
            try {
                val bytes = client.pull(entry.path)
                val dir = saveContext.getExternalFilesDir("Downloads") ?: saveContext.filesDir
                val file = java.io.File(dir, entry.name)
                file.writeBytes(bytes)
                _state.value = _state.value.copy(message = "Downloaded to ${file.absolutePath}")
            } catch (t: Throwable) {
                _state.value = _state.value.copy(message = "Download failed")
            }
        }
    }

    private fun join(name: String) = "${_state.value.path.trimEnd('/')}/$name"

    private fun runCommand(command: String, successMessage: String) {
        scope.launch {
            try {
                val result = client.shell(command)
                if (result is com.rel.adbhelper.core.adb.AdbResult.Success) {
                    _state.value = _state.value.copy(message = successMessage)
                } else {
                    _state.value = _state.value.copy(message = "Command failed")
                }
            } catch (t: Throwable) {
                _state.value = _state.value.copy(message = "Connection to the device was lost")
            }
            open(_state.value.path)
        }
    }

    companion object {
        const val DEFAULT_ROOT = "/sdcard"
    }
}

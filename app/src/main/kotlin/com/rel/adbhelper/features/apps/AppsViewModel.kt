package com.rel.adbhelper.features.apps

import com.rel.adbhelper.core.adb.AdbClient
import com.rel.adbhelper.core.adb.AdbResult
import com.rel.adbhelper.core.apps.AppDetails
import com.rel.adbhelper.core.apps.AppEntry
import com.rel.adbhelper.core.apps.classifyApps
import com.rel.adbhelper.core.apps.parseDumpsysPackage
import com.rel.adbhelper.core.apps.parsePmList
import com.rel.adbhelper.core.shell.shellQuote
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AppsViewModel(
    private val client: AdbClient,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined),
) {
    enum class Filter { ALL, THIRD_PARTY, SYSTEM }

    data class State(
        val loading: Boolean = true,
        val apps: List<AppEntry> = emptyList(),
        val filter: Filter = Filter.ALL,
        val message: String = "",
        val details: Pair<AppEntry, AppDetails>? = null,
        val busy: String = "",
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun load() {
        scope.launch {
            _state.value = _state.value.copy(loading = true)
            try {
                // Sequential, not concurrent — live-reproduced: three shell() calls fired at once
                // over this app's one shared multiplexed connection intermittently force-closed
                // the two larger reads (system/disabled, each listing 200+ packages) while the
                // tiny third-party read finished first, surfacing as a silent empty app list (no
                // error shown, since the failure was swallowed by .success().orEmpty()). Same
                // underlying class of bug streamOpenMutex's doc already covers for concurrent
                // opens — here it's concurrent reads racing on one connection instead. Each read
                // is still individually fast; the cost of going sequential is one extra round
                // trip, not three stacked full timeouts.
                val thirdParty = parsePmList(client.shell("pm list packages -3").success().orEmpty())
                val system = parsePmList(client.shell("pm list packages -s").success().orEmpty())
                val disabled = parsePmList(client.shell("pm list packages -d").success().orEmpty()).toSet()
                val apps = (thirdParty + system)
                    .map { AppEntry(pkg = it, isSystem = it !in thirdParty.toSet(), isEnabled = it !in disabled) }
                    .sortedBy { it.pkg }
                _state.value = _state.value.copy(loading = false, apps = apps)
            } catch (t: Throwable) {
                _state.value = _state.value.copy(loading = false, message = "Connection to the device was lost")
            }
        }
    }

    fun setFilter(filter: Filter) {
        _state.value = _state.value.copy(filter = filter)
    }

    fun visibleApps(): List<AppEntry> = when (_state.value.filter) {
        Filter.ALL -> _state.value.apps
        Filter.THIRD_PARTY -> _state.value.apps.filter { !it.isSystem }
        Filter.SYSTEM -> _state.value.apps.filter { it.isSystem }
    }

    fun details(entry: AppEntry) {
        scope.launch {
            try {
                val raw = client.shell("dumpsys package ${shellQuote(entry.pkg)}").success().orEmpty()
                _state.value = _state.value.copy(details = entry to parseDumpsysPackage(raw))
            } catch (t: Throwable) {
                _state.value = _state.value.copy(message = "Failed to read app details")
            }
        }
    }

    fun dismissDetails() {
        _state.value = _state.value.copy(details = null)
    }

    fun setEnabled(entry: AppEntry, enabled: Boolean) {
        val command = if (enabled) "pm enable ${shellQuote(entry.pkg)}" else "pm disable-user --user 0 ${shellQuote(entry.pkg)}"
        run(command, if (enabled) "Enabled ${entry.pkg}" else "Disabled ${entry.pkg}")
    }

    fun uninstall(entry: AppEntry) = run("pm uninstall --user 0 ${shellQuote(entry.pkg)}", "Uninstalled ${entry.pkg}")
    fun clearData(entry: AppEntry) = run("pm clear ${shellQuote(entry.pkg)}", "Data cleared")
    fun clearCache(entry: AppEntry) = run("pm clear --cache-only ${shellQuote(entry.pkg)}", "Cache cleared")
    fun launch(entry: AppEntry) = run("monkey -p ${shellQuote(entry.pkg)} -c android.intent.category.LAUNCHER 1", "Launched")

    /** Root-gated "activate" flow: grant the app shell-level secure-settings permissions. */
    fun activate(entry: AppEntry) {
        scope.launch {
            val root = client.shell("su -c id") is AdbResult.Success
            if (!root) {
                _state.value = _state.value.copy(
                    message = "This action needs root access on this device, which isn't available.",
                )
            } else {
                val result = client.shell("su -c pm grant ${shellQuote(entry.pkg)} android.permission.WRITE_SECURE_SETTINGS")
                _state.value = _state.value.copy(
                    message = if (result is AdbResult.Success) "Activated ${entry.pkg}" else "Activation failed",
                )
            }
        }
    }

    fun install(bytes: ByteArray, name: String) {
        scope.launch {
            _state.value = _state.value.copy(busy = "Installing $name…")
            try {
                val tempPath = "/data/local/tmp/adbhelper-install.apk"
                client.push(tempPath, bytes)
                val result = client.shell("pm install -r ${shellQuote(tempPath)}")
                client.shell("rm ${shellQuote(tempPath)}")
                _state.value = _state.value.copy(
                    busy = if (result is AdbResult.Success) "Installed $name" else "Install failed",
                )
                load()
            } catch (t: Throwable) {
                _state.value = _state.value.copy(busy = "Install failed")
            }
        }
    }

    private fun run(command: String, successMessage: String) {
        scope.launch {
            try {
                val result = client.shell(command)
                _state.value = _state.value.copy(
                    message = if (result is AdbResult.Success) successMessage else "Command failed",
                )
            } catch (t: Throwable) {
                _state.value = _state.value.copy(message = "Connection to the device was lost")
            }
            load()
        }
    }

    private fun AdbResult.success(): String? = (this as? AdbResult.Success)?.output
}

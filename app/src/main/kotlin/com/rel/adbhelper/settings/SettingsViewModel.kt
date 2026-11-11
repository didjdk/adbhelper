package com.rel.adbhelper.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: SettingsRepository,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined),
) {
    val settings: StateFlow<AppSettings> = repository.settings.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = AppSettings(),
    )

    fun onShowHiddenFilesByDefaultChanged(value: Boolean) {
        scope.launch { repository.update(settings.value.copy(showHiddenFilesByDefault = value)) }
    }

    fun onScreenshotDirectoryChanged(value: String) {
        scope.launch { repository.update(settings.value.copy(screenshotDirectory = value)) }
    }

    fun onDefaultPostConnectScreenChanged(value: String) {
        scope.launch { repository.update(settings.value.copy(defaultPostConnectScreen = value)) }
    }
}

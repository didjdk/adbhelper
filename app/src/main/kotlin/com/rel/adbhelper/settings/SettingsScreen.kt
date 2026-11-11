package com.rel.adbhelper.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val viewModel = remember {
        SettingsViewModel(SettingsRepository(context.applicationContext.settingsDataStore))
    }
    val settings by viewModel.settings.collectAsState()

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(text = "Show hidden files by default", modifier = Modifier.fillMaxWidth(0.8f))
            Switch(
                checked = settings.showHiddenFilesByDefault,
                onCheckedChange = viewModel::onShowHiddenFilesByDefaultChanged,
            )
        }
        OutlinedTextField(
            value = settings.screenshotDirectory,
            onValueChange = viewModel::onScreenshotDirectoryChanged,
            label = { Text("Screenshot save directory") },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

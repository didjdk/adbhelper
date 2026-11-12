package com.rel.adbhelper.device

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rel.adbhelper.core.session.DeviceSession
import com.rel.adbhelper.features.apps.AppsScreen
import com.rel.adbhelper.features.deviceinfo.InfoScreen
import com.rel.adbhelper.features.files.FilesScreen
import com.rel.adbhelper.features.livescreen.LiveScreenScreen
import com.rel.adbhelper.features.power.PowerScreen
import com.rel.adbhelper.features.processes.ProcessListScreen
import com.rel.adbhelper.features.shell.ShellScreen

/** Hub screen shown once a target is connected; one tab per feature. */
@Composable
fun DeviceScreen(modifier: Modifier = Modifier) {
    val tabs = listOf("Info", "Apps", "Files", "Shell", "Power", "Monitor")
    var selected by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        onDispose { DeviceSession.detach() }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selected) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selected == index,
                    onClick = { selected = index },
                    text = { Text(title) },
                )
            }
        }
        when (selected) {
            0 -> InfoScreen()
            1 -> AppsScreen()
            2 -> FilesScreen()
            3 -> ShellScreen()
            4 -> PowerScreen()
            5 -> LiveScreenScreen()
        }
    }
}

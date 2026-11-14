package com.rel.adbhelper.device

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rel.adbhelper.core.session.DeviceSession
import com.rel.adbhelper.features.apps.AppsScreen
import com.rel.adbhelper.features.deviceinfo.InfoScreen
import com.rel.adbhelper.features.files.FilesScreen
import com.rel.adbhelper.features.livescreen.LiveScreenScreen
import com.rel.adbhelper.features.power.PowerScreen
import com.rel.adbhelper.features.processes.ProcessListScreen
import com.rel.adbhelper.features.shell.ShellScreen

private val TAB_TITLES = listOf("Info", "Apps", "Files", "Shell", "Power", "Monitor", "Mirror")

/** Hub screen shown once a target is connected; one tab per feature.
 *
 * [ScrollableTabRow], not the fixed [androidx.compose.material3.TabRow]: a fixed tab row divides
 * the screen width evenly across all tabs, and seven labels ("Monitor", "Mirror", …) don't fit
 * un-clipped at that width on a phone-width screen — this scrolls instead of squeezing/clipping
 * labels.
 */
@Composable
fun DeviceScreen(modifier: Modifier = Modifier) {
    var selected by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        onDispose { DeviceSession.detach() }
    }

    Column(modifier = modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = selected, edgePadding = 16.dp) {
            TAB_TITLES.forEachIndexed { index, title ->
                Tab(
                    selected = selected == index,
                    onClick = { selected = index },
                    text = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
            6 -> MirrorScreen()
        }
    }
}

/**
 * scrcpy-based remote control/mirroring is unbuilt: it needs fetching `scrcpy-server.jar`,
 * pushing it to the target, an `app_process`-started server, a `MediaCodec` H.264 decoder, and a
 * control-message protocol — out of scope for this pass (user decision: stub this visibly rather
 * than build it or omit the tab).
 */
@Composable
private fun MirrorScreen(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            "Screen mirroring isn't implemented yet. Use the Monitor tab for screenshots instead.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

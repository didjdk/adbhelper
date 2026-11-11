package com.rel.adbhelper.device

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Hub screen shown once a target is connected; one tab per feature. */
@Composable
fun DeviceScreen(modifier: Modifier = Modifier) {
    val tabs = listOf("Info", "Apps", "Files", "Shell", "Power")
    var selected by remember { mutableIntStateOf(0) }

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
        // Tab content is filled in by each feature task; this placeholder only holds the
        // scaffolding until then.
        Text(
            text = "Connected to ${com.rel.adbhelper.core.session.DeviceSession.target}",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(24.dp),
        )
    }
}

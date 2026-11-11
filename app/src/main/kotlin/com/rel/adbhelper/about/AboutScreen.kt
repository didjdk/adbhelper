package com.rel.adbhelper.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rel.adbhelper.BuildConfig

@Composable
fun AboutScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = "ADB Helper")
        Text(text = "Version ${BuildConfig.VERSION_NAME}")
        // Populated once the real bundled/third-party component set (the ADB client
        // library, BouncyCastle, fastboot binary, scrcpy-server) is pinned in later plans.
        Text(text = "Open-source licenses: to be listed once dependencies are finalized.")
    }
}

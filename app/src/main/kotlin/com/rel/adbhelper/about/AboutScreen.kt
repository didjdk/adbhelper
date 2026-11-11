package com.rel.adbhelper.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.rel.adbhelper.BuildConfig
import com.rel.adbhelper.ui.components.ScreenHeader

@Composable
fun AboutScreen(modifier: Modifier = Modifier, onBack: () -> Unit = {}) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ScreenHeader(title = "About", onBack = onBack)

        Text(text = "ADB Helper", style = MaterialTheme.typography.headlineSmall)
        // The version string is data about a build — set it in monospace per the theme's rule.
        Text(
            text = "Version ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Populated once the real bundled/third-party component set (the ADB client
        // library, BouncyCastle, fastboot binary, scrcpy-server) is pinned in later plans.
        Text(
            text = "Open-source licenses: to be listed once dependencies are finalized.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

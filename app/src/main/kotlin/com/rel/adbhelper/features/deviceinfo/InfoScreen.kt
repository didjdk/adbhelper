package com.rel.adbhelper.features.deviceinfo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.rel.adbhelper.core.device.DeviceInfo
import com.rel.adbhelper.core.session.DeviceSession

private val DeviceInfo.rows: List<Pair<String, String>>
    get() = listOf(
        "Model" to model,
        "Manufacturer" to manufacturer,
        "Serial" to serial,
        "Device" to device,
        "CPU" to cpuAbi,
        "Cores" to cores.toString(),
        "Android" to androidVersion,
        "SDK" to sdkLevel,
        "Security patch" to securityPatch,
        "Battery" to listOfNotNull(
            if (batteryLevel > 0) "$batteryLevel%" else null,
            batteryTemperatureC.takeIf { it.isNotEmpty() },
            batteryState,
        ).joinToString(", "),
        "Storage" to if (storageTotalKb > 0) {
            "${usedGb()} GB used of ${totalGb()} GB"
        } else "",
        "Screen" to screenResolution,
        "Density" to screenDensity.toString(),
        "Wi-Fi IP" to wifiIp,
    )

private fun DeviceInfo.usedGb() = storageUsedKb / 1024 / 1024
private fun DeviceInfo.totalGb() = storageTotalKb / 1024 / 1024

@Composable
fun InfoScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val viewModel = remember {
        DeviceInfoViewModel(requireNotNull(DeviceSession.client))
    }
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) { viewModel.load() }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { viewModel.screenshot(context) }) { Text("Screenshot") }
            }
        }
        state.message.takeIf { it.isNotEmpty() }?.let { message ->
            item {
                Text(message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }
        }
        if (state.loading) {
            item { Text("Loading…", modifier = Modifier.padding(top = 16.dp)) }
        }
        items(state.info?.rows ?: emptyList()) { (label, value) ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(label, modifier = Modifier.fillMaxWidth(0.35f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
            }
        }
    }
}

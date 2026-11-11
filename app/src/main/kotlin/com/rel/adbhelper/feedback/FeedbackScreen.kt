package com.rel.adbhelper.feedback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp

private const val ISSUES_URL = "https://github.com/mon231/com.rel.adbhelper/issues"

@Composable
fun FeedbackScreen(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = "Found a bug or have a suggestion?")
        androidx.compose.material3.TextButton(onClick = { uriHandler.openUri(ISSUES_URL) }) {
            Text(text = "Open an issue on GitHub: $ISSUES_URL")
        }
    }
}

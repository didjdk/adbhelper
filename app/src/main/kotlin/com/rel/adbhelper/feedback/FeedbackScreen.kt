package com.rel.adbhelper.feedback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private const val ISSUES_URL = "https://github.com/" // left for the real repo path to fill in

@Composable
fun FeedbackScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = "Found a bug or have a suggestion?")
        Text(text = "Open an issue on this project's GitHub repository: $ISSUES_URL")
    }
}

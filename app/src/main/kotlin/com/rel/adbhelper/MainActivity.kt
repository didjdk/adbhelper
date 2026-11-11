package com.rel.adbhelper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.rel.adbhelper.navigation.AppNavHost

/**
 * The app's single Activity. Hosts the Compose UI via [AppNavHost].
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                // targetSdk 36 enforces edge-to-edge by default: content draws under the
                // system bars unless it explicitly insets itself, which previously left
                // top-of-screen controls (e.g. Settings' first switch) unreachable because
                // touches there were consumed by the status bar instead of the app.
                Surface(modifier = Modifier.safeDrawingPadding()) {
                    AppNavHost()
                }
            }
        }
    }
}

package com.rel.adbhelper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.rel.adbhelper.navigation.AppNavHost

/**
 * The app's single Activity. Hosts the Compose UI via [AppNavHost].
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    AppNavHost()
                }
            }
        }
    }
}

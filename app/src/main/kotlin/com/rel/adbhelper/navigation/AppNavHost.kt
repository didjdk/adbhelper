package com.rel.adbhelper.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

private const val ROUTE_PLACEHOLDER = "placeholder"

/**
 * Hosts the app's navigation graph. Currently a single placeholder destination; later tasks
 * add the Connect, Settings, and About/Feedback screens here.
 */
@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = ROUTE_PLACEHOLDER) {
        composable(ROUTE_PLACEHOLDER) {
            PlaceholderScreen()
        }
    }
}

@Composable
private fun PlaceholderScreen(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "ADB Helper")
    }
}

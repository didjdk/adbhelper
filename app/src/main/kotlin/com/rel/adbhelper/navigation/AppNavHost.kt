package com.rel.adbhelper.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rel.adbhelper.connect.ConnectScreen
import com.rel.adbhelper.settings.SettingsScreen

private const val ROUTE_CONNECT = "connect"
private const val ROUTE_SETTINGS = "settings"

/**
 * Hosts the app's navigation graph. Connect is the start destination; later tasks add
 * About and Feedback here.
 */
@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = ROUTE_CONNECT) {
        composable(ROUTE_CONNECT) {
            ConnectScreen(onOpenSettings = { navController.navigate(ROUTE_SETTINGS) })
        }
        composable(ROUTE_SETTINGS) {
            SettingsScreen()
        }
    }
}

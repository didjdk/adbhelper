package com.rel.adbhelper.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rel.adbhelper.connect.ConnectScreen

private const val ROUTE_CONNECT = "connect"

/**
 * Hosts the app's navigation graph. Connect is the start destination; later tasks add
 * Settings, About, and Feedback here.
 */
@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = ROUTE_CONNECT) {
        composable(ROUTE_CONNECT) {
            ConnectScreen()
        }
    }
}

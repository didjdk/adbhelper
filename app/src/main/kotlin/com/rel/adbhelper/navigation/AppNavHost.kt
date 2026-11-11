package com.rel.adbhelper.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rel.adbhelper.about.AboutScreen
import com.rel.adbhelper.connect.ConnectScreen
import com.rel.adbhelper.feedback.FeedbackScreen
import com.rel.adbhelper.settings.SettingsScreen

private const val ROUTE_CONNECT = "connect"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_ABOUT = "about"
private const val ROUTE_FEEDBACK = "feedback"

/**
 * Hosts the app's navigation graph. Connect is the start destination.
 */
@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = ROUTE_CONNECT) {
        composable(ROUTE_CONNECT) {
            ConnectScreen(
                onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
                onOpenAbout = { navController.navigate(ROUTE_ABOUT) },
                onOpenFeedback = { navController.navigate(ROUTE_FEEDBACK) },
            )
        }
        composable(ROUTE_SETTINGS) {
            SettingsScreen(onBack = navController::popBackStack)
        }
        composable(ROUTE_ABOUT) {
            AboutScreen(onBack = navController::popBackStack)
        }
        composable(ROUTE_FEEDBACK) {
            FeedbackScreen(onBack = navController::popBackStack)
        }
    }
}

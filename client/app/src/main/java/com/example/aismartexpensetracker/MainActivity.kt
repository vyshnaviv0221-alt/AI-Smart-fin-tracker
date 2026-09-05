package com.example.aismartexpensetracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.aismartexpensetracker.ui.AnalyticsScreen
import com.example.aismartexpensetracker.ui.BudgetScreen
import com.example.aismartexpensetracker.ui.DashboardScreen
import com.example.aismartexpensetracker.ui.LoginScreen
import com.example.aismartexpensetracker.ui.MenuScreen
import com.example.aismartexpensetracker.ui.PredictionsScreen
import com.example.aismartexpensetracker.ui.ProfileScreen
import com.example.aismartexpensetracker.ui.RecommendationsScreen
import com.example.aismartexpensetracker.ui.TransactionsScreen
import com.example.aismartexpensetracker.ui.theme.AISMARTEXPENSETRACKERTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AISMARTEXPENSETRACKERTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavHost()
                }
            }
        }
    }
}

/**
 * Route names match the strings MenuScreen already navigates to.
 *
 * The ExpenseViewModel is created once here and passed to every screen.
 * Calling viewModel() inside each NavHost destination would instead scope a
 * separate instance to each back-stack entry: Room-backed data would still
 * agree (same database), but in-memory state -- the Supabase session, sync
 * status, forecast results -- would not. Signing in on Login would then leave
 * Profile still showing "Not signed in".
 */
@Composable
private fun AppNavHost() {
    val navController = rememberNavController()
    val vm: ExpenseViewModel = viewModel()

    NavHost(navController = navController, startDestination = "dashboard") {
        composable("dashboard") { DashboardScreen(navController = navController, viewModel = vm) }
        composable("menu") { MenuScreen(navController) }
        composable("transactions") { TransactionsScreen(viewModel = vm) }
        composable("budgets") { BudgetScreen(viewModel = vm) }
        composable("analytics") { AnalyticsScreen(viewModel = vm) }
        composable("predictions") { PredictionsScreen(viewModel = vm) }
        composable("recommendations") { RecommendationsScreen(viewModel = vm) }
        composable("profile") {
            ProfileScreen(
                onLogout = { navController.navigate("login") },
                viewModel = vm
            )
        }
        composable("login") {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate("dashboard") {
                        popUpTo("login") { inclusive = true }
                    }
                },
                viewModel = vm
            )
        }
    }
}

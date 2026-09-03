package com.example.aismartexpensetracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.aismartexpensetracker.ui.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppNavigation()
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "login") {
        composable(route = "login") {
            LoginScreen(onLoginSuccess = { navController.navigate("menu") })
        }
        composable(route = "menu") {
            MenuScreen(navController = navController)
        }
        composable(route = "dashboard") {
            DashboardScreen(navController = navController)
        }
        composable(route = "transactions") {
            TransactionsScreen()
        }
        composable(route = "budgets") {
            BudgetScreen()
        }
        composable(route = "analytics") {
            AnalyticsScreen()
        }
        composable(route = "predictions") {
            PredictionsScreen()
        }
        composable(route = "recommendations") {
            RecommendationsScreen()
        }
        composable(route = "profile") {
            ProfileScreen()
        }
    }
}
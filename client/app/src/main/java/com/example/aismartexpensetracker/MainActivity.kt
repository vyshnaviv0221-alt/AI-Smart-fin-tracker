package com.example.aismartexpensetracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
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
import com.example.aismartexpensetracker.ui.theme.LocalReducedMotion

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AISMARTEXPENSETRACKERTheme {
                Surface(
                    // safeDrawingPadding keeps content clear of the status and
                    // navigation bars. Applied once here rather than per screen.
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavHost()
                }
            }
        }
    }
}

/**
 * Route names match the strings MenuScreen navigates to.
 *
 * The ExpenseViewModel is created once here and passed to every screen.
 * Calling viewModel() inside each NavHost destination would instead scope a
 * separate instance to each back-stack entry: Room-backed data would still
 * agree (same database), but in-memory state -- the Supabase session, sync
 * status, forecast results -- would not.
 *
 * Transitions are spatially symmetric: a screen entered by sliding in from
 * the right leaves back to the right. Something that arrives one way and
 * departs another breaks the sense of where it went. The outgoing screen
 * moves a shorter distance than the incoming one, so the two read as a stack
 * rather than a swap.
 */
@Composable
private fun AppNavHost() {
    val navController = rememberNavController()
    val vm: ExpenseViewModel = viewModel()
    val reduced = LocalReducedMotion.current

    val duration = 280
    val fade = tween<Float>(durationMillis = duration)
    val slide = tween<IntOffset>(durationMillis = duration)

    NavHost(
        navController = navController,
        startDestination = "dashboard",
        // Reduced motion keeps the transition legible as a cross-fade rather
        // than removing feedback entirely.
        enterTransition = {
            if (reduced) fadeIn(fade)
            else slideInHorizontally(slide) { full -> full / 3 } + fadeIn(fade)
        },
        exitTransition = {
            if (reduced) fadeOut(fade)
            else slideOutHorizontally(slide) { full -> -full / 6 } + fadeOut(fade)
        },
        popEnterTransition = {
            if (reduced) fadeIn(fade)
            else slideInHorizontally(slide) { full -> -full / 6 } + fadeIn(fade)
        },
        popExitTransition = {
            if (reduced) fadeOut(fade)
            else slideOutHorizontally(slide) { full -> full / 3 } + fadeOut(fade)
        }
    ) {
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

package com.example.aismartexpensetracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
 * The AuthViewModel is also created at this level so auth state is shared
 * across screens (ProfileScreen needs it for sign-out; LoginScreen for sign-in).
 *
 * Auth gate: the NavHost starts at "login" when no Firebase user is signed in,
 * and at "dashboard" when one is. A LaunchedEffect observes user state so that
 * a sign-out from ProfileScreen automatically redirects back to "login" without
 * any additional wiring in the individual screens.
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
    val authVm: AuthViewModel = viewModel()
    val reduced = LocalReducedMotion.current

    // Observe auth state: redirect to login when the user signs out.
    val user by authVm.user.collectAsStateWithLifecycle()
    LaunchedEffect(user) {
        if (user == null) {
            navController.navigate("login") {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    // The slide is a spring, not a tween. A spring animates from the value
    // currently on screen, so interrupting a transition -- swiping back while
    // a screen is still arriving -- redirects from where it actually is
    // instead of restarting. Critically damped: navigation should settle, not
    // wobble. The fade stays a short tween because opacity has no momentum to
    // preserve and a spring on alpha reads as a flicker.
    val slide = spring<IntOffset>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
        visibilityThreshold = IntOffset.VisibilityThreshold
    )
    val fade = tween<Float>(durationMillis = 180)

    // Start at "login" when no user is signed in; otherwise go to "dashboard".
    val startDestination = if (authVm.user.value != null) "dashboard" else "login"

    NavHost(
        navController = navController,
        startDestination = startDestination,
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
        composable("login") {
            LoginScreen(authViewModel = authVm)
            // Navigate to dashboard once sign-in succeeds.
            LaunchedEffect(user) {
                if (user != null) {
                    navController.navigate("dashboard") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            }
        }
        composable("dashboard") { DashboardScreen(navController = navController, viewModel = vm) }
        composable("menu") { MenuScreen(navController) }
        composable("transactions") { TransactionsScreen(viewModel = vm) }
        composable("budgets") { BudgetScreen(viewModel = vm) }
        composable("analytics") { AnalyticsScreen(viewModel = vm) }
        composable("predictions") { PredictionsScreen(viewModel = vm) }
        composable("recommendations") { RecommendationsScreen(viewModel = vm) }
        composable("profile") { ProfileScreen(viewModel = vm, authViewModel = authVm) }
    }
}

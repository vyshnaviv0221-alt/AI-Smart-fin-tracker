package com.example.aismartexpensetracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.aismartexpensetracker.ui.components.*
import com.example.aismartexpensetracker.ui.theme.*

private data class Destination(
    val route: String,
    val icon: String,
    val title: String,
    val subtitle: String,
    val tint: Color
)

/**
 * Menu items name what they contain rather than using vague umbrellas -- the
 * subtitle says what you will find, so the destination is predictable before
 * you tap it.
 */
private val DESTINATIONS = listOf(
    Destination("dashboard", "🏠", "Dashboard", "This month at a glance", Indigo500),
    Destination("transactions", "🧾", "Transactions", "Every capture, and corrections", CatBills),
    Destination("budgets", "🎯", "Budgets", "Limits and how close you are", CatGroceries),
    Destination("analytics", "📊", "Analytics", "Where the money actually went", CatTravel),
    Destination("predictions", "🔮", "Predictions", "What next month may cost", CatEntertainment),
    Destination("recommendations", "💡", "Recommendations", "Generated from your data", CatShopping),
    Destination("profile", "⚙️", "Profile & Settings", "Account, sync, notification access", CatTransfer)
)

@Composable
fun MenuScreen(navController: NavController) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Canvas)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.lg)
    ) {
        Spacer(Modifier.height(Space.sm))
        ScreenTitle(text = "Smart Expense\nTracker", subtitle = "Where would you like to go?")
        Spacer(Modifier.height(Space.xl))

        DESTINATIONS.forEach { destination ->
            PressableCard(
                onClick = {
                    navController.navigate(destination.route) {
                        launchSingleTop = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Space.md)
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(Space.lg),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(destination.tint.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) { Text(destination.icon, style = BodyStyle) }

                    Spacer(Modifier.width(Space.md))

                    Column(Modifier.weight(1f)) {
                        Text(destination.title, style = RowTitleStyle, color = Ink)
                        Spacer(Modifier.height(Space.xxs))
                        Text(destination.subtitle, style = CaptionStyle, color = InkMuted)
                    }

                    Text("›", style = SectionStyle, color = InkFaint)
                }
            }
        }

        Spacer(Modifier.height(Space.xxxl))
    }
}

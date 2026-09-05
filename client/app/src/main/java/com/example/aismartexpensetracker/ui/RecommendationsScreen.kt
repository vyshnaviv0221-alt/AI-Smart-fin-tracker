package com.example.aismartexpensetracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aismartexpensetracker.ExpenseViewModel
import com.example.aismartexpensetracker.Insight
import com.example.aismartexpensetracker.InsightLevel
import com.example.aismartexpensetracker.ui.components.*
import com.example.aismartexpensetracker.ui.theme.*

/**
 * Every card here is generated from the user's own transactions and budgets by
 * ExpenseViewModel.buildInsights -- there are no canned tips. When there is
 * nothing genuine to say the screen says so rather than inventing advice.
 */
@Composable
fun RecommendationsScreen(viewModel: ExpenseViewModel = viewModel()) {
    val insights by viewModel.insights.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .background(Canvas)
            .padding(horizontal = Space.lg)
    ) {
        Spacer(Modifier.height(Space.sm))
        ScreenTitle(
            text = "Recommendations",
            subtitle = if (insights.isEmpty()) "Nothing to flag right now"
            else "From this month's spending and your budgets"
        )
        Spacer(Modifier.height(Space.xl))

        if (insights.isEmpty()) {
            EmptyState(
                icon = "💡",
                title = "No recommendations yet",
                message = "Capture a few transactions and set some limits. Advice " +
                    "appears here only when your own data supports it."
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(Space.md),
                contentPadding = PaddingValues(bottom = Space.xxxl)
            ) {
                items(insights) { InsightCard(it) }
            }
        }
    }
}

@Composable
private fun InsightCard(insight: Insight) {
    val accent: Color = when (insight.level) {
        InsightLevel.GOOD -> Success
        InsightLevel.WARNING -> Warning
        InsightLevel.DANGER -> Danger
        InsightLevel.NEUTRAL -> Indigo500
    }
    val soft: Color = when (insight.level) {
        InsightLevel.GOOD -> SuccessSoft
        InsightLevel.WARNING -> WarningSoft
        InsightLevel.DANGER -> DangerSoft
        InsightLevel.NEUTRAL -> Indigo50
    }

    AppCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(Space.lg)) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(soft),
                contentAlignment = Alignment.Center
            ) { Text(insight.icon, style = BodyStyle) }

            Spacer(Modifier.width(Space.md))

            Column(Modifier.weight(1f)) {
                Text(insight.title, style = RowTitleStyle, color = accent)
                Spacer(Modifier.height(Space.xs))
                Text(insight.message, style = CaptionStyle, color = InkMuted)
            }
        }
    }
}

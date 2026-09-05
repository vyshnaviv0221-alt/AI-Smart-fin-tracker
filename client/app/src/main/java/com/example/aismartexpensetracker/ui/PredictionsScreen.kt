package com.example.aismartexpensetracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aismartexpensetracker.CategoryForecast
import com.example.aismartexpensetracker.ExpenseViewModel
import com.example.aismartexpensetracker.ForecastState
import com.example.aismartexpensetracker.ui.components.*
import com.example.aismartexpensetracker.ui.theme.*

private val MONTH_NAMES = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December"
)

/**
 * Calls POST /predict on the FastAPI server for each category the user has
 * spent in. This is the only screen that exercises the forecaster, so it needs
 * the server running -- failures are shown explicitly rather than silently
 * falling back to sample numbers.
 */
@Composable
fun PredictionsScreen(viewModel: ExpenseViewModel = viewModel()) {
    val state by viewModel.forecastState.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadForecasts() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Canvas)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.lg)
    ) {
        Spacer(Modifier.height(Space.sm))
        ScreenTitle(
            text = "Predictions",
            subtitle = "From the forecasting model on the server"
        )
        Spacer(Modifier.height(Space.xl))

        when (val s = state) {
            is ForecastState.Idle, is ForecastState.Loading -> {
                AppCard(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(Space.xl),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Indigo500)
                        Spacer(Modifier.width(Space.md))
                        Text("Asking the model…", style = BodyStyle, color = InkMuted)
                    }
                }
            }

            is ForecastState.Error -> {
                AppCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(Space.xl)) {
                        Text("Predictions unavailable", style = RowTitleStyle, color = Danger)
                        Spacer(Modifier.height(Space.sm))
                        Text(s.message, style = CaptionStyle, color = InkMuted)
                        Spacer(Modifier.height(Space.lg))
                        Button(
                            onClick = { viewModel.loadForecasts() },
                            shape = Radius.chip,
                            colors = ButtonDefaults.buttonColors(containerColor = Indigo500)
                        ) { Text("Retry", style = RowTitleStyle) }
                    }
                }
            }

            is ForecastState.Ready -> {
                val monthName = MONTH_NAMES.getOrElse(s.month - 1) { "next month" }
                val predictedTotal = s.forecasts.sumOf { it.predicted }
                val spentTotal = s.forecasts.sumOf { it.spentSoFar }

                AppCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(Space.xl)) {
                        Text("Predicted for $monthName", style = CaptionStyle, color = InkMuted)
                        Spacer(Modifier.height(Space.xs))
                        Text(rupees(predictedTotal), style = AmountStyle, color = Indigo900)
                        Spacer(Modifier.height(Space.sm))
                        if (spentTotal > 0.0) {
                            val change = (predictedTotal - spentTotal) / spentTotal * 100
                            Text(
                                if (change >= 0)
                                    "▲ ${"%.0f".format(change)}% above what you've spent so far"
                                else "▼ ${"%.0f".format(-change)}% below what you've spent so far",
                                style = CaptionStyle,
                                color = if (change >= 0) Warning else Success
                            )
                        }
                        Spacer(Modifier.height(Space.md))
                        Text(
                            "Across ${s.forecasts.size} " +
                                if (s.forecasts.size == 1) "category" else "categories",
                            style = CaptionStyle,
                            color = InkFaint
                        )
                    }
                }

                Spacer(Modifier.height(Space.xxl))
                SectionHeader("By category")

                AppCard(Modifier.fillMaxWidth()) {
                    s.forecasts.forEachIndexed { index, item ->
                        ForecastRow(item)
                        if (index != s.forecasts.lastIndex) {
                            RowDivider(Modifier.padding(start = 68.dp))
                        }
                    }
                }

                Spacer(Modifier.height(Space.lg))
                Text(
                    "The forecaster explains only a small share of month-to-month " +
                        "variance — household spending is dominated by irregular " +
                        "one-off purchases. Treat these as indicative.",
                    style = CaptionStyle,
                    color = InkFaint
                )
            }
        }

        Spacer(Modifier.height(Space.xxxl))
    }
}

@Composable
private fun ForecastRow(item: CategoryForecast) {
    // A category can be forecast before the user has spent anything in it.
    val hasBaseline = item.spentSoFar > 0.0
    val change = if (hasBaseline) (item.predicted - item.spentSoFar) / item.spentSoFar * 100 else 0.0
    val ratio = if (hasBaseline) (item.predicted / (item.spentSoFar * 1.5)).coerceIn(0.0, 1.0) else 1.0

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.lg, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CategoryAvatar(item.category)
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(item.category, style = RowTitleStyle, color = Ink)
                Text(rupees(item.predicted), style = RowTitleStyle, color = Indigo700)
            }
            Spacer(Modifier.height(Space.sm))
            AnimatedBar(
                progress = ratio.toFloat(),
                color = categoryColor(item.category),
                height = 6.dp
            )
            Spacer(Modifier.height(Space.sm))
            Text(
                if (hasBaseline)
                    "so far ${rupees(item.spentSoFar)} · " +
                        "${if (change >= 0) "+" else ""}${"%.0f".format(change)}%"
                else "nothing recorded yet this month",
                style = CaptionStyle,
                color = InkFaint
            )
        }
    }
}

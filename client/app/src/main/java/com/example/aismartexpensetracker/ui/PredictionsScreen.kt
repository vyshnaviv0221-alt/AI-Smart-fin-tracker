package com.example.aismartexpensetracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aismartexpensetracker.CategoryForecast
import com.example.aismartexpensetracker.ExpenseViewModel
import com.example.aismartexpensetracker.ForecastState

private val PredPurpleDark = Color(0xFF3C3489)
private val PredPurpleMid = Color(0xFF534AB7)
private val PredBgGray = Color(0xFFF7F6FA)
private val PredTextSecondary = Color(0xFF757575)
private val PredWarning = Color(0xFFF9A825)
private val PredWarningText = Color(0xFFB05B00)
private val PredTrackGray = Color(0xFFE0DDE8)
private val PredErrorText = Color(0xFFD32F2F)

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
            .background(PredBgGray)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Predictions", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = PredPurpleDark)
        Spacer(Modifier.height(4.dp))
        Text(
            "Forecast from the RandomForest model on the server",
            fontSize = 14.sp,
            color = PredTextSecondary
        )
        Spacer(Modifier.height(20.dp))

        when (val s = state) {
            is ForecastState.Idle, is ForecastState.Loading -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Asking the model…", fontSize = 14.sp, color = PredTextSecondary)
                }
            }

            is ForecastState.Error -> {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp)) {
                        Text(
                            "Predictions unavailable",
                            fontWeight = FontWeight.Bold,
                            color = PredErrorText
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(s.message, fontSize = 13.sp, color = PredTextSecondary)
                        Spacer(Modifier.height(14.dp))
                        Button(onClick = { viewModel.loadForecasts() }) { Text("Retry") }
                    }
                }
            }

            is ForecastState.Ready -> {
                val monthName = MONTH_NAMES.getOrElse(s.month - 1) { "next month" }
                val predictedTotal = s.forecasts.sumOf { it.predicted }
                val spentTotal = s.forecasts.sumOf { it.spentSoFar }

                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp)) {
                        Text(
                            "Predicted spend for $monthName",
                            fontSize = 13.sp,
                            color = PredTextSecondary
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "₹${"%,.0f".format(predictedTotal)}",
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            color = PredPurpleDark
                        )
                        Spacer(Modifier.height(6.dp))
                        if (spentTotal > 0.0) {
                            val change = (predictedTotal - spentTotal) / spentTotal * 100
                            Text(
                                if (change >= 0) "▲ ${"%.1f".format(change)}% above what you've spent so far"
                                else "▼ ${"%.1f".format(-change)}% below what you've spent so far",
                                fontSize = 13.sp,
                                color = if (change >= 0) PredWarningText else PredPurpleMid
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Across ${s.forecasts.size} categories",
                            fontSize = 12.sp,
                            color = PredTextSecondary
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))
                Text(
                    "Category-wise predictions",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = PredPurpleDark
                )
                Spacer(Modifier.height(12.dp))

                s.forecasts.forEach { item ->
                    CategoryPredictionCard(item)
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun CategoryPredictionCard(item: CategoryForecast) {
    // Guard against divide-by-zero: a category can be forecast before the user
    // has spent anything in it.
    val hasBaseline = item.spentSoFar > 0.0
    val percentChange =
        if (hasBaseline) (item.predicted - item.spentSoFar) / item.spentSoFar * 100 else 0.0
    val fillPercent =
        if (hasBaseline) (item.predicted / (item.spentSoFar * 1.3)).coerceIn(0.0, 1.0).toFloat()
        else 1f

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(emojiFor(item.category), fontSize = 18.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(item.category, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                Text(
                    "₹${"%,.0f".format(item.predicted)}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = PredPurpleDark
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (hasBaseline)
                    "Spent so far: ₹${"%,.0f".format(item.spentSoFar)}  •  " +
                        "${if (percentChange >= 0) "+" else ""}${"%.0f".format(percentChange)}%"
                else "No spending recorded yet in this category",
                fontSize = 12.sp,
                color = PredTextSecondary
            )
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(PredTrackGray)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fillPercent)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (percentChange >= 10) PredWarning else PredPurpleMid)
                )
            }
        }
    }
}

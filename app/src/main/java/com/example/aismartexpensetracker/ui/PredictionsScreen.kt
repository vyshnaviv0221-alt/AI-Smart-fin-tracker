package com.example.aismartexpensetracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ============================================================
// Local theme colors (self-contained, prefixed to avoid any
// clash with colors declared elsewhere in the project — same
// pattern as LoginScreen.kt).
// ============================================================
private val PredPurpleDark = Color(0xFF3C3489)
private val PredPurpleMid = Color(0xFF534AB7)
private val PredBgGray = Color(0xFFF7F6FA)
private val PredTextSecondary = Color(0xFF757575)
private val PredWarning = Color(0xFFF9A825)
private val PredWarningText = Color(0xFFB05B00)
private val PredTrackGray = Color(0xFFE0DDE8)

data class MonthTrend(val label: String, val amount: Int)
data class CategoryPrediction(
    val category: String,
    val icon: String,
    val lastMonth: Int,
    val predicted: Int
)

@Composable
fun PredictionsScreen() {
    // Sample data — replace with real history from Room once available
    val trend = listOf(
        MonthTrend("May", 14200),
        MonthTrend("Jun", 15800),
        MonthTrend("Jul", 16500),
        MonthTrend("Aug", 17100),
        MonthTrend("Sep (predicted)", 18400)
    )

    val categoryPredictions = listOf(
        CategoryPrediction("Food", "🍔", 5200, 5600),
        CategoryPrediction("Travel", "🚕", 3100, 3400),
        CategoryPrediction("Bills", "🧾", 6400, 6500),
        CategoryPrediction("Shopping", "🛍️", 3540, 4100)
    )

    val predictedTotal = trend.last().amount
    val lastMonthTotal = trend[trend.size - 2].amount
    val changePercent = ((predictedTotal - lastMonthTotal).toFloat() / lastMonthTotal * 100)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PredBgGray)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            "Predictions",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = PredPurpleDark
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Estimated spending based on your recent trend",
            fontSize = 14.sp,
            color = PredTextSecondary
        )
        Spacer(Modifier.height(20.dp))

        // ---- Predicted total card ----
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text("Predicted spend this month", fontSize = 13.sp, color = PredTextSecondary)
                Spacer(Modifier.height(6.dp))
                Text(
                    "₹$predictedTotal",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = PredPurpleDark
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (changePercent >= 0)
                        "▲ ${"%.1f".format(changePercent)}% higher than last month"
                    else
                        "▼ ${"%.1f".format(-changePercent)}% lower than last month",
                    fontSize = 13.sp,
                    color = if (changePercent >= 0) PredWarningText else PredPurpleMid
                )

                Spacer(Modifier.height(20.dp))
                TrendLineChart(trend)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    trend.forEach {
                        Text(
                            it.label.replace(" (predicted)", "*"),
                            fontSize = 10.sp,
                            color = PredTextSecondary
                        )
                    }
                }
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

        categoryPredictions.forEach { item ->
            CategoryPredictionCard(item)
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun TrendLineChart(trend: List<MonthTrend>) {
    val maxAmount = trend.maxOf { it.amount }.toFloat()
    val minAmount = trend.minOf { it.amount }.toFloat()

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
    ) {
        val stepX = size.width / (trend.size - 1)
        val points = trend.mapIndexed { index, item ->
            val normalized = (item.amount - minAmount) / (maxAmount - minAmount + 1f)
            val y = size.height - (normalized * size.height)
            Offset(index * stepX, y)
        }

        for (i in 0 until points.size - 1) {
            val isLastSegment = i == points.size - 2
            drawLine(
                color = if (isLastSegment) PredWarning else PredPurpleMid,
                start = points[i],
                end = points[i + 1],
                strokeWidth = 6f,
                cap = StrokeCap.Round
            )
        }
        points.forEachIndexed { index, point ->
            drawCircle(
                color = if (index == points.size - 1) PredWarning else PredPurpleMid,
                radius = 8f,
                center = point
            )
        }
    }
}

@Composable
private fun CategoryPredictionCard(item: CategoryPrediction) {
    val percentChange = ((item.predicted - item.lastMonth).toFloat() / item.lastMonth * 100)
    val fillPercent = (item.predicted.toFloat() / (item.lastMonth * 1.3f)).coerceIn(0f, 1f)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.icon, fontSize = 18.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(item.category, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                Text(
                    "₹${item.predicted}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = PredPurpleDark
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Last month: ₹${item.lastMonth}  •  ${if (percentChange >= 0) "+" else ""}${"%.0f".format(percentChange)}%",
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




package com.example.aismartexpensetracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ============================================================
// Local theme colors (self-contained — same pattern as
// LoginScreen.kt). No dependency on ui/theme/Theme.kt.
// ============================================================
private val AnaPurpleDark = Color(0xFF3C3489)
private val AnaBgGray = Color(0xFFF7F6FA)
private val AnaTextSecondary = Color(0xFF757575)
private val AnaTrackGray = Color(0xFFE0DDE8)
private val AnaCategoryFood = Color(0xFF534AB7)
private val AnaCategoryTravel = Color(0xFF1D9E75)
private val AnaCategoryBills = Color(0xFFD85A30)
private val AnaCategoryShopping = Color(0xFFD4537E)
private val AnaCategoryEntertainment = Color(0xFF378ADD)

data class CategorySpend(
    val category: String,
    val amount: Int,
    val color: Color
)

@Composable
fun AnalyticsScreen() {
    val spendData = listOf(
        CategorySpend("Food", 5200, AnaCategoryFood),
        CategorySpend("Travel", 3100, AnaCategoryTravel),
        CategorySpend("Bills", 6400, AnaCategoryBills),
        CategorySpend("Shopping", 3540, AnaCategoryShopping),
        CategorySpend("Entertainment", 1200, AnaCategoryEntertainment)
    )
    val total = spendData.sumOf { it.amount }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AnaBgGray)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            "Analytics",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = AnaPurpleDark
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "This month's spending by category",
            fontSize = 14.sp,
            color = AnaTextSecondary
        )
        Spacer(Modifier.height(20.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(contentAlignment = Alignment.Center) {
                    DonutChart(spendData, total)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "₹$total",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = AnaPurpleDark
                        )
                        Text(
                            "Total spent",
                            fontSize = 12.sp,
                            color = AnaTextSecondary
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Text(
            "Breakdown",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = AnaPurpleDark
        )
        Spacer(Modifier.height(12.dp))

        spendData.sortedByDescending { it.amount }.forEach { item ->
            CategoryBar(item, total)
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun DonutChart(data: List<CategorySpend>, total: Int) {
    val strokeWidth = 28.dp
    Canvas(modifier = Modifier.size(180.dp)) {
        var startAngle = -90f
        val diameter = size.minDimension
        val topLeft = Offset(
            (size.width - diameter) / 2f + strokeWidth.toPx() / 2f,
            (size.height - diameter) / 2f + strokeWidth.toPx() / 2f
        )
        val arcSize = Size(diameter - strokeWidth.toPx(), diameter - strokeWidth.toPx())

        data.forEach { item ->
            val sweep = (item.amount.toFloat() / total) * 360f
            drawArc(
                color = item.color,
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth.toPx())
            )
            startAngle += sweep
        }
    }
}

@Composable
private fun CategoryBar(item: CategorySpend, total: Int) {
    val percent = (item.amount.toFloat() / total.toFloat()).coerceIn(0f, 1f)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(item.color)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(item.category, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                Text(
                    "₹${item.amount}  •  ${(percent * 100).toInt()}%",
                    fontSize = 13.sp,
                    color = AnaTextSecondary
                )
            }
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(AnaTrackGray)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(percent)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(item.color)
                )
            }
        }
    }
}
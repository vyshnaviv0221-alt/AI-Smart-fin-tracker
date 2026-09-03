package com.example.aismartexpensetracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ============================================================
// Local theme colors (self-contained — same pattern as
// LoginScreen.kt). No dependency on ui/theme/Theme.kt.
// ============================================================
private val BudgetPurpleDark = Color(0xFF3C3489)
private val BudgetBgGray = Color(0xFFF7F6FA)
private val BudgetTextSecondary = Color(0xFF757575)
private val BudgetStatusGood = Color(0xFF534AB7)
private val BudgetStatusWarning = Color(0xFFF9A825)
private val BudgetStatusWarningText = Color(0xFFB05B00)
private val BudgetStatusDanger = Color(0xFFD32F2F)
private val BudgetTrackGray = Color(0xFFE0DDE8)

data class BudgetItem(
    val category: String,
    val spent: Int,
    val limit: Int,
    val icon: String
)

@Composable
fun BudgetScreen() {
    val budgets = listOf(
        BudgetItem("Food", 5200, 6000, "🍔"),
        BudgetItem("Travel", 3100, 4000, "🚕"),
        BudgetItem("Bills", 6400, 6500, "🧾"),
        BudgetItem("Shopping", 3540, 3000, "🛍️")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BudgetBgGray)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            "Budgets & Alerts",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = BudgetPurpleDark
        )
        Spacer(Modifier.height(16.dp))

        budgets.forEach { budget ->
            BudgetCard(budget)
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun BudgetCard(item: BudgetItem) {
    val percent = (item.spent.toFloat() / item.limit.toFloat()).coerceIn(0f, 1f)
    val isOverBudget = item.spent > item.limit
    val isNearLimit = percent >= 0.85f && !isOverBudget

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.icon, fontSize = 20.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(item.category, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Text(
                    "₹${item.spent} / ₹${item.limit}",
                    fontSize = 14.sp,
                    color = BudgetTextSecondary
                )
            }

            Spacer(Modifier.height(10.dp))

            LinearProgressIndicator(
                progress = percent,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = if (isOverBudget) BudgetStatusDanger
                else if (isNearLimit) BudgetStatusWarning
                else BudgetStatusGood,
                trackColor = BudgetTrackGray
            )

            if (isOverBudget) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "⚠️ Over budget by ₹${item.spent - item.limit}",
                    color = BudgetStatusDanger,
                    fontSize = 13.sp
                )
            } else if (isNearLimit) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "⚠️ Close to your ${item.category} budget limit",
                    color = BudgetStatusWarningText,
                    fontSize = 13.sp
                )
            }
        }
    }
}
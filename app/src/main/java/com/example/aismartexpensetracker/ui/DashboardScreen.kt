package com.example.aismartexpensetracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.navigation.NavController

private val DashPurpleDark = Color(0xFF3C3489)
private val DashPurpleMid = Color(0xFF534AB7)
private val DashBgGray = Color(0xFFF7F6FA)
private val DashTextSecondary = Color(0xFF757575)
private val DashGreen = Color(0xFF1D9E75)
private val DashRed = Color(0xFFD32F2F)
private val DashTrackGray = Color(0xFFE0DDE8)

data class RecentTxn(
    val merchant: String,
    val icon: String,
    val amount: Int,
    val isDebit: Boolean,
    val time: String
)

@Composable
fun DashboardScreen(navController: NavController? = null) {
    val totalExpenses = 18400
    val monthlyBudget = 25000
    val remaining = monthlyBudget - totalExpenses
    val percentUsed = (totalExpenses.toFloat() / monthlyBudget).coerceIn(0f, 1f)

    val recentTransactions = listOf(
        RecentTxn("Swiggy Bangalore", "🍔", 420, true, "Today, 9:14 PM"),
        RecentTxn("Salary Credit", "💰", 45000, false, "Yesterday, 10:00 AM"),
        RecentTxn("BigBasket", "🛒", 1230, true, "Yesterday, 6:40 PM"),
        RecentTxn("Uber Trip", "🚕", 260, true, "2 days ago, 8:05 AM")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DashBgGray)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Top bar with title and back-to-menu button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Dashboard",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = DashPurpleDark
            )
            if (navController != null) {
                Button(onClick = { navController.navigate("menu") }) {
                    Text("Go to Menu")
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ---- Total expenses + monthly budget card ----
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                Text("Total spent this month", fontSize = 13.sp, color = DashTextSecondary)
                Spacer(Modifier.height(4.dp))
                Text(
                    "₹$totalExpenses",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = DashPurpleDark
                )
                Spacer(Modifier.height(12.dp))

                LinearProgressIndicator(
                    progress = percentUsed,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = if (percentUsed >= 0.9f) DashRed else DashPurpleMid,
                    trackColor = DashTrackGray
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Budget: ₹$monthlyBudget", fontSize = 12.sp, color = DashTextSecondary)
                    Text("₹$remaining left", fontSize = 12.sp, color = DashTextSecondary)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ---- Quick summary row ----
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryCard(
                modifier = Modifier.weight(1f),
                label = "Transactions",
                value = recentTransactions.size.toString(),
                icon = "📋"
            )
            SummaryCard(
                modifier = Modifier.weight(1f),
                label = "Days left",
                value = "12",
                icon = "📅"
            )
        }

        Spacer(Modifier.height(24.dp))

        Text(
            "Recent transactions",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = DashPurpleDark
        )
        Spacer(Modifier.height(12.dp))

        recentTransactions.forEach { txn ->
            RecentTxnRow(txn)
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun SummaryCard(modifier: Modifier = Modifier, label: String, value: String, icon: String) {
    Card(modifier = modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(icon, fontSize = 18.sp)
            Spacer(Modifier.height(6.dp))
            Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = DashPurpleDark)
            Text(label, fontSize = 12.sp, color = DashTextSecondary)
        }
    }
}

@Composable
private fun RecentTxnRow(txn: RecentTxn) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(DashBgGray),
                    contentAlignment = Alignment.Center
                ) {
                    Text(txn.icon, fontSize = 16.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(txn.merchant, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(txn.time, fontSize = 11.sp, color = DashTextSecondary)
                }
            }
            Text(
                (if (txn.isDebit) "-₹" else "+₹") + txn.amount,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = if (txn.isDebit) DashRed else DashGreen
            )
        }
    }
}
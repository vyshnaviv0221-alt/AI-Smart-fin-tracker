package com.example.aismartexpensetracker.ui

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ============================================================
// Local theme colors (self-contained — same pattern as every
// other screen in this project). No dependency on Theme.kt.
// ============================================================
private val TxnPurpleDark = Color(0xFF3C3489)
private val TxnBgGray = Color(0xFFF7F6FA)
private val TxnTextSecondary = Color(0xFF757575)
private val TxnGreen = Color(0xFF1D9E75)
private val TxnRed = Color(0xFFD32F2F)
private val TxnChipBg = Color(0xFFEEEDFE)

data class Transaction(
    val merchant: String,
    val icon: String,
    val amount: Int,
    val category: String,
    val dateTime: String,
    val isDebit: Boolean
)

@Composable
fun TransactionsScreen() {
    // Sample data — replace with real Room query once the
    // notification -> parser -> DB -> ML pipeline is wired up.
    val transactions = listOf(
        Transaction("Swiggy Bangalore", "🍔", 420, "Food", "Today, 9:14 PM", true),
        Transaction("Salary Credit", "💰", 45000, "Income", "Yesterday, 10:00 AM", false),
        Transaction("BigBasket", "🛒", 1230, "Groceries", "Yesterday, 6:40 PM", true),
        Transaction("Uber Trip", "🚕", 260, "Travel", "2 days ago, 8:05 AM", true),
        Transaction("Netflix", "🎬", 649, "Entertainment", "3 days ago, 12:01 AM", true),
        Transaction("Electricity Board", "🧾", 1840, "Bills", "4 days ago, 5:30 PM", true),
        Transaction("Amazon", "🛍️", 2199, "Shopping", "5 days ago, 3:12 PM", true),
        Transaction("Freelance Payment", "💰", 8000, "Income", "6 days ago, 11:45 AM", false)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TxnBgGray)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            "Transactions",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = TxnPurpleDark
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "${transactions.size} transactions this month",
            fontSize = 14.sp,
            color = TxnTextSecondary
        )
        Spacer(Modifier.height(20.dp))

        transactions.forEach { txn ->
            TransactionCard(txn)
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun TransactionCard(txn: Transaction) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(TxnBgGray),
                contentAlignment = Alignment.Center
            ) {
                Text(txn.icon, fontSize = 18.sp)
            }
            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(txn.merchant, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(TxnChipBg)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(txn.category, fontSize = 11.sp, color = TxnPurpleDark)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(txn.dateTime, fontSize = 11.sp, color = TxnTextSecondary)
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    (if (txn.isDebit) "-₹" else "+₹") + txn.amount,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = if (txn.isDebit) TxnRed else TxnGreen
                )
                Text(
                    if (txn.isDebit) "Debit" else "Credit",
                    fontSize = 10.sp,
                    color = TxnTextSecondary
                )
            }
        }
    }
}


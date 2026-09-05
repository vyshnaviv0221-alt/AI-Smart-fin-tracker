package com.example.aismartexpensetracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aismartexpensetracker.CategoryKeywords
import com.example.aismartexpensetracker.Expense
import com.example.aismartexpensetracker.ExpenseViewModel
import java.text.SimpleDateFormat
import java.util.Locale

private val TxnPurpleDark = Color(0xFF3C3489)
private val TxnBgGray = Color(0xFFF7F6FA)
private val TxnTextSecondary = Color(0xFF757575)
private val TxnRed = Color(0xFFD32F2F)
private val TxnChipBg = Color(0xFFEEEDFE)

@Composable
fun TransactionsScreen(viewModel: ExpenseViewModel = viewModel()) {
    val expenses by viewModel.expenses.collectAsState()
    val dateFormat = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }

    // Human-in-the-Loop: the transaction currently being re-categorized.
    var editing by remember { mutableStateOf<Expense?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(TxnBgGray)
            .padding(16.dp)
    ) {
        Text("Transactions", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TxnPurpleDark)
        Spacer(Modifier.height(4.dp))
        Text(
            if (expenses.isEmpty()) "No transactions captured yet"
            else "${expenses.size} captured · tap one to correct its category",
            fontSize = 13.sp,
            color = TxnTextSecondary
        )
        Spacer(Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(expenses, key = { it.id }) { expense ->
                TransactionRow(
                    expense = expense,
                    formattedDate = dateFormat.format(expense.date),
                    onClick = { editing = expense }
                )
            }
        }
    }

    editing?.let { expense ->
        CategoryCorrectionDialog(
            expense = expense,
            onDismiss = { editing = null },
            onPick = { newCategory ->
                viewModel.correctCategory(expense.id, newCategory)
                editing = null
            },
            onDelete = {
                viewModel.deleteExpense(expense.id)
                editing = null
            }
        )
    }
}

@Composable
private fun TransactionRow(expense: Expense, formattedDate: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
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
                        .background(TxnBgGray),
                    contentAlignment = Alignment.Center
                ) { Text(emojiFor(expense.category), fontSize = 16.sp) }
                Spacer(Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(expense.merchant, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        if (expense.isAnomaly) {
                            Spacer(Modifier.width(6.dp))
                            Text("UNUSUAL", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TxnRed)
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(TxnChipBg)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(expense.category, fontSize = 10.sp, color = TxnPurpleDark)
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(formattedDate, fontSize = 11.sp, color = TxnTextSecondary)
                    }
                }
            }
            Text(
                "-₹${"%,.0f".format(expense.amount)}",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = TxnRed
            )
        }
    }
}

/**
 * Human-in-the-Loop correction step (implementation plan §4.2, §10).
 *
 * When the classifier gets a category wrong the user overrides it here. The
 * write goes through ExpenseDao.updateCategory, so every screen reading the
 * same Room Flow updates at once.
 */
@Composable
private fun CategoryCorrectionDialog(
    expense: Expense,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Correct category") },
        text = {
            Column {
                Text(
                    "${expense.merchant} · ₹${"%,.0f".format(expense.amount)}",
                    fontSize = 13.sp,
                    color = TxnTextSecondary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Currently: ${expense.category}",
                    fontSize = 12.sp,
                    color = TxnTextSecondary
                )
                Spacer(Modifier.height(12.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(CategoryKeywords.ALL_CATEGORIES) { category ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPick(category) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(emojiFor(category), fontSize = 16.sp)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                category,
                                fontSize = 14.sp,
                                fontWeight = if (category == expense.category) FontWeight.Bold
                                else FontWeight.Normal,
                                color = if (category == expense.category) TxnPurpleDark
                                else Color.Unspecified
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        dismissButton = {
            TextButton(onClick = onDelete) { Text("Delete", color = TxnRed) }
        }
    )
}

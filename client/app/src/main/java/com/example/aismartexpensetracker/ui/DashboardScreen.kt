package com.example.aismartexpensetracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.aismartexpensetracker.Expense
import com.example.aismartexpensetracker.ExpenseViewModel
import java.text.SimpleDateFormat
import java.util.Locale

private val DashPurpleDark = Color(0xFF3C3489)
private val DashPurpleMid = Color(0xFF534AB7)
private val DashBgGray = Color(0xFFF7F6FA)
private val DashTextSecondary = Color(0xFF757575)
private val DashRed = Color(0xFFD32F2F)
private val DashTrackGray = Color(0xFFE0DDE8)

@Composable
fun DashboardScreen(
    navController: NavController? = null,
    viewModel: ExpenseViewModel = viewModel()
) {
    val monthExpenses by viewModel.expensesThisMonth.collectAsState()
    val categoryTotals by viewModel.categoryTotals.collectAsState()
    val budgets by viewModel.budgets.collectAsState()
    val isCategorizing by viewModel.isCategorizing.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    val totalSpent = monthExpenses.sumOf { it.amount }
    // Only shown once the user has actually set limits -- no invented target.
    val totalBudget = budgets.sumOf { it.monthlyLimit }.takeIf { budgets.isNotEmpty() }
    val anomalyCount = monthExpenses.count { it.isAnomaly }
    val recent = monthExpenses.take(5)
    val dateFormat = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .background(DashBgGray)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Dashboard", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = DashPurpleDark)
                if (navController != null) {
                    TextButton(onClick = { navController.navigate("menu") }) { Text("Menu") }
                }
            }

            Spacer(Modifier.height(16.dp))

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text("Spent this month", fontSize = 13.sp, color = DashTextSecondary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "₹${"%,.0f".format(totalSpent)}",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = DashPurpleDark
                    )

                    if (totalBudget != null && totalBudget > 0) {
                        val used = (totalSpent / totalBudget).coerceIn(0.0, 1.0).toFloat()
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { used },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = if (used >= 0.9f) DashRed else DashPurpleMid,
                            trackColor = DashTrackGray
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                "Budgeted ₹${"%,.0f".format(totalBudget)}",
                                fontSize = 12.sp,
                                color = DashTextSecondary
                            )
                            Text(
                                "₹${"%,.0f".format(totalBudget - totalSpent)} left",
                                fontSize = 12.sp,
                                color = DashTextSecondary
                            )
                        }
                    } else {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Set category limits in Budgets to track progress",
                            fontSize = 12.sp,
                            color = DashTextSecondary
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryCard(Modifier.weight(1f), "Transactions", monthExpenses.size.toString(), "📋")
                SummaryCard(Modifier.weight(1f), "Flagged unusual", anomalyCount.toString(), "⚠️")
            }

            Spacer(Modifier.height(20.dp))

            Text("Spend by category", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DashPurpleDark)
            Spacer(Modifier.height(12.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    if (categoryTotals.isEmpty()) {
                        Text(
                            "No expenses yet — tap + to add one, or grant notification " +
                                "access in Profile to capture them automatically.",
                            fontSize = 12.sp,
                            color = DashTextSecondary
                        )
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            categoryTotals.take(4).forEach { total ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(DashBgGray),
                                        contentAlignment = Alignment.Center
                                    ) { Text(emojiFor(total.category), fontSize = 16.sp) }
                                    Spacer(Modifier.height(4.dp))
                                    Text(total.category, fontSize = 10.sp, color = DashTextSecondary)
                                    Text(
                                        "₹${"%,.0f".format(total.amount)}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = DashPurpleDark
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Recent transactions", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DashPurpleDark)
                if (isCategorizing) {
                    Spacer(Modifier.width(8.dp))
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                }
            }
            Spacer(Modifier.height(12.dp))

            if (recent.isEmpty()) {
                Text("Nothing logged yet", fontSize = 12.sp, color = DashTextSecondary)
            } else {
                recent.forEach { expense ->
                    RecentExpenseRow(expense, dateFormat.format(expense.date))
                    Spacer(Modifier.height(10.dp))
                }
            }

            Spacer(Modifier.height(80.dp)) // room for the FAB
        }

        FloatingActionButton(
            onClick = { showAddDialog = true },
            containerColor = DashPurpleMid,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add expense")
        }
    }

    if (showAddDialog) {
        AddExpenseDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { merchant, amount ->
                viewModel.addExpense(merchant, amount)
                showAddDialog = false
            }
        )
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
private fun RecentExpenseRow(expense: Expense, formattedDate: String) {
    Card(Modifier.fillMaxWidth()) {
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
                ) { Text(emojiFor(expense.category), fontSize = 16.sp) }
                Spacer(Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(expense.merchant, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        if (expense.isAnomaly) {
                            Spacer(Modifier.width(6.dp))
                            Text("UNUSUAL", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = DashRed)
                        }
                    }
                    Text("${expense.category} · $formattedDate", fontSize = 11.sp, color = DashTextSecondary)
                }
            }
            Text(
                "-₹${"%,.0f".format(expense.amount)}",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = DashRed
            )
        }
    }
}

@Composable
private fun AddExpenseDialog(onDismiss: () -> Unit, onConfirm: (String, Double) -> Unit) {
    var merchant by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    val amountValue = amountText.toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add expense") },
        text = {
            Column {
                OutlinedTextField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    label = { Text("Merchant (e.g. Swiggy Order)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount (₹)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(merchant.trim(), amountValue ?: 0.0) },
                enabled = merchant.isNotBlank() && amountValue != null && amountValue > 0
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

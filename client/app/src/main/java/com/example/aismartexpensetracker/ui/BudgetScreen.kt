package com.example.aismartexpensetracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import com.example.aismartexpensetracker.BudgetStatus
import com.example.aismartexpensetracker.ExpenseViewModel

private val BudgetPurpleDark = Color(0xFF3C3489)
private val BudgetBgGray = Color(0xFFF7F6FA)
private val BudgetTextSecondary = Color(0xFF757575)
private val BudgetStatusGood = Color(0xFF534AB7)
private val BudgetStatusWarning = Color(0xFFF9A825)
private val BudgetStatusDanger = Color(0xFFD32F2F)
private val BudgetTrackGray = Color(0xFFE0DDE8)

/**
 * Budgets are entirely user-owned: no category starts with an invented limit.
 * The list shows the categories the user has actually spent in this month plus
 * any they've set a limit for, and spend is measured against the current
 * calendar month.
 */
@Composable
fun BudgetScreen(viewModel: ExpenseViewModel = viewModel()) {
    val statuses by viewModel.budgetStatuses.collectAsState()
    var editing by remember { mutableStateOf<BudgetStatus?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BudgetBgGray)
            .padding(16.dp)
    ) {
        Text(
            "Budgets & Alerts",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = BudgetPurpleDark
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (statuses.isEmpty()) "No spending recorded this month yet"
            else "This month · tap a category to set or change its limit",
            fontSize = 13.sp,
            color = BudgetTextSecondary
        )
        Spacer(Modifier.height(16.dp))

        if (statuses.isEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text("Nothing to budget yet", fontWeight = FontWeight.Bold, color = BudgetPurpleDark)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Once transactions are captured — automatically from notifications, " +
                            "or with the + button on the dashboard — each category appears " +
                            "here and you can set a monthly limit.",
                        fontSize = 13.sp,
                        color = BudgetTextSecondary
                    )
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(statuses, key = { it.category }) { status ->
                    BudgetCard(status) { editing = status }
                }
            }
        }
    }

    editing?.let { status ->
        SetLimitDialog(
            status = status,
            onDismiss = { editing = null },
            onSave = { limit ->
                viewModel.setBudget(status.category, limit)
                editing = null
            },
            onClear = {
                viewModel.clearBudget(status.category)
                editing = null
            }
        )
    }
}

@Composable
private fun BudgetCard(status: BudgetStatus, onClick: () -> Unit) {
    val ratio = status.ratio
    val barColor = when {
        status.isOver -> BudgetStatusDanger
        status.isNear -> BudgetStatusWarning
        else -> BudgetStatusGood
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(emojiFor(status.category), fontSize = 18.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(status.category, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                Text(
                    "₹${"%,.0f".format(status.spent)}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = if (status.isOver) BudgetStatusDanger else BudgetPurpleDark
                )
            }

            Spacer(Modifier.height(10.dp))

            if (status.limit == null) {
                Text(
                    "No limit set — tap to add one",
                    fontSize = 12.sp,
                    color = BudgetTextSecondary
                )
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(BudgetTrackGray)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth((ratio ?: 0.0).coerceIn(0.0, 1.0).toFloat())
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
                            .background(barColor)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "Limit ₹${"%,.0f".format(status.limit)}",
                        fontSize = 12.sp,
                        color = BudgetTextSecondary
                    )
                    Text(
                        when {
                            status.isOver ->
                                "₹${"%,.0f".format(status.spent - status.limit)} over"
                            else ->
                                "₹${"%,.0f".format(status.limit - status.spent)} left"
                        },
                        fontSize = 12.sp,
                        fontWeight = if (status.isOver) FontWeight.Bold else FontWeight.Normal,
                        color = if (status.isOver) BudgetStatusDanger else BudgetTextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun SetLimitDialog(
    status: BudgetStatus,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit,
    onClear: () -> Unit
) {
    var text by remember(status.category) {
        mutableStateOf(status.limit?.let { "%.0f".format(it) } ?: "")
    }
    val value = text.toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${status.category} budget") },
        text = {
            Column {
                Text(
                    "Spent this month: ₹${"%,.0f".format(status.spent)}",
                    fontSize = 13.sp,
                    color = BudgetTextSecondary
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Monthly limit (₹)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { value?.let(onSave) },
                enabled = value != null && value > 0
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (status.limit != null) {
                    TextButton(onClick = onClear) { Text("Remove") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

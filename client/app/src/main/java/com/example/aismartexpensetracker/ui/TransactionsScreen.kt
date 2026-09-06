package com.example.aismartexpensetracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aismartexpensetracker.CategoryKeywords
import com.example.aismartexpensetracker.Expense
import com.example.aismartexpensetracker.ExpenseViewModel
import com.example.aismartexpensetracker.ui.components.*
import com.example.aismartexpensetracker.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun TransactionsScreen(viewModel: ExpenseViewModel = viewModel()) {
    val expenses by viewModel.expenses.collectAsState()
    val dateFormat = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }

    // Human-in-the-Loop: the transaction currently being re-categorised.
    var editing by remember { mutableStateOf<Expense?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Canvas)
            .padding(horizontal = Space.lg)
    ) {
        Spacer(Modifier.height(Space.sm))
        ScreenTitle(
            text = "Transactions",
            subtitle = if (expenses.isEmpty()) "Nothing captured yet"
            else "${expenses.size} captured · tap one to correct its category"
        )
        Spacer(Modifier.height(Space.xl))

        if (expenses.isEmpty()) {
            EmptyState(
                icon = "🧾",
                title = "No transactions yet",
                message = "Grant notification access in Profile to capture bank and " +
                    "UPI alerts automatically, or add one by hand from the dashboard."
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(Space.md),
                contentPadding = PaddingValues(bottom = Space.xxxl)
            ) {
                items(expenses, key = { it.id }) { expense ->
                    PressableCard(
                        onClick = { editing = expense },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TransactionRow(expense, dateFormat.format(expense.date))
                    }
                }
            }
        }
    }

    editing?.let { expense ->
        CategoryCorrectionSheet(
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
private fun TransactionRow(expense: Expense, formattedDate: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(Space.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CategoryAvatar(expense.category)
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(expense.merchant, style = RowTitleStyle, color = Ink)
                if (expense.isAnomaly) {
                    Spacer(Modifier.width(Space.sm))
                    UnusualBadge()
                }
            }
            Spacer(Modifier.height(Space.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryChip(expense.category)
                Spacer(Modifier.width(Space.sm))
                Text(formattedDate, style = CaptionStyle, color = InkFaint)
            }
        }
        Spacer(Modifier.width(Space.sm))
        Text("−${rupees(expense.amount)}", style = RowTitleStyle, color = Ink)
    }
}

/**
 * Human-in-the-Loop correction (implementation plan sections 4.2 and 10).
 *
 * The current category is marked rather than merely highlighted, so the
 * screen answers "what is it now?" before "what could it be?". Committing a
 * correction fires a haptic: it is a real state change the user should feel,
 * and it is the one place in this screen where feedback is earned.
 */
@Composable
private fun CategoryCorrectionSheet(
    expense: Expense,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
    onDelete: () -> Unit
) {
    val haptics = LocalHapticFeedback.current

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = Radius.sheet,
        containerColor = SurfaceWhite,
        title = { Text("Correct category", style = SectionStyle, color = Ink) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryAvatar(expense.category, size = 36.dp)
                    Spacer(Modifier.width(Space.md))
                    Column {
                        Text(expense.merchant, style = RowTitleStyle, color = Ink)
                        Text(
                            "${rupees(expense.amount)} · currently ${expense.category}",
                            style = CaptionStyle,
                            color = InkMuted
                        )
                    }
                }

                Spacer(Modifier.height(Space.lg))
                RowDivider()
                Spacer(Modifier.height(Space.sm))

                Column(Modifier.verticalScroll(rememberScrollState())) {
                    CategoryKeywords.ALL_CATEGORIES.forEach { category ->
                        val selected = category == expense.category
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(Radius.chip)
                                .clickable {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onPick(category)
                                }
                                .background(if (selected) Indigo50 else SurfaceWhite)
                                .padding(horizontal = Space.sm, vertical = Space.md),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CategoryAvatar(category, size = 32.dp)
                            Spacer(Modifier.width(Space.md))
                            Text(
                                category,
                                style = RowTitleStyle,
                                color = if (selected) Indigo700 else Ink,
                                modifier = Modifier.weight(1f)
                            )
                            if (selected) {
                                Text("Current", style = LabelStyle, color = Indigo500)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", style = RowTitleStyle, color = InkMuted)
            }
        },
        dismissButton = {
            TextButton(onClick = onDelete) {
                Text("Delete", style = RowTitleStyle, color = Danger)
            }
        }
    )
}

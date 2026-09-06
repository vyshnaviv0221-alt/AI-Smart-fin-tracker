package com.example.aismartexpensetracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aismartexpensetracker.BudgetStatus
import com.example.aismartexpensetracker.ExpenseViewModel
import com.example.aismartexpensetracker.ui.components.*
import com.example.aismartexpensetracker.ui.theme.*

/**
 * Budgets are entirely user-owned: no category starts with an invented limit.
 * The list shows the categories the user has actually spent in this month plus
 * any they have set a limit for, measured against the current calendar month.
 */
@Composable
fun BudgetScreen(viewModel: ExpenseViewModel = viewModel()) {
    val statuses by viewModel.budgetStatuses.collectAsState()
    var editing by remember { mutableStateOf<BudgetStatus?>(null) }

    val overCount = statuses.count { it.isOver }

    Column(
        Modifier
            .fillMaxSize()
            .background(Canvas)
            .padding(horizontal = Space.lg)
    ) {
        Spacer(Modifier.height(Space.sm))
        ScreenTitle(
            text = "Budgets",
            subtitle = when {
                statuses.isEmpty() -> "No spending recorded this month"
                overCount > 0 -> "$overCount over limit · tap a category to adjust"
                else -> "This month · tap a category to set a limit"
            }
        )
        Spacer(Modifier.height(Space.xl))

        if (statuses.isEmpty()) {
            EmptyState(
                icon = "🎯",
                title = "Nothing to budget yet",
                message = "Once transactions are captured, each category appears " +
                    "here and you can give it a monthly limit."
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(Space.md),
                contentPadding = PaddingValues(bottom = Space.xxxl)
            ) {
                items(statuses, key = { it.category }) { status ->
                    PressableCard(
                        onClick = { editing = status },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        BudgetRow(status)
                    }
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
private fun BudgetRow(status: BudgetStatus) {
    val ratio = status.ratio
    val barColor = when {
        status.isOver -> Danger
        status.isNear -> Warning
        else -> categoryColor(status.category)
    }

    Column(Modifier.padding(Space.lg)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryAvatar(status.category)
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text(status.category, style = RowTitleStyle, color = Ink)
                Spacer(Modifier.height(Space.xxs))
                Text(
                    if (status.limit == null) "No limit set"
                    else "of ${rupees(status.limit)}",
                    style = CaptionStyle,
                    color = InkMuted
                )
            }
            Text(
                rupees(status.spent),
                style = RowTitleStyle,
                color = if (status.isOver) Danger else Ink
            )
        }

        if (status.limit != null) {
            Spacer(Modifier.height(Space.md))
            AnimatedBar(progress = (ratio ?: 0.0).toFloat(), color = barColor)
            Spacer(Modifier.height(Space.sm))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "${((ratio ?: 0.0) * 100).toInt()}% used",
                    style = CaptionStyle,
                    color = InkMuted
                )
                Text(
                    if (status.isOver) "${rupees(status.spent - status.limit)} over"
                    else "${rupees(status.limit - status.spent)} left",
                    style = CaptionStyle,
                    color = if (status.isOver) Danger else if (status.isNear) Warning else InkMuted
                )
            }
        } else {
            Spacer(Modifier.height(Space.md))
            Text("Tap to set a monthly limit", style = CaptionStyle, color = Indigo500)
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
    val haptics = LocalHapticFeedback.current
    var text by remember(status.category) {
        mutableStateOf(status.limit?.let { "%.0f".format(it) } ?: "")
    }
    val value = text.toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = Radius.sheet,
        containerColor = SurfaceWhite,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryAvatar(status.category, size = 36.dp)
                Spacer(Modifier.width(Space.md))
                Text(status.category, style = SectionStyle, color = Ink)
            }
        },
        text = {
            Column {
                Text(
                    "Spent this month: ${rupees(status.spent)}",
                    style = CaptionStyle,
                    color = InkMuted
                )
                Spacer(Modifier.height(Space.lg))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Monthly limit") },
                    prefix = { Text("₹") },
                    singleLine = true,
                    shape = Radius.chip,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                if (value != null && value > 0 && status.spent > value) {
                    Spacer(Modifier.height(Space.md))
                    Text(
                        "You have already spent ${rupees(status.spent - value)} more " +
                            "than this limit this month.",
                        style = CaptionStyle,
                        color = Warning
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    value?.let(onSave)
                },
                enabled = value != null && value > 0
            ) { Text("Save", style = RowTitleStyle) }
        },
        dismissButton = {
            Row {
                if (status.limit != null) {
                    TextButton(onClick = onClear) {
                        Text("Remove", style = RowTitleStyle, color = Danger)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel", style = RowTitleStyle, color = InkMuted)
                }
            }
        }
    )
}

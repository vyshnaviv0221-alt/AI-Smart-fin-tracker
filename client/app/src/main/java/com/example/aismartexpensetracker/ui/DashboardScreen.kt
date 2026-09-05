package com.example.aismartexpensetracker.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.aismartexpensetracker.AddResult
import com.example.aismartexpensetracker.CategoryTotal
import com.example.aismartexpensetracker.Expense
import com.example.aismartexpensetracker.ExpenseViewModel
import com.example.aismartexpensetracker.ui.components.*
import com.example.aismartexpensetracker.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun DashboardScreen(
    navController: NavController? = null,
    viewModel: ExpenseViewModel = viewModel()
) {
    val monthExpenses by viewModel.expensesThisMonth.collectAsState()
    val categoryTotals by viewModel.categoryTotals.collectAsState()
    val budgets by viewModel.budgets.collectAsState()
    val isCategorizing by viewModel.isCategorizing.collectAsState()
    val addResult by viewModel.addResult.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Confirm what happened after an add. Previously the dialog just closed,
    // so a suppressed duplicate looked identical to a successful save.
    LaunchedEffect(addResult) {
        when (val r = addResult) {
            is AddResult.Added ->
                snackbarHostState.showSnackbar("Added ${r.merchant} as ${r.category}")
            is AddResult.Duplicate ->
                snackbarHostState.showSnackbar("${r.merchant} looks like a repeat — not added")
            null -> Unit
        }
        if (addResult != null) viewModel.clearAddResult()
    }

    val totalSpent = monthExpenses.sumOf { it.amount }
    // Only shown once the user has actually set limits -- no invented target.
    val totalBudget = budgets.sumOf { it.monthlyLimit }.takeIf { budgets.isNotEmpty() }
    val anomalyCount = monthExpenses.count { it.isAnomaly }
    val recent = monthExpenses.take(4)
    val dateFormat = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }

    Box(Modifier.fillMaxSize().background(Canvas)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.lg)
        ) {
            Spacer(Modifier.height(Space.sm))
            ScreenTitle(
                text = "Dashboard",
                subtitle = "This month",
                trailing = {
                    if (navController != null) {
                        TextButton(onClick = { navController.navigate("menu") }) {
                            Text("Menu", style = RowTitleStyle, color = Indigo500)
                        }
                    }
                }
            )
            Spacer(Modifier.height(Space.xl))

            SpendHeroCard(totalSpent, totalBudget)

            Spacer(Modifier.height(Space.md))

            Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                StatTile(
                    icon = "🧾",
                    value = monthExpenses.size.toString(),
                    label = "Transactions",
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    icon = "⚠️",
                    value = anomalyCount.toString(),
                    label = "Flagged unusual",
                    modifier = Modifier.weight(1f),
                    valueColor = if (anomalyCount > 0) Warning else Ink
                )
            }

            Spacer(Modifier.height(Space.xxl))

            SectionHeader("Spend by category")
            if (categoryTotals.isEmpty()) {
                EmptyState(
                    icon = "📊",
                    title = "Nothing tracked yet",
                    message = "Add an expense with +, or turn on notification access " +
                        "in Profile to capture bank and UPI alerts automatically."
                )
            } else {
                AppCard(Modifier.fillMaxWidth()) {
                    val max = categoryTotals.maxOf { it.amount }
                    categoryTotals.forEachIndexed { index, total ->
                        CategoryBreakdownRow(total, max)
                        if (index != categoryTotals.lastIndex) {
                            RowDivider(Modifier.padding(start = 68.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(Space.xxl))

            SectionHeader(
                text = "Recent",
                trailing = {
                    if (isCategorizing) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                Modifier.size(13.dp),
                                strokeWidth = 2.dp,
                                color = Indigo500
                            )
                            Spacer(Modifier.width(Space.sm))
                            Text("Categorising", style = LabelStyle, color = InkMuted)
                        }
                    } else if (navController != null && monthExpenses.isNotEmpty()) {
                        TextButton(onClick = { navController.navigate("transactions") }) {
                            Text("See all", style = CaptionStyle, color = Indigo500)
                        }
                    }
                }
            )

            if (recent.isEmpty()) {
                EmptyState(
                    icon = "💤",
                    title = "Nothing logged yet",
                    message = "Captured transactions will appear here as they arrive."
                )
            } else {
                AppCard(Modifier.fillMaxWidth()) {
                    recent.forEachIndexed { index, expense ->
                        ExpenseRow(expense, dateFormat.format(expense.date))
                        if (index != recent.lastIndex) {
                            RowDivider(Modifier.padding(start = 68.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(Space.fabClearance))
        }

        AddFab(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(Space.xl)
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = Space.fabClearance)
        )
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

/**
 * The headline number.
 *
 * The amount counts up to its new value rather than cutting, so an expense
 * landing from the notification listener is visible as a change even if the
 * user wasn't looking at the moment it arrived.
 */
@Composable
private fun SpendHeroCard(totalSpent: Double, totalBudget: Double?) {
    val reduced = LocalReducedMotion.current
    val shown by animateFloatAsState(
        targetValue = totalSpent.toFloat(),
        animationSpec = if (reduced) snap() else Motion.gentle(),
        label = "totalSpent"
    )

    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Space.xl)) {
            Text("Spent this month", style = CaptionStyle, color = InkMuted)
            Spacer(Modifier.height(Space.xs))
            Text(rupees(shown.toDouble()), style = AmountStyle, color = Indigo900)

            if (totalBudget != null && totalBudget > 0) {
                val ratio = (totalSpent / totalBudget).toFloat()
                val over = totalSpent > totalBudget
                Spacer(Modifier.height(Space.lg))
                AnimatedBar(
                    progress = ratio,
                    color = when {
                        over -> Danger
                        ratio >= 0.85f -> Warning
                        else -> Indigo500
                    }
                )
                Spacer(Modifier.height(Space.sm))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("of ${rupees(totalBudget)} budgeted", style = CaptionStyle, color = InkMuted)
                    Text(
                        if (over) "${rupees(totalSpent - totalBudget)} over"
                        else "${rupees(totalBudget - totalSpent)} left",
                        style = CaptionStyle,
                        color = if (over) Danger else InkMuted
                    )
                }
            } else {
                Spacer(Modifier.height(Space.sm))
                Text(
                    "Set category limits in Budgets to track progress",
                    style = CaptionStyle,
                    color = InkFaint
                )
            }
        }
    }
}

/**
 * One category, as a row rather than a cramped column.
 *
 * The previous layout spread up to four circles with SpaceBetween, which left
 * a large gap when only two categories existed and truncated the labels when
 * there were four. Rows scale to any number and give each category a bar you
 * can actually compare.
 */
@Composable
private fun CategoryBreakdownRow(total: CategoryTotal, maxAmount: Double) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.lg, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CategoryAvatar(total.category)
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(total.category, style = RowTitleStyle, color = Ink)
                Text(rupees(total.amount), style = RowTitleStyle, color = Ink)
            }
            Spacer(Modifier.height(Space.sm))
            AnimatedBar(
                progress = if (maxAmount > 0) (total.amount / maxAmount).toFloat() else 0f,
                color = categoryColor(total.category),
                height = 6.dp
            )
        }
    }
}

@Composable
private fun ExpenseRow(expense: Expense, formattedDate: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.lg, vertical = Space.md),
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
            Spacer(Modifier.height(Space.xxs))
            Text("${expense.category} · $formattedDate", style = CaptionStyle, color = InkMuted)
        }
        Spacer(Modifier.width(Space.sm))
        Text("−${rupees(expense.amount)}", style = RowTitleStyle, color = Ink)
    }
}

@Composable
fun UnusualBadge() {
    Box(
        Modifier
            .clip(Radius.chip)
            .background(WarningSoft)
            .padding(horizontal = Space.sm, vertical = 2.dp)
    ) {
        Text("UNUSUAL", style = LabelStyle, color = Warning)
    }
}

/** The FAB gets the same press physics as every other touch target. */
@Composable
private fun AddFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interactionSource)

    FloatingActionButton(
        onClick = onClick,
        modifier = modifier.scale(scale),
        containerColor = Indigo500,
        contentColor = SurfaceWhite,
        shape = Radius.card,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = Elevation.fab),
        interactionSource = interactionSource
    ) {
        Icon(Icons.Filled.Add, contentDescription = "Add expense")
    }
}

@Composable
private fun AddExpenseDialog(onDismiss: () -> Unit, onConfirm: (String, Double) -> Unit) {
    var merchant by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    val amountValue = amountText.toDoubleOrNull()
    val valid = merchant.isNotBlank() && amountValue != null && amountValue > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = Radius.sheet,
        containerColor = SurfaceWhite,
        title = { Text("Add expense", style = SectionStyle, color = Ink) },
        text = {
            Column {
                Text(
                    "The merchant name is what the model categorises, so keep it " +
                        "as it appears on the transaction.",
                    style = CaptionStyle,
                    color = InkMuted
                )
                Spacer(Modifier.height(Space.lg))
                OutlinedTextField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    label = { Text("Merchant") },
                    placeholder = { Text("Swiggy Order") },
                    singleLine = true,
                    shape = Radius.chip,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(Space.md))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount") },
                    prefix = { Text("₹") },
                    singleLine = true,
                    shape = Radius.chip,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(merchant.trim(), amountValue ?: 0.0) }, enabled = valid) {
                Text("Add", style = RowTitleStyle)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", style = RowTitleStyle, color = InkMuted) }
        }
    )
}

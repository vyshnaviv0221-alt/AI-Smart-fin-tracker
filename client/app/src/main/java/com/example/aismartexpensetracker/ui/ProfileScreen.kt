package com.example.aismartexpensetracker.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aismartexpensetracker.CloudState
import com.example.aismartexpensetracker.ExpenseViewModel
import com.example.aismartexpensetracker.ui.components.*
import com.example.aismartexpensetracker.ui.theme.*

private fun isListenerEnabled(context: Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

@Composable
fun ProfileScreen(
    onLogout: () -> Unit = {},
    viewModel: ExpenseViewModel = viewModel()
) {
    val context = LocalContext.current
    val stats by viewModel.profileStats.collectAsState()
    val email by viewModel.signedInEmail.collectAsState()
    val cloudState by viewModel.cloudState.collectAsState()
    var listenerEnabled by remember { mutableStateOf(isListenerEnabled(context)) }
    var showLogoutConfirm by remember { mutableStateOf(false) }

    // Re-check on resume: the user grants access in system Settings and returns.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                listenerEnabled = isListenerEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Canvas)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.lg)
    ) {
        Spacer(Modifier.height(Space.sm))
        ScreenTitle(text = "Profile", subtitle = "Account, sync and permissions")
        Spacer(Modifier.height(Space.xl))

        // ---- Account ----
        AppCard(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(Space.xl), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Indigo500),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        (email?.firstOrNull() ?: '?').uppercaseChar().toString(),
                        style = StatStyle,
                        color = SurfaceWhite
                    )
                }
                Spacer(Modifier.width(Space.lg))
                Column {
                    Text(email ?: "Not signed in", style = RowTitleStyle, color = Ink)
                    Spacer(Modifier.height(Space.xxs))
                    Text(
                        when {
                            email != null -> "Synced with Supabase"
                            !viewModel.cloudConfigured -> "Cloud sync not configured"
                            else -> "Saved on this device only"
                        },
                        style = CaptionStyle,
                        color = InkMuted
                    )
                }
            }
        }

        if (email != null) {
            Spacer(Modifier.height(Space.md))
            AppCard(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(Space.xl),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Cloud sync", style = RowTitleStyle, color = Ink)
                        Spacer(Modifier.height(Space.xxs))
                        Text(
                            when (val s = cloudState) {
                                is CloudState.Busy -> "Syncing…"
                                is CloudState.Message -> s.text
                                else -> "Push local transactions to Supabase"
                            },
                            style = CaptionStyle,
                            color = if ((cloudState as? CloudState.Message)?.isError == true)
                                Danger else InkMuted
                        )
                    }
                    Spacer(Modifier.width(Space.md))
                    Button(
                        onClick = { viewModel.syncNow() },
                        enabled = cloudState !is CloudState.Busy,
                        shape = Radius.chip,
                        colors = ButtonDefaults.buttonColors(containerColor = Indigo500)
                    ) { Text("Sync", style = RowTitleStyle) }
                }
            }
        }

        Spacer(Modifier.height(Space.md))

        // ---- Notification access: nothing is captured without it ----
        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(Space.xl)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Notification access", style = RowTitleStyle, color = Ink)
                        Spacer(Modifier.height(Space.xxs))
                        Text(
                            if (listenerEnabled) "Granted — capturing bank and UPI alerts"
                            else "Not granted — automatic capture is off",
                            style = CaptionStyle,
                            color = if (listenerEnabled) Success else Danger
                        )
                    }
                    if (!listenerEnabled) {
                        Spacer(Modifier.width(Space.md))
                        Button(
                            onClick = {
                                context.startActivity(
                                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                )
                            },
                            shape = Radius.chip,
                            colors = ButtonDefaults.buttonColors(containerColor = Indigo500)
                        ) { Text("Grant", style = RowTitleStyle) }
                    }
                }
                if (!listenerEnabled) {
                    Spacer(Modifier.height(Space.md))
                    Text(
                        "Find “AI SMART EXPENSE TRACKER” in the list and enable it. " +
                            "Only notifications from payment and banking apps are read; " +
                            "no credentials, PINs or OTPs are accessed.",
                        style = CaptionStyle,
                        color = InkMuted
                    )
                }
            }
        }

        Spacer(Modifier.height(Space.xxl))
        SectionHeader("Your data")

        AppCard(Modifier.fillMaxWidth()) {
            val rows = listOf(
                "Transactions captured" to stats.transactionCount.toString(),
                "Categories used" to stats.categoriesUsed.toString(),
                "Spent this month" to rupees(stats.totalSpentThisMonth),
                "Spent all time" to rupees(stats.totalSpentAllTime),
                "Flagged unusual" to stats.anomalyCount.toString(),
                "Budgets set" to stats.budgetsSet.toString()
            )
            rows.forEachIndexed { index, (label, value) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Space.lg, vertical = Space.lg),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(label, style = BodyStyle, color = InkMuted)
                    Text(value, style = RowTitleStyle, color = Ink)
                }
                if (index != rows.lastIndex) RowDivider(Modifier.padding(horizontal = Space.lg))
            }
        }

        Spacer(Modifier.height(Space.xxl))

        OutlinedButton(
            onClick = { showLogoutConfirm = true },
            modifier = Modifier.fillMaxWidth(),
            shape = Radius.chip,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Danger)
        ) {
            Text(
                if (email != null) "Sign out" else "Go to sign in",
                style = RowTitleStyle
            )
        }

        Spacer(Modifier.height(Space.xxxl))
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            shape = Radius.sheet,
            containerColor = SurfaceWhite,
            title = {
                Text(
                    if (email != null) "Sign out?" else "Go to sign in?",
                    style = SectionStyle,
                    color = Ink
                )
            },
            text = {
                Text(
                    if (email != null)
                        "Your transactions stay on this device. Cloud sync stops " +
                            "until you sign in again."
                    else "You'll be taken to the sign-in screen.",
                    style = BodyStyle,
                    color = InkMuted
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (email != null) viewModel.signOut()
                    showLogoutConfirm = false
                    onLogout()
                }) { Text("Continue", style = RowTitleStyle, color = Danger) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) {
                    Text("Cancel", style = RowTitleStyle, color = InkMuted)
                }
            }
        )
    }
}

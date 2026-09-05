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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aismartexpensetracker.CloudState
import com.example.aismartexpensetracker.ExpenseViewModel

private val ProfPurpleDark = Color(0xFF3C3489)
private val ProfPurpleMid = Color(0xFF534AB7)
private val ProfBgGray = Color(0xFFF7F6FA)
private val ProfTextSecondary = Color(0xFF757575)
private val ProfDangerRed = Color(0xFFD32F2F)
private val ProfGood = Color(0xFF1D9E75)

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

    // Re-check on resume: the user grants access in system Settings and comes back.
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
            .background(ProfBgGray)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            "Profile & Settings",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = ProfPurpleDark
        )
        Spacer(Modifier.height(20.dp))

        // ---- Account ----
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(ProfPurpleMid),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        (email?.firstOrNull() ?: '?').uppercaseChar().toString(),
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        email ?: "Not signed in",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = ProfPurpleDark
                    )
                    Text(
                        when {
                            email != null -> "Signed in with Supabase"
                            !viewModel.cloudConfigured ->
                                "Cloud sync not configured — saved on this device only"
                            else -> "Not signed in — saved on this device only"
                        },
                        fontSize = 12.sp,
                        color = ProfTextSecondary
                    )
                }
            }
        }

        if (email != null) {
            Spacer(Modifier.height(12.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Cloud sync", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                when (val s = cloudState) {
                                    is CloudState.Busy -> "Syncing…"
                                    is CloudState.Message -> s.text
                                    else -> "Push local transactions to Supabase"
                                },
                                fontSize = 12.sp,
                                color = if ((cloudState as? CloudState.Message)?.isError == true)
                                    ProfDangerRed else ProfTextSecondary
                            )
                        }
                        Button(
                            onClick = { viewModel.syncNow() },
                            enabled = cloudState !is CloudState.Busy
                        ) { Text("Sync") }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ---- Notification access: the app cannot capture anything without it ----
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Notification access", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            if (listenerEnabled) "Granted — capturing bank and UPI alerts"
                            else "Not granted — automatic capture is off",
                            fontSize = 12.sp,
                            color = if (listenerEnabled) ProfGood else ProfDangerRed
                        )
                    }
                    if (!listenerEnabled) {
                        Button(onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            )
                        }) { Text("Grant") }
                    }
                }
                if (!listenerEnabled) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Find “AI SMART EXPENSE TRACKER” in the list and enable it. " +
                            "Only notifications from payment and banking apps are read; " +
                            "no credentials, PINs or OTPs are accessed.",
                        fontSize = 12.sp,
                        color = ProfTextSecondary
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ---- Real usage stats ----
        Text("Your data", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ProfPurpleDark)
        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(4.dp)) {
                StatRow("Transactions captured", stats.transactionCount.toString())
                HorizontalDivider()
                StatRow("Categories used", stats.categoriesUsed.toString())
                HorizontalDivider()
                StatRow("Spent this month", "₹${"%,.0f".format(stats.totalSpentThisMonth)}")
                HorizontalDivider()
                StatRow("Spent all time", "₹${"%,.0f".format(stats.totalSpentAllTime)}")
                HorizontalDivider()
                StatRow("Flagged unusual", stats.anomalyCount.toString())
                HorizontalDivider()
                StatRow("Budgets set", stats.budgetsSet.toString())
            }
        }

        Spacer(Modifier.height(24.dp))

        OutlinedButton(
            onClick = { showLogoutConfirm = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = ProfDangerRed)
        ) { Text(if (email != null) "Sign out" else "Go to sign in") }

        Spacer(Modifier.height(24.dp))
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text(if (email != null) "Sign out?" else "Go to sign in?") },
            text = {
                Text(
                    if (email != null)
                        "Your transactions stay on this device. Cloud sync stops until " +
                            "you sign in again."
                    else "You'll be taken to the sign-in screen."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (email != null) viewModel.signOut()
                    showLogoutConfirm = false
                    onLogout()
                }) { Text("Continue") }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 14.sp, color = ProfTextSecondary)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ProfPurpleDark)
    }
}

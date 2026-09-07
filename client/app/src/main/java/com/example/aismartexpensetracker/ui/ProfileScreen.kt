package com.example.aismartexpensetracker.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhoneAndroid
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
import com.example.aismartexpensetracker.ExpenseViewModel
import com.example.aismartexpensetracker.ui.components.*
import com.example.aismartexpensetracker.ui.theme.*

private fun isListenerEnabled(context: Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

@Composable
fun ProfileScreen(
    viewModel: ExpenseViewModel = viewModel()
) {
    val context = LocalContext.current
    val stats by viewModel.profileStats.collectAsState()
    var listenerEnabled by remember { mutableStateOf(isListenerEnabled(context)) }

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
        ScreenTitle(text = "Profile", subtitle = "Storage and permissions")
        Spacer(Modifier.height(Space.xl))

        // ---- Where the data lives ----
        // There is no account. Everything is in Room on this device, which is
        // worth stating plainly: a user who sees no sign-in should know that
        // means "private", not "not set up yet".
        AppCard(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(Space.xl), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Indigo500),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.PhoneAndroid,
                        contentDescription = null,
                        tint = SurfaceWhite
                    )
                }
                Spacer(Modifier.width(Space.lg))
                Column {
                    Text("Stored on this device", style = RowTitleStyle, color = Ink)
                    Spacer(Modifier.height(Space.xxs))
                    Text(
                        "No account, no cloud. Your transactions never leave this phone.",
                        style = CaptionStyle,
                        color = InkMuted
                    )
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

        Spacer(Modifier.height(Space.xxxl))
    }
}

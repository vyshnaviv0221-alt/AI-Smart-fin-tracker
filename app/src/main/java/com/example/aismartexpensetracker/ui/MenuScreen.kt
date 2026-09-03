package com.example.aismartexpensetracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Smart Expense Tracker", color = Color(0xFF534AB7)) },
                modifier = Modifier.background(MaterialTheme.colorScheme.primary)
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            item {
                MenuItem(icon = Icons.Default.Home, title = "Dashboard") {
                    navController.navigate("dashboard")
                }
            }
            item {
                MenuItem(icon = Icons.Default.List, title = "Transactions") {
                    navController.navigate("transactions")
                }
            }
            item {
                MenuItem(icon = Icons.Default.Notifications, title = "Budgets & Alerts") {
                    navController.navigate("budgets")
                }
            }
            item {
                MenuItem(icon = Icons.Default.Info, title = "Analytics") {
                    navController.navigate("analytics")
                }
            }
            item {
                MenuItem(icon = Icons.Default.Star, title = "Predictions") {
                    navController.navigate("predictions")
                }
            }
            item {
                MenuItem(icon = Icons.Default.Favorite, title = "Recommendations") {
                    navController.navigate("recommendations")
                }
            }
            item {
                MenuItem(icon = Icons.Default.Settings, title = "Profile & Settings") {
                    navController.navigate("profile")
                }
            }
        }
    }
}

@Composable
fun MenuItem(icon: ImageVector, title: String, onClick: () -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(20.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
        }
        Divider(
            modifier = Modifier.fillMaxWidth(),
            thickness = 1.dp,
            color = Color.LightGray.copy(alpha = 0.4f)
        )
    }
}
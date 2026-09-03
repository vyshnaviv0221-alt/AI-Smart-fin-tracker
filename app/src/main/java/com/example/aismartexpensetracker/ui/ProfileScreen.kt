package com.example.aismartexpensetracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ============================================================
// Local theme colors (self-contained — same pattern as
// LoginScreen.kt / PredictionsScreen.kt / RecommendationsScreen.kt).
// ============================================================
private val ProfPurpleDark = Color(0xFF3C3489)
private val ProfPurpleMid = Color(0xFF534AB7)
private val ProfBgGray = Color(0xFFF7F6FA)
private val ProfTextSecondary = Color(0xFF757575)
private val ProfDangerRed = Color(0xFFD32F2F)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onLogout: () -> Unit = {}
) {
    // Sample user info — replace with real data from
    // FirebaseAuth.getInstance().currentUser once wired up
    val userName = "Vyshnavi V"
    val userEmail = "vyshnavi@example.com"

    var notificationsEnabled by remember { mutableStateOf(true) }
    var darkModeEnabled by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }

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

        // ---- User info card ----
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(ProfPurpleMid),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        userName.first().toString(),
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(userName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(2.dp))
                    Text(userEmail, fontSize = 13.sp, color = ProfTextSecondary)
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ---- Account settings ----
        Text(
            "Account settings",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = ProfPurpleDark
        )
        Spacer(Modifier.height(10.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                SettingsRow(
                    label = "Notification access",
                    subtitle = "Read transaction alerts automatically",
                    checked = notificationsEnabled,
                    onCheckedChange = { notificationsEnabled = it }
                )
                Divider(color = Color(0xFFECEAF3))
                SettingsRow(
                    label = "Dark mode",
                    subtitle = "Switch to a darker app theme",
                    checked = darkModeEnabled,
                    onCheckedChange = { darkModeEnabled = it }
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        // ---- Logout ----
        if (showLogoutConfirm) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Log out of your account?",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "You'll need to log in again to access your expenses.",
                        fontSize = 13.sp,
                        color = ProfTextSecondary
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        OutlinedButton(onClick = { showLogoutConfirm = false }) {
                            Text("Cancel")
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                showLogoutConfirm = false
                                // UI-only for now — wire to
                                // FirebaseAuth.getInstance().signOut() here later
                                onLogout()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ProfDangerRed)
                        ) {
                            Text("Log out")
                        }
                    }
                }
            }
        } else {
            OutlinedButton(
                onClick = { showLogoutConfirm = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ProfDangerRed)
            ) {
                Text("Log out", fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun SettingsRow(
    label: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, fontSize = 12.sp, color = ProfTextSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = ProfPurpleMid)
        )
    }
}
package com.example.aismartexpensetracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aismartexpensetracker.CloudState
import com.example.aismartexpensetracker.ExpenseViewModel

private val LoginPurpleDark = Color(0xFF3C3489)
private val LoginPurpleMid = Color(0xFF534AB7)
private val LoginBgGray = Color(0xFFF7F6FA)
private val LoginTextSecondary = Color(0xFF757575)
private val LoginErrorRed = Color(0xFFD32F2F)

/**
 * Supabase email/password authentication (GoTrue).
 *
 * Signing in is optional by design: capture, categorisation, budgets and
 * insights all work on-device with Room as the source of truth. An account
 * only adds cloud sync, so "Skip" is a first-class choice rather than a way
 * around a broken screen.
 */
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit = {},
    viewModel: ExpenseViewModel = viewModel()
) {
    var isRegisterMode by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    val cloudState by viewModel.cloudState.collectAsState()
    val signedInEmail by viewModel.signedInEmail.collectAsState()
    val busy = cloudState is CloudState.Busy

    // Navigate on a real session appearing, not on the button press.
    LaunchedEffect(signedInEmail) {
        if (signedInEmail != null) onLoginSuccess()
    }

    val remoteMessage = (cloudState as? CloudState.Message)?.takeIf { it.isError }?.text
    val errorText = localError ?: remoteMessage

    fun submit() {
        val trimmed = email.trim()
        localError = when {
            trimmed.isBlank() || password.isBlank() -> "Please fill in both fields"
            !trimmed.contains("@") -> "Enter a valid email address"
            password.length < 6 -> "Password must be at least 6 characters"
            else -> null
        }
        if (localError != null) return

        viewModel.clearCloudMessage()
        if (isRegisterMode) viewModel.signUp(trimmed, password)
        else viewModel.signIn(trimmed, password)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LoginBgGray)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Smart Expense Tracker",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = LoginPurpleDark
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (isRegisterMode) "Create an account to sync across devices"
            else "Sign in to sync across devices",
            fontSize = 14.sp,
            color = LoginTextSecondary
        )

        if (!viewModel.cloudConfigured) {
            Spacer(Modifier.height(16.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("Cloud sync not configured", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Add supabase.url and supabase.anonKey to client/local.properties " +
                            "and rebuild. Everything else works without it.",
                        fontSize = 12.sp,
                        color = LoginTextSecondary
                    )
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it; localError = null },
            label = { Text("Email") },
            singleLine = true,
            enabled = !busy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it; localError = null },
            label = { Text("Password") },
            singleLine = true,
            enabled = !busy,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )

        errorText?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = LoginErrorRed, fontSize = 13.sp)
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = { submit() },
            enabled = !busy && viewModel.cloudConfigured,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = LoginPurpleMid)
        ) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = Color.White
                )
            } else {
                Text(if (isRegisterMode) "Register" else "Sign in", fontSize = 16.sp)
            }
        }

        Spacer(Modifier.height(8.dp))

        TextButton(
            onClick = { isRegisterMode = !isRegisterMode; localError = null; viewModel.clearCloudMessage() },
            enabled = !busy,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text(
                if (isRegisterMode) "Already have an account? Sign in"
                else "Don't have an account? Register",
                color = LoginPurpleDark,
                fontSize = 13.sp
            )
        }

        TextButton(
            onClick = onLoginSuccess,
            enabled = !busy,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("Skip — use without an account", color = LoginTextSecondary, fontSize = 13.sp)
        }
    }
}

package com.example.aismartexpensetracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ============================================================
// THEME COLORS — kept inside this file, with unique names
// (prefixed "Login") so they can never collide with any other
// color declared anywhere else in the project.
// ============================================================
private val LoginPurpleDark = Color(0xFF3C3489)
private val LoginPurpleMid = Color(0xFF534AB7)
private val LoginBgGray = Color(0xFFF7F6FA)
private val LoginTextSecondary = Color(0xFF757575)
private val LoginErrorRed = Color(0xFFD32F2F)

// ============================================================
// LOGIN / REGISTRATION SCREEN
// ============================================================
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit = {}
) {
    var isRegisterMode by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

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
            if (isRegisterMode) "Create your account" else "Log in to continue",
            fontSize = 14.sp,
            color = LoginTextSecondary
        )
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it; errorText = null },
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it; errorText = null },
            label = { Text("Password") },
            singleLine = true,
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
            onClick = {
                when {
                    email.isBlank() || password.isBlank() ->
                        errorText = "Please fill in both fields"
                    !email.contains("@") ->
                        errorText = "Enter a valid email address"
                    password.length < 6 ->
                        errorText = "Password must be at least 6 characters"
                    else -> {
                        errorText = null
                        // UI-only for now — wire to Firebase Auth here later,
                        // e.g. FirebaseAuth.getInstance().signInWithEmailAndPassword(...)
                        onLoginSuccess()
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = LoginPurpleMid)
        ) {
            Text(if (isRegisterMode) "Register" else "Login", fontSize = 16.sp)
        }

        Spacer(Modifier.height(12.dp))

        TextButton(
            onClick = { isRegisterMode = !isRegisterMode; errorText = null },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text(
                if (isRegisterMode) "Already have an account? Login"
                else "Don't have an account? Register",
                color = LoginPurpleDark,
                fontSize = 13.sp
            )
        }
    }
}
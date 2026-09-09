package com.example.aismartexpensetracker.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aismartexpensetracker.AuthViewModel
import com.example.aismartexpensetracker.ui.theme.*
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException

/**
 * Login screen with a single Google Sign-In button.
 *
 * The launcher fires the Google account picker; on success the ID token is
 * handed to AuthViewModel which exchanges it for a Firebase credential.
 * Navigation away from this screen is driven by the auth state change in
 * MainActivity -- LoginScreen itself just shows progress and errors.
 */
@Composable
fun LoginScreen(
    authViewModel: AuthViewModel = viewModel()
) {
    val context = LocalContext.current
    val authState by authViewModel.authState.collectAsStateWithLifecycle()

    // Register an Activity result launcher for the Google Sign-In intent.
    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                authViewModel.handleSignInResult(account.idToken)
            } catch (e: ApiException) {
                authViewModel.handleSignInResult(null)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Canvas),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Space.lg)
        ) {
            // App logo / title area
            Text(
                text = "💰",
                style = MaterialTheme.typography.displayLarge
            )
            Spacer(Modifier.height(Space.sm))
            Text(
                text = "AI Smart\nExpense Tracker",
                style = DisplayStyle,
                color = Ink,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Sign in to get started",
                style = CaptionStyle,
                color = InkMuted,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(Space.xl))

            // Error banner
            if (authState is AuthViewModel.AuthState.Error) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Danger.copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = (authState as AuthViewModel.AuthState.Error).message,
                        color = Danger,
                        style = CaptionStyle,
                        modifier = Modifier.padding(Space.lg),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Google Sign-In button
            Button(
                onClick = {
                    authViewModel.clearError()
                    val intent = authViewModel.getSignInIntent(context)
                    signInLauncher.launch(intent)
                },
                enabled = authState !is AuthViewModel.AuthState.Loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Indigo500)
            ) {
                if (authState is AuthViewModel.AuthState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = SurfaceWhite,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "Continue with Google",
                        style = RowTitleStyle,
                        color = SurfaceWhite
                    )
                }
            }

            Spacer(Modifier.height(Space.sm))
            Text(
                text = "Your data stays on this device.\nNo data is shared with third parties.",
                style = CaptionStyle,
                color = InkFaint,
                textAlign = TextAlign.Center
            )
        }
    }
}

package com.example.aismartexpensetracker

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Drives Firebase Google Sign-In.
 *
 * The ViewModel survives configuration changes, so the sign-in state is
 * stable across rotations. A sign-out clears local Room data so a subsequent
 * sign-in with a different account always sees a clean slate.
 */
class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db = AppDatabase.getDatabase(application)

    private val _user = MutableStateFlow<FirebaseUser?>(auth.currentUser)
    val user: StateFlow<FirebaseUser?> = _user

    sealed interface AuthState {
        data object Idle : AuthState
        data object Loading : AuthState
        data class Error(val message: String) : AuthState
    }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState

    /**
     * Builds a GoogleSignInClient configured with the default Web client ID
     * that Firebase generates. The ID is read from strings.xml (populated
     * by google-services.json at build time).
     */
    fun getSignInIntent(context: Context): Intent {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        val client: GoogleSignInClient = GoogleSignIn.getClient(context, gso)
        return client.signInIntent
    }

    /**
     * Called by the login screen once Google returns a signed-in account.
     * Exchanges the Google ID token for a Firebase credential and signs in.
     */
    fun handleSignInResult(idToken: String?) {
        if (idToken == null) {
            _authState.value = AuthState.Error("Google Sign-In returned no token.")
            return
        }
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                auth.signInWithCredential(credential).await()
                _user.value = auth.currentUser
                _authState.value = AuthState.Idle
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Authentication failed.")
            }
        }
    }

    /**
     * Signs the current user out and clears their data from Room.
     *
     * Data is cleared deliberately: the user chose "clear on sign-out" as
     * the isolation strategy, so a subsequent sign-in always sees a fresh
     * database. This is not a silent wipe -- sign-out is an explicit action.
     */
    fun signOut() {
        viewModelScope.launch {
            val uid = auth.currentUser?.uid
            if (uid != null) {
                db.expenseDao().deleteAllForUser(uid)
                db.budgetDao().deleteAllForUser(uid)
            }
            auth.signOut()
            _user.value = null
            _authState.value = AuthState.Idle
        }
    }

    fun clearError() {
        _authState.value = AuthState.Idle
    }
}

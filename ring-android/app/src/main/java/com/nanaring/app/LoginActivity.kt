package com.nanaring.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.nanaring.app.ui.screens.LoginScreen
import com.nanaring.app.ui.theme.NanaRingTheme
import kotlinx.coroutines.launch

class LoginActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val syncAuth = (application as NanaRingApplication).container.supabaseSyncAuth

        setContent {
            NanaRingTheme {
                val scope = rememberCoroutineScope()
                var isLoading by remember { mutableStateOf(false) }
                var errorMessage by remember { mutableStateOf<String?>(null) }

                LoginScreen(
                    isLoading    = isLoading,
                    errorMessage = errorMessage,
                    onSignIn     = { email, password ->
                        scope.launch {
                            isLoading = true
                            errorMessage = null
                            syncAuth.signIn(email, password)
                                .onSuccess  { navigateToMain() }
                                .onFailure  { errorMessage = "Sign-in failed. Check your credentials." }
                            isLoading = false
                        }
                    },
                    onSignUp     = { email, password ->
                        scope.launch {
                            isLoading = true
                            errorMessage = null
                            syncAuth.signUp(email, password)
                                .onSuccess  { navigateToMain() }
                                .onFailure  { errorMessage = "Sign-up failed. Try a different email." }
                            isLoading = false
                        }
                    },
                )
            }
        }
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}

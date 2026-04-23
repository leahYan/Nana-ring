package com.nanaring.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.nanaring.app.ui.theme.AccentBlue
import com.nanaring.app.ui.theme.DarkCharcoal
import com.nanaring.app.ui.theme.DeepNavy
import com.nanaring.app.ui.theme.MidnightBlue
import com.nanaring.app.ui.theme.TextPrimary
import com.nanaring.app.ui.theme.TextSecondary

@Composable
fun LoginScreen(
    isLoading: Boolean,
    errorMessage: String?,
    onSignIn: (email: String, password: String) -> Unit,
    onSignUp: (email: String, password: String) -> Unit,
) {
    var email    by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor     = TextPrimary,
        unfocusedTextColor   = TextSecondary,
        focusedLabelColor    = AccentBlue,
        unfocusedLabelColor  = TextSecondary,
        focusedBorderColor   = AccentBlue,
        unfocusedBorderColor = TextSecondary,
        cursorColor          = AccentBlue,
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(DeepNavy, MidnightBlue, DarkCharcoal)))
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text  = "Nana Ring",
            style = MaterialTheme.typography.headlineLarge,
            color = TextPrimary,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text  = "Sign in to sync your ring data",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )

        Spacer(Modifier.height(36.dp))

        OutlinedTextField(
            value         = email,
            onValueChange = { email = it },
            label         = { Text("Email") },
            modifier      = Modifier.fillMaxWidth(),
            singleLine    = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            colors        = fieldColors,
        )

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value                  = password,
            onValueChange          = { password = it },
            label                  = { Text("Password") },
            modifier               = Modifier.fillMaxWidth(),
            singleLine             = true,
            visualTransformation   = PasswordVisualTransformation(),
            keyboardOptions        = KeyboardOptions(keyboardType = KeyboardType.Password),
            colors                 = fieldColors,
        )

        if (errorMessage != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text  = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(Modifier.height(28.dp))

        if (isLoading) {
            CircularProgressIndicator(color = AccentBlue)
        } else {
            Button(
                onClick  = { onSignIn(email.trim(), password) },
                enabled  = email.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = AccentBlue),
            ) {
                Text("Sign In", color = TextPrimary)
            }

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick  = { onSignUp(email.trim(), password) },
                enabled  = email.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(12.dp),
            ) {
                Text("Create Account", color = AccentBlue)
            }
        }
    }
}

package com.nanaring.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.nanaring.app.ui.theme.AccentBlue
import com.nanaring.app.ui.theme.DarkCharcoal
import com.nanaring.app.ui.theme.DeepNavy
import com.nanaring.app.ui.theme.MidnightBlue
import com.nanaring.app.ui.theme.NanaRingTheme
import com.nanaring.app.ui.theme.TextPrimary
import com.nanaring.app.ui.theme.TextSecondary

class DeveloperSettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val authManager = (application as NanaRingApplication).container.authManager

        setContent {
            NanaRingTheme {
                var jwt       by remember { mutableStateOf(authManager.jwt) }
                var serverUrl by remember { mutableStateOf(authManager.serverUrl) }

                Scaffold { _ ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(listOf(DeepNavy, MidnightBlue, DarkCharcoal))
                            )
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            text  = "Developer Settings",
                            style = MaterialTheme.typography.headlineMedium,
                            color = TextPrimary,
                        )
                        Text(
                            text  = "Internal use only — not visible in production builds.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = serverUrl,
                            onValueChange = { serverUrl = it },
                            label = { Text("Server URL") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextField.nanaColors(),
                        )

                        OutlinedTextField(
                            value = jwt,
                            onValueChange = { jwt = it },
                            label = { Text("JWT Token") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 5,
                            colors = OutlinedTextField.nanaColors(),
                        )

                        Button(
                            onClick = {
                                authManager.serverUrl = serverUrl.trim()
                                authManager.jwt       = jwt.trim()
                                finish()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape  = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                        ) {
                            Text("Save & Close", color = TextPrimary)
                        }
                    }
                }
            }
        }
    }
}

@Suppress("FunctionName")
private fun OutlinedTextField.Companion.nanaColors() =
    OutlinedTextFieldDefaults.colors(
        focusedTextColor    = TextPrimary,
        unfocusedTextColor  = TextSecondary,
        focusedLabelColor   = AccentBlue,
        unfocusedLabelColor = TextSecondary,
        focusedBorderColor  = AccentBlue,
        unfocusedBorderColor = TextSecondary,
        cursorColor         = AccentBlue,
    )

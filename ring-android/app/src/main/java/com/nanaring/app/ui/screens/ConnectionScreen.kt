package com.nanaring.app.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nanaring.app.ui.theme.AccentBlue
import com.nanaring.app.ui.theme.DarkCharcoal
import com.nanaring.app.ui.theme.DeepNavy
import com.nanaring.app.ui.theme.MidnightBlue
import com.nanaring.app.ui.theme.StatusGreen
import com.nanaring.app.ui.theme.StatusOrange
import com.nanaring.app.ui.theme.StatusRed
import com.nanaring.app.ui.theme.TextMuted
import com.nanaring.app.ui.theme.TextPrimary
import com.nanaring.app.ui.theme.TextSecondary

sealed interface ConnectionState {
    data object Idle : ConnectionState
    data object Scanning : ConnectionState
    data class Connected(val deviceName: String, val bpm: Int) : ConnectionState
}

@Composable
fun ConnectionScreen(
    state: ConnectionState,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onOpenDeveloperSettings: () -> Unit,
    appVersion: String = "v1.0.0-poc",
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(DeepNavy, MidnightBlue, DarkCharcoal)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // ---- Header ----
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Nana Ring",
                    style = MaterialTheme.typography.headlineLarge,
                    color = TextPrimary,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Health Monitor",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary,
                )
            }

            // ---- State-specific content ----
            when (state) {
                ConnectionState.Idle        -> IdleContent(onStartScan)
                ConnectionState.Scanning    -> ScanningContent(onStopScan)
                is ConnectionState.Connected -> ConnectedContent(state)
            }

            // ---- Version string — 5× tap reveals Developer Settings ----
            VersionTapTarget(
                version = appVersion,
                onUnlocked = onOpenDeveloperSettings,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Idle state
// ---------------------------------------------------------------------------
@Composable
private fun IdleContent(onStartScan: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Ring icon placeholder
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(MidnightBlue, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "○", fontSize = 64.sp, color = TextMuted)
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "No device connected",
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Tap below to search for your Nana Ring",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(40.dp))

        Button(
            onClick = onStartScan,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
        ) {
            Text(
                text = "Start Scanning",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Scanning state — pulsing ring icon
// ---------------------------------------------------------------------------
@Composable
private fun ScanningContent(onStopScan: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue  = 1.15f,
        animationSpec = infiniteRepeatable(
            animation  = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .scale(scale)
                .background(
                    Brush.radialGradient(
                        colors = listOf(StatusOrange.copy(alpha = 0.3f), Color.Transparent)
                    ),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(StatusOrange.copy(alpha = 0.8f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "◎", fontSize = 40.sp, color = TextPrimary)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Searching...",
            style = MaterialTheme.typography.titleLarge,
            color = StatusOrange,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Looking for Nana Ring nearby",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(40.dp))

        Button(
            onClick = onStopScan,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = DarkCharcoal,
            ),
        ) {
            Text(
                text = "Cancel",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Connected state — green status card
// ---------------------------------------------------------------------------
@Composable
private fun ConnectedContent(state: ConnectionState.Connected) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Status indicator
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(StatusGreen, CircleShape)
            )
            Text(
                text = "Connected",
                style = MaterialTheme.typography.titleLarge,
                color = StatusGreen,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = state.deviceName,
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Live BPM card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MidnightBlue,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Heart Rate",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${state.bpm}",
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                )
                Text(
                    text = "BPM",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 5× tap secret door — attached to version Text composable
// ---------------------------------------------------------------------------
@Composable
private fun VersionTapTarget(
    version: String,
    onUnlocked: () -> Unit,
) {
    var tapCount by remember { mutableIntStateOf(0) }

    Text(
        text = version,
        style = MaterialTheme.typography.labelSmall,
        color = TextMuted,
        modifier = Modifier
            .clickable(role = Role.Button) {
                tapCount++
                if (tapCount >= 5) {
                    tapCount = 0
                    onUnlocked()
                }
            }
            .padding(vertical = 8.dp),
    )
}

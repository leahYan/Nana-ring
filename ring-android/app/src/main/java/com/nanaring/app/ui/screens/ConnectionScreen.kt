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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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

@Composable
fun ConnectionScreen(
    state: ConnectionState,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnectToDevice: (mac: String) -> Unit,
    onOpenDeveloperSettings: () -> Unit,
    onOpenMyDoctors: () -> Unit = {},
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
            Box(modifier = Modifier.fillMaxWidth()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.align(Alignment.TopCenter),
                ) {
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
                Text(
                    text = "My Doctors",
                    style = MaterialTheme.typography.labelMedium,
                    color = AccentBlue,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .clickable(role = Role.Button, onClick = onOpenMyDoctors)
                        .padding(8.dp),
                )
            }

            // ---- State-specific content ----
            when (state) {
                is ConnectionState.Idle ->
                    IdleContent(onStartScan)
                is ConnectionState.Scanning ->
                    ScanningContent(onStopScan)
                is ConnectionState.ScanResults ->
                    ScanResultsContent(state, onStartScan, onConnectToDevice)
                is ConnectionState.Connecting ->
                    ConnectingContent(state)
                is ConnectionState.Connected ->
                    ConnectedContent(state)
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
            colors = ButtonDefaults.buttonColors(containerColor = DarkCharcoal),
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
// Scan results — list of real discovered devices for user selection
// ---------------------------------------------------------------------------
@Composable
private fun ScanResultsContent(
    state: ConnectionState.ScanResults,
    onStartScan: () -> Unit,
    onConnectToDevice: (mac: String) -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (state.devices.isEmpty()) {
            // No rings found — show honest empty state, never fabricate results.
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .background(MidnightBlue, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "○", fontSize = 64.sp, color = StatusRed)
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "No Rings Found",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Make sure your ring is nearby and paired to this phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center,
            )
        } else {
            Text(
                text = "Select Your Ring",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${state.devices.size} device${if (state.devices.size != 1) "s" else ""} found",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f, fill = false),
            ) {
                items(state.devices) { device ->
                    DeviceRow(device = device, onConnect = { onConnectToDevice(device.mac) })
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onStartScan,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
        ) {
            Text(
                text = "Scan Again",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
            )
        }
    }
}

@Composable
private fun DeviceRow(device: DiscoveredDevice, onConnect: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onConnect),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MidnightBlue),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                )
                Text(
                    text = device.mac,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                )
                if (device.isLastKnown) {
                    Text(
                        text = "Last connected",
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentBlue,
                    )
                }
            }
            Text(
                text = "Connect →",
                style = MaterialTheme.typography.labelMedium,
                color = AccentBlue,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Connecting state — spinner while GATT handshake is in progress
// ---------------------------------------------------------------------------
@Composable
private fun ConnectingContent(state: ConnectionState.Connecting) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(
            modifier = Modifier.size(80.dp),
            color = AccentBlue,
            strokeWidth = 6.dp,
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Connecting…",
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = state.deviceName,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
    }
}

// ---------------------------------------------------------------------------
// Connected state — green status card with live BPM
// ---------------------------------------------------------------------------
@Composable
private fun ConnectedContent(state: ConnectionState.Connected) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(modifier = Modifier.size(12.dp).background(StatusGreen, CircleShape))
            Text(text = "Connected", style = MaterialTheme.typography.titleLarge, color = StatusGreen)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = state.deviceName, style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
        Spacer(modifier = Modifier.height(24.dp))

        // All metrics shown immediately — "—" until first reading arrives.
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f, fill = false),
        ) {
            item {
                MetricCard(
                    label = "Heart Rate",
                    value = if (state.bpm > 0) "${state.bpm}" else "—",
                    unit  = "BPM",
                    large = true,
                )
            }
            item {
                MetricCard(
                    label = "Blood Oxygen",
                    value = if (state.spO2 > 0) "${state.spO2}" else "—",
                    unit  = "%",
                )
            }
            item {
                MetricCard(
                    label = "HRV",
                    value = if (state.hrv > 0) "${state.hrv}" else "—",
                    unit  = "ms",
                )
            }
            item {
                MetricCard(
                    label = "Stress",
                    value = if (state.stress > 0) "${state.stress}" else "—",
                    unit  = "/ 100",
                )
            }
            item {
                MetricCard(
                    label = "Temperature",
                    value = if (state.temperature > 0f) "%.1f".format(state.temperature) else "—",
                    unit  = "°C",
                )
            }
            if (state.systolic > 0 && state.diastolic > 0) item {
                MetricCard(
                    label = "Blood Pressure",
                    value = "${state.systolic}/${state.diastolic}",
                    unit  = "mmHg",
                )
            }
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, unit: String, large: Boolean = false) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MidnightBlue),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = if (large) 20.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = value,
                    fontSize = if (large) 48.sp else 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                )
                Text(
                    text = unit,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 6.dp),
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

package com.nanaring.app.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nanaring.app.data.local.entity.HeartRateEntity
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
    recentReadings: List<HeartRateEntity> = emptyList(),
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
                    ConnectedContent(state, recentReadings)
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
// Connected state — spark-card grid
// ---------------------------------------------------------------------------
@Composable
private fun ConnectedContent(
    state: ConnectionState.Connected,
    recentReadings: List<HeartRateEntity>,
) {
    // DB returns newest-first; reverse so oldest→newest for the chart left→right.
    val sorted = remember(recentReadings) { recentReadings.reversed() }
    val bpmList    = remember(sorted) { sorted.map { it.bpm.toFloat() } }
    val spO2List   = remember(sorted) { sorted.mapNotNull { it.spO2?.toFloat() } }
    val hrvList    = remember(sorted) { sorted.mapNotNull { it.hrv?.toFloat() } }
    val stressList = remember(sorted) { sorted.mapNotNull { it.stress?.toFloat() } }
    val tempList   = remember(sorted) { sorted.mapNotNull { it.temperature } }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Status row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(10.dp).background(StatusGreen, CircleShape))
            Text("Connected", style = MaterialTheme.typography.titleMedium, color = StatusGreen)
            Text("·", color = TextMuted)
            Text(state.deviceName, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }

        Spacer(Modifier.height(4.dp))

        // Row 1: Pulse + Oxygen
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SparkCard(
                label      = "PULSE",
                value      = if (state.bpm > 0) "${state.bpm}" else "—",
                unit       = "BPM",
                sparkValues = bpmList,
                sparkColor = Color(0xFF9B8FC7),
                modifier   = Modifier.weight(1f),
            )
            SparkCard(
                label      = "OXYGEN",
                value      = if (state.spO2 > 0) "${state.spO2}" else "—",
                unit       = "%",
                sparkValues = spO2List,
                sparkColor = Color(0xFF6BAED6),
                modifier   = Modifier.weight(1f),
            )
        }

        // Row 2: HRV + Stress
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SparkCard(
                label      = "HARMONY · HRV",
                value      = if (state.hrv > 0) "${state.hrv}" else "—",
                unit       = "ms",
                sparkValues = hrvList,
                sparkColor = StatusGreen,
                modifier   = Modifier.weight(1f),
            )
            SparkCard(
                label      = "STILLNESS",
                value      = if (state.stress > 0) "${state.stress}" else "—",
                unit       = "/ 100",
                sparkValues = stressList,
                sparkColor = StatusOrange,
                modifier   = Modifier.weight(1f),
            )
        }

        // Row 3: Temperature + Blood Pressure (conditional)
        val showTemp = state.temperature > 0f
        val showBP   = state.systolic > 0 && state.diastolic > 0
        if (showTemp || showBP) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (showTemp) {
                    SparkCard(
                        label      = "TEMPERATURE",
                        value      = "%.1f".format(state.temperature),
                        unit       = "°C",
                        sparkValues = tempList,
                        sparkColor = Color(0xFFFC8181),
                        modifier   = Modifier.weight(1f),
                    )
                }
                if (showBP) {
                    SparkCard(
                        label      = "PRESSURE",
                        value      = "${state.systolic}/${state.diastolic}",
                        unit       = "mmHg",
                        sparkValues = emptyList(),
                        sparkColor = Color(0xFFF9A8D4),
                        modifier   = Modifier.weight(1f),
                    )
                }
                // Pad to keep grid even when only one card in the row
                if (showTemp xor showBP) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SparkCard(
    label: String,
    value: String,
    unit: String,
    sparkValues: List<Float>,
    sparkColor: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.height(110.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MidnightBlue),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            // Label + value — top-left
            Column(modifier = Modifier.align(Alignment.TopStart)) {
                Text(
                    text = label,
                    fontSize = 10.sp,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.2.sp,
                    color = TextMuted,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = value,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        lineHeight = 30.sp,
                    )
                    Text(
                        text = unit,
                        fontSize = 11.sp,
                        color = TextMuted,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }

            // Sparkline — right 55%, vertically centered
            if (sparkValues.size >= 2) {
                Sparkline(
                    values = sparkValues,
                    color  = sparkColor,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .fillMaxWidth(0.55f),
                )
            }
        }
    }
}

@Composable
private fun Sparkline(values: List<Float>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val minV  = values.min()
        val maxV  = values.max()
        val range = (maxV - minV).coerceAtLeast(1f)

        val pts = values.mapIndexed { i, v ->
            Offset(
                x = i * size.width / (values.size - 1),
                y = size.height * 0.85f - (v - minV) / range * size.height * 0.7f,
            )
        }

        // Catmull-Rom → cubic Bézier
        val linePath = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 0 until pts.size - 1) {
                val p0 = if (i == 0) pts[0] else pts[i - 1]
                val p1 = pts[i]
                val p2 = pts[i + 1]
                val p3 = if (i + 2 < pts.size) pts[i + 2] else pts[i + 1]
                cubicTo(
                    p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f,
                    p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f,
                    p2.x, p2.y,
                )
            }
        }

        // Gradient fill under the curve
        val fillPath = Path().apply {
            addPath(linePath)
            lineTo(pts.last().x, size.height)
            lineTo(pts.first().x, size.height)
            close()
        }
        drawPath(
            fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(color.copy(alpha = 0.28f), Color.Transparent),
            ),
        )

        drawPath(
            linePath,
            color = color,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
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

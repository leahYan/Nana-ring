package com.nanaring.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nanaring.app.sync.DoctorProfile
import com.nanaring.app.sync.SupabaseSyncAuth
import com.nanaring.app.ui.theme.AccentBlue
import com.nanaring.app.ui.theme.DarkCharcoal
import com.nanaring.app.ui.theme.DeepNavy
import com.nanaring.app.ui.theme.MidnightBlue
import com.nanaring.app.ui.theme.StatusRed
import com.nanaring.app.ui.theme.TextMuted
import com.nanaring.app.ui.theme.TextPrimary
import com.nanaring.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun MyDoctorsScreen(
    syncAuth: SupabaseSyncAuth,
    onBack: () -> Unit,
) {
    var linkedDoctors by remember { mutableStateOf<List<DoctorProfile>>(emptyList()) }
    var activeDoctors by remember { mutableStateOf<List<DoctorProfile>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun reload() {
        scope.launch {
            isLoading = true
            errorMessage = null
            linkedDoctors = syncAuth.fetchLinkedDoctors()
            activeDoctors = syncAuth.fetchActiveDoctors()
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { reload() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(colors = listOf(DeepNavy, MidnightBlue, DarkCharcoal))
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp),
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) {
                    Text("← Back", color = AccentBlue, fontSize = 14.sp)
                }
                Text(
                    text = "My Doctors",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (isLoading) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentBlue)
                }
                return@Column
            }

            errorMessage?.let {
                Text(text = it, color = StatusRed, style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(12.dp))
            }

            // ── Linked doctors ──────────────────────────────────────
            Text(
                text = "Linked Doctors",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (linkedDoctors.isEmpty()) {
                Text(
                    text = "No doctors linked yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    items(linkedDoctors, key = { it.id }) { doctor ->
                        DoctorCard(
                            doctor = doctor,
                            actionLabel = "Remove",
                            actionColor = StatusRed,
                            onAction = {
                                scope.launch {
                                    val ok = syncAuth.removeDoctorLink(doctor.id)
                                    if (ok) reload() else errorMessage = "Failed to remove. Try again."
                                }
                            },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Available doctors ───────────────────────────────────
            val linkedIds = linkedDoctors.map { it.id }.toSet()
            val available = activeDoctors.filter { it.id !in linkedIds }

            Text(
                text = "Add a Doctor",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (available.isEmpty()) {
                Text(
                    text = "All available doctors are already linked.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    items(available, key = { it.id }) { doctor ->
                        DoctorCard(
                            doctor = doctor,
                            actionLabel = "Add",
                            actionColor = AccentBlue,
                            onAction = {
                                scope.launch {
                                    val ok = syncAuth.addDoctorLink(doctor.id)
                                    if (ok) reload() else errorMessage = "Failed to add. Try again."
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DoctorCard(
    doctor: DoctorProfile,
    actionLabel: String,
    actionColor: androidx.compose.ui.graphics.Color,
    onAction: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MidnightBlue),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = doctor.fullName ?: "Doctor",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                )
                doctor.email?.let {
                    Text(text = it, style = MaterialTheme.typography.labelSmall, color = TextMuted)
                }
            }
            Button(
                onClick = onAction,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = actionColor),
            ) {
                Text(text = actionLabel, fontSize = 13.sp, color = TextPrimary)
            }
        }
    }
}

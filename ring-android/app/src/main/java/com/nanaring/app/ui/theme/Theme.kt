package com.nanaring.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ---- Brand palette ----
val DeepNavy        = Color(0xFF0A0E1A)
val DarkCharcoal    = Color(0xFF111827)
val MidnightBlue    = Color(0xFF1A2340)
val AccentBlue      = Color(0xFF2563EB)
val AccentBlueDim   = Color(0xFF1D4ED8)

val StatusGreen     = Color(0xFF22C55E)
val StatusOrange    = Color(0xFFF97316)
val StatusRed       = Color(0xFFEF4444)

val TextPrimary     = Color(0xFFFFFFFF)
val TextSecondary   = Color(0xFFCBD5E1)
val TextMuted       = Color(0xFF64748B)

private val NanaRingDarkColorScheme = darkColorScheme(
    primary         = AccentBlue,
    onPrimary       = TextPrimary,
    primaryContainer = MidnightBlue,
    onPrimaryContainer = TextPrimary,
    secondary       = AccentBlueDim,
    onSecondary     = TextPrimary,
    background      = DeepNavy,
    onBackground    = TextPrimary,
    surface         = DarkCharcoal,
    onSurface       = TextPrimary,
    surfaceVariant  = MidnightBlue,
    onSurfaceVariant = TextSecondary,
    error           = StatusRed,
    onError         = TextPrimary,
)

@Composable
fun NanaRingTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NanaRingDarkColorScheme,
        typography  = NanaRingTypography,
        content     = content,
    )
}

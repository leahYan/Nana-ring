package com.nanaring.app.ui.screens

/** A ring device discovered during a BLE scan or found in the bonded-device cache. */
data class DiscoveredDevice(
    val name: String,
    val mac: String,
    val isLastKnown: Boolean = false,
)

sealed interface ConnectionState {
    data object Idle : ConnectionState
    data object Scanning : ConnectionState
    data class ScanResults(val devices: List<DiscoveredDevice>) : ConnectionState
    data class Connecting(val deviceName: String) : ConnectionState

    /** GATT connected. All metric fields are 0/0f until the ring returns a reading. */
    data class Connected(
        val deviceName: String,
        val bpm: Int        = 0,
        val spO2: Int       = 0,   // blood oxygen %
        val systolic: Int   = 0,   // blood pressure systolic mmHg
        val diastolic: Int  = 0,   // blood pressure diastolic mmHg
        val hrv: Int        = 0,   // heart rate variability ms
        val stress: Int     = 0,   // stress level 0–100
        val temperature: Float = 0f, // body temperature °C
    ) : ConnectionState
}

package com.nanaring.app.ui.screens

/** A ring device discovered during a BLE scan or found in the bonded-device cache. */
data class DiscoveredDevice(
    val name: String,
    val mac: String,
    val isLastKnown: Boolean = false, // true when loaded from prefs, not from active scan
)

sealed interface ConnectionState {
    /** No scan running, no device connected. */
    data object Idle : ConnectionState

    /** BLE scan is actively running. */
    data object Scanning : ConnectionState

    /**
     * Scan has finished. [devices] is the list of rings found — may be empty
     * (we show "No Rings Found" rather than fabricating results).
     */
    data class ScanResults(val devices: List<DiscoveredDevice>) : ConnectionState

    /** User selected a ring; GATT connection is being established. */
    data class Connecting(val deviceName: String) : ConnectionState

    /** GATT connected and ring is active. [bpm] is 0 until the first HR reading arrives. */
    data class Connected(val deviceName: String, val bpm: Int) : ConnectionState
}

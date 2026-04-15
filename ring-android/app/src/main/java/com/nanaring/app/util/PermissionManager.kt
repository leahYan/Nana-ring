package com.nanaring.app.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat

/**
 * Encapsulates Bluetooth permission logic for API 26+.
 *
 * Android 12+ (API 31+): BLUETOOTH_SCAN + BLUETOOTH_CONNECT with neverForLocation flags.
 *   - BLUETOOTH_SCAN  usesPermissionFlags="neverForLocation"  (we don't derive location)
 *   - BLUETOOTH_CONNECT  (required to connect to a paired device)
 *
 * Android <12 (API 26–30): ACCESS_FINE_LOCATION is the legacy gate for BLE scanning.
 *
 * Usage in a Composable/Activity:
 *   val launcher = rememberLauncherForActivityResult(
 *       ActivityResultContracts.RequestMultiplePermissions()
 *   ) { results -> permissionManager.onPermissionResult(results) }
 *
 *   permissionManager.requestPermissionsIfNeeded(launcher)
 */
class PermissionManager(private val context: Context) {

    /** All permissions required for BLE on the current API level. */
    val requiredPermissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
            )
        } else {
            // Legacy BLE scanning gate for API 26–30
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    /** True only when every required permission is currently granted. */
    fun hasAllPermissions(): Boolean =
        requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) ==
                    PackageManager.PERMISSION_GRANTED
        }

    /**
     * Launches the system permission dialog if any required permission is missing.
     * Does nothing when all permissions are already granted.
     */
    fun requestPermissionsIfNeeded(
        launcher: ActivityResultLauncher<Array<String>>,
    ) {
        if (!hasAllPermissions()) {
            launcher.launch(requiredPermissions)
        }
    }

    /**
     * Interprets the [results] map returned by [ActivityResultContracts.RequestMultiplePermissions].
     * Returns true if every required permission was granted.
     */
    fun onPermissionResult(results: Map<String, Boolean>): Boolean =
        requiredPermissions.all { results[it] == true }
}

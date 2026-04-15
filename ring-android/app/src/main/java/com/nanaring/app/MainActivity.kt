package com.nanaring.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.nanaring.app.ui.screens.ConnectionScreen
import com.nanaring.app.ui.screens.ConnectionState
import com.nanaring.app.ui.theme.NanaRingTheme
import com.nanaring.app.util.PermissionManager

class MainActivity : ComponentActivity() {

    private lateinit var permissionManager: PermissionManager
    private var connectionState: ConnectionState by mutableStateOf(ConnectionState.Idle)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (permissionManager.onPermissionResult(results)) {
            connectionState = ConnectionState.Scanning
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        permissionManager = PermissionManager(this)

        setContent {
            NanaRingTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { _ ->
                    ConnectionScreen(
                        state = connectionState,
                        onStartScan = ::handleStartScan,
                        onStopScan  = ::handleStopScan,
                        onOpenDeveloperSettings = ::openDeveloperSettings,
                    )
                }
            }
        }
    }

    private fun handleStartScan() {
        if (permissionManager.hasAllPermissions()) {
            connectionState = ConnectionState.Scanning
            // TODO: start BLE scan via AppContainer.ringRepository
        } else {
            permissionManager.requestPermissionsIfNeeded(permissionLauncher)
        }
    }

    private fun handleStopScan() {
        // TODO: stop BLE scan via AppContainer.ringRepository
        connectionState = ConnectionState.Idle
    }

    private fun openDeveloperSettings() {
        startActivity(Intent(this, DeveloperSettingsActivity::class.java))
    }
}

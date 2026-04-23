package com.nanaring.app

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.nanaring.app.ui.screens.ConnectionScreen
import com.nanaring.app.ui.theme.NanaRingTheme
import com.nanaring.app.util.PermissionManager
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var permissionManager: PermissionManager
    private lateinit var bluetoothAdapter: BluetoothAdapter

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (permissionManager.onPermissionResult(results)) {
            checkBluetoothAndScan()
        }
    }

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (bluetoothAdapter.isEnabled) {
            startScanInBackground()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Require Supabase Auth before doing anything else.
        val syncAuth = (application as NanaRingApplication).container.supabaseSyncAuth
        if (!syncAuth.restoreSession()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        enableEdgeToEdge()

        permissionManager = PermissionManager(this)
        bluetoothAdapter  = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        val repository    = (application as NanaRingApplication).container.ringRepository

        setContent {
            NanaRingTheme {
                val connectionState by repository.connectionState.collectAsStateWithLifecycle()
                Scaffold(modifier = Modifier.fillMaxSize()) { _ ->
                    ConnectionScreen(
                        state               = connectionState,
                        onStartScan         = ::handleStartScan,
                        onStopScan          = ::handleStopScan,
                        onConnectToDevice   = ::handleConnectToDevice,
                        onOpenDeveloperSettings = ::openDeveloperSettings,
                    )
                }
            }
        }
    }

    private fun handleStartScan() {
        if (!permissionManager.hasAllPermissions()) {
            permissionManager.requestPermissionsIfNeeded(permissionLauncher)
        } else {
            checkBluetoothAndScan()
        }
    }

    private fun checkBluetoothAndScan() {
        if (!bluetoothAdapter.isEnabled) {
            enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        } else {
            startScanInBackground()
        }
    }

    private fun handleStopScan() {
        val repository = (application as NanaRingApplication).container.ringRepository
        lifecycleScope.launch { repository.stopScan() }
    }

    private fun handleConnectToDevice(mac: String) {
        val repository = (application as NanaRingApplication).container.ringRepository
        lifecycleScope.launch { repository.connectToDevice(mac) }
    }

    private fun startScanInBackground() {
        val repository = (application as NanaRingApplication).container.ringRepository
        lifecycleScope.launch { repository.startScan() }
    }

    private fun openDeveloperSettings() {
        startActivity(Intent(this, DeveloperSettingsActivity::class.java))
    }
}

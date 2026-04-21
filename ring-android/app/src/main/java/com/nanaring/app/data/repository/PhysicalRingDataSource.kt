package com.nanaring.app.data.repository

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.IntentFilter
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.nanaring.app.data.local.dao.HeartRateDao
import com.nanaring.app.data.local.entity.HeartRateEntity
import com.nanaring.app.ui.screens.ConnectionState
import com.nanaring.app.ui.screens.DiscoveredDevice
import com.oudmon.ble.base.bluetooth.BleOperateManager
import com.oudmon.ble.base.bluetooth.QCBluetoothCallbackCloneReceiver
import com.oudmon.ble.base.communication.CommandHandle
import com.oudmon.ble.base.communication.Constants
import com.oudmon.ble.base.communication.ICommandResponse
import com.oudmon.ble.base.communication.req.SetTimeReq
import com.oudmon.ble.base.communication.req.SimpleKeyReq
import com.oudmon.ble.base.communication.rsp.BaseRspCmd
import com.oudmon.ble.base.communication.rsp.RealTimeHeartRateRsp
import com.oudmon.ble.base.communication.rsp.SetTimeRsp
import com.oudmon.ble.base.communication.rsp.StopHeartRateRsp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Real BLE implementation backed by qring_sdk_1.0.0.1.aar.
 *
 * Connection state flow:
 *   Idle → Scanning → ScanResults → (user selects) → Connecting → Connected
 *
 * SDK broadcast contract (LocalBroadcastManager — internal to this process):
 *   "com.qc.sdk.ble.gatt_connected"      → connectStatue(device, true)
 *   "com.qc.sdk.ble.gatt_disconnected"   → connectStatue(device, false)
 *   "com.qc.sdk.ble.service_discovered"  → onServiceDiscovered()
 *   "com.qc.sdk.ble.characteristic_read" → onCharacteristicRead(uuid, data)
 *
 * Post-connection setup (connectStatue connected):
 *   1. SimpleKeyReq(CMD_BIND_SUCCESS)            — bind/key exchange (required first)
 *   2. CommandHandle.executeReqCmd(SetTimeReq()) — syncs clock, returns SetTimeRsp
 *      with capability flags (mSupportTemperature, mSupportHrv, etc.)
 *   3. CommandHandle.execReadCmd(getReadFmRequest()) — firmware version string
 *   4. CommandHandle.execReadCmd(getReadHwRequest()) — hardware version string
 */
class PhysicalRingDataSource(
    private val context: Context,
    private val application: Application,
    private val heartRateDao: HeartRateDao,
) : RingRepository {

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Cached from BLE characteristic reads after each connection.
    @Volatile private var firmwareVersion = "unknown"
    @Volatile private var hardwareVersion = "unknown"

    // Devices accumulated during the current scan pass.
    private val discoveredDevices = mutableListOf<DiscoveredDevice>()

    @Volatile private var connectingMac: String? = null
    @Volatile private var receiverRegistered = false
    private var scanTimeoutJob: Job? = null

    // Known name prefixes from colmi_r02_client — rings matching these float to the top.
    private val ringNamePrefixes = setOf(
        "R01", "R02", "R03", "R04", "R05", "R06", "R07", "R09", "R10",
        "COLMI", "VK-5098", "MERLIN", "Hello Ring", "RING1", "boAtring",
        "TR-R02", "SE", "EVOLVEO", "GL-SR2", "Blaupunkt", "KSIX RING",
    )

    private fun isLikelyRing(name: String?) =
        name != null && ringNamePrefixes.any { name.startsWith(it, ignoreCase = true) }

    // Persists the last successfully connected ring so it can be offered in scan
    // results even when the ring is not advertising (connected to another client,
    // power-saving mode, etc.). Colmi rings stop advertising once connected.
    private val prefs = context.getSharedPreferences("ring_prefs", Context.MODE_PRIVATE)
    private var lastKnownMac: String?
        get() = prefs.getString("last_mac", null)
        set(v) = prefs.edit().let { if (v != null) it.putString("last_mac", v) else it.remove("last_mac") }.apply()
    private var lastKnownName: String?
        get() = prefs.getString("last_name", null)
        set(v) = prefs.edit().let { if (v != null) it.putString("last_name", v) else it.remove("last_name") }.apply()

    // -------------------------------------------------------------------------
    // SDK manager — lazy so HandlerThread is started before init() is called
    // -------------------------------------------------------------------------

    private val bleManager: BleOperateManager by lazy {
        BleOperateManager.getInstance(application).also { mgr ->
            if (!mgr.isAlive) mgr.start()
            mgr.init()
            registerDataListeners(mgr)
        }
    }

    // -------------------------------------------------------------------------
    // Connection-state callback receiver
    //
    // QCBluetoothCallbackCloneReceiver is a BroadcastReceiver whose onReceive()
    // parses SDK-internal LocalBroadcastManager actions and dispatches to the
    // virtual methods we override here.
    // -------------------------------------------------------------------------

    private val connectionReceiver = object : QCBluetoothCallbackCloneReceiver() {

        override fun connectStatue(device: BluetoothDevice, connected: Boolean) {
            if (connected) {
                val name = device.name?.takeIf { it.isNotBlank() } ?: device.address
                connectingMac = device.address

                // Persist so we can surface this ring in future scans even when not advertising.
                lastKnownMac = device.address
                lastKnownName = name

                val ch = CommandHandle.getInstance()

                // 1. Bind — required before any data commands.
                ch.executeReqCmdNoCallback(SimpleKeyReq(Constants.CMD_BIND_SUCCESS))

                // 2. Sync ring clock; response carries capability flags.
                ch.executeReqCmd(SetTimeReq(), object : ICommandResponse<SetTimeRsp> {
                    override fun onDataResponse(rsp: SetTimeRsp) {
                        // capability flags (mSupportTemperature, mSupportHrv, etc.) available here
                    }
                })

                // 3. Read firmware / hardware version via CommandHandle.
                ch.execReadCmd(ch.getReadFmRequest())
                ch.execReadCmd(ch.getReadHwRequest())

                _connectionState.value = ConnectionState.Connected(name, 0)
            } else {
                connectingMac = null
                firmwareVersion = "unknown"
                hardwareVersion = "unknown"
                _connectionState.value = ConnectionState.Idle
            }
        }

        override fun onCharacteristicRead(uuid: String, data: ByteArray) {
            when {
                uuid.equals(Constants.CHAR_FIRMWARE_REVISION.toString(), ignoreCase = true) ->
                    firmwareVersion = String(data, Charsets.UTF_8).trim().ifEmpty { "unknown" }
                uuid.equals(Constants.CHAR_HW_REVISION.toString(), ignoreCase = true) ->
                    hardwareVersion = String(data, Charsets.UTF_8).trim().ifEmpty { "unknown" }
            }
        }

        override fun onServiceDiscovered() {
            // Characteristic reads + SetTimeReq are already sent in connectStatue().
        }
    }

    // -------------------------------------------------------------------------
    // Raw Android BLE scan — no UUID filter, catches devices the SDK scan misses
    // (e.g. already-bonded rings that stop advertising, or Samsung-paired devices
    //  not present in bondedDevices).
    // -------------------------------------------------------------------------

    private val rawScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val mac  = device.address ?: return
            val name = device.name?.takeIf { it.isNotBlank() } ?: mac
            Log.d("BLE_DEBUG", "raw onScanResult: $name | $mac | rssi=${result.rssi}")
            if (discoveredDevices.none { it.mac == mac }) {
                discoveredDevices.add(DiscoveredDevice(name = name, mac = mac))
            }
        }
        override fun onBatchScanResults(results: List<ScanResult>) {
            results.forEach { onScanResult(0, it) }
        }
        override fun onScanFailed(errorCode: Int) {
            Log.e("BLE_DEBUG", "raw scan FAILED errorCode=$errorCode")
        }
    }

    @SuppressLint("MissingPermission")
    private fun startRawBleScan() {
        val scanner = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)
            ?.adapter?.bluetoothLeScanner ?: return
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        try {
            scanner.startScan(null, settings, rawScanCallback)
        } catch (_: Exception) {}
    }

    @SuppressLint("MissingPermission")
    private fun stopRawBleScan() {
        val scanner = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)
            ?.adapter?.bluetoothLeScanner ?: return
        try { scanner.stopScan(rawScanCallback) } catch (_: Exception) {}
    }

    // -------------------------------------------------------------------------
    // RingRepository
    // -------------------------------------------------------------------------

    override suspend fun startScan() {
        discoveredDevices.clear()
        _connectionState.value = ConnectionState.Scanning
        bleManager // init SDK before registering receiver (reference doc §4 step order)
        ensureReceiverRegistered()

        // Pre-populate last connected ring — Colmi rings stop advertising once
        // connected, so the active scan won't find them. Persisted MAC lets the
        // user reconnect without waiting for the ring to advertise.
        lastKnownMac?.let { mac ->
            val name = lastKnownName ?: mac
            discoveredDevices.add(DiscoveredDevice(name = name, mac = mac, isLastKnown = true))
        }

        findBondedRings().forEach { bonded ->
            if (discoveredDevices.none { it.mac == bonded.mac }) {
                discoveredDevices.add(bonded)
            }
        }

        startRawBleScan()

        // Stop scan after 10 s and publish results — colmi_r02_client uses the same approach.
        scanTimeoutJob = scope.launch {
            delay(10_000)
            finishScan()
        }
    }

    private fun finishScan() {
        scanTimeoutJob?.cancel()
        scanTimeoutJob = null
        stopRawBleScan()
        // Sort: rings by known prefix first, then everything else, then last-known at top.
        val sorted = discoveredDevices
            .sortedWith(compareByDescending<DiscoveredDevice> { it.isLastKnown }
                .thenByDescending { isLikelyRing(it.name) })
        Log.d("BLE_DEBUG", "Scan finished — ${sorted.size} device(s). Rings: ${sorted.filter { isLikelyRing(it.name) || it.isLastKnown }.map { it.name }}")
        _connectionState.value = ConnectionState.ScanResults(sorted)
    }

    override suspend fun stopScan() {
        scanTimeoutJob?.cancel()
        scanTimeoutJob = null
        stopRawBleScan()
        if (connectingMac != null) bleManager.unBindDevice()
        connectingMac = null
        _connectionState.value = ConnectionState.Idle
    }

    override suspend fun connectToDevice(mac: String) {
        val name = discoveredDevices.firstOrNull { it.mac == mac }?.name ?: mac
        _connectionState.value = ConnectionState.Connecting(name)
        connectingMac = mac
        // connectDirectly — ring is already known to be in range (just found in scan).
        // connectWithScan would re-scan first; if the ring stopped advertising after
        // our scan ended it would never find it.
        bleManager.connectDirectly(mac)

        // Timeout: if connectStatue(connected=true) hasn't fired after 15 s, give up.
        scope.launch {
            delay(15_000)
            if (_connectionState.value is ConnectionState.Connecting) {
                Log.e("BLE_DEBUG", "Connection timed out for $mac")
                bleManager.unBindDevice()
                connectingMac = null
                _connectionState.value = ConnectionState.ScanResults(discoveredDevices.toList())
            }
        }
    }

    override suspend fun getUnsynced(): List<HeartRateEntity> = heartRateDao.getUnsynced()
    override suspend fun markSynced(ids: List<Long>) = heartRateDao.markSynced(ids)

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Register [connectionReceiver] with [LocalBroadcastManager] using all of the
     * SDK's internal broadcast actions. System registerReceiver will never see these.
     * Called once before the first scan.
     */
    private fun ensureReceiverRegistered() {
        if (receiverRegistered) return
        receiverRegistered = true

        val filter = IntentFilter().apply {
            addAction("com.qc.sdk.ble.gatt_connected")
            addAction("com.qc.sdk.ble.gatt_disconnected")
            addAction("com.qc.sdk.ble.BLE_NO_CALLBACK")
            addAction("com.qc.characteristic_write_qc")
            addAction("com.qc.sdk.ble.characteristic_read")
            addAction("com.qc.characteristic_changed_qc")
            addAction("com.qc.sdk.ble.service_discovered")
            addAction("com.qc.sdk.ble.descriptor.write")
        }
        LocalBroadcastManager.getInstance(context).registerReceiver(connectionReceiver, filter)
    }

    /**
     * Register data listeners on [BleOperateManager].
     * Called once inside the [bleManager] lazy block.
     */
    private fun registerDataListeners(mgr: BleOperateManager) {
        // Real-time heart rate stream — CMD_REAL_TIME_HEART_RATE = 30.
        // RealTimeHeartRateRsp.heart is the live BPM shown on the connection screen.
        mgr.addNotifyListener(
            Constants.CMD_REAL_TIME_HEART_RATE.toInt() and 0xFF,
            object : ICommandResponse<BaseRspCmd> {
                override fun onDataResponse(rsp: BaseRspCmd) {
                    val hr = rsp as? RealTimeHeartRateRsp ?: return
                    if (hr.heart > 0) onHeartRateSample(hr.heart, 0, "continuous")
                }
            }
        )

        // Manual measurement result — CMD_STOP_HEART_RATE = 106.
        // StopHeartRateRsp.value is the BPM from a completed on-demand measurement.
        // Also carries rri, sbp, dbp, hrv, stress, temperature, bloodOxygen.
        mgr.addNotifyListener(
            Constants.CMD_STOP_HEART_RATE.toInt() and 0xFF,
            object : ICommandResponse<BaseRspCmd> {
                override fun onDataResponse(rsp: BaseRspCmd) {
                    val hr = rsp as? StopHeartRateRsp ?: return
                    if (hr.errCode.toInt() == 0 && hr.value > 0) {
                        onHeartRateSample(hr.value, hr.rri, "manual")
                    }
                }
            }
        )
    }

    /** Look up already-bonded rings so they appear in ScanResults even while not advertising. */
    @SuppressLint("MissingPermission")
    private fun findBondedRings(): List<DiscoveredDevice> {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)
            ?.adapter ?: return emptyList()
        // Return ALL bonded devices — no type/name/UUID filter.
        // Android sometimes registers BLE rings as CLASSIC or UNKNOWN type, and
        // frequently does not cache service UUIDs or names for devices paired via
        // third-party apps, making any filter unreliable. The user selects their ring.
        return adapter.bondedDevices.map { device ->
            DiscoveredDevice(
                name = device.name?.takeIf { it.isNotBlank() } ?: device.address,
                mac  = device.address,
            )
        }
    }

    private fun onHeartRateSample(bpm: Int, rri: Int, source: String) {
        val deviceName = (_connectionState.value as? ConnectionState.Connected)?.deviceName ?: "Ring"
        _connectionState.value = ConnectionState.Connected(deviceName, bpm)
        scope.launch {
            heartRateDao.insert(
                HeartRateEntity(
                    deviceTimestamp = System.currentTimeMillis(),
                    bpm             = bpm,
                    rri             = rri,
                    source          = source,
                    syncId          = java.util.UUID.randomUUID().toString(),
                    firmwareVersion = firmwareVersion,
                    hardwareVersion = hardwareVersion,
                )
            )
        }
    }
}

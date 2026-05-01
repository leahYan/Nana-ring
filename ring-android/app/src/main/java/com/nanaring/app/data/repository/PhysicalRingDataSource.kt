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
import com.nanaring.app.data.local.dao.ActivityDao
import com.nanaring.app.data.local.dao.HeartRateDao
import com.nanaring.app.data.local.dao.SleepDao
import com.nanaring.app.data.local.entity.ActivityEntity
import com.nanaring.app.data.local.entity.HeartRateEntity
import com.nanaring.app.data.local.entity.SleepEntity
import com.nanaring.app.ui.screens.ConnectionState
import com.oudmon.ble.base.communication.req.ReadSleepDetailsReq
import com.oudmon.ble.base.communication.req.ReadTotalSportDataReq
import java.util.Calendar
import com.nanaring.app.ui.screens.DiscoveredDevice
import com.oudmon.ble.base.bluetooth.BleOperateManager
import com.oudmon.ble.base.bluetooth.ListenerKey
import com.oudmon.ble.base.communication.responseImpl.DeviceNotifyListener
import com.oudmon.ble.base.communication.rsp.DeviceNotifyRsp
import com.oudmon.ble.base.bluetooth.QCBluetoothCallbackCloneReceiver
import com.oudmon.ble.base.communication.CommandHandle
import com.oudmon.ble.base.communication.Constants
import com.oudmon.ble.base.communication.ICommandResponse
import com.oudmon.ble.base.communication.req.SetTimeReq
import com.oudmon.ble.base.communication.req.SimpleKeyReq
import com.oudmon.ble.base.communication.rsp.BaseRspCmd
import com.oudmon.ble.base.communication.rsp.RealTimeHeartRateRsp
import com.oudmon.ble.base.communication.rsp.SetTimeRsp
import com.oudmon.ble.base.communication.rsp.StartHeartRateRsp
import com.oudmon.ble.base.communication.rsp.StopHeartRateRsp
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.nanaring.app.sync.SyncWorker
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
 * Post-connection setup (onServiceDiscovered — ring is ready for commands here):
 *   1. SimpleKeyReq(CMD_BIND_SUCCESS)            — bind/key exchange (required first)
 *   2. CommandHandle.executeReqCmd(SetTimeReq()) — syncs clock, returns SetTimeRsp
 *      with capability flags (mSupportTemperature, mSupportHrv, etc.)
 *   3. CommandHandle.execReadCmd(getReadFmRequest()) — firmware version string
 *   4. CommandHandle.execReadCmd(getReadHwRequest()) — hardware version string
 *   connectStatue(connected=true) fires on GATT_STATE_CONNECTED — too early for commands.
 */
class PhysicalRingDataSource(
    private val context: Context,
    private val application: Application,
    private val heartRateDao: HeartRateDao,
    private val activityDao: ActivityDao,
    private val sleepDao: SleepDao,
) : RingRepository {

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _recentReadings = MutableStateFlow<List<HeartRateEntity>>(emptyList())
    override val recentReadings: StateFlow<List<HeartRateEntity>> = _recentReadings.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Cached from BLE characteristic reads after each connection.
    @Volatile private var firmwareVersion = "unknown"
    @Volatile private var hardwareVersion = "unknown"

    // Devices accumulated during the current scan pass.
    private val discoveredDevices = mutableListOf<DiscoveredDevice>()

    @Volatile private var connectingMac: String? = null
    @Volatile private var receiverRegistered = false
    @Volatile private var measurementStarted = false
    @Volatile private var isMeasuring = false
    @Volatile private var historicalDataRead = false
    private var scanTimeoutJob: Job? = null
    private var measurementJob: Job? = null

    // Capability flags populated from SetTimeRsp after each connection.
    @Volatile private var supportsOneKey = false
    @Volatile private var supportsHrv = false
    @Volatile private var supportsSpO2 = false
    @Volatile private var supportsBP = false
    @Volatile private var supportsTemp = false

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

                // Show Connected UI immediately so the user knows the GATT link is up.
                // Commands are sent in onServiceDiscovered() when the ring is actually ready.
                _connectionState.value = ConnectionState.Connected(name)
                Log.d("BLE_DEBUG", "GATT connected to $name (${device.address}) — awaiting service discovery")
            } else {
                stopPeriodicMeasurement()
                measurementStarted = false
                historicalDataRead = false
                connectingMac = null
                firmwareVersion = "unknown"
                hardwareVersion = "unknown"
                _connectionState.value = ConnectionState.Idle
                Log.d("BLE_DEBUG", "GATT disconnected")
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
            // Ring is fully ready — safe to send commands now.
            Log.d("BLE_DEBUG", "Service discovered — sending bind + SetTimeReq + FW/HW reads")
            val ch = CommandHandle.getInstance()

            // 1. Bind — required before any data commands.
            ch.executeReqCmdNoCallback(SimpleKeyReq(Constants.CMD_BIND_SUCCESS))

            // 2. Sync ring clock; response carries capability flags.
            ch.executeReqCmd(SetTimeReq(), object : ICommandResponse<SetTimeRsp> {
                override fun onDataResponse(rsp: SetTimeRsp) {
                    supportsOneKey = rsp.mSupportOneKeyCheck
                    supportsHrv    = rsp.mSupportHrv
                    supportsSpO2   = rsp.mSupportBloodOxygen
                    supportsBP     = rsp.mSupportBloodPressure
                    supportsTemp   = rsp.mSupportTemperature
                    Log.d("BLE_DEBUG", "Capabilities — oneKey=$supportsOneKey hrv=$supportsHrv spO2=$supportsSpO2 bp=$supportsBP temp=$supportsTemp")
                    // Guard: SetTimeRsp can fire multiple times — only start once.
                    if (!measurementStarted) {
                        measurementStarted = true
                        startPeriodicMeasurement()
                    }
                }
            })

            // 3. Read firmware / hardware version via CommandHandle.
            ch.execReadCmd(ch.getReadFmRequest())
            ch.execReadCmd(ch.getReadHwRequest())
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

    private suspend fun refreshRecentReadings() {
        _recentReadings.value = heartRateDao.getRecent(12)
    }

    override suspend fun getUnsynced(): List<HeartRateEntity> = heartRateDao.getUnsynced()
    override suspend fun markSynced(ids: List<Long>) = heartRateDao.markSynced(ids)

    override suspend fun getUnsyncedActivity(): List<ActivityEntity> = activityDao.getUnsynced()
    override suspend fun markActivitySynced(ids: List<Long>) = activityDao.markSynced(ids)

    override suspend fun getUnsyncedSleep(): List<SleepEntity> = sleepDao.getUnsynced()
    override suspend fun markSleepSynced(ids: List<Long>) = sleepDao.markSynced(ids)

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
     *
     * Two listener types:
     *  - addNotifyListener: fires for specific BLE command responses
     *  - addOutDeviceListener: fires when the ring proactively pushes its own measurements
     *    (built-in background monitoring — no command needed, fires on the ring's schedule)
     */
    private fun registerDataListeners(mgr: BleOperateManager) {
        // Proactive listener — ring pushes data from its own background monitoring.
        // dataType: 1=HR, 2=BP, 3=SpO2, 5=temperature, 7=sport/activity
        mgr.addOutDeviceListener(ListenerKey.All, object : DeviceNotifyListener() {
            override fun onDataResponse(rsp: DeviceNotifyRsp?) {
                val r = rsp ?: return
                if (r.status != 0) return // only accept RESULT_OK
                Log.d("BLE_DEBUG", "proactive — dataType=${r.dataType}")
                if (_connectionState.value !is ConnectionState.Connected) return
                // Ring-initiated measurement: trigger a full read so we capture it.
                when (r.dataType) {
                    1 -> if (!isMeasuring) takeMeasurement() // HR changed
                }
            }
        })

        // Real-time heart rate stream — CMD_REAL_TIME_HEART_RATE = 30.
        // RealTimeHeartRateRsp.heart is the live BPM shown on the connection screen.
        mgr.addNotifyListener(
            Constants.CMD_REAL_TIME_HEART_RATE.toInt() and 0xFF,
            object : ICommandResponse<BaseRspCmd> {
                override fun onDataResponse(rsp: BaseRspCmd) {
                    val hr = rsp as? RealTimeHeartRateRsp ?: return
                    if (hr.heart > 0) {
                        val prev = _connectionState.value as? ConnectionState.Connected ?: return
                        _connectionState.value = prev.copy(bpm = hr.heart)
                    }
                }
            }
        )

        // Measurement result — CMD_STOP_HEART_RATE = 106.
        // Fired at the end of any measurement (manual, periodic, or oneClick).
        mgr.addNotifyListener(
            Constants.CMD_STOP_HEART_RATE.toInt() and 0xFF,
            object : ICommandResponse<BaseRspCmd> {
                override fun onDataResponse(rsp: BaseRspCmd) {
                    Log.d("BLE_DEBUG", "CMD_STOP_HEART_RATE notify — type=${rsp::class.simpleName}")
                    val hr = rsp as? StopHeartRateRsp ?: run {
                        Log.w("BLE_DEBUG", "CMD_STOP_HEART_RATE cast failed — got ${rsp::class.simpleName}")
                        return
                    }
                    Log.d("BLE_DEBUG", "StopHeartRateRsp raw — errCode=${hr.errCode} heart=${hr.heart} spO2=${hr.bloodOxygen} bp=${hr.sbp}/${hr.dbp} hrv=${hr.hrv} stress=${hr.stress} temp=${hr.temperature}")
                    if (hr.errCode.toInt() == 0 && hr.heart > 0) {
                        onMeasurementComplete(hr)
                    } else {
                        Log.w("BLE_DEBUG", "StopHeartRateRsp discarded — errCode=${hr.errCode} heart=${hr.heart}")
                    }
                }
            }
        )
    }

    private fun startPeriodicMeasurement() {
        measurementJob?.cancel()
        measurementJob = scope.launch {
            while (true) {
                takeMeasurement()
                delay(30 * 60 * 1_000L) // 30 minutes
            }
        }
    }

    private fun stopPeriodicMeasurement() {
        measurementJob?.cancel()
        measurementJob = null
    }

    private fun takeMeasurement() {
        if (_connectionState.value !is ConnectionState.Connected) {
            Log.w("BLE_DEBUG", "takeMeasurement skipped — not connected")
            return
        }
        if (isMeasuring) {
            Log.w("BLE_DEBUG", "takeMeasurement skipped — already measuring")
            return
        }
        isMeasuring = true
        Log.d("BLE_DEBUG", "Taking measurement — oneKey=$supportsOneKey")

        if (supportsOneKey) {
            // Single 35 s pass — all metrics in one shot.
            bleManager.oneClickMeasurement(object : ICommandResponse<StopHeartRateRsp> {
                override fun onDataResponse(rsp: StopHeartRateRsp) {
                    isMeasuring = false
                    Log.d("BLE_DEBUG", "oneClick — errCode=${rsp.errCode} heart=${rsp.heart} spO2=${rsp.bloodOxygen} hrv=${rsp.hrv} stress=${rsp.stress} temp=${rsp.temperature}")
                    if (rsp.errCode.toInt() == 0 && rsp.heart > 0) onMeasurementComplete(rsp)
                }
            }, false)
            return
        }

        // Sequential manual measurements. The ring measures one metric at a time;
        // HR streams live readings (~35 s), then SpO2, HRV, Temperature in sequence.
        // Total: ~2 min. Each metric fires StartHeartRateRsp.value (unsigned byte).
        // Temperature encoding: ring sends (temp_tenths mod 256); add 256 to recover.
        scope.launch {
            try {
                var latestHr        = 0
                var latestSpO2      = 0
                var latestHrv       = 0
                var latestSystolic  = 0
                var latestDiastolic = 0
                var latestTemp      = 0f

                // --- Heart Rate (35 s) ---
                val hrCb = object : ICommandResponse<StartHeartRateRsp> {
                    override fun onDataResponse(rsp: StartHeartRateRsp) {
                        val v = rsp.value.toInt() and 0xFF
                        Log.d("BLE_DEBUG", "HR stream — errCode=${rsp.errCode} type=${rsp.type} value=$v")
                        if (rsp.errCode.toInt() == 0 && v in 30..250) {
                            latestHr = v
                            val prev = _connectionState.value as? ConnectionState.Connected ?: return
                            _connectionState.value = prev.copy(bpm = v)
                        }
                    }
                }
                bleManager.manualModeHeart(hrCb, false)
                delay(35_000)
                bleManager.manualModeHeart(hrCb, true)
                Log.d("BLE_DEBUG", "HR done — latestHr=$latestHr")
                delay(1_000)

                if (latestHr == 0) {
                    Log.w("BLE_DEBUG", "HR returned 0 — aborting")
                    return@launch
                }

                // --- Blood Oxygen (30 s) ---
                if (supportsSpO2) {
                    val spO2Cb = object : ICommandResponse<StartHeartRateRsp> {
                        override fun onDataResponse(rsp: StartHeartRateRsp) {
                            val v = rsp.value.toInt() and 0xFF
                            Log.d("BLE_DEBUG", "SpO2 stream — errCode=${rsp.errCode} value=$v")
                            if (rsp.errCode.toInt() == 0 && v in 70..100) latestSpO2 = v
                        }
                    }
                    bleManager.manualModeSpO2(spO2Cb, false)
                    delay(30_000)
                    bleManager.manualModeSpO2(spO2Cb, true)
                    Log.d("BLE_DEBUG", "SpO2 done — latestSpO2=$latestSpO2")
                    delay(1_000)
                }

                // --- HRV (30 s) ---
                if (supportsHrv) {
                    val hrvCb = object : ICommandResponse<StartHeartRateRsp> {
                        override fun onDataResponse(rsp: StartHeartRateRsp) {
                            val v = rsp.value.toInt() and 0xFF
                            Log.d("BLE_DEBUG", "HRV stream — errCode=${rsp.errCode} value=$v")
                            if (rsp.errCode.toInt() == 0 && v > 0) latestHrv = v
                        }
                    }
                    bleManager.manualModeHrv(hrvCb, false)
                    delay(30_000)
                    bleManager.manualModeHrv(hrvCb, true)
                    Log.d("BLE_DEBUG", "HRV done — latestHrv=$latestHrv")
                    delay(1_000)
                }

                // --- Blood Pressure (30 s) ---
                if (supportsBP) {
                    val bpCb = object : ICommandResponse<StartHeartRateRsp> {
                        override fun onDataResponse(rsp: StartHeartRateRsp) {
                            Log.d("BLE_DEBUG", "BP stream — errCode=${rsp.errCode} type=${rsp.type} sbp=${rsp.sbp} dbp=${rsp.dbp}")
                            if (rsp.errCode.toInt() == 0 && rsp.sbp > 0 && rsp.dbp > 0) {
                                latestSystolic  = rsp.sbp
                                latestDiastolic = rsp.dbp
                            }
                        }
                    }
                    bleManager.manualModeBP(bpCb, false)
                    delay(30_000)
                    bleManager.manualModeBP(bpCb, true)
                    Log.d("BLE_DEBUG", "BP done — sys=$latestSystolic dia=$latestDiastolic")
                    delay(1_000)
                }

                // --- Temperature (30 s) ---
                // Ring encodes temp as tenths-of-°C mod 256 (e.g. 36.0°C → 360 mod 256 = 104).
                if (supportsTemp) {
                    val tempCb = object : ICommandResponse<StartHeartRateRsp> {
                        override fun onDataResponse(rsp: StartHeartRateRsp) {
                            val raw = rsp.value.toInt() and 0xFF
                            val v = (raw + 256) / 10f
                            Log.d("BLE_DEBUG", "Temp stream — errCode=${rsp.errCode} raw=$raw decoded=${v}°C")
                            if (rsp.errCode.toInt() == 0 && v in 30f..42f) latestTemp = v
                        }
                    }
                    bleManager.manualTemperature(tempCb, false)
                    delay(30_000)
                    bleManager.manualTemperature(tempCb, true)
                    Log.d("BLE_DEBUG", "Temp done — latestTemp=$latestTemp")
                    delay(1_000)
                }

                val prev = _connectionState.value as? ConnectionState.Connected
                _connectionState.value = ConnectionState.Connected(
                    deviceName  = prev?.deviceName ?: (lastKnownName ?: "Ring"),
                    bpm         = latestHr,
                    spO2        = if (latestSpO2 > 0) latestSpO2 else prev?.spO2 ?: 0,
                    systolic    = if (latestSystolic > 0) latestSystolic else prev?.systolic ?: 0,
                    diastolic   = if (latestDiastolic > 0) latestDiastolic else prev?.diastolic ?: 0,
                    hrv         = if (latestHrv > 0) latestHrv else prev?.hrv ?: 0,
                    stress      = prev?.stress ?: 0,
                    temperature = if (latestTemp > 0f) latestTemp else prev?.temperature ?: 0f,
                )
                Log.d("BLE_DEBUG", "Measurement complete — bpm=$latestHr spO2=$latestSpO2 bp=$latestSystolic/$latestDiastolic hrv=$latestHrv temp=$latestTemp")

                heartRateDao.insert(
                    HeartRateEntity(
                        deviceTimestamp = System.currentTimeMillis(),
                        bpm             = latestHr,
                        rri             = null,
                        spO2            = latestSpO2.takeIf { it > 0 },
                        systolic        = latestSystolic.takeIf { it > 0 },
                        diastolic       = latestDiastolic.takeIf { it > 0 },
                        hrv             = latestHrv.takeIf { it > 0 },
                        stress          = null,
                        temperature     = latestTemp.takeIf { it > 0f },
                        source          = "periodic",
                        syncId          = java.util.UUID.randomUUID().toString(),
                        firmwareVersion = firmwareVersion,
                        hardwareVersion = hardwareVersion,
                    )
                )
                refreshRecentReadings()
                WorkManager.getInstance(context).enqueueUniqueWork(
                    "ring_sync",
                    ExistingWorkPolicy.KEEP,
                    OneTimeWorkRequestBuilder<SyncWorker>().build(),
                )
            } finally {
                isMeasuring = false
            }
            // Historical read runs after the first measurement so it never
            // sends commands concurrently with an active measurement.
            if (!historicalDataRead) {
                historicalDataRead = true
                readHistoricalData()
            }
        }
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

    // -------------------------------------------------------------------------
    // Historical data — activity + sleep for the last 7 days
    // -------------------------------------------------------------------------

    private fun readHistoricalData() {
        scope.launch {
            Log.d("BLE_DEBUG", "readHistoricalData — reading 7 days of activity + sleep")
            readActivityHistory()
            readSleepHistory()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "ring_sync",
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<SyncWorker>().build(),
            )
        }
    }

    private fun getInt(obj: Any, vararg names: String): Int {
        for (name in names) {
            try { return obj.javaClass.getField(name).getInt(obj) } catch (_: Exception) {}
        }
        return 0
    }

    private suspend fun readActivityHistory() {
        val ch = CommandHandle.getInstance()
        for (dayOffset in 0..6) {
            val latch = kotlinx.coroutines.CompletableDeferred<Unit>()
            ch.executeReqCmd(ReadTotalSportDataReq(dayOffset), object : ICommandResponse<BaseRspCmd> {
                override fun onDataResponse(rsp: BaseRspCmd) {
                    scope.launch {
                        try {
                            val steps    = getInt(rsp, "totalSteps", "steps")
                            val running  = getInt(rsp, "runningSteps")
                            val dist     = getInt(rsp, "walkDistance", "distance")
                            val calorie  = getInt(rsp, "calorie", "calories")
                            val sportDur = getInt(rsp, "sportDuration", "sportTime")
                            val sleepDur = getInt(rsp, "sleepDuration", "sleepTime")
                            if (steps <= 0 && calorie <= 0) return@launch
                            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -dayOffset) }
                            val syncId = "activity-$dayOffset-${cal.get(Calendar.DAY_OF_MONTH)}-${cal.get(Calendar.MONTH)+1}-${cal.get(Calendar.YEAR)}"
                            activityDao.insert(
                                ActivityEntity(
                                    deviceTimestamp      = System.currentTimeMillis(),
                                    daysAgo              = dayOffset,
                                    steps                = steps,
                                    runningSteps         = running,
                                    walkDistanceMeters   = dist,
                                    caloriesKcal         = calorie / 1000,
                                    sportDurationSeconds = sportDur,
                                    sleepDurationSeconds = sleepDur,
                                    syncId               = syncId,
                                )
                            )
                            Log.d("BLE_DEBUG", "activity day-$dayOffset: steps=$steps cal=${calorie/1000}kcal")
                        } finally {
                            latch.complete(Unit)
                        }
                    }
                }
            })
            kotlinx.coroutines.withTimeoutOrNull(10_000) { latch.await() }
            delay(500)
        }
    }

    private suspend fun readSleepHistory() {
        val ch = CommandHandle.getInstance()
        for (dayOffset in 0..6) {
            val latch = kotlinx.coroutines.CompletableDeferred<Unit>()
            ch.executeReqCmd(ReadSleepDetailsReq(dayOffset, 0, 0), object : ICommandResponse<BaseRspCmd> {
                override fun onDataResponse(rsp: BaseRspCmd) {
                    scope.launch {
                        try {
                            // SDK field on ReadSleepDetailsRsp — try common names
                            val details = listOf("details", "sleepDetails", "bleDetails", "data")
                                .firstNotNullOfOrNull { name ->
                                    try { rsp.javaClass.getField(name).get(rsp) as? List<*> } catch (_: Exception) { null }
                                } ?: return@launch
                            if (details.isEmpty()) return@launch
                            processSleepDetails(details, dayOffset)
                        } finally {
                            latch.complete(Unit)
                        }
                    }
                }
            })
            kotlinx.coroutines.withTimeoutOrNull(10_000) { latch.await() }
            delay(500)
        }
    }

    private suspend fun processSleepDetails(details: List<*>, dayOffset: Int) {
        // Build slotIndex → stageType map from each BleSleepDetails entry
        val allQualities = mutableMapOf<Int, Int>()
        for (detail in details) {
            val d = detail ?: continue
            // BleSleepDetails fields: timeIndex (Int), sleepQualities (IntArray)
            val timeIndex = try {
                d.javaClass.getField("timeIndex").getInt(d)
            } catch (_: Exception) { continue }
            val qualities = try {
                d.javaClass.getField("sleepQualities").get(d) as? IntArray
            } catch (_: Exception) { null } ?: continue
            for ((i, type) in qualities.withIndex()) {
                if (type > 0) allQualities[timeIndex + i] = type
            }
        }
        if (allQualities.isEmpty()) return

        val validSlots = allQualities.entries.filter { it.value in 2..5 }
        if (validSlots.isEmpty()) return

        val firstSlot = validSlots.minOf { it.key }
        val lastSlot  = validSlots.maxOf { it.key }

        // Midnight of the day (now - dayOffset days)
        val midnight = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -dayOffset)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val startTs = midnight + firstSlot * 15 * 60 * 1000L
        val endTs   = midnight + (lastSlot + 1) * 15 * 60 * 1000L

        var deepMinutes  = 0; var lightMinutes = 0; var remMinutes   = 0
        var awakeMinutes = 0; var notWornMinutes = 0

        for ((_, type) in allQualities) {
            when (type) {
                1 -> notWornMinutes += 15
                2 -> lightMinutes   += 15
                3 -> deepMinutes    += 15
                4 -> remMinutes     += 15
                5 -> awakeMinutes   += 15
            }
        }

        // Group consecutive same-type slots into segments for stagesJson
        val segments = mutableListOf<Pair<Int, Int>>() // type, durationMinutes
        var segType = -1; var segSlots = 0
        for (slotIdx in firstSlot..lastSlot) {
            val type = allQualities[slotIdx] ?: 0
            if (type == segType) {
                segSlots++
            } else {
                if (segType > 0 && segSlots > 0) segments.add(segType to segSlots * 15)
                segType = type; segSlots = 1
            }
        }
        if (segType > 0 && segSlots > 0) segments.add(segType to segSlots * 15)

        val stagesJson = "[" + segments.joinToString(",") { """{"type":${it.first},"durationMinutes":${it.second}}""" } + "]"
        val totalMinutes = deepMinutes + lightMinutes + remMinutes + awakeMinutes

        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -dayOffset) }
        val syncId = "sleep-$dayOffset-${cal.get(Calendar.DAY_OF_MONTH)}-${cal.get(Calendar.MONTH)+1}-${cal.get(Calendar.YEAR)}"

        sleepDao.insert(
            SleepEntity(
                sleepId         = java.util.UUID.randomUUID().toString(),
                deviceTimestamp = System.currentTimeMillis(),
                daysAgo         = dayOffset,
                startTimestamp  = startTs,
                endTimestamp    = endTs,
                deepMinutes     = deepMinutes,
                lightMinutes    = lightMinutes,
                remMinutes      = remMinutes,
                awakeMinutes    = awakeMinutes,
                notWornMinutes  = notWornMinutes,
                totalMinutes    = totalMinutes,
                wakingCount     = segments.count { it.first == 5 },
                stagesJson      = stagesJson,
                syncId          = syncId,
            )
        )
        Log.d("BLE_DEBUG", "sleep day-$dayOffset: deep=$deepMinutes light=$lightMinutes rem=$remMinutes awake=$awakeMinutes total=$totalMinutes min")
    }

    // Called only from oneClickMeasurement path — StopHeartRateRsp.heart is the HR field.
    private fun onMeasurementComplete(rsp: StopHeartRateRsp) {
        val prev = _connectionState.value as? ConnectionState.Connected
        val deviceName = prev?.deviceName ?: "Ring"
        _connectionState.value = ConnectionState.Connected(
            deviceName  = deviceName,
            bpm         = rsp.heart,
            spO2        = rsp.bloodOxygen.takeIf { it > 0 } ?: prev?.spO2 ?: 0,
            systolic    = rsp.sbp.takeIf { it > 0 } ?: prev?.systolic ?: 0,
            diastolic   = rsp.dbp.takeIf { it > 0 } ?: prev?.diastolic ?: 0,
            hrv         = rsp.hrv.takeIf { it > 0 } ?: prev?.hrv ?: 0,
            stress      = rsp.stress.takeIf { it > 0 } ?: prev?.stress ?: 0,
            temperature = rsp.temperature.takeIf { it > 0 }?.toFloat() ?: prev?.temperature ?: 0f,
        )
        Log.d("BLE_DEBUG", "Measurement — heart=${rsp.heart} rri=${rsp.rri} spO2=${rsp.bloodOxygen} bp=${rsp.sbp}/${rsp.dbp} hrv=${rsp.hrv} stress=${rsp.stress} temp=${rsp.temperature}")
        scope.launch {
            heartRateDao.insert(
                HeartRateEntity(
                    deviceTimestamp = System.currentTimeMillis(),
                    bpm             = rsp.heart,
                    rri             = rsp.rri.takeIf { it > 0 },
                    spO2            = rsp.bloodOxygen.takeIf { it > 0 },
                    systolic        = rsp.sbp.takeIf { it > 0 },
                    diastolic       = rsp.dbp.takeIf { it > 0 },
                    hrv             = rsp.hrv.takeIf { it > 0 },
                    stress          = rsp.stress.takeIf { it > 0 },
                    temperature     = rsp.temperature.takeIf { it > 0 }?.toFloat(),
                    source          = "periodic",
                    syncId          = java.util.UUID.randomUUID().toString(),
                    firmwareVersion = firmwareVersion,
                    hardwareVersion = hardwareVersion,
                )
            )
            refreshRecentReadings()
            // Enqueue sync immediately after insert — deduplicated so rapid measurements
            // don't stack up multiple workers.
            WorkManager.getInstance(context).enqueueUniqueWork(
                "ring_sync",
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<SyncWorker>().build(),
            )
            if (!historicalDataRead) {
                historicalDataRead = true
                readHistoricalData()
            }
        }
    }
}

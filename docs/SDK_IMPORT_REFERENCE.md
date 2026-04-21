# SDK Import Reference (Decompiled from qring_sdk_1_0_0_1.aar)

> **Source:** These import paths and method signatures were extracted by decompiling the actual `qring_sdk_1_0_0_1.aar` file using `javap`. They are verified correct.

---

## 1. The Three Missing Classes

### 1.1 CommandHandle

```kotlin
import com.oudmon.ble.base.communication.CommandHandle

// Public API:
CommandHandle.getInstance(): CommandHandle
commandHandle.getReadHwRequest(): ReadRequest        // hardware version
commandHandle.getReadFmRequest(): ReadRequest        // firmware version
commandHandle.executeReqCmd(cmd: BaseReqCmd, callback: ICommandResponse)
commandHandle.executeReqCmdNoCallback(cmd: BaseReqCmd)
commandHandle.execReadCmd(request: ReadRequest)
```

### 1.2 SimpleKeyReq (Bind Command)

```kotlin
import com.oudmon.ble.base.communication.req.SimpleKeyReq

// Constructor takes a single byte — use Constants.CMD_BIND_SUCCESS (= 16)
val bindReq = SimpleKeyReq(Constants.CMD_BIND_SUCCESS)
CommandHandle.getInstance().executeReqCmdNoCallback(bindReq)
```

### 1.3 connectWithScan — Confirmed on BleOperateManager

```kotlin
import com.oudmon.ble.base.bluetooth.BleOperateManager

// Method is exactly: connectWithScan(address: String)
BleOperateManager.getInstance().connectWithScan(macAddress)

// Also confirmed:
BleOperateManager.getInstance().connectDirectly(macAddress)
BleOperateManager.getInstance().unBindDevice()
BleOperateManager.getInstance().disconnect()
BleOperateManager.getInstance().setNeedConnect(needConnect: Boolean)
BleOperateManager.getInstance().setBluetoothTurnOff(isOff: Boolean)
BleOperateManager.getInstance().isConnected(): Boolean
BleOperateManager.getInstance().getConnectState(): Int
```

---

## 2. Complete Import Map

### Scanning

```kotlin
import com.oudmon.ble.base.scan.BleScannerHelper
import com.oudmon.ble.base.scan.ScanWrapperCallback
import com.oudmon.ble.base.scan.OnTheScanResult
```

**ScanWrapperCallback interface:**
```kotlin
interface ScanWrapperCallback {
    fun onStart()
    fun onStop()
    fun onLeScan(device: BluetoothDevice, rssi: Int, scanRecord: ByteArray)
    fun onScanFailed(errorCode: Int)
    fun onParsedData(device: BluetoothDevice, scanRecord: ScanRecord)
    fun onBatchScanResults(results: List<ScanResult>)
}
```

**OnTheScanResult interface (for targeted MAC scan):**
```kotlin
interface OnTheScanResult {
    fun onResult(device: BluetoothDevice)
    fun onScanFailed(errorCode: Int)
}
```

### Connection & BLE Management

```kotlin
import com.oudmon.ble.base.bluetooth.BleOperateManager
import com.oudmon.ble.base.bluetooth.OnGattEventCallback
import com.oudmon.ble.base.bluetooth.QCBluetoothCallbackCloneReceiver
```

**BleOperateManager — requires Application context on first call:**
```kotlin
// IMPORTANT: First call must pass Application
BleOperateManager.getInstance(application)
// Subsequent calls can omit it
BleOperateManager.getInstance()
```

**OnGattEventCallback interface:**
```kotlin
interface OnGattEventCallback {
    fun onReceivedData(uuid: String, data: ByteArray)
}
```

**QCBluetoothCallbackCloneReceiver — extend this for BLE events:**
```kotlin
// This is a BroadcastReceiver. Override these methods:
class MyBluetoothReceiver : QCBluetoothCallbackCloneReceiver() {
    override fun connectStatue(device: BluetoothDevice, isConnected: Boolean) { }
    override fun onCharacteristicRead(uuid: String?, data: ByteArray?) { }
    override fun onServiceDiscovered() { }
    override fun onCommandSend(data: ByteArray?) { }
    override fun onCharacteristicChange(serviceUuid: String, charUuid: String, data: ByteArray) { }
    override fun onCharacteristicChangeFilter(serviceUuid: String, charUuid: String, data: ByteArray) { }
    override fun onDescriptorWrite(uuid: String, status: Int) { }
}
```

### Commands & Requests

```kotlin
import com.oudmon.ble.base.communication.CommandHandle
import com.oudmon.ble.base.communication.ICommandResponse
import com.oudmon.ble.base.communication.Constants
import com.oudmon.ble.base.request.ReadRequest

// Request classes (in com.oudmon.ble.base.communication.req)
import com.oudmon.ble.base.communication.req.BaseReqCmd
import com.oudmon.ble.base.communication.req.SimpleKeyReq
import com.oudmon.ble.base.communication.req.SetTimeReq
import com.oudmon.ble.base.communication.req.TimeFormatReq
import com.oudmon.ble.base.communication.req.StartHeartRateReq
import com.oudmon.ble.base.communication.req.StopHeartRateReq
import com.oudmon.ble.base.communication.req.ReadHeartRateReq
import com.oudmon.ble.base.communication.req.ReadDetailSportDataReq
import com.oudmon.ble.base.communication.req.ReadTotalSportDataReq
import com.oudmon.ble.base.communication.req.PressureReq
import com.oudmon.ble.base.communication.req.HRVReq
import com.oudmon.ble.base.communication.req.BloodOxygenSettingReq
import com.oudmon.ble.base.communication.req.TargetSettingReq
import com.oudmon.ble.base.communication.req.ReadSleepDetailsReq
```

### Responses

```kotlin
// Response classes (in com.oudmon.ble.base.communication.rsp)
import com.oudmon.ble.base.communication.rsp.BaseRspCmd
import com.oudmon.ble.base.communication.rsp.SetTimeRsp
import com.oudmon.ble.base.communication.rsp.StartHeartRateRsp
import com.oudmon.ble.base.communication.rsp.StopHeartRateRsp
import com.oudmon.ble.base.communication.rsp.BatteryRsp
import com.oudmon.ble.base.communication.rsp.ReadHeartRateRsp
import com.oudmon.ble.base.communication.rsp.ReadDetailSportDataRsp
import com.oudmon.ble.base.communication.rsp.TodaySportDataRsp
import com.oudmon.ble.base.communication.rsp.TotalSportDataRsp
import com.oudmon.ble.base.communication.rsp.PressureRsp
import com.oudmon.ble.base.communication.rsp.HRVRsp
import com.oudmon.ble.base.communication.rsp.BloodOxygenSettingRsp
import com.oudmon.ble.base.communication.rsp.SleepNewProtoResp
import com.oudmon.ble.base.communication.rsp.UserProfileRsp
import com.oudmon.ble.base.communication.rsp.TimeFormatRsp
import com.oudmon.ble.base.communication.rsp.ReadBlePressureRsp
```

### Constants (Relevant Subset)

```kotlin
import com.oudmon.ble.base.communication.Constants

// BLE UUIDs
Constants.UUID_SERVICE          // UART Service: 6E40FFF0-B5A3-F393-E0A9-E50E24DCCA9E
Constants.UUID_READ             // TX (ring → phone): 6E400003-...
Constants.UUID_WRITE            // RX (phone → ring): 6E400002-...
Constants.SERVICE_DEVICE_INFO   // Device Info: 0000180A-...
Constants.CHAR_FIRMWARE_REVISION // Firmware: 00002A26-...
Constants.CHAR_HW_REVISION      // Hardware: 00002A27-...

// Bind command
Constants.CMD_BIND_SUCCESS      // = 16 (byte), used with SimpleKeyReq
```

---

## 3. Manual Measurement Methods on BleOperateManager

These are the methods for triggering real-time measurements. Each takes an `ICommandResponse` callback:

```kotlin
// Heart rate (returns StartHeartRateRsp during measurement, StopHeartRateRsp at end)
BleOperateManager.getInstance().manualModeHeart(callback: ICommandResponse<StartHeartRateRsp>, start: Boolean)

// Blood pressure (SDK-derived from HR)
BleOperateManager.getInstance().manualModeBP(callback: ICommandResponse<StartHeartRateRsp>, start: Boolean)

// SpO2
BleOperateManager.getInstance().manualModeSpO2(callback: ICommandResponse<StartHeartRateRsp>, start: Boolean)

// Stress/Pressure
BleOperateManager.getInstance().manualModePressure(callback: ICommandResponse<StartHeartRateRsp>, start: Boolean)

// HRV
BleOperateManager.getInstance().manualModeHrv(callback: ICommandResponse<StartHeartRateRsp>, start: Boolean)

// Temperature (single point)
BleOperateManager.getInstance().manualTemperature(callback: ICommandResponse<StartHeartRateRsp>, start: Boolean)

// Temperature (three-point)
BleOperateManager.getInstance().manualThreeTemperature(callback: ICommandResponse<StartHeartRateRsp>, start: Boolean)

// One-click measurement (all metrics at once, if ring supports it)
BleOperateManager.getInstance().oneClickMeasurement(callback: ICommandResponse<StopHeartRateRsp>, start: Boolean)

// Raw PPG data modes
BleOperateManager.getInstance().manualModeHeartRateRawData(callback: ICommandResponse<StopHeartRateRsp>, type: Int, start: Boolean)
BleOperateManager.getInstance().manualModeBloodOxygenRawData(callback: ICommandResponse<StopHeartRateRsp>, type: Int, start: Boolean)
```

**Start/stop pattern:** Call with `start = true` to begin, `start = false` to stop.

---

## 4. SetTimeRsp Capability Flags

After sending `SetTimeReq`, the ring responds with `SetTimeRsp` containing these boolean flags:

```kotlin
setTimeRsp.mSupportTemperature: Boolean
setTimeRsp.mSupportBloodOxygen: Boolean
setTimeRsp.mSupportBloodPressure: Boolean
setTimeRsp.mSupportOneKeyCheck: Boolean
setTimeRsp.mSupportHrv: Boolean
setTimeRsp.mSupportManualHeart: Boolean
setTimeRsp.mSupportContact: Boolean
setTimeRsp.mSupportWeather: Boolean
setTimeRsp.mSupportPlate: Boolean
setTimeRsp.mNewSleepProtocol: Boolean
setTimeRsp.mMaxWatchFace: Int
setTimeRsp.mMaxContacts: Int
setTimeRsp.mMusicSupport: Boolean
setTimeRsp.mEbookSupport: Boolean
setTimeRsp.rtkMcu: Boolean
```

Use these to determine which measurements to enable in the UI.

---

## 5. Correct Full Initialisation Sequence (Updated)

```
1. Check & request runtime permissions:
   - BLUETOOTH_SCAN, BLUETOOTH_CONNECT (Android 12+)
   - ACCESS_FINE_LOCATION (all versions)
2. Verify Bluetooth is enabled (BluetoothAdapter.isEnabled())
3. Initialise SDK: BleOperateManager.getInstance(application)
4. Register MyBluetoothReceiver (extends QCBluetoothCallbackCloneReceiver)
5. Scan: BleScannerHelper.getInstance().scanDevice(context, Constants.UUID_SERVICE, scanCallback)
6. User selects ring → BleScannerHelper.getInstance().stopScan(context)
7. Connect: BleOperateManager.getInstance().connectWithScan(macAddress)
8. In connectStatue(device, true) callback:
   a. Send bind: CommandHandle.getInstance().executeReqCmdNoCallback(SimpleKeyReq(Constants.CMD_BIND_SUCCESS))
   b. Send SetTimeReq: CommandHandle.getInstance().executeReqCmd(SetTimeReq(), setTimeCallback)
   c. Read firmware: CommandHandle.getInstance().execReadCmd(CommandHandle.getInstance().getReadFmRequest())
   d. Read hardware: CommandHandle.getInstance().execReadCmd(CommandHandle.getInstance().getReadHwRequest())
9. In SetTimeRsp callback: read capability flags, store supported features
10. NOW the ring is ready for measurements and data sync
```

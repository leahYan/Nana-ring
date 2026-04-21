# BLE Connection & Protocol Reference

> **Purpose:** This document exists because the dev agent has repeatedly failed to establish a working Bluetooth connection to the ring. It combines two sources of truth: (1) the official SDK documentation for `ring_sdk_1.0.0.1.aar`, and (2) a proven working open-source client (`colmi_r02_client`) that communicates with the same ring hardware. Use this to understand what the SDK is doing under the hood and to implement the connection correctly.

---

## 1. How the Ring Actually Works (From Working Open-Source Client)

The Colmi ring family (R02, R06, R10, and rebranded variants) uses a **Nordic UART Service (NUS)** pattern over BLE GATT. There is **no proprietary pairing or binding protocol** — it's standard BLE.

### 1.1 GATT Service & Characteristics

| Purpose                              | UUID                                   |
| ------------------------------------ | -------------------------------------- |
| UART Service                         | `6E40FFF0-B5A3-F393-E0A9-E50E24DCCA9E` |
| RX (write commands TO the ring)      | `6E400002-B5A3-F393-E0A9-E50E24DCCA9E` |
| TX (receive responses FROM the ring) | `6E400003-B5A3-F393-E0A9-E50E24DCCA9E` |
| Device Information Service           | `0000180A-0000-1000-8000-00805F9B34FB` |
| Hardware Version Characteristic      | `00002A27-0000-1000-8000-00805F9B34FB` |
| Firmware Version Characteristic      | `00002A26-0000-1000-8000-00805F9B34FB` |

### 1.2 Packet Protocol

All communication uses **fixed 16-byte packets** in both directions:

- **Byte 0:** Command ID (0–255)
- **Bytes 1–14:** Payload / sub-data (zero-padded if unused)
- **Byte 15:** Checksum = `sum(bytes[0:15]) & 0xFF`

### 1.3 Proven Working Connection Sequence

From `colmi_r02_client/client.py`, the connection sequence that **actually works** is:

```
1. Scan for BLE devices (filter by known name prefixes: R02, R06, R10, COLMI, Hello Ring, etc.)
2. Connect to device by MAC address
3. Discover GATT services
4. Get the UART service by UUID (6E40FFF0-...)
5. Get the RX characteristic from that service (6E400002-...)
6. Subscribe to TX characteristic notifications (6E400003-...) with a callback handler
7. NOW you can send commands by writing 16-byte packets to RX
8. Responses arrive asynchronously via the TX notification callback
```

**Critical:** Step 6 (subscribing to TX notifications) MUST happen BEFORE sending any commands. Without this, the ring sends responses but nobody is listening.

### 1.4 Known Device Name Prefixes

The ring advertises itself with these BLE name prefixes (from the working client):

```
R01, R02, R03, R04, R05, R06, R07, R09, R10,
COLMI, VK-5098, MERLIN, Hello Ring, RING1,
boAtring, TR-R02, SE, EVOLVEO, GL-SR2,
Blaupunkt, KSIX RING
```

Use these prefixes to filter BLE scan results when looking for compatible rings.

### 1.5 Key Command IDs (For Reference)

| Command                     | ID (decimal) | Purpose                                  |
| --------------------------- | ------------ | ---------------------------------------- |
| Set Time                    | 1            | Sync clock — send early after connection |
| Battery                     | 3            | Get battery level and charging status    |
| Heart Rate Log              | 21           | Get stored heart rate data for a day     |
| Start Real-Time Measurement | 105          | Begin live HR/SpO2/BP reading            |
| Stop Real-Time Measurement  | 106          | End live reading                         |

### 1.6 Post-Connection Initialisation

After connecting, the working client:

1. Reads device info (firmware + hardware version) from the Device Information Service (`0x180A`)
2. Sends `CMD_SET_TIME` (command `0x01`) with BCD-encoded UTC date/time
3. The ring responds to `set_time` with a **capability flags** packet that indicates which features the ring supports (`mSupportTemperature`, `mSupportBloodOxygen`, `mSupportBloodPressure`, `mSupportHrv`, `mSupportOneKeyCheck`, etc.)

**If your app skips set_time, the ring may not respond to subsequent data commands.**

---

## 2. Official SDK Usage (From SDK Documentation PDF)

The `.aar` SDK wraps the raw BLE protocol above into higher-level Java/Kotlin classes. Here is the correct usage sequence.

### 2.1 Required Android Permissions

Add ALL of these to `AndroidManifest.xml`:

```xml
<!-- Location (required for BLE scanning on all Android versions) -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />

<!-- Bluetooth (Android 12+ / API 31+) -->
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />
```

**CRITICAL: On Android 12+ (API 31+), you MUST request `BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT` as runtime permissions BEFORE calling any scan/connect methods. Without runtime permission grants, scanning will silently return zero results and connections will fail silently. This is the most common cause of "BLE not working" on modern Android.**

### 2.2 Scanning for Rings

```kotlin
// Start a general BLE scan
// mUuid should be the UART Service UUID: 6E40FFF0-B5A3-F393-E0A9-E50E24DCCA9E
BleScannerHelper.getInstance().scanDevice(
    context,       // Activity or Application context
    mUuid,         // UUID filter for the ring's UART service
    scanCallback   // ScanWrapperCallback implementation
)

// Stop scanning
BleScannerHelper.getInstance().stopScan(context)

// Scan for a specific known device by MAC address
BleScannerHelper.getInstance().scanTheDevice(
    context,
    macAddress,    // e.g., "E7:E9:42:AE:59:1B"
    scanResult     // OnTheScanResult callback
)
```

**IMPORTANT on the UUID parameter:** The `mUuid` parameter should be the UART Service UUID (`6E40FFF0-B5A3-F393-E0A9-E50E24DCCA9E`). The working open-source client confirms this is the service the ring advertises. If you pass `null` or a wrong UUID, scanning will either return all BLE devices (noisy) or miss the ring entirely.

### 2.3 Connecting to a Ring

```kotlin
// Option 1: Direct connection (if you know the MAC and the device is in range)
BleOperateManager.getInstance().connectDirectly(deviceAddress)

// Option 2: Scan then connect (scans for the MAC first, then connects when found)
BleOperateManager.getInstance().connectWithScan(deviceAddress)

// Disconnect
BleOperateManager.getInstance().unBindDevice()

// Enable auto-reconnect
BleOperateManager.getInstance().setNeedConnect(true)

// Handle Bluetooth being turned off by the user
BleOperateManager.getInstance().setBluetoothTurnOff(false)
BleOperateManager.getInstance().disconnect()

// Re-enable Bluetooth state monitoring
BleOperateManager.getInstance().setBluetoothTurnOff(true)
```

### 2.4 Post-Connection Bind

After the connection is established, you MUST send a bind/key exchange command:

```kotlin
CommandHandle.getInstance()
    .executeReqCmdNoCallback(
        SimpleKeyReq(Constants.BIND_KEY)  // or whatever key constant the SDK defines
    )
```

This is the SDK's equivalent of the open-source client's `set_time` initialisation. **Without this step, the ring will not respond to data commands.**

### 2.5 Wearing Calibration

The ring supports a calibration mode for when it's first worn:

```kotlin
// Start calibration (enable = true)
BleOperateManager.getInstance().ringCalibration(false) {
    // Callback: it.success returns status
    // 2 = Measuring
    // 1 = Success
    // 3 = Fail
}

// End calibration (enable = false) — same method call pattern
```

### 2.6 Reading Firmware & Hardware Version

This must be done using the SDK's GATT read commands, NOT by directly reading characteristics:

```kotlin
// Request hardware version
CommandHandle.getInstance().execReadCmd(
    CommandHandle.getInstance().getReadHwRequest()
)

// Request firmware version
CommandHandle.getInstance().execReadCmd(
    CommandHandle.getInstance().getReadFmRequest()
)
```

**Receiving the response:** You must implement `QCBluetoothCallbackCloneReceiver` (refer to the SDK demo's `MyBluetoothReceiver` class). Override `onCharacteristicRead` and check the UUID:

```kotlin
override fun onCharacteristicRead(uuid: String?, data: ByteArray?) {
    if (uuid != null && data != null) {
        val version = String(data, Charsets.UTF_8)
        when (uuid) {
            Constants.CHAR_FIRMWARE_REVISION.toString() -> {
                // Firmware version string, e.g., "1.0.0.1"
            }
            Constants.CHAR_HW_REVISION.toString() -> {
                // Hardware version string, e.g., "HW_v2"
            }
        }
    }
}
```

**Under the hood:** The open-source client confirms these map to standard BLE Device Information Service characteristics — firmware is UUID `0x2A26` and hardware is UUID `0x2A27` on service `0x180A`. The SDK wraps this into its own read command pattern.

---

## 3. Common Failure Modes & Debugging Checklist

If BLE scanning or connection is not working, check these in order:

### 3.1 Scanning Returns Zero Results

- [ ] **Runtime permissions not granted.** On Android 12+, `BLUETOOTH_SCAN` must be granted at runtime BEFORE calling `scanDevice()`. Check with `ContextCompat.checkSelfPermission()` and request if not granted. This is the #1 cause of silent scan failure.
- [ ] **Location permission not granted.** On Android 11 and below, `ACCESS_FINE_LOCATION` must be granted at runtime for BLE scanning to work.
- [ ] **Bluetooth is turned off.** Check `BluetoothAdapter.isEnabled()` before scanning.
- [ ] **Location services are off.** On some Android versions, location services must be enabled for BLE scanning even with permissions granted.
- [ ] **Wrong UUID filter.** If `scanDevice()` is called with an incorrect UUID, the ring won't appear. Use `6E40FFF0-B5A3-F393-E0A9-E50E24DCCA9E`.
- [ ] **Ring is already connected to another device.** The ring can only maintain one BLE connection at a time. Disconnect from the QRing app or any other app first.
- [ ] **Ring is out of range or battery is dead.** BLE range on the ring is very short (1–3 meters typically).

### 3.2 Connection Fails or Drops Immediately

- [ ] **`BLUETOOTH_CONNECT` permission not granted** (Android 12+). Required for `connectDirectly()` and `connectWithScan()`.
- [ ] **No bind/set_time sent after connection.** The SDK expects `SimpleKeyReq` or equivalent to be sent immediately after connection. Without it, the ring may reject further commands.
- [ ] **Ring is bonded to another phone's Bluetooth stack.** Go to Android Bluetooth settings and "Forget" the ring, then retry.
- [ ] **Using `connectDirectly()` when ring is not in range.** Use `connectWithScan()` instead — it scans first, then connects when the ring is found.

### 3.3 Connected But No Data / Commands Time Out

- [ ] **TX notifications not subscribed.** The SDK should handle this internally, but verify that `onCharacteristicChanged` callbacks are firing. If not, the app is sending commands but not receiving responses.
- [ ] **`SetTimeReq` not sent.** The ring needs its clock synced after every connection. Without this, data retrieval commands may return empty or fail.
- [ ] **Ring is not being worn.** Real-time heart rate and SpO2 measurements require skin contact. The ring will return error codes (not fake data) if it can't get a reading.

---

## 4. Correct Full Initialisation Sequence (SDK)

For the dev agent, here is the exact sequence to implement:

```
1. Check & request runtime permissions (BLUETOOTH_SCAN, BLUETOOTH_CONNECT, ACCESS_FINE_LOCATION)
2. Verify Bluetooth is enabled (BluetoothAdapter.isEnabled())
3. Call BleScannerHelper.getInstance().scanDevice(context, UART_UUID, callback)
4. In scan callback: display found ring(s) to user with name + MAC address
5. User selects a ring → call BleScannerHelper.getInstance().stopScan(context)
6. Call BleOperateManager.getInstance().connectWithScan(selectedMacAddress)
7. In connection state callback: when connected, send bind command
8. Send SetTimeReq to sync the ring's clock
9. Read SetTimeRsp to get capability flags (what the ring supports)
10. Read firmware + hardware version via CommandHandle.getInstance().execReadCmd(...)
11. Register QCBluetoothCallbackCloneReceiver (MyBluetoothReceiver) for data callbacks
12. NOW the ring is ready for measurements and data sync
```

**If any step from 1–8 is skipped or done out of order, the connection will fail or data commands will not work.**

---

## 5. What NOT To Do

- **Do NOT generate fake BLE scan results.** If no ring is found, show "No rings found — move the ring closer and try again."
- **Do NOT simulate a BLE connection.** If `connectWithScan()` fails, show the actual error state.
- **Do NOT create mock implementations of `BleScannerHelper`, `BleOperateManager`, or `MyBluetoothReceiver`.** These are SDK classes that handle real BLE operations.
- **Do NOT hardcode MAC addresses, device names, or health readings.**
- **Do NOT skip runtime permission requests.** This is the most common cause of "scanning doesn't work" on modern Android.

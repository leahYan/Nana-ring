# System Requirements & Functional Scope

> **Reminder:** Read `PROJECT_OVERVIEW.md § 2. Implementation Constraints` before implementing any requirement below. All data must come from the physical ring via the SDK. No mock data, fake BLE, or synthetic values.

## 1. Android Application (Ring Bridge)

- **SDK Integration:** Must interface with the real `ring_sdk_1.0.0.1.aar` library. All BLE operations and health data retrieval must go through the SDK's classes — do NOT create wrapper classes that simulate or replace SDK behaviour.
- **Connectivity:** Use the SDK's `BleScannerHelper` for BLE scanning and `BleOperateManager` for connection management. Support pairing and automatic reconnection using the ring's MAC address. If the ring is not in range or not paired, display an appropriate "Not Connected" state — do NOT simulate a connection.
- **Data Capture:** Capture health metrics using the SDK callback classes specified in `ring_data_dictionary.csv` (column: `sdk_field`). The 8 core metric categories are:
  1. Heart Rate (BPM) — via `StopHeartRateRsp`, `ReadHeartRateRsp`
  2. Blood Oxygen (SpO2) — via `StopHeartRateRsp`, `BloodOxygenEntity`, `IntervalBloodOxygenEntity`
  3. Blood Pressure (Systolic/Diastolic) — via `StopHeartRateRsp`, `BlePressure`
  4. Body Temperature — via `TemperatureOnceEntity`, `TemperatureEntity`, `IntervalTemperature`
  5. Stress Levels — via `StopHeartRateRsp`, `PressureRsp`
  6. Heart Rate Variability (HRV) — via `StopHeartRateRsp`, `HRVRsp`
  7. Activity/Step Counts — via `BleStepTotal` (daily), `BleStepDetails` (15-min detail)
  8. Sleep Stages — via `SleepNewProtoResp`, `SleepDisplay`
- **Data Transformations:** The `ring_data_dictionary.csv` specifies exact transformations needed (e.g., `StopHeartRateRsp.temperature / 100.0` for Celsius, `BleStepTotal.calorie / 1000` for kcal). Follow these precisely.
- **Local Storage:** Use Room SQLite to store all readings locally before syncing. Room entity fields must match the columns defined in `ring_data_dictionary.csv` for each table.

## 2. Sync & API

- **Background Sync:** Use Android WorkManager to flush unsynced data to the cloud every 15 minutes.
- **Batch Upload:** The backend must support a `POST /data/batch` endpoint to receive multiple records in one request. Payload format follows the `api_batch_upload` section of the data dictionary, including `envelope_id` for idempotency and a `type` field per record to route to the correct table.
- **Authentication:** Secure all API traffic using JWT (JSON Web Tokens). The `user_id` is extracted server-side from the JWT — the Android app must never send `user_id` in the payload.

## 3. Web Dashboard

- **Visualisation:** Render 7-day trend charts for all health metrics using Recharts or similar.
- **Empty States:** If no ring data exists for the selected period, display a clear "Waiting for Data" message. Never populate charts with placeholder or example data.
- **Data Management:** Provide a dedicated "Export" page where the user can select a date range and download health data as a CSV file. The CSV headers must match the column names from `ring_data_dictionary.csv`.

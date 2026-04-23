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

- **Direct Supabase Sync:** The Android app syncs data directly to Supabase via its REST API (`POST /rest/v1/ring_*`). The FastAPI backend is NOT in the data ingestion path.
- **Authentication (Android):** The app authenticates via Supabase Auth (email + password) on first launch. The returned JWT is stored in SharedPreferences and auto-refreshed before expiry. The `user_id` is derived from the JWT's `sub` field — never hardcoded.
- **Idempotency:** Each row carries a client-generated `sync_id` (UUID). The `Prefer: resolution=ignore-duplicates` header leverages the `UNIQUE(sync_id, user_id)` constraint to prevent duplicates on retry.
- **Background Sync:** Use Android WorkManager to flush unsynced data to Supabase every 15 minutes. Failed inserts are queued in Room and retried with exponential backoff.
- **Row Level Security:** RLS policies on all `ring_*` tables enforce `user_id = auth.uid()` for INSERT and SELECT. Doctors access patient data via an additional `is_linked_doctor()` check against the `doctor_patient` linking table.
- **Backend (Admin Only):** The FastAPI backend handles CSV export, doctor-patient link management, and admin queries. It connects via direct Postgres (`DATABASE_URL`), bypassing RLS.

## 3. Web Dashboard

- **Authentication:** Doctors sign in via Supabase Auth (email + password) on the web dashboard. The JWT is sent with every Supabase REST request.
- **Doctor Access:** RLS + `doctor_patient` linking table ensures each doctor only sees their assigned patients' data. The dashboard queries `/rest/v1/ring_heart_rate?user_id=eq.<patient_uuid>` — RLS silently filters out patients not linked to the requesting doctor.
- **Visualisation:** Render 7-day trend charts for all health metrics using Recharts or similar.
- **Empty States:** If no ring data exists for the selected period, display a clear "Waiting for Data" message. Never populate charts with placeholder or example data.
- **Data Management:** Provide a dedicated "Export" page where the user can select a date range and download health data as a CSV file. The CSV headers must match the column names from `ring_data_dictionary.csv`.

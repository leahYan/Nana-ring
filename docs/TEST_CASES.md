# Quality Assurance & Test Scenarios

> **Reminder:** All tests must be performed against the **real physical ring and SDK**. Test cases verify that the system handles real-world conditions (ring out of range, no data yet, network failure) — NOT simulated or mocked versions of those conditions. "Passing" means the real hardware scenario works, not that a mock returns the expected value.

## 1. Mobile & Hardware (Android)

- **TC-MOB-01:** Verify the app successfully connects to the **physical ring** via the SDK's `BleScannerHelper` and `BleOperateManager`. The test passes when a real BLE connection is established and the ring's MAC address, device name, firmware version, and hardware version are read from BLE characteristics.
- **TC-MOB-02:** Verify that when a real measurement is triggered (e.g., manual heart rate via `manualModeHeart`), the SDK callback (`StopHeartRateRsp`) fires and the resulting data is saved to the Room database — even when the phone is in Airplane Mode (confirming offline-first storage).
- **TC-MOB-03:** Verify that the WorkManager successfully retries a sync if the Hostinger VPS is temporarily unreachable. The test passes when records that failed to upload are eventually delivered once connectivity is restored. No data is lost.
- **TC-MOB-04:** Verify that when the ring is **not connected or out of range**, the app displays a "Not Connected" state. The app must NOT generate fake readings, fall back to demo data, or simulate a connection.
- **TC-MOB-05:** Verify that SDK data transformations match the `ring_data_dictionary.csv` specifications — e.g., `StopHeartRateRsp.temperature` is divided by 100 before storage, `BleStepTotal.calorie` is divided by 1000 to convert to kcal, `HRVRsp.pressureArray` elements are divided by 10.

## 2. Backend & Data (Python/Postgres)

- **TC-BE-01:** Validate that the `POST /data/batch` endpoint rejects malformed JSON, invalid data types, and values outside the `validation_rules` defined in `ring_data_dictionary.csv` (e.g., heart rate < 30 or > 250, SpO2 < 70).
- **TC-BE-02:** Ensure duplicate records (same `sync_id` and `user_id`) are handled gracefully via `ON CONFLICT DO NOTHING` to prevent data bloating.
- **TC-BE-03:** Verify the `GET /export/csv` endpoint generates a file with headers matching the column names in `ring_data_dictionary.csv` and correct date formatting.
- **TC-BE-04:** Verify that `user_id` is always extracted from the JWT server-side and never accepted from the request payload.
- **TC-BE-05:** Verify the database starts empty. No seed data, demo data, or fixture scripts should populate health metric tables on deployment.

## 3. Web & UI (React)

- **TC-WEB-01:** Verify the dashboard displays a "Waiting for Data" state if no ring records exist for the selected period. The dashboard must NOT display placeholder charts, example data, or demo values.
- **TC-WEB-02:** Ensure the CSV download contains the exact number of records present in the database for the selected date range.
- **TC-WEB-03:** Verify that all chart labels and units match the `unit` column from `ring_data_dictionary.csv` (e.g., "bpm" for heart rate, "% SpO2" for blood oxygen, "°C" for temperature).

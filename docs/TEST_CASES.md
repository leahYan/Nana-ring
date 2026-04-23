# Quality Assurance & Test Scenarios

> **Reminder:** All tests must be performed against the **real physical ring and SDK**. Test cases verify that the system handles real-world conditions (ring out of range, no data yet, network failure) — NOT simulated or mocked versions of those conditions. "Passing" means the real hardware scenario works, not that a mock returns the expected value.

## 1. Mobile & Hardware (Android)

- **TC-MOB-01:** Verify the app successfully connects to the **physical ring** via the SDK's `BleScannerHelper` and `BleOperateManager`. The test passes when a real BLE connection is established and the ring's MAC address, device name, firmware version, and hardware version are read from BLE characteristics.
- **TC-MOB-02:** Verify that when a real measurement is triggered (e.g., manual heart rate via `manualModeHeart`), the SDK callback (`StopHeartRateRsp`) fires and the resulting data is saved to the Room database — even when the phone is in Airplane Mode (confirming offline-first storage).
- **TC-MOB-03:** Verify that the WorkManager successfully retries a sync if Supabase is temporarily unreachable. The test passes when records that failed to upload are eventually delivered once connectivity is restored. The `Prefer: resolution=ignore-duplicates` header ensures no duplicates on retry. No data is lost.
- **TC-MOB-03b:** Verify Supabase Auth sign-in flow. On first launch, the user enters email + password. The app receives a JWT and stores it (along with the refresh token) in SharedPreferences. On subsequent launches, the app restores the session without re-prompting for credentials.
- **TC-MOB-04:** Verify that when the ring is **not connected or out of range**, the app displays a "Not Connected" state. The app must NOT generate fake readings, fall back to demo data, or simulate a connection.
- **TC-MOB-05:** Verify that SDK data transformations match the `ring_data_dictionary.csv` specifications — e.g., `StopHeartRateRsp.temperature` is divided by 100 before storage, `BleStepTotal.calorie` is divided by 1000 to convert to kcal, `HRVRsp.pressureArray` elements are divided by 10.

## 2. Backend, Sync & Data (Supabase + Python/Postgres)

- **TC-BE-01:** Verify that the Supabase REST API rejects inserts with invalid data types or values outside the column constraints (e.g., `bpm` outside SMALLINT range, `source` not matching allowed values). The REST API returns HTTP 400 with a descriptive error.
- **TC-BE-02:** Ensure duplicate records (same `sync_id` and `user_id`) are handled gracefully. Sending the same row twice with `Prefer: resolution=ignore-duplicates` must return HTTP 200 (not 409) and not create a duplicate row.
- **TC-BE-03:** Verify the `GET /export/csv` endpoint (FastAPI backend) generates a file with headers matching the column names in `ring_data_dictionary.csv` and correct date formatting.
- **TC-BE-04:** Verify that RLS enforces `user_id = auth.uid()`. Attempting to insert a row where `user_id` does not match the JWT's `sub` field must return HTTP 403 (RLS violation).
- **TC-BE-05:** Verify the database starts empty. No seed data, demo data, or fixture scripts should populate health metric tables on deployment.
- **TC-BE-06:** Verify that token refresh works. After the JWT expires (1 hour), the Android app must auto-refresh using the stored refresh token. Sync must continue without user intervention.
- **TC-BE-07:** Verify doctor access via RLS. A doctor linked to Patient A via `doctor_patient` can `SELECT` Patient A's ring data. The same doctor querying Patient B's data (not linked) receives zero rows — no error, just empty results.
- **TC-BE-08:** Verify that the `doctor_patient` table cannot be modified by clients (patients or doctors). Only the backend (service role) can INSERT or DELETE links. Attempting to INSERT via the anon key + JWT must be blocked by RLS.
- **TC-BE-09:** Verify that a patient logging in from a second device (different phone) sees the same data. Both JWTs carry the same `sub` (user UUID), so RLS returns identical results.

## 3. Web & UI (React)

- **TC-WEB-01:** Verify the dashboard displays a "Waiting for Data" state if no ring records exist for the selected period. The dashboard must NOT display placeholder charts, example data, or demo values.
- **TC-WEB-02:** Ensure the CSV download contains the exact number of records present in the database for the selected date range.
- **TC-WEB-03:** Verify that all chart labels and units match the `unit` column from `ring_data_dictionary.csv` (e.g., "bpm" for heart rate, "% SpO2" for blood oxygen, "°C" for temperature).
- **TC-WEB-04:** Verify doctor authentication. A doctor can sign in via Supabase Auth on the web dashboard and see a list of their linked patients. Clicking a patient shows that patient's health data.
- **TC-WEB-05:** Verify doctor data isolation. A doctor signed into the dashboard cannot see data for patients not linked to them via the `doctor_patient` table. No error is shown — the query simply returns zero rows for unlinked patients.

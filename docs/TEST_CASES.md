# Quality Assurance & Test Scenarios

## 1. Mobile & Hardware (Android)

- **TC-MOB-01:** Verify the app successfully connects to the ring via `BleScannerHelper`.
- **TC-MOB-02:** Verify that measurements are saved to the Room database even when the phone is in Airplane Mode.
- **TC-MOB-03:** Verify that the WorkManager successfully retries a sync if the Hostinger VPS is temporarily unreachable.

## 2. Backend & Data (Python/Postgres)

- **TC-BE-01:** Validate that the `POST /data/batch` endpoint rejects malformed JSON or invalid data types.
- **TC-BE-02:** Ensure duplicate records (same timestamp and user_id) are handled gracefully (ignored or updated) to prevent data bloating.
- **TC-BE-03:** Verify the `GET /export/csv` endpoint generates a file with correct headers and date formatting.

## 3. Web & UI (React)

- **TC-WEB-01:** Verify the dashboard displays a "Waiting for Data" state if no ring records exist for the selected period.
- **TC-WEB-02:** Ensure the CSV download contains the exact number of records present in the database for the selected date range.

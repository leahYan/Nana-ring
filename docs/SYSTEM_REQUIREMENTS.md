# System Requirements & Functional Scope

## 1. Android Application (Ring Bridge)

- **SDK Integration:** Must interface with `ring_sdk_1.0.0.1.aar`.
- **Connectivity:** Support BLE scanning, pairing, and automatic reconnection using the ring's MAC address.
- **Data Capture:** Capture 8 core health metrics:
  1. Heart Rate (BPM)
  2. Blood Oxygen (SpO2)
  3. Blood Pressure (Systolic/Diastolic)
  4. Body Temperature
  5. Stress Levels
  6. Heart Rate Variability (HRV)
  7. Activity/Step Counts
  8. Sleep Stages (Light, Deep, REM, Awake)
- **Local Storage:** Use Room SQLite to store all readings locally before syncing.

## 2. Sync & API

- **Background Sync:** Use Android WorkManager to flush unsynced data to the cloud every 15 minutes.
- **Batch Upload:** The backend must support a `/data/batch` endpoint to receive multiple records in one request.
- **Authentication:** Secure all API traffic using JWT (JSON Web Tokens).

## 3. Web Dashboard

- **Visualisation:** Render 7-day trend charts for all health metrics using Recharts or similar.
- **Data Management:** Provide a dedicated "Export" page where the user can select a date range and download health data as a CSV file.

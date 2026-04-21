# Technical Implementation Specification

> **Reminder:** Read `PROJECT_OVERVIEW.md § 2. Implementation Constraints` before implementing anything below. All health data must originate from the physical smart ring via the SDK. No mock data, fake BLE, or synthetic values at any layer.

## 1. Database Schema (PostgreSQL via Supabase)

The `ring_data_dictionary.csv` is the **authoritative, machine-readable schema definition**. Use it to generate all database tables, column types, constraints, and validation rules. Do not invent columns or tables not defined in that file.

All time-series tables (e.g., `heart_rate`, `spo2`, `blood_pressure`, `temperature`, `hrv`, `stress`, `activity`, `activity_detail`, `sleep`, `sleep_stage_detail`, `sport_session`) share common columns defined in the `_common` section of the data dictionary:

- `id`: UUID v4 (Primary Key, server-generated)
- `user_id`: UUID v4 (Foreign Key to `users` table, extracted from JWT server-side)
- `device_timestamp`: BIGINT (Unix milliseconds — sourced from SDK fields, see `sdk_field` column per table)
- `server_timestamp`: BIGINT (Unix milliseconds, server-generated on insert)
- `source`: ENUM (`manual` | `continuous`)
- `sync_id`: UUID v4 (nullable, client-generated idempotency key)
- `firmware_version`: VARCHAR(32) (nullable, from BLE characteristic read)
- `hardware_version`: VARCHAR(32) (nullable, from BLE characteristic read)

Each table then adds metric-specific columns as defined in the data dictionary.

Reference tables (`sport_type_map`) and identity tables (`users`, `device`) are also fully defined in the data dictionary.

## 2. Backend Strategy (FastAPI)

- **ORM:** SQLAlchemy for database interactions.
- **Validation:** Pydantic models must match the data dictionary column definitions exactly — types, ranges, nullable flags, and allowed ENUM values. Apply the `validation_rules` column from the data dictionary as Pydantic validators.
- **No seed data:** Do not create scripts or fixtures that insert fake health readings into the database. The database should start empty and only receive data from the Android app via the batch upload endpoint.
- **Deployment:** A `docker-compose.yml` file should define the FastAPI service and an Nginx reverse proxy for the Hostinger VPS.

## 3. Frontend Strategy (React)

- **Framework:** Vite + React + TypeScript.
- **State Management:** React Query (TanStack Query) for efficient data fetching from the VPS.
- **Styling:** Tailwind CSS for a clean, "humanised" health dashboard interface.
- **Empty states are mandatory:** Every chart and data view must handle the case where no data exists for the selected period. Display a clear "Waiting for Data" or "No Readings Available" message. Never render charts with placeholder, example, or demo data.

## 4. Authentication & Multi-Tenancy (Multi-User POC)

- **Isolation Strategy:** Every record is hard-linked to a `user_id`. The backend enforces a `WHERE user_id = current_user` filter on all requests unless the requester has an `is_admin` flag.
- **Auth Flow:** No public registration or login UI for the POC.
- **Provisioning:** A backend utility script (`generate_tokens.py`) creates unique JWTs for the 5 testers.
- **Android Integration:** The JWT is manually entered into the Android app's configuration settings.
- **Admin Access:** Admins can view all users' data via a `target_user_id` query parameter on the dashboard endpoints.

## 5. Data Ingestion (Sync Engine)

- **Endpoint:** `POST /data/batch`
- **Payload format:** Defined in the `api_batch_upload` section of `ring_data_dictionary.csv`. Includes an `envelope_id` for batch-level idempotency and a `type` field per record to route to the correct database table.
- **Idempotency:** Implement `ON CONFLICT (sync_id, user_id) DO NOTHING`. This allows the Android app to safely retry failed syncs without duplicating health data.
- **Validation:** Apply all `validation_rules` from the data dictionary server-side. Reject records with values outside clinical ranges (e.g., SpO2 < 70%, heart rate outside 30–250 BPM). Return per-record error details in the response so the Android app can flag problematic readings.
- **Clock Skew:** Validate `device_timestamp` is not more than 60 seconds into the future relative to `server_timestamp` (as defined in the `_common` validation rules). Reject timestamps that fail this check.

## 6. Android SDK Integration Contract

The following SDK classes and callbacks are the **only** permitted sources of health data. The Android app must register listeners on these and persist the data they provide. See the `sdk_field` column in `ring_data_dictionary.csv` for exact field mappings.

| Metric         | Manual Measurement Callback    | Continuous/Synced Data Source                                      |
| -------------- | ------------------------------ | ------------------------------------------------------------------ |
| Heart Rate     | `StopHeartRateRsp.value`       | `ReadHeartRateRsp.mHeartRateArray`                                 |
| SpO2           | `StopHeartRateRsp.bloodOxygen` | `BloodOxygenEntity`, `IntervalBloodOxygenEntity`                   |
| Blood Pressure | `StopHeartRateRsp.sbp / .dbp`  | `BlePressure` via `BpDataEntity`                                   |
| Temperature    | `TemperatureOnceEntity.mValue` | `TemperatureEntity.mValues[]`, `IntervalTemperature`               |
| HRV            | `StopHeartRateRsp.hrv`         | `HRVRsp.pressureArray` (÷10)                                       |
| Stress         | `StopHeartRateRsp.stress`      | `PressureRsp.pressureArray` (÷10)                                  |
| Activity       | —                              | `BleStepTotal` (daily), `BleStepDetails` (15-min)                  |
| Sleep          | —                              | `SleepNewProtoResp`, `SleepDisplay`                                |
| Sport Sessions | —                              | `SportPlusEntity`                                                  |
| Device Info    | —                              | `BatteryRsp`, `SetTimeRsp` (capability flags), BLE characteristics |

**Do NOT create alternative data sources, random number generators, or mock implementations of these classes.**

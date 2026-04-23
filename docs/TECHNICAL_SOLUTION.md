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

## 4. Authentication & Multi-Tenancy

### 4.1 Supabase Auth (User-Facing)

All patients and doctors authenticate via **Supabase Auth** (email + password). The auth flow:

1. **Sign up / Sign in** — Android app or web dashboard calls Supabase Auth endpoints using the **anon key** (safe to embed in APK/frontend).
2. **JWT issued** — Supabase returns a JWT containing `sub` (user UUID), `role` (`authenticated`), and `exp` (expiry).
3. **JWT sent with every request** — `Authorization: Bearer <JWT>` header on all REST calls.
4. **RLS enforces isolation** — Postgres checks `auth.uid()` (extracted from the JWT) against `user_id` on every row operation.

The anon key is **public by design** — RLS is the security gate, not the key.

### 4.2 Row Level Security (RLS) Policies

Every `ring_*` table has RLS enabled with these policies:

| Policy | Operation | Rule |
|---|---|---|
| Patient inserts own data | INSERT | `user_id = auth.uid()` |
| Patient reads own data | SELECT | `user_id = auth.uid()` |
| Doctor reads linked patients | SELECT | `is_linked_doctor(user_id)` returns TRUE |
| No client UPDATE/DELETE | — | No policies = blocked |

The `doctor_patient` linking table is also RLS-protected:
- Doctors can SELECT their own links.
- Patients can SELECT their own links.
- Only the backend (service role / direct Postgres) can INSERT or DELETE links.

### 4.3 Doctor-Patient Linking

The `doctor_patient` table (see `docs/doctor_patient_rls.sql`):

```sql
CREATE TABLE doctor_patient (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    doctor_id   UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    patient_id  UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(doctor_id, patient_id)
);
```

A helper function `is_linked_doctor(p_patient_id UUID)` is used in all `ring_*` SELECT policies. It runs as `SECURITY DEFINER` so the RLS check can query the linking table.

Admin operations (linking/unlinking) are done via the FastAPI backend or Supabase SQL Editor:
```sql
INSERT INTO doctor_patient (doctor_id, patient_id) VALUES ('doctor-uuid', 'patient-uuid');
```

### 4.4 Backend Admin Access

The FastAPI backend connects via `DATABASE_URL` (direct Postgres connection), which **bypasses RLS entirely**. This is correct — the backend is trusted server-side code used for:
- Admin queries across all users
- CSV export
- Doctor-patient link management
- Any future admin dashboard features

### 4.5 JWT Behaviour Across Devices

The JWT `sub` field (user UUID) is **identical regardless of which device the user signs in from**. Session-specific fields (`iat`, `exp`, `jti`) change per login. This means:
- Data synced from Phone A is immediately visible from Phone B (same `user_id`).
- RLS treats all devices equally — there is no device-binding.

## 5. Data Ingestion (Direct Supabase REST Sync)

### 5.1 Android → Supabase (Primary Data Path)

The Android app syncs health data **directly** to Supabase via its REST API, bypassing the FastAPI backend entirely for data ingestion.

- **Endpoint pattern:** `POST https://<ref>.supabase.co/rest/v1/ring_heart_rate` (one endpoint per table).
- **Headers:**
  - `apikey: <SUPABASE_ANON_KEY>` — public key, safe in APK.
  - `Authorization: Bearer <JWT>` — user's Supabase Auth JWT.
  - `Content-Type: application/json`
  - `Prefer: resolution=ignore-duplicates` — uses the `UNIQUE(sync_id, user_id)` constraint for safe retries.
- **Payload:** JSON array of row objects. Column names must match the Supabase table exactly (snake_case, as defined in `ring_data_dictionary.csv`).
- **`user_id`:** Set by the Android app to `auth.uid()` from the JWT. RLS verifies it matches — a mismatch is rejected.
- **`id`:** UUID v4 generated client-side per row.
- **`server_timestamp`:** Omitted from the payload — the DB column default `(EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT` fills it.
- **`sync_id`:** UUID v4 generated client-side for idempotency.

### 5.2 Response Codes

| Code | Meaning |
|---|---|
| 201 | Row(s) inserted successfully |
| 200 | Duplicate ignored (idempotent retry) |
| 401 | Bad or expired JWT — trigger token refresh |
| 400 | Column name mismatch or type error |
| 403 | RLS policy violation (user_id doesn't match JWT) |

### 5.3 Batch Sync Strategy

The `SupabaseSyncAuth` helper class (see `ring-android/.../sync/SupabaseSyncAuth.kt`) provides:
- Per-table insert methods with typed parameters matching the schema.
- Auto token refresh (checks expiry within 60s, refreshes using mutex).
- `insertBatch(table, rows)` for bulk inserts when the SDK delivers multiple readings at once.

### 5.4 Offline Retry

WorkManager tasks flush failed inserts from a local Room queue. On network restoration, pending rows are re-sent with their original `sync_id` — the `ignore-duplicates` header ensures no duplication even if the original POST actually succeeded but the response was lost.

### 5.5 FastAPI Backend Role (Post-Ingestion Only)

The FastAPI backend is **not in the data ingestion path**. It is used for:
- `GET /export/csv` — CSV download for a date range.
- `POST /admin/doctor-patient` — Link/unlink doctors to patients.
- `GET /admin/users` — List registered users.
- Any future aggregate analytics or reporting endpoints.

All backend endpoints connect via `DATABASE_URL` (direct Postgres, bypasses RLS) and use the service role key for Supabase API calls when needed.

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

# Web Dashboard Specification

> **Reminder:** Read `PROJECT_OVERVIEW.md § 2. Implementation Constraints` before implementing. The dashboard displays **real data only** — never generate placeholder charts, demo data, or fake readings. If no data exists, show an appropriate empty state.

## 1. Tech Stack

- **Framework:** Vite + React 18 + TypeScript
- **Styling:** Tailwind CSS
- **Charting:** Recharts
- **Data Fetching:** TanStack Query (React Query)
- **Routing:** React Router v6
- **Hosting:** Vercel

---

## 2. Authentication & Roles

### 2.1 Two User Roles

| Role | Description | Data Access |
|---|---|---|
| **User** (ring wearer) | A person who wears the ring and wants to view their own health data | Can ONLY see their own data. All API requests are scoped by their `user_id` extracted from the JWT. |
| **Doctor** | A healthcare provider who monitors patients | Can see data for ALL patients assigned to them. Uses a `target_user_id` query parameter to switch between patients. |

### 2.2 Auth Flow (POC)

- No public registration or sign-up page. Accounts are provisioned via the backend utility script (`generate_tokens.py`).
- The login page accepts a **JWT token** that the user or doctor pastes into a single input field. This is a POC simplification — no username/password login for now.
- On submission, the frontend stores the JWT in memory (not localStorage) and includes it as a `Bearer` token in the `Authorization` header on all API requests.
- The JWT payload contains: `user_id`, `role` (`user` | `doctor`), `name`, and `exp` (expiry).
- On page load, if no valid JWT is present or the token has expired, redirect to the login page.

### 2.3 Role-Based Routing

After login, the frontend reads the `role` claim from the JWT and routes accordingly:

- **User role** → redirects to `/dashboard` (their own data)
- **Doctor role** → redirects to `/doctor/patients` (patient list)

The frontend must enforce role checks on every route. A user-role JWT must never be able to access `/doctor/*` routes. If attempted, redirect to `/dashboard`.

---

## 3. Pages

### 3.1 Home Page (`/`)

A public landing page (no login required) that introduces the Ring Health product. This page is marketing/informational — it does NOT display any health data.

**Content sections:**

1. **Hero Section** — headline (e.g., "Your Health, On Your Finger"), subheadline describing 24/7 health monitoring, and a call-to-action button linking to the login page.
2. **Features Overview** — highlight the 8 core metrics the ring tracks: Heart Rate, Blood Oxygen (SpO2), Blood Pressure, Body Temperature, Stress, HRV, Activity/Steps, and Sleep. Use icons or illustrations for each. Do NOT show real data or fake sample charts here — just describe the capabilities.
3. **How It Works** — three-step visual: (1) Wear the ring, (2) Sync via the app, (3) View your data on the dashboard. Keep it simple.
4. **For Healthcare Providers** — a brief section explaining that doctors can monitor multiple patients through a dedicated dashboard view. Link to the login page.
5. **Footer** — minimal: project name, copyright, link to login.

**Design direction:** Clean, modern health/wellness aesthetic. Light colour palette. No stock photos of rings — use abstract health/wellness graphics or simple icons. Do NOT embed any copyrighted imagery.

### 3.2 Login Page (`/login`)

A simple page with:

- Project logo / name at the top
- A text input field labelled "Enter your access token"
- A "Sign In" button
- On invalid or expired token: show an inline error message "Invalid or expired token. Please contact your administrator."
- On success: decode the JWT, store it in app state, and redirect based on role

No "forgot password" or "register" links — this is token-based for the POC.

### 3.3 User Dashboard (`/dashboard`) — Requires User or Doctor Role

The main health data view. When accessed by a **user**, it shows their own data. When accessed by a **doctor** via `/doctor/patient/:userId`, it shows that specific patient's data. The underlying dashboard component is the same — only the data source changes.

**Layout:**

- **Top bar:** User name (from JWT or patient record), date range selector (default: last 7 days), logout button.
- **Metric cards row:** Summary cards showing the latest reading for each metric — heart rate (bpm), SpO2 (%), systolic/diastolic BP (mmHg), temperature (°C), stress (0–100), HRV (ms). Each card shows the most recent value and a small trend indicator (up/down/stable vs. previous period).
- **Charts section:** One time-series chart per metric for the selected date range, rendered using Recharts. Charts must use the correct units from `ring_data_dictionary.csv` (e.g., "bpm", "% SpO2", "°C", "mmHg").
- **Activity section:** Daily step count bar chart, distance, calories, active minutes for the selected period.
- **Sleep section:** Sleep stage breakdown (deep, light, REM, awake) as a stacked bar or timeline visualisation per night.
- **Sport sessions:** List of recent sport sessions with type, duration, distance, calories, and average heart rate.

**Empty states (mandatory):**

- If no data exists for any metric in the selected period, display "No readings available for this period" in place of the chart. Do NOT render charts with zero lines, placeholder data, or demo values.
- If the user has never synced any data at all, show a full-page "Waiting for Data" state with a message like "No health data has been synced yet. Please connect your ring via the mobile app to start seeing your health metrics here."

### 3.4 Export Page (`/export`) — Requires User or Doctor Role

- Date range picker (start date, end date)
- Metric type selector (checkboxes): select which tables to include in the export
- "Download CSV" button
- The CSV is generated server-side via `GET /export/csv` with query parameters for date range, metric types, and user_id (from JWT for users, from `target_user_id` for doctors)
- CSV headers must match the column names from `ring_data_dictionary.csv`

### 3.5 Doctor — Patient List (`/doctor/patients`) — Requires Doctor Role

A table listing all patients assigned to this doctor.

**Columns:**

- Patient name
- Last sync time (when data was last received from their ring)
- Quick-glance latest readings: heart rate, SpO2, temperature
- "View Dashboard" link → navigates to `/doctor/patient/:userId`

**Behaviour:**

- The patient list is fetched from a backend endpoint (e.g., `GET /doctor/patients`) which returns all users assigned to this doctor.
- If a patient has never synced data, show "—" for their latest readings instead of fake values.
- The doctor can click any patient row to view their full dashboard.

### 3.6 Doctor — Patient Dashboard (`/doctor/patient/:userId`) — Requires Doctor Role

Renders the same dashboard component as `/dashboard` (§3.3), but:

- The `userId` from the URL is passed as `target_user_id` to all API calls
- The top bar shows the patient's name instead of the doctor's name
- A "← Back to Patients" link returns to the patient list
- The doctor cannot modify the patient's data — this is read-only

---

## 4. API Endpoints Required

The frontend expects the following endpoints from the FastAPI backend. All require JWT authentication via `Authorization: Bearer <token>` header.

| Method | Path | Role | Description |
|---|---|---|---|
| `GET` | `/me` | Any | Returns current user profile (name, role, email) from JWT |
| `GET` | `/data/{metric_type}` | User | Returns time-series data for the authenticated user. Query params: `start_date`, `end_date`. `metric_type` is one of: `heart_rate`, `spo2`, `blood_pressure`, `temperature`, `hrv`, `stress`, `activity`, `activity_detail`, `sleep`, `sport_session` |
| `GET` | `/data/{metric_type}?target_user_id={id}` | Doctor | Same as above but for a specific patient. Backend must verify the doctor has access to this patient. |
| `GET` | `/data/latest` | User | Returns the most recent reading for each metric type (for summary cards) |
| `GET` | `/data/latest?target_user_id={id}` | Doctor | Latest readings for a specific patient |
| `GET` | `/doctor/patients` | Doctor | Returns list of patients assigned to this doctor with basic profile info and last sync time |
| `GET` | `/export/csv` | Any | Generates and returns a CSV file. Query params: `start_date`, `end_date`, `metrics` (comma-separated list of metric types), `target_user_id` (doctor only) |

---

## 5. Backend Changes Required for Doctor Role

The existing backend (from the POC) needs these additions to support the doctor role:

### 5.1 Database

Add to the `users` table (or add to `ring_data_dictionary.csv`):

- `role`: ENUM (`user` | `doctor`). Default: `user`.

Create a new `doctor_patient` mapping table:

| Column | Type | Description |
|---|---|---|
| `id` | UUID v4 | Primary key |
| `doctor_id` | UUID v4 (FK → users.id) | The doctor |
| `patient_id` | UUID v4 (FK → users.id) | The patient (ring wearer) |
| `assigned_at` | BIGINT (Unix ms UTC) | When the relationship was created |

### 5.2 Token Generation

Update `generate_tokens.py` to accept a `--role` parameter (`user` or `doctor`) and include the role in the JWT payload.

### 5.3 Access Control

- **User role:** All data endpoints filter by `WHERE user_id = <jwt_user_id>`. The `target_user_id` parameter is ignored.
- **Doctor role:** If `target_user_id` is provided, the backend must verify that a `doctor_patient` record exists linking the doctor to that patient before returning data. If no `target_user_id` is provided, return an error (doctors don't have their own health data).
- **Reject cross-role access:** A user-role JWT must never be able to call `/doctor/patients`. Return `403 Forbidden`.

---

## 6. Implementation Constraints (Website-Specific)

1. **No demo/sample data in the UI.** Every chart, card, and table must either show real data from the API or an explicit empty state. Never hardcode example values for visual testing — use the real API with an empty database to verify empty states render correctly.
2. **No mock API calls.** The frontend must always call the real FastAPI backend. Do not create a mock API layer, fake JSON files, or in-memory data stores that simulate the backend.
3. **Units must match the data dictionary.** All labels, axis titles, and CSV headers must use the exact units defined in `ring_data_dictionary.csv` (`bpm`, `% SpO2`, `mmHg`, `°C`, `ms`, `steps`, `meters`, `kcal`, `minutes`, `seconds`).
4. **Role enforcement is both frontend AND backend.** The frontend hides UI elements based on role for UX purposes, but the backend must independently enforce access control. Never rely on frontend-only role checks for security.

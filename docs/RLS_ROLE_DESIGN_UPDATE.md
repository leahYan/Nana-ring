# RLS & Role-Based Access — Design Update

## Purpose

This document defines the complete authentication and authorization model for Nana Ring. The agent must review all existing code (Android, FastAPI backend, web dashboard) and ensure every data path conforms to these rules.

**Read this entire document before making any code changes.**

---

## 1. Role model

There are three roles. A user can only have ONE role in `user_profiles`, but a doctor can also use a ring as a patient (see §2).

| Role | Where they operate | What they can do |
|---|---|---|
| `patient` | Android app | Insert own ring data, read own ring data, link/unlink a doctor to themselves |
| `doctor` | Android app + web dashboard | Everything a patient can do (insert/read own ring data), PLUS read linked patients' data on the web dashboard |
| `admin` | Web dashboard + FastAPI backend | Read ALL users' data, manage all doctor-patient links, CSV export, promote/demote roles |

### 1.1 Doctors are also patients

A doctor who wears a ring and logs into the Android app works exactly like a patient for their own data. Their `user_id` goes on the rows they insert, and RLS allows it because `user_id = auth.uid()` passes regardless of role. On the web dashboard, the same doctor sees their linked patients' data via the `is_linked_doctor()` check.

There is **no need for a separate patient account** for doctors. One Supabase Auth account, one UUID, one `user_profiles` row with `role = 'doctor'`.

### 1.2 Patients can manage their own doctor links

Patients can:
- **View** which doctors are linked to them.
- **Add** a doctor link by entering the doctor's email or share code.
- **Remove** a doctor link (revoke access).

This means the `doctor_patient` table needs INSERT and DELETE policies for patients — not just SELECT. See §4.3.

---

## 2. Database tables for auth/roles

### 2.1 `user_profiles`

Created automatically via a trigger on `auth.users`. Every sign-up gets `role = 'patient'` by default.

```sql
CREATE TABLE user_profiles (
    id          UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    role        TEXT NOT NULL DEFAULT 'patient'
                CHECK (role IN ('admin', 'patient', 'doctor')),
    full_name   TEXT,
    email       TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

**Role assignment:**
- New sign-ups → `patient` (automatic via trigger)
- Promote to `doctor` → admin runs `UPDATE user_profiles SET role = 'doctor' WHERE id = '...'`
- Promote to `admin` → admin runs `UPDATE user_profiles SET role = 'admin' WHERE id = '...'`
- Clients (Android/web) can NEVER change their own or others' role. No UPDATE policy exists.

### 2.2 `doctor_patient`

Links a doctor to a patient. Created by either an admin or the patient themselves.

```sql
CREATE TABLE doctor_patient (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    doctor_id   UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    patient_id  UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(doctor_id, patient_id)
);
```

### 2.3 Auto-create profile trigger

```sql
CREATE OR REPLACE FUNCTION handle_new_user()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
    INSERT INTO user_profiles (id, email, role)
    VALUES (NEW.id, NEW.email, 'patient');
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW
    EXECUTE FUNCTION handle_new_user();
```

---

## 3. Helper functions (used by RLS policies)

These run as `SECURITY DEFINER` so they can read `user_profiles` and `doctor_patient` even when the calling user doesn't have direct access.

```sql
-- Returns the current user's role
CREATE OR REPLACE FUNCTION get_user_role()
RETURNS TEXT
LANGUAGE sql SECURITY DEFINER STABLE
AS $$
    SELECT role FROM user_profiles WHERE id = auth.uid();
$$;

-- Returns TRUE if current user is admin
CREATE OR REPLACE FUNCTION is_admin()
RETURNS BOOLEAN
LANGUAGE sql SECURITY DEFINER STABLE
AS $$
    SELECT EXISTS (
        SELECT 1 FROM user_profiles
        WHERE id = auth.uid() AND role = 'admin'
    );
$$;

-- Returns TRUE if current user is a doctor linked to the given patient
CREATE OR REPLACE FUNCTION is_linked_doctor(p_patient_id UUID)
RETURNS BOOLEAN
LANGUAGE sql SECURITY DEFINER STABLE
AS $$
    SELECT EXISTS (
        SELECT 1 FROM doctor_patient
        WHERE doctor_id = auth.uid()
          AND patient_id = p_patient_id
    );
$$;

-- Returns TRUE if the given doctor_id belongs to a user with role = 'doctor'
CREATE OR REPLACE FUNCTION is_doctor(p_user_id UUID)
RETURNS BOOLEAN
LANGUAGE sql SECURITY DEFINER STABLE
AS $$
    SELECT EXISTS (
        SELECT 1 FROM user_profiles
        WHERE id = p_user_id AND role = 'doctor'
    );
$$;
```

---

## 4. RLS policies (complete set)

### 4.1 `user_profiles`

```sql
ALTER TABLE user_profiles ENABLE ROW LEVEL SECURITY;

-- Everyone reads their own profile
DROP POLICY IF EXISTS "Users read own profile" ON user_profiles;
CREATE POLICY "Users read own profile"
    ON user_profiles FOR SELECT
    USING (id = auth.uid());

-- Admins read all profiles
DROP POLICY IF EXISTS "Admins read all profiles" ON user_profiles;
CREATE POLICY "Admins read all profiles"
    ON user_profiles FOR SELECT
    USING (is_admin());

-- Patients/doctors can look up a doctor's profile by UUID
-- (needed to display doctor name when linking)
DROP POLICY IF EXISTS "Users read doctor profiles" ON user_profiles;
CREATE POLICY "Users read doctor profiles"
    ON user_profiles FOR SELECT
    USING (role = 'doctor');

-- No INSERT/UPDATE/DELETE policies for clients.
-- Profile creation is handled by the trigger (SECURITY DEFINER).
-- Role changes are backend-only (service role).
```

### 4.2 `doctor_patient`

```sql
ALTER TABLE doctor_patient ENABLE ROW LEVEL SECURITY;

-- Doctors see their own links
DROP POLICY IF EXISTS "Doctors see own links" ON doctor_patient;
DROP POLICY IF EXISTS "Doctors and admins see links" ON doctor_patient;
DROP POLICY IF EXISTS "Patients see own links" ON doctor_patient;

CREATE POLICY "Users see own links"
    ON doctor_patient FOR SELECT
    USING (
        doctor_id = auth.uid()
        OR patient_id = auth.uid()
        OR is_admin()
    );

-- Patients can ADD a doctor link (but only linking TO themselves)
-- The doctor_id must reference a real doctor (role = 'doctor')
DROP POLICY IF EXISTS "Patients add doctor link" ON doctor_patient;
CREATE POLICY "Patients add doctor link"
    ON doctor_patient FOR INSERT
    WITH CHECK (
        patient_id = auth.uid()           -- can only link to yourself
        AND is_doctor(doctor_id)          -- target must be a doctor
    );

-- Patients can REMOVE a doctor link (only their own)
DROP POLICY IF EXISTS "Patients remove doctor link" ON doctor_patient;
CREATE POLICY "Patients remove doctor link"
    ON doctor_patient FOR DELETE
    USING (
        patient_id = auth.uid()           -- can only unlink from yourself
    );

-- Admins can INSERT and DELETE any link
DROP POLICY IF EXISTS "Admins manage links" ON doctor_patient;
CREATE POLICY "Admins insert links"
    ON doctor_patient FOR INSERT
    WITH CHECK (is_admin());

DROP POLICY IF EXISTS "Admins delete links" ON doctor_patient;
CREATE POLICY "Admins delete links"
    ON doctor_patient FOR DELETE
    USING (is_admin());
```

### 4.3 All `ring_*` tables (apply to each of the 10 tables)

Replace `{TABLE}` with each table name: `ring_heart_rate`, `ring_spo2`, `ring_blood_pressure`, `ring_temperature`, `ring_hrv`, `ring_stress`, `ring_activity`, `ring_activity_detail`, `ring_sleep`, `ring_sleep_stage_detail`.

```sql
ALTER TABLE {TABLE} ENABLE ROW LEVEL SECURITY;

-- INSERT: patients and doctors insert their own data only
DROP POLICY IF EXISTS "Users insert own data" ON {TABLE};
CREATE POLICY "Users insert own data"
    ON {TABLE} FOR INSERT
    WITH CHECK (user_id = auth.uid());

-- SELECT: three-way access
DROP POLICY IF EXISTS "Role-based read" ON {TABLE};
CREATE POLICY "Role-based read"
    ON {TABLE} FOR SELECT
    USING (
        user_id = auth.uid()              -- own data
        OR is_linked_doctor(user_id)       -- doctor reads linked patient
        OR is_admin()                      -- admin reads all
    );

-- No UPDATE or DELETE policies = clients cannot modify or delete data.
```

**Note on INSERT:** The policy is `user_id = auth.uid()`. This works for BOTH patients AND doctors who wear a ring — because `auth.uid()` is their UUID regardless of role. A doctor inserting their own heart rate data passes this check the same way a patient does.

---

## 5. What each client must do

### 5.1 Android app

**Sign-in / Sign-up:**
- Call Supabase Auth endpoints (unchanged from current implementation).
- After sign-in, fetch the user's profile: `GET /rest/v1/user_profiles?id=eq.{userId}` to get their `role` and `full_name`.
- Store role in AuthManager alongside the JWT tokens.

**Ring data sync (patient AND doctor):**
- Unchanged. `SupabaseSyncAuth.kt` inserts with `user_id = auth.uid()`. Works for any role.

**Doctor linking (patient-initiated):**
- New screen: "My doctors" — shows linked doctors from `GET /rest/v1/doctor_patient?patient_id=eq.{userId}&select=*,doctor:user_profiles!doctor_id(full_name,email)`.
- "Add doctor" — patient enters doctor's email. App queries `GET /rest/v1/user_profiles?email=eq.{email}&role=eq.doctor` to find the doctor's UUID. If found, `POST /rest/v1/doctor_patient` with `{ patient_id: myId, doctor_id: foundDoctorId }`. RLS enforces that `patient_id = auth.uid()` and `is_doctor(doctor_id)`.
- "Remove doctor" — `DELETE /rest/v1/doctor_patient?patient_id=eq.{userId}&doctor_id=eq.{doctorId}`. RLS enforces `patient_id = auth.uid()`.

**Role-based UI:**
- If `role = 'patient'`: show ring connection, measurements, sync status, "My doctors" screen.
- If `role = 'doctor'`: show everything a patient sees, PLUS a "My patients" section (optional, or redirect to web dashboard for patient data viewing).
- If `role = 'admin'`: not expected to use the Android app. Admin functions are web/backend only.

### 5.2 Web dashboard

**Sign-in:**
- Supabase Auth via `@supabase/supabase-js` (unchanged).
- After sign-in, fetch `user_profiles` to determine role.

**Role-based views:**

| Role | What they see |
|---|---|
| `patient` | Own health data charts, "My doctors" management |
| `doctor` | Patient selector (from `doctor_patient`), selected patient's health data charts, own health data |
| `admin` | All users list, all health data, doctor-patient link management, CSV export, role management |

**Doctor view:**
- Query `GET /rest/v1/doctor_patient?doctor_id=eq.{myId}&select=*,patient:user_profiles!patient_id(full_name,email)` to get patient list.
- Select a patient → query `GET /rest/v1/ring_heart_rate?user_id=eq.{patientId}&order=device_timestamp.desc` (RLS allows because `is_linked_doctor` passes).

**Admin view:**
- Query `GET /rest/v1/user_profiles` (RLS allows because `is_admin()` passes) to list all users.
- Can view any user's data by querying `ring_*` tables with any `user_id`.
- Can change roles via backend endpoint: `PATCH /api/v1/admin/users/{userId}` with `{ role: 'doctor' }`.
- Can manage doctor-patient links via `POST/DELETE /api/v1/admin/doctor-patient`.

### 5.3 FastAPI backend (admin operations)

The backend connects via `DATABASE_URL` (direct Postgres, bypasses RLS). It handles operations that clients should not do directly:

**Existing endpoints (keep):**
- `GET /api/v1/export/csv` — CSV export for a date range (all users or filtered).
- `POST /api/v1/admin/doctor-patient` — Link a doctor to a patient.
- `DELETE /api/v1/admin/doctor-patient` — Unlink.

**New endpoints needed:**
- `GET /api/v1/admin/users` — List all users with roles.
- `PATCH /api/v1/admin/users/{userId}` — Update a user's role. Accept `{ "role": "doctor" }` or `{ "role": "admin" }` or `{ "role": "patient" }`. Only admin-authenticated requests allowed.
- `GET /api/v1/admin/doctor-patient` — List all doctor-patient links (admin view).

**Models to add/update:**
- Add `UserProfile` model to `app/models/health.py`:
```python
class UserProfile(Base):
    __tablename__ = "user_profiles"
    id: Mapped[uuid.UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True)
    role: Mapped[str] = mapped_column(String(16), nullable=False, default="patient")
    full_name: Mapped[str | None] = mapped_column(String(128), nullable=True)
    email: Mapped[str | None] = mapped_column(String(256), nullable=True)
```

---

## 6. Security rules (agent must verify)

The agent must check all code paths against these rules:

| Rule | Enforced by | Verify in |
|---|---|---|
| Users can only insert ring data where `user_id` matches their JWT | RLS INSERT policy | `SupabaseSyncAuth.kt` — confirm `user_id` comes from `authStore.userId` |
| Patients can only read their own ring data | RLS SELECT policy | Android app queries, web dashboard queries |
| Doctors can only read data for patients linked to them | `is_linked_doctor()` in RLS | Web dashboard patient data queries |
| Admins can read all data | `is_admin()` in RLS | Admin dashboard queries |
| Patients can add/remove their own doctor links | RLS INSERT/DELETE on `doctor_patient` | Android "My doctors" screen |
| Patients cannot link to a non-doctor user | `is_doctor(doctor_id)` in RLS | Android "Add doctor" flow |
| Patients cannot change anyone's role | No UPDATE policy on `user_profiles` | Verify no endpoint exposes this |
| Doctors cannot modify patient data | No UPDATE/DELETE on `ring_*` | Verify no code attempts this |
| Service role key is NEVER in Android or web code | Code review | Check `local.properties`, `BuildConfig`, `.env.local`, source files |
| JWT tokens are stored securely on Android | `AuthManager` with EncryptedSharedPreferences | Check `AuthManager.kt` |

---

## 7. Test cases to verify

| ID | Scenario | Expected |
|---|---|---|
| TC-ROLE-01 | New user signs up | Profile auto-created with `role = 'patient'` |
| TC-ROLE-02 | Patient inserts heart rate | HTTP 201, row has `user_id = auth.uid()` |
| TC-ROLE-03 | Doctor inserts own heart rate | HTTP 201 (doctors can use the ring too) |
| TC-ROLE-04 | Patient reads own data | Returns only their rows |
| TC-ROLE-05 | Doctor reads own data | Returns only their rows (ring data) |
| TC-ROLE-06 | Doctor reads linked patient data | Returns patient's rows |
| TC-ROLE-07 | Doctor reads unlinked patient data | Returns zero rows (no error) |
| TC-ROLE-08 | Admin reads any user's data | Returns all rows for that user |
| TC-ROLE-09 | Patient adds doctor link | HTTP 201, link created |
| TC-ROLE-10 | Patient tries to link to a non-doctor | HTTP 403 (RLS rejects: `is_doctor()` fails) |
| TC-ROLE-11 | Patient removes their doctor link | HTTP 200, link deleted |
| TC-ROLE-12 | Patient tries to remove someone else's link | HTTP 403 or zero rows affected |
| TC-ROLE-13 | Doctor tries to INSERT into `doctor_patient` | HTTP 403 (only patients and admins can) |
| TC-ROLE-14 | Admin changes user role to doctor | Role updated in `user_profiles` |
| TC-ROLE-15 | Client tries to UPDATE own role | No policy exists → HTTP 403 |
| TC-ROLE-16 | Same doctor, two devices (phone + web) | Same UUID, same data on both |

---

## 8. Execution order for SQL

If starting fresh, run in this order:

1. `init_db.py` (creates ring_* tables)
2. Base RLS enablement + INSERT policies (from `MIGRATION_PLAN.md` or the SQL block in this doc §4.3)
3. `doctor_patient_rls.sql` (creates `doctor_patient` table, `is_linked_doctor()` function)
4. `user_profiles_roles.sql` (creates `user_profiles`, trigger, helper functions, updated SELECT policies)

If tables and policies already exist, §4 of this document uses `DROP POLICY IF EXISTS` before every `CREATE POLICY`, so it is safe to re-run.

---

## 9. Files affected

| File | Change needed |
|---|---|
| `ring-android/.../sync/SupabaseSyncAuth.kt` | No change needed — INSERT uses `auth.uid()`, works for any role |
| `ring-android/.../util/AuthManager.kt` | Add `userRole` field to store role from `user_profiles` |
| `ring-android/.../ui/LoginActivity.kt` | After sign-in, fetch `user_profiles` and store role |
| `ring-android/` (new screen) | "My doctors" screen: list, add, remove doctor links |
| `ring-backend/app/models/health.py` | Add `UserProfile` model, verify `DoctorPatient` model exists |
| `ring-backend/app/api/` (new/updated) | Add `GET /admin/users`, `PATCH /admin/users/{id}`, `GET /admin/doctor-patient` |
| `ring-web/` | Add role-based routing: patient view, doctor view, admin view |
| `docs/user_profiles_roles.sql` | Run in Supabase SQL Editor |

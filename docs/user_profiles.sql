-- ============================================================
-- Nana Ring: Role-based access (admin, patient, doctor)
--
-- Run in Supabase SQL Editor AFTER doctor_patient_rls.sql
--
-- How it works:
--   1. Every Supabase Auth user gets a row in user_profiles
--   2. The `role` column determines what they can do
--   3. RLS policies call get_user_role() to check permissions
--   4. Admin bypasses patient/doctor restrictions
-- ============================================================


-- ── 1. Create user_profiles table ───────────────────────────

CREATE TABLE IF NOT EXISTS user_profiles (
    id          UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    role        TEXT NOT NULL DEFAULT 'patient'
                CHECK (role IN ('admin', 'patient', 'doctor')),
    full_name   TEXT,
    email       TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- RLS on user_profiles itself
ALTER TABLE user_profiles ENABLE ROW LEVEL SECURITY;

-- Everyone can read their own profile
CREATE POLICY "Users read own profile"
    ON user_profiles FOR SELECT
    USING (id = auth.uid());

-- Admins can read all profiles (for user management)
CREATE POLICY "Admins read all profiles"
    ON user_profiles FOR SELECT
    USING (
        (SELECT role FROM user_profiles WHERE id = auth.uid()) = 'admin'
    );

-- Only backend (service role) can INSERT/UPDATE/DELETE profiles
-- No client-side policy = blocked


-- ── 2. Auto-create profile on sign-up ───────────────────────
--
-- This trigger fires when a new user signs up via Supabase Auth.
-- Every new user defaults to 'patient'. Admins change roles
-- manually via the backend or SQL Editor.

CREATE OR REPLACE FUNCTION handle_new_user()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
    INSERT INTO user_profiles (id, email, role)
    VALUES (
        NEW.id,
        NEW.email,
        'patient'   -- default role; change via admin endpoint
    );
    RETURN NEW;
END;
$$;

-- Drop existing trigger if re-running
DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;

CREATE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW
    EXECUTE FUNCTION handle_new_user();


-- ── 3. Helper functions for RLS ─────────────────────────────

-- Returns the current user's role
CREATE OR REPLACE FUNCTION get_user_role()
RETURNS TEXT
LANGUAGE sql
SECURITY DEFINER
STABLE
AS $$
    SELECT role FROM user_profiles WHERE id = auth.uid();
$$;

-- Returns TRUE if current user is admin
CREATE OR REPLACE FUNCTION is_admin()
RETURNS BOOLEAN
LANGUAGE sql
SECURITY DEFINER
STABLE
AS $$
    SELECT EXISTS (
        SELECT 1 FROM user_profiles
        WHERE id = auth.uid() AND role = 'admin'
    );
$$;


-- ── 4. Updated ring_* SELECT policies ───────────────────────
--
-- Three-way access:
--   (a) Patient reads own data
--   (b) Doctor reads linked patients' data
--   (c) Admin reads ALL data
--
-- INSERT policies stay unchanged (patients insert own data only).

DROP POLICY IF EXISTS "Users and doctors read data" ON ring_heart_rate;
CREATE POLICY "Role-based read" ON ring_heart_rate
    FOR SELECT USING (
        user_id = auth.uid()              -- patient reads own
        OR is_linked_doctor(user_id)       -- doctor reads linked
        OR is_admin()                      -- admin reads all
    );

DROP POLICY IF EXISTS "Users and doctors read data" ON ring_spo2;
CREATE POLICY "Role-based read" ON ring_spo2
    FOR SELECT USING (
        user_id = auth.uid()
        OR is_linked_doctor(user_id)
        OR is_admin()
    );

DROP POLICY IF EXISTS "Users and doctors read data" ON ring_blood_pressure;
CREATE POLICY "Role-based read" ON ring_blood_pressure
    FOR SELECT USING (
        user_id = auth.uid()
        OR is_linked_doctor(user_id)
        OR is_admin()
    );

DROP POLICY IF EXISTS "Users and doctors read data" ON ring_temperature;
CREATE POLICY "Role-based read" ON ring_temperature
    FOR SELECT USING (
        user_id = auth.uid()
        OR is_linked_doctor(user_id)
        OR is_admin()
    );

DROP POLICY IF EXISTS "Users and doctors read data" ON ring_hrv;
CREATE POLICY "Role-based read" ON ring_hrv
    FOR SELECT USING (
        user_id = auth.uid()
        OR is_linked_doctor(user_id)
        OR is_admin()
    );

DROP POLICY IF EXISTS "Users and doctors read data" ON ring_stress;
CREATE POLICY "Role-based read" ON ring_stress
    FOR SELECT USING (
        user_id = auth.uid()
        OR is_linked_doctor(user_id)
        OR is_admin()
    );

DROP POLICY IF EXISTS "Users and doctors read data" ON ring_activity;
CREATE POLICY "Role-based read" ON ring_activity
    FOR SELECT USING (
        user_id = auth.uid()
        OR is_linked_doctor(user_id)
        OR is_admin()
    );

DROP POLICY IF EXISTS "Users and doctors read data" ON ring_activity_detail;
CREATE POLICY "Role-based read" ON ring_activity_detail
    FOR SELECT USING (
        user_id = auth.uid()
        OR is_linked_doctor(user_id)
        OR is_admin()
    );

DROP POLICY IF EXISTS "Users and doctors read data" ON ring_sleep;
CREATE POLICY "Role-based read" ON ring_sleep
    FOR SELECT USING (
        user_id = auth.uid()
        OR is_linked_doctor(user_id)
        OR is_admin()
    );

DROP POLICY IF EXISTS "Users and doctors read data" ON ring_sleep_stage_detail;
CREATE POLICY "Role-based read" ON ring_sleep_stage_detail
    FOR SELECT USING (
        user_id = auth.uid()
        OR is_linked_doctor(user_id)
        OR is_admin()
    );


-- ── 5. Doctor-patient table: admin can also see all links ───

DROP POLICY IF EXISTS "Doctors see own links" ON doctor_patient;
CREATE POLICY "Doctors and admins see links" ON doctor_patient
    FOR SELECT USING (
        doctor_id = auth.uid()
        OR patient_id = auth.uid()
        OR is_admin()
    );


-- ── 6. How to assign roles ──────────────────────────────────
--
-- New sign-ups default to 'patient' via the trigger.
-- To promote someone to doctor or admin, run from the backend
-- (service role) or SQL Editor:
--
--   UPDATE user_profiles SET role = 'doctor'
--   WHERE id = 'user-uuid-here';
--
--   UPDATE user_profiles SET role = 'admin'
--   WHERE id = 'your-own-uuid-here';
--
-- To find your UUID after signing up:
--
--   SELECT id, email, role FROM user_profiles;
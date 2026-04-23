-- ============================================================
-- doctor_patient linking table + doctor access RLS
--
-- Run in Supabase SQL Editor after the base RLS setup.
--
-- How it works:
--   1. Doctors and patients both sign up via Supabase Auth
--   2. A doctor_patient row links them (created by admin/backend)
--   3. RLS SELECT policy on ring_* tables checks:
--      "Is the requester this patient, OR a doctor linked to them?"
--   4. Doctors can read (not write) their patients' data
-- ============================================================


-- ── 1. Create the linking table ─────────────────────────────

CREATE TABLE IF NOT EXISTS doctor_patient (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    doctor_id   UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    patient_id  UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- One doctor-patient pair only
    UNIQUE(doctor_id, patient_id)
);

-- Index for the RLS lookup (doctor checking which patients they have)
CREATE INDEX IF NOT EXISTS idx_doctor_patient_doctor
    ON doctor_patient(doctor_id);

-- Index for reverse lookup (which doctors can see this patient)
CREATE INDEX IF NOT EXISTS idx_doctor_patient_patient
    ON doctor_patient(patient_id);

-- Enable RLS on the linking table itself
ALTER TABLE doctor_patient ENABLE ROW LEVEL SECURITY;

-- Doctors can see their own linkages
CREATE POLICY "Doctors see own links"
    ON doctor_patient
    FOR SELECT
    USING (doctor_id = auth.uid());

-- Patients can see who their doctors are
CREATE POLICY "Patients see own links"
    ON doctor_patient
    FOR SELECT
    USING (patient_id = auth.uid());

-- Only the backend (service role) can INSERT/DELETE links
-- No INSERT/DELETE policy = clients can't modify linkages


-- ── 2. Helper function for cleaner RLS policies ─────────────
--
-- Returns TRUE if the current user is a doctor linked to
-- the given patient_id. Used in every ring_* SELECT policy.

CREATE OR REPLACE FUNCTION is_linked_doctor(p_patient_id UUID)
RETURNS BOOLEAN
LANGUAGE sql
SECURITY DEFINER
STABLE
AS $$
    SELECT EXISTS (
        SELECT 1
        FROM doctor_patient
        WHERE doctor_id = auth.uid()
          AND patient_id = p_patient_id
    );
$$;


-- ── 3. Drop old SELECT policies and create new ones ─────────
--
-- The new policy: you can SELECT if:
--   (a) you ARE the patient (user_id = auth.uid()), OR
--   (b) you are a doctor linked to that patient

-- ring_heart_rate
DROP POLICY IF EXISTS "Users read own data" ON ring_heart_rate;
CREATE POLICY "Users and doctors read data" ON ring_heart_rate
    FOR SELECT USING (
        user_id = auth.uid()
        OR is_linked_doctor(user_id)
    );

-- ring_spo2
DROP POLICY IF EXISTS "Users read own data" ON ring_spo2;
CREATE POLICY "Users and doctors read data" ON ring_spo2
    FOR SELECT USING (
        user_id = auth.uid()
        OR is_linked_doctor(user_id)
    );

-- ring_blood_pressure
DROP POLICY IF EXISTS "Users read own data" ON ring_blood_pressure;
CREATE POLICY "Users and doctors read data" ON ring_blood_pressure
    FOR SELECT USING (
        user_id = auth.uid()
        OR is_linked_doctor(user_id)
    );

-- ring_temperature
DROP POLICY IF EXISTS "Users read own data" ON ring_temperature;
CREATE POLICY "Users and doctors read data" ON ring_temperature
    FOR SELECT USING (
        user_id = auth.uid()
        OR is_linked_doctor(user_id)
    );

-- ring_hrv
DROP POLICY IF EXISTS "Users read own data" ON ring_hrv;
CREATE POLICY "Users and doctors read data" ON ring_hrv
    FOR SELECT USING (
        user_id = auth.uid()
        OR is_linked_doctor(user_id)
    );

-- ring_stress
DROP POLICY IF EXISTS "Users read own data" ON ring_stress;
CREATE POLICY "Users and doctors read data" ON ring_stress
    FOR SELECT USING (
        user_id = auth.uid()
        OR is_linked_doctor(user_id)
    );

-- ring_activity
DROP POLICY IF EXISTS "Users read own data" ON ring_activity;
CREATE POLICY "Users and doctors read data" ON ring_activity
    FOR SELECT USING (
        user_id = auth.uid()
        OR is_linked_doctor(user_id)
    );

-- ring_activity_detail
DROP POLICY IF EXISTS "Users read own data" ON ring_activity_detail;
CREATE POLICY "Users and doctors read data" ON ring_activity_detail
    FOR SELECT USING (
        user_id = auth.uid()
        OR is_linked_doctor(user_id)
    );

-- ring_sleep
DROP POLICY IF EXISTS "Users read own data" ON ring_sleep;
CREATE POLICY "Users and doctors read data" ON ring_sleep
    FOR SELECT USING (
        user_id = auth.uid()
        OR is_linked_doctor(user_id)
    );

-- ring_sleep_stage_detail
DROP POLICY IF EXISTS "Users read own data" ON ring_sleep_stage_detail;
CREATE POLICY "Users and doctors read data" ON ring_sleep_stage_detail
    FOR SELECT USING (
        user_id = auth.uid()
        OR is_linked_doctor(user_id)
    );


-- ── 4. INSERT policies remain unchanged ─────────────────────
-- Patients can only insert their own data (user_id = auth.uid())
-- Doctors cannot insert data into patient tables
-- These were created in the base RLS setup and don't need changes


-- ── 5. Admin: Link a doctor to a patient ─────────────────────
--
-- Run from your backend (service role) or Supabase SQL Editor:
--
--   INSERT INTO doctor_patient (doctor_id, patient_id)
--   VALUES (
--       'dddddddd-0000-0000-0000-000000000001',  -- doctor's auth UUID
--       '11111111-0000-0000-0000-000000000001'    -- patient's auth UUID
--   );
--
-- To unlink:
--
--   DELETE FROM doctor_patient
--   WHERE doctor_id = 'dddddddd-...'
--     AND patient_id = '11111111-...';

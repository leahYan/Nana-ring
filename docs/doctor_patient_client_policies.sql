-- ============================================================
-- Nana Ring: Client-side doctor_patient policies
--
-- Run in Supabase SQL Editor AFTER user_profiles.sql
--
-- Adds:
--   1. is_doctor() helper function
--   2. "Users read doctor profiles" policy on user_profiles
--   3. Patient INSERT / DELETE on doctor_patient
--   4. Admin INSERT / DELETE on doctor_patient
-- ============================================================


-- ── 1. is_doctor() ──────────────────────────────────────────

CREATE OR REPLACE FUNCTION is_doctor(p_user_id UUID)
RETURNS BOOLEAN
LANGUAGE sql
SECURITY DEFINER
STABLE
AS $$
    SELECT EXISTS (
        SELECT 1 FROM user_profiles
        WHERE id = p_user_id AND role = 'doctor'
    );
$$;


-- ── 2. user_profiles: any authenticated user can read doctor rows ──

DROP POLICY IF EXISTS "Users read doctor profiles" ON user_profiles;
CREATE POLICY "Users read doctor profiles"
    ON user_profiles FOR SELECT
    USING (role = 'doctor');


-- ── 3. doctor_patient: patient can add their own doctor link ──

DROP POLICY IF EXISTS "Patients add doctor link" ON doctor_patient;
CREATE POLICY "Patients add doctor link"
    ON doctor_patient FOR INSERT
    WITH CHECK (
        patient_id = auth.uid()
        AND is_doctor(doctor_id)
    );


-- ── 4. doctor_patient: patient can remove their own doctor link ──

DROP POLICY IF EXISTS "Patients remove doctor link" ON doctor_patient;
CREATE POLICY "Patients remove doctor link"
    ON doctor_patient FOR DELETE
    USING (patient_id = auth.uid());


-- ── 5. doctor_patient: admin can insert / delete any link ────

DROP POLICY IF EXISTS "Admins insert links" ON doctor_patient;
CREATE POLICY "Admins insert links"
    ON doctor_patient FOR INSERT
    WITH CHECK (is_admin());

DROP POLICY IF EXISTS "Admins delete links" ON doctor_patient;
CREATE POLICY "Admins delete links"
    ON doctor_patient FOR DELETE
    USING (is_admin());

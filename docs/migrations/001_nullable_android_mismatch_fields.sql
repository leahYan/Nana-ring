-- Migration 001: make columns nullable where Android sends wrong field names
--
-- Root cause: SupabaseSyncAuth.kt sends JSON keys that don't match the column
-- names below. PostgREST silently drops unknown keys, leaving these columns
-- NULL and causing NOT NULL constraint violations on every affected insert.
--
-- Tracking: see docs/android_supabase_field_map.csv rows with status = BUG
--
-- Run in Supabase SQL Editor (or psql) against the live database.
-- Safe to re-run — ALTER COLUMN DROP NOT NULL is idempotent when the
-- constraint is already absent.

-- ring_activity
-- Android sends: walk_distance_meters  →  column: distance_meters
ALTER TABLE ring_activity ALTER COLUMN distance_meters DROP NOT NULL;

-- Android sends: sport_duration_seconds  →  column: active_duration_seconds
ALTER TABLE ring_activity ALTER COLUMN active_duration_seconds DROP NOT NULL;

-- ring_sleep
-- Android sends: days_ago  →  column: day_offset
ALTER TABLE ring_sleep ALTER COLUMN day_offset DROP NOT NULL;

-- ring_sleep_stage_detail
-- Android sends: "stage" (string label)  →  column: stage_label (wrong key, dropped)
ALTER TABLE ring_sleep_stage_detail ALTER COLUMN stage_label DROP NOT NULL;

-- Android never sends stage_type (the int); only derives a label string
ALTER TABLE ring_sleep_stage_detail ALTER COLUMN stage_type DROP NOT NULL;

-- Android never sends stage_index; no loop counter tracked in insertSleepSessions()
ALTER TABLE ring_sleep_stage_detail ALTER COLUMN stage_index DROP NOT NULL;

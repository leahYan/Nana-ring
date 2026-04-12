#!/usr/bin/env python3
"""
init_db.py — Create all Ring Health tables in Supabase.

Run once after pointing DATABASE_URL at the live Supabase pooler:

    python init_db.py

Prerequisites
-------------
1. .env must contain a working DATABASE_URL (connection pooler, NOT direct):
       postgresql://postgres.<ref>:<password>@aws-0-<region>.pooler.supabase.com:6543/postgres
2. Dependencies installed:
       pip install -r requirements.txt

Tables created (idempotent — safe to re-run, existing tables are untouched)
----------------------------------------------------------------------------
  ring_heart_rate
  ring_spo2
  ring_blood_pressure
  ring_temperature
  ring_hrv
  ring_stress
  ring_activity
  ring_activity_detail
  ring_sleep
  ring_sleep_stage_detail
"""

import sys
from pathlib import Path

# Allow running as `python init_db.py` from the ring-backend directory
sys.path.insert(0, str(Path(__file__).parent))

from app.core.config import settings  # noqa: E402
from app.database import engine, Base  # noqa: E402

# Import every model so SQLAlchemy registers it with Base.metadata.
# Without these imports the tables would not be created even though
# Base.metadata.create_all() is called.
from app.models.health import (  # noqa: E402, F401
    HeartRate,
    SpO2,
    BloodPressure,
    Temperature,
    HRV,
    Stress,
    Activity,
    ActivityDetail,
    Sleep,
    SleepStageDetail,
)

EXPECTED_TABLES = [
    "ring_heart_rate",
    "ring_spo2",
    "ring_blood_pressure",
    "ring_temperature",
    "ring_hrv",
    "ring_stress",
    "ring_activity",
    "ring_activity_detail",
    "ring_sleep",
    "ring_sleep_stage_detail",
]


def main():
    # Guard against an unfilled placeholder
    if "PASTE" in settings.DATABASE_URL or "YOUR" in settings.DATABASE_URL.upper():
        print(
            "\nERROR: DATABASE_URL in .env is still a placeholder.\n"
            "Go to Supabase → Settings → Database → Connection pooling\n"
            "and paste the Transaction-mode URI (port 6543) into .env first.\n"
        )
        sys.exit(1)

    print(f"\nConnecting to: {settings.DATABASE_URL.split('@')[-1]}")  # hide password
    print("Creating tables ...\n")

    try:
        Base.metadata.create_all(bind=engine)
    except Exception as exc:
        print(f"ERROR: {exc}")
        sys.exit(1)

    # Confirm by inspecting what actually exists
    from sqlalchemy import inspect
    inspector = inspect(engine)
    existing = inspector.get_table_names()

    all_ok = True
    for table in EXPECTED_TABLES:
        status = "OK" if table in existing else "MISSING"
        if status == "MISSING":
            all_ok = False
        print(f"  {status}  {table}")

    print()
    if all_ok:
        print("All tables created successfully. Supabase is ready.")
    else:
        print("Some tables are missing — check the errors above.")
        sys.exit(1)


if __name__ == "__main__":
    main()

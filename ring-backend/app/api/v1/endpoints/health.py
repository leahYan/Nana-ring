"""
Health data endpoints.

POST /api/v1/data/batch
  - Requires a valid Bearer JWT.
  - user_id is extracted from the token; Android never sends it.
  - Multi-tenant: every inserted row is hard-linked to the caller's user_id.
  - Idempotency: if a record arrives with a sync_id that already exists for
    this user, the record is skipped (not duplicated).
  - Returns a 207 Multi-Status summary so Android can confirm counts.

GET /api/v1/health
  - Public liveness probe (no auth required).
"""

import time
import uuid

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from app.core.deps import CurrentUser, get_current_user, get_db
from app.models.health import (
    Activity,
    ActivityDetail,
    BloodPressure,
    HeartRate,
    HRV,
    Sleep,
    SleepStageDetail,
    SpO2,
    Stress,
    Temperature,
)
from app.schemas.health import (
    BatchUploadRequest,
    BatchUploadResponse,
)

router = APIRouter(tags=["health"])

# Maps SDK stage_type int → human-readable label stored in ring_sleep_stage_detail
_STAGE_LABEL: dict[int, str] = {
    0: "none",
    1: "removed",
    2: "light",
    3: "deep",
    4: "rem",
    5: "awake",
}


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _sync_id_exists(db: Session, model, user_id: uuid.UUID, sync_id: uuid.UUID) -> bool:
    """Return True if this (sync_id, user_id) pair is already in the table."""
    return (
        db.query(model.id)
        .filter(model.sync_id == sync_id, model.user_id == user_id)
        .first()
        is not None
    )


def _build_row(record_dict: dict, user_id: uuid.UUID) -> dict:
    """
    Inject the three server-side fields into a model_dump() dict.
    The caller must have already stripped 'stages' from sleep records.
    """
    record_dict["id"] = uuid.uuid4()
    record_dict["user_id"] = user_id
    record_dict["server_timestamp"] = int(time.time() * 1000)
    return record_dict


def _insert_simple(
    db: Session,
    model,
    records,
    user_id: uuid.UUID,
    results: dict,
):
    """
    Insert a list of non-sleep metric records with sync_id deduplication.
    Updates results["inserted"] / results["skipped"] / results["errors"] in place.
    """
    table = model.__tablename__
    for rec in records:
        try:
            row = rec.model_dump()
            sync_id = row.get("sync_id")
            if sync_id and _sync_id_exists(db, model, user_id, sync_id):
                results["skipped"] += 1
                continue
            db.add(model(**_build_row(row, user_id)))
            results["inserted"] += 1
        except Exception as exc:
            results["errors"].append(f"{table}: {exc}")


# ---------------------------------------------------------------------------
# Routes
# ---------------------------------------------------------------------------

@router.get("/health", include_in_schema=True)
async def health_check():
    """Public liveness probe — used by Docker HEALTHCHECK and nginx upstream."""
    return {"status": "Ring Health API is live"}


@router.post(
    "/data/batch",
    response_model=BatchUploadResponse,
    status_code=status.HTTP_207_MULTI_STATUS,
    summary="Batch-upload health metrics from the Android app",
)
def batch_upload(
    payload: BatchUploadRequest,
    current_user: CurrentUser = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    """
    Accepts up to 9 metric lists in a single JSON body.  Android sends whichever
    tables have new data; empty lists are fine and ignored.

    Multi-tenancy contract
    ----------------------
    * `user_id` comes **only** from the verified JWT — never from the request body.
    * Tester tokens may only write/read their own data.
    * Admin tokens (is_admin=true) can query any user's data on GET endpoints
      (not relevant here but enforced in future dashboard routes).

    Idempotency
    -----------
    If Android retries a failed sync, records that already have a matching
    (sync_id, user_id) pair in the DB are silently skipped.  Records without a
    sync_id are always inserted (no deduplication possible).

    Returns
    -------
    207 with a summary of received / inserted / skipped / errors.
    The caller can check `errors` to detect partial failures without a 4xx/5xx.
    """
    user_id = current_user.user_id
    results: dict = {"inserted": 0, "skipped": 0, "errors": []}

    # --- Simple metric tables (no child rows) --------------------------------
    _insert_simple(db, HeartRate,     payload.heart_rate,     user_id, results)
    _insert_simple(db, SpO2,          payload.spo2,           user_id, results)
    _insert_simple(db, BloodPressure, payload.blood_pressure, user_id, results)
    _insert_simple(db, Temperature,   payload.temperature,    user_id, results)
    _insert_simple(db, HRV,           payload.hrv,            user_id, results)
    _insert_simple(db, Stress,        payload.stress,         user_id, results)
    _insert_simple(db, Activity,      payload.activity,       user_id, results)
    _insert_simple(db, ActivityDetail,payload.activity_detail,user_id, results)

    # --- Sleep (parent + embedded stage children) ----------------------------
    for sleep_rec in payload.sleep:
        try:
            stages = sleep_rec.stages                       # extract before dump
            row = sleep_rec.model_dump(exclude={"stages"})

            sync_id = row.get("sync_id")
            if sync_id and _sync_id_exists(db, Sleep, user_id, sync_id):
                results["skipped"] += 1
                continue

            # Server derives total_minutes from the four stage buckets
            row["total_minutes"] = (
                row["deep_minutes"]
                + row["light_minutes"]
                + row["rem_minutes"]
                + row["awake_minutes"]
            )

            sleep_id = uuid.uuid4()
            row["id"] = sleep_id
            row["user_id"] = user_id
            row["server_timestamp"] = int(time.time() * 1000)
            db.add(Sleep(**row))
            results["inserted"] += 1

            # Insert each stage segment as a child row
            for stage in stages:
                stage_row = stage.model_dump()
                stage_row["id"] = uuid.uuid4()
                stage_row["sleep_id"] = sleep_id
                stage_row["user_id"] = user_id
                stage_row["stage_label"] = _STAGE_LABEL.get(
                    stage_row["stage_type"], "unknown"
                )
                db.add(SleepStageDetail(**stage_row))

        except Exception as exc:
            results["errors"].append(f"ring_sleep: {exc}")

    # --- Commit --------------------------------------------------------------
    try:
        db.commit()
    except Exception as exc:
        db.rollback()
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Database commit failed: {exc}",
        )

    received = (
        len(payload.heart_rate)
        + len(payload.spo2)
        + len(payload.blood_pressure)
        + len(payload.temperature)
        + len(payload.hrv)
        + len(payload.stress)
        + len(payload.activity)
        + len(payload.activity_detail)
        + len(payload.sleep)
    )

    return BatchUploadResponse(
        received=received,
        inserted=results["inserted"],
        skipped=results["skipped"],
        errors=results["errors"],
    )

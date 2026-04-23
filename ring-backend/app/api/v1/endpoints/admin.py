"""
Admin endpoints for doctor-patient link management.

POST   /admin/doctor-patient   — Link a doctor to a patient.
DELETE /admin/doctor-patient   — Unlink a doctor from a patient.

These endpoints connect via DATABASE_URL (direct Postgres, bypasses RLS) and
require a valid admin JWT (is_admin=True in the token payload).  The anon key
cannot call these endpoints — clients (patients, doctors) cannot modify linkages
(TC-BE-08).
"""

import uuid

from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel
from sqlalchemy.orm import Session

from app.core.deps import CurrentUser, get_current_user, get_db
from app.models.health import DoctorPatient

router = APIRouter(prefix="/admin", tags=["admin"])


# ---------------------------------------------------------------------------
# Schemas
# ---------------------------------------------------------------------------

class DoctorPatientLink(BaseModel):
    doctor_id: uuid.UUID
    patient_id: uuid.UUID


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _require_admin(current_user: CurrentUser) -> CurrentUser:
    if not current_user.is_admin:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Admin access required",
        )
    return current_user


def _get_link(db: Session, doctor_id: uuid.UUID, patient_id: uuid.UUID) -> DoctorPatient | None:
    return (
        db.query(DoctorPatient)
        .filter(
            DoctorPatient.doctor_id  == doctor_id,
            DoctorPatient.patient_id == patient_id,
        )
        .first()
    )


# ---------------------------------------------------------------------------
# Routes
# ---------------------------------------------------------------------------

@router.post(
    "/doctor-patient",
    status_code=status.HTTP_201_CREATED,
    summary="Link a doctor to a patient",
)
def link_doctor_patient(
    body: DoctorPatientLink,
    current_user: CurrentUser = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    _require_admin(current_user)

    if _get_link(db, body.doctor_id, body.patient_id):
        return {"detail": "Link already exists"}

    db.add(DoctorPatient(
        id         = uuid.uuid4(),
        doctor_id  = body.doctor_id,
        patient_id = body.patient_id,
    ))
    try:
        db.commit()
    except Exception as exc:
        db.rollback()
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Database error: {exc}",
        )

    return {"detail": "Linked", "doctor_id": str(body.doctor_id), "patient_id": str(body.patient_id)}


@router.delete(
    "/doctor-patient",
    status_code=status.HTTP_200_OK,
    summary="Unlink a doctor from a patient",
)
def unlink_doctor_patient(
    body: DoctorPatientLink,
    current_user: CurrentUser = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    _require_admin(current_user)

    link = _get_link(db, body.doctor_id, body.patient_id)
    if not link:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Link not found",
        )

    db.delete(link)
    try:
        db.commit()
    except Exception as exc:
        db.rollback()
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Database error: {exc}",
        )

    return {"detail": "Unlinked", "doctor_id": str(body.doctor_id), "patient_id": str(body.patient_id)}

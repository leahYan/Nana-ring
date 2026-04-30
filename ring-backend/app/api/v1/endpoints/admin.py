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
from datetime import datetime

from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel
from sqlalchemy.orm import Session

from app.core.deps import CurrentUser, get_current_user, get_db
from app.models.health import DoctorPatient, UserProfile

router = APIRouter(prefix="/admin", tags=["admin"])


# ---------------------------------------------------------------------------
# Schemas
# ---------------------------------------------------------------------------

class DoctorPatientLink(BaseModel):
    doctor_id: uuid.UUID
    patient_id: uuid.UUID


class UserProfileOut(BaseModel):
    id: uuid.UUID
    role: str
    full_name: str | None
    email: str | None
    created_at: datetime

    model_config = {"from_attributes": True}


class UserRoleUpdate(BaseModel):
    role: str


class DoctorPatientLinkOut(BaseModel):
    id: uuid.UUID
    doctor_id: uuid.UUID
    patient_id: uuid.UUID
    created_at: datetime

    model_config = {"from_attributes": True}


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


# ---------------------------------------------------------------------------
# User management
# ---------------------------------------------------------------------------

_VALID_ROLES = {"patient", "doctor", "admin"}


@router.get(
    "/users",
    response_model=list[UserProfileOut],
    summary="List all users with their roles",
)
def list_users(
    current_user: CurrentUser = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    _require_admin(current_user)
    return db.query(UserProfile).all()


@router.patch(
    "/users/{user_id}",
    summary="Update a user's role",
)
def update_user_role(
    user_id: uuid.UUID,
    body: UserRoleUpdate,
    current_user: CurrentUser = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    _require_admin(current_user)

    if body.role not in _VALID_ROLES:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail=f"role must be one of {sorted(_VALID_ROLES)}",
        )

    profile = db.query(UserProfile).filter(UserProfile.id == user_id).first()
    if not profile:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="User not found")

    profile.role = body.role
    try:
        db.commit()
    except Exception as exc:
        db.rollback()
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Database error: {exc}",
        )

    return {"detail": "Role updated", "user_id": str(user_id), "role": body.role}


@router.get(
    "/doctor-patient",
    response_model=list[DoctorPatientLinkOut],
    summary="List all doctor-patient links",
)
def list_doctor_patient_links(
    current_user: CurrentUser = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    _require_admin(current_user)
    return db.query(DoctorPatient).all()

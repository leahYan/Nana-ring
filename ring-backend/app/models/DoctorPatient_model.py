"""
Add this class to app/models/health.py (after the existing models).

This model matches the doctor_patient table created by doctor_patient_rls.sql.
It is only used by the FastAPI backend for admin operations like linking
a doctor to a patient — the table is also created via the SQL script in
Supabase, so this model is for ORM convenience, not for init_db.py.
"""

# Add this import at the top of health.py:
# from sqlalchemy import DateTime
# from sqlalchemy.sql import func

class DoctorPatient(Base):
    """
    Links a doctor (Supabase Auth user) to a patient (Supabase Auth user).
    RLS uses this table to decide if a doctor can SELECT a patient's ring data.

    Only the backend (service role / direct Postgres) can INSERT or DELETE rows.
    """
    __tablename__ = "doctor_patient"
    __table_args__ = (
        UniqueConstraint("doctor_id", "patient_id", name="uq_doctor_patient"),
    )

    id: Mapped[uuid.UUID] = mapped_column(
        Uuid(as_uuid=True), primary_key=True, default=uuid.uuid4
    )
    doctor_id: Mapped[uuid.UUID] = mapped_column(
        Uuid(as_uuid=True),
        # ForeignKey("auth.users.id") — can't reference Supabase auth schema
        # from SQLAlchemy, but the SQL script handles this with a real FK.
        nullable=False,
        index=True,
    )
    patient_id: Mapped[uuid.UUID] = mapped_column(
        Uuid(as_uuid=True),
        nullable=False,
        index=True,
    )
    # created_at uses server default so the backend doesn't need to set it
    # If you add this column, import DateTime and func at the top of health.py:
    #
    # created_at: Mapped[datetime] = mapped_column(
    #     DateTime(timezone=True),
    #     nullable=False,
    #     server_default=func.now(),
    # )
